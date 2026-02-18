package com.javaclaw.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.bus.InboundMessage;
import com.javaclaw.bus.MessageBus;
import com.javaclaw.bus.OutboundMessage;
import com.javaclaw.config.ExecToolConfig;
import com.javaclaw.config.MCPServerConfig;
import com.javaclaw.cron.CronService;
import com.javaclaw.providers.LLMProvider;
import com.javaclaw.providers.ToolCallRequest;
import com.javaclaw.session.Session;
import com.javaclaw.session.SessionManager;
import com.javaclaw.agent.tools.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Agent 主循环：消费 inbound、建会话/上下文、调 LLM、执行 tool_call、写回复/会话；支持 processDirect（CLI/Cron/Heartbeat）。
 */
public class AgentLoop {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REFLECT_USER_MSG = "Reflect on the results and decide next steps.";

    private final MessageBus bus;
    private final LLMProvider provider;
    private final Path workspace;
    private final String model;
    private final int maxIterations;
    private final double temperature;
    private final int maxTokens;
    private final int memoryWindow;
    private final ExecToolConfig execConfig;
    private final boolean restrictToWorkspace;
    private final SessionManager sessionManager;
    private final ToolRegistry toolRegistry;
    private final ContextBuilder contextBuilder;
    private final MemoryStore memoryStore;
    private final SkillsLoader skillsLoader;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private CronService cronService;
    private Map<String, MCPServerConfig> mcpServers;

    public AgentLoop(MessageBus bus,
                    LLMProvider provider,
                    Path workspace,
                    String model,
                    int maxIterations,
                    double temperature,
                    int maxTokens,
                    int memoryWindow,
                    String braveApiKey,
                    ExecToolConfig execConfig,
                    CronService cronService,
                    boolean restrictToWorkspace,
                    SessionManager sessionManager,
                    Map<String, MCPServerConfig> mcpServers) {
        this.bus = bus;
        this.provider = provider;
        this.workspace = workspace != null ? workspace : java.nio.file.Paths.get(".");
        this.model = model != null && !model.isEmpty() ? model : provider.getDefaultModel();
        this.maxIterations = maxIterations > 0 ? maxIterations : 10;
        this.temperature = temperature >= 0 ? temperature : 0.7;
        this.maxTokens = maxTokens > 0 ? maxTokens : 4096;
        this.memoryWindow = memoryWindow > 0 ? memoryWindow : 20;
        this.execConfig = execConfig;
        this.restrictToWorkspace = restrictToWorkspace;
        this.sessionManager = sessionManager;
        this.cronService = cronService;
        this.mcpServers = mcpServers != null ? mcpServers : Collections.emptyMap();
        this.memoryStore = new MemoryStore(this.workspace);
        this.skillsLoader = new SkillsLoader(this.workspace, null);
        this.contextBuilder = new ContextBuilder(this.workspace, null, memoryStore, skillsLoader);
        this.toolRegistry = new ToolRegistry();
        registerDefaultTools();
    }

    /** 注册默认工具：read_file、write_file、list_dir、exec、message */
    private void registerDefaultTools() {
        toolRegistry.register(new ReadFileTool(workspace, restrictToWorkspace));
        toolRegistry.register(new WriteFileTool(workspace, restrictToWorkspace));
        toolRegistry.register(new ListDirTool(workspace, restrictToWorkspace));
        toolRegistry.register(new ExecTool(execConfig));
        toolRegistry.register(new MessageTool(bus));
    }

    /** 主循环：不断 consumeInbound，processMessage，publishOutbound */
    public void run() {
        while (running.get()) {
            try {
                InboundMessage msg = bus.consumeInbound();
                OutboundMessage response = processMessage(msg);
                if (response != null) {
                    bus.publishOutbound(response);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** 处理单条入站消息：session、命令、buildMessages、runAgentLoop、写 session、返回 OutboundMessage */
    public OutboundMessage processMessage(InboundMessage msg) {
        return processMessage(msg, null, null);
    }

    public OutboundMessage processMessage(InboundMessage msg, String sessionKeyOverride) {
        return processMessage(msg, sessionKeyOverride, null);
    }

    /**
     * 同上；streamConsumer 非 null 时，将 LLM 的 content 增量回调（流式输出）。仅最后一轮纯文本回复会流式。
     */
    public OutboundMessage processMessage(InboundMessage msg, String sessionKeyOverride, Consumer<String> streamConsumer) {
        String sessionKey = sessionKeyOverride != null ? sessionKeyOverride : msg.getSessionKey();
        String channel = msg.getChannel();
        String chatId = msg.getChatId();
        String content = msg.getContent() != null ? msg.getContent() : "";

        if ("system".equals(channel)) {
            return processSystemMessage(msg);
        }

        Session session = sessionManager.getOrCreate(sessionKey);

        if (content.trim().equals("/new")) {
            session.clear();
            sessionManager.save(session);
            OutboundMessage out = new OutboundMessage(channel, chatId, "New session started.");
            out.setMetadata(msg.getMetadata() != null ? msg.getMetadata() : java.util.Collections.emptyMap());
            return out;
        }
        if (content.trim().equals("/help")) {
            OutboundMessage out = new OutboundMessage(channel, chatId,
                    "Commands: /new (clear session), /help (this message).");
            out.setMetadata(msg.getMetadata() != null ? msg.getMetadata() : java.util.Collections.emptyMap());
            return out;
        }

        if (session.getMessages().size() > memoryWindow) {
            Session s = session;
            java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newSingleThreadExecutor();
            exec.submit(() -> consolidateMemory(s, false));
            exec.shutdown();
        }

        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("channel", channel);
        requestContext.put("chatId", chatId);
        requestContext.put("metadata", msg.getMetadata() != null ? msg.getMetadata() : Collections.emptyMap());
        List<Map<String, Object>> history = session.getHistory(memoryWindow);
        List<String> skillNames = skillsLoader.getAlwaysSkills();
        List<Map<String, Object>> initialMessages = contextBuilder.buildMessages(
                history, content, skillNames, msg.getMedia(), channel, chatId);
        RunResult result = runAgentLoop(initialMessages, streamConsumer, requestContext);
        session.addMessage("user", content, null);
        session.addMessage("assistant", result.getContent(), null);
        sessionManager.save(session);
        OutboundMessage out = new OutboundMessage(channel, chatId, result.getContent());
        out.setMetadata(msg.getMetadata() != null ? msg.getMetadata() : java.util.Collections.emptyMap());
        return out;
    }

    private OutboundMessage processSystemMessage(InboundMessage msg) {
        return processMessage(msg, msg.getSessionKey());
    }

    /** 多轮 chat + tool 直到无 tool_calls；返回最终回复与使用过的工具名。streamConsumer 非 null 时对最后一轮纯文本回复做流式回调。requestContext 可为 null，供 message 等工具使用。 */
    public RunResult runAgentLoop(List<Map<String, Object>> initialMessages) {
        return runAgentLoop(initialMessages, null, null);
    }

    public RunResult runAgentLoop(List<Map<String, Object>> initialMessages, Consumer<String> streamConsumer) {
        return runAgentLoop(initialMessages, streamConsumer, null);
    }

    public RunResult runAgentLoop(List<Map<String, Object>> initialMessages, Consumer<String> streamConsumer, Map<String, Object> requestContext) {
        List<Map<String, Object>> messages = new ArrayList<>(initialMessages);
        List<String> toolsUsed = new ArrayList<>();
        int iter = 0;
        while (iter < maxIterations) {
            iter++;
            com.javaclaw.providers.LLMResponse response = provider.chat(
                    messages,
                    toolRegistry.getDefinitions(),
                    model,
                    maxTokens,
                    temperature,
                    streamConsumer);
            if (response.hasToolCalls()) {
                List<Map<String, Object>> toolCallsForMessage = new ArrayList<>();
                for (ToolCallRequest tc : response.getToolCalls()) {
                    Map<String, Object> fn = new HashMap<>();
                    fn.put("id", tc.getId());
                    fn.put("type", "function");
                    Map<String, Object> f = new HashMap<>();
                    f.put("name", tc.getName());
                    try {
                        f.put("arguments", MAPPER.writeValueAsString(tc.getArguments()));
                    } catch (Exception e) {
                        f.put("arguments", "{}");
                    }
                    fn.put("function", f);
                    toolCallsForMessage.add(fn);
                }
                contextBuilder.addAssistantMessage(messages,
                        response.getContent(),
                        toolCallsForMessage,
                        response.getReasoningContent());
                for (ToolCallRequest tc : response.getToolCalls()) {
                    Map<String, Object> params = new HashMap<>(tc.getArguments() != null ? tc.getArguments() : Collections.<String, Object>emptyMap());
                    if (requestContext != null) {
                        params.putAll(requestContext);
                    }
                    String result = toolRegistry.execute(tc.getName(), params);
                    toolsUsed.add(tc.getName());
                    contextBuilder.addToolResult(messages, tc.getId(), tc.getName(), result);
                }
                Map<String, Object> userReflect = new HashMap<>();
                userReflect.put("role", "user");
                userReflect.put("content", REFLECT_USER_MSG);
                messages.add(userReflect);
            } else {
                String finalContent = response.getContent() != null ? response.getContent() : "";
                return new RunResult(finalContent, toolsUsed);
            }
        }
        return new RunResult("[Max tool iterations reached]", toolsUsed);
    }

    /** 直接处理一条用户消息（不经过总线），返回 Agent 回复文本。用于 CLI、Cron、Heartbeat。 */
    public String processDirect(String content, String sessionKey, String channel, String chatId) {
        return processDirect(content, sessionKey, channel, chatId, null);
    }

    /** 带流式回调：streamConsumer 非 null 时，回复内容会增量回调，控制台可边收边打。 */
    public String processDirect(String content, String sessionKey, String channel, String chatId, Consumer<String> streamConsumer) {
        InboundMessage msg = new InboundMessage(channel, "user", chatId, content);
        OutboundMessage out = processMessage(msg, sessionKey, streamConsumer);
        return out != null ? out.getContent() : "";
    }

    /** 重载：默认 sessionKey=cli:direct, channel=cli, chatId=direct */
    public String processDirect(String content) {
        return processDirect(content, "cli:direct", "cli", "direct");
    }

    /** 重载：带流式回调，用于 CLI 控制台逐字输出 */
    public String processDirect(String content, Consumer<String> streamConsumer) {
        return processDirect(content, "cli:direct", "cli", "direct", streamConsumer);
    }

    public void stop() {
        running.set(false);
    }

    /** 关闭 MCP 连接（占位） */
    public void closeMcp() {
        // no-op for now
    }

    /** 懒连接 MCP（占位） */
    public void connectMcp() {
        // no-op for now
    }

    /** 记忆合并：将会话片段压缩进 MEMORY.md / HISTORY.md */
    public void consolidateMemory(Session session, boolean archiveAll) {
        if (session == null) {
            return;
        }
        MemoryStore mem = new MemoryStore(workspace);
        String existing = mem.readLongTerm();
        StringBuilder toMerge = new StringBuilder();
        for (Map<String, Object> m : session.getHistory(memoryWindow * 2)) {
            Object role = m.get("role");
            Object content = m.get("content");
            if (role != null && content != null) {
                toMerge.append(role).append(": ").append(content).append("\n");
            }
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", "You are a memory consolidation assistant. Reply with JSON only: {\"historyEntry\": \"...\", \"memoryUpdate\": \"...\"}");
        messages.add(sys);
        Map<String, Object> u = new HashMap<>();
        u.put("role", "user");
        u.put("content", "Summarize the following conversation into a brief memory update.\n\nCurrent memory:\n" + existing + "\n\nConversation:\n" + toMerge);
        messages.add(u);
        com.javaclaw.providers.LLMResponse response = provider.chat(messages, Collections.<Map<String, Object>>emptyList(), model, 1024, 0.3);
        String text = response.getContent();
        if (text != null && text.trim().startsWith("{")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = MAPPER.readValue(text, Map.class);
                Object he = parsed.get("historyEntry");
                Object mu = parsed.get("memoryUpdate");
                if (he != null) {
                    mem.appendHistory(he.toString());
                }
                if (mu != null) {
                    mem.writeLongTerm(existing + "\n" + mu.toString());
                }
            } catch (Exception e) {
                mem.appendHistory("consolidate failed: " + text);
            }
        }
        if (archiveAll) {
            session.clear();
        } else {
            session.setLastConsolidated(java.time.Instant.now());
        }
        sessionManager.save(session);
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }
}
