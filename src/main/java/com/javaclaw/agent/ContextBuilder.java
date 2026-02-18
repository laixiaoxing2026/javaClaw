package com.javaclaw.agent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装发给 LLM 的 system prompt 与消息列表：身份、bootstrap 文件、记忆、技能；buildMessages、addToolResult、addAssistantMessage。
 */
public class ContextBuilder {

    private static final String[] BOOTSTRAP_FILES = {"AGENTS.md", "SOUL.md", "USER.md", "TOOLS.md", "IDENTITY.md"};

    private final Path workspace;
    private final MemoryStore memory;
    private final SkillsLoader skillsLoader;

    public ContextBuilder(Path workspace) {
        this.workspace = workspace;
        this.memory = new MemoryStore(workspace);
        this.skillsLoader = new SkillsLoader(workspace, null);
    }

    public ContextBuilder(Path workspace, Path builtinSkillsDir, MemoryStore memory, SkillsLoader skillsLoader) {
        this.workspace = workspace;
        this.memory = memory != null ? memory : new MemoryStore(workspace);
        this.skillsLoader = skillsLoader != null ? skillsLoader : new SkillsLoader(workspace, builtinSkillsDir);
    }

    /** 组装系统提示：身份、bootstrap 文件、记忆、技能（常驻 + 摘要） */
    public String buildSystemPrompt(List<String> skillNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current time: ").append(ZonedDateTime.now()).append("\n\n");
        sb.append("Workspace: ").append(workspace).append("\n\n");
        for (String name : BOOTSTRAP_FILES) {
            Path p = workspace.resolve(name);
            if (Files.isRegularFile(p)) {
                try {
                    String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    sb.append("--- ").append(name).append(" ---\n").append(content).append("\n\n");
                } catch (Exception e) {
                    // skip
                }
            }
        }
        String mem = memory.getMemoryContext();
        if (mem != null && !mem.isEmpty()) {
            sb.append(mem);
        }
        if (skillNames != null && !skillNames.isEmpty()) {
            sb.append(skillsLoader.loadSkillsForContext(skillNames));
        }
        sb.append(skillsLoader.buildSkillsSummary());
        return sb.toString();
    }

    public String buildSystemPrompt() {
        return buildSystemPrompt(skillsLoader.getAlwaysSkills());
    }

    /** 返回给 LLM 的消息列表：[system, ...history, userMessage] */
    public List<Map<String, Object>> buildMessages(List<Map<String, Object>> history,
                                                   String currentMessage,
                                                   List<String> skillNames,
                                                   List<String> media,
                                                   String channel,
                                                   String chatId) {
        String system = buildSystemPrompt(skillNames);
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", system);
        messages.add(systemMsg);
        if (history != null) {
            for (Map<String, Object> m : history) {
                messages.add(new HashMap<>(m));
            }
        }
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", currentMessage != null ? currentMessage : "");
        messages.add(userMsg);
        return messages;
    }

    /** 向消息列表追加一条 tool 结果 */
    public void addToolResult(List<Map<String, Object>> messages, String toolCallId, String toolName, String result) {
        Map<String, Object> tr = new HashMap<>();
        tr.put("role", "tool");
        tr.put("tool_call_id", toolCallId);
        tr.put("content", result != null ? result : "");
        messages.add(tr);
    }

    /** 向消息列表追加一条 assistant 消息（可含 tool_calls、reasoning_content） */
    public void addAssistantMessage(List<Map<String, Object>> messages, String content,
                                    List<Map<String, Object>> toolCalls, String reasoningContent) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "assistant");
        if (content != null && !content.isEmpty()) {
            msg.put("content", content);
        } else {
            msg.put("content", "");
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            msg.put("tool_calls", toolCalls);
        }
        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            msg.put("reasoning_content", reasoningContent);
        }
        messages.add(msg);
    }
}
