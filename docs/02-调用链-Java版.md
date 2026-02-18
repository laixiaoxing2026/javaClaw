# javaClaw · 调用链说明

> 对应 Python 版 [调用链](../调用链.md)，描述 **javaClaw**（Java 8、仅钉钉渠道）的主要执行路径与调用关系。

---

## 一、程序入口

```
java -jar javaclaw.jar [command] [options]
  或 java -cp ... com.javaclaw.cli.Main [command] [options]
  └── cli.Main.main(String[])
        └── Picocli / JCommander 解析子命令，派发到对应 Command
```

常用命令入口：

- `gateway` → `GatewayCommand.run()` 或 `GatewayCommand.call()`
- `agent` → `AgentCommand.run()` / `AgentCommand.call()`
- `onboard` → `OnboardCommand.run()`
- `status` → `StatusCommand.run()`

---

## 二、Gateway 启动调用链

执行 `javaClaw gateway`（或 `gateway` 子命令）时的组装与启动顺序。**并发均使用 Java 8 的 ExecutorService，不使用虚拟线程。**

```
GatewayCommand.run()
  ├── ConfigLoader.loadConfig()              // config 包，读 ~/.nanobot/config.json
  ├── new MessageBus()                       // bus 包，建 inbound/outbound BlockingQueue
  ├── ProviderFactory.fromConfig(config)     // 得到 LLMProvider 实现
  ├── new SessionManager(config.getWorkspacePath())
  ├── new CronService(cronStorePath)
  ├── new AgentLoop(bus, provider, workspace, ...)  // agent 包
  │     ├── new ContextBuilder(workspace)
  │     ├── new ToolRegistry() + registerDefaultTools()
  │     ├── new SubagentManager(...)
  │     └── (MCP 在 run() 时懒连接 connectMcp())
  ├── cron.setOnJob(this::onCronJob)          // 回调里 agent.processDirect(...) + bus.publishOutbound(...)
  ├── new HeartbeatService(..., agent::processDirect)
  ├── new ChannelManager(config, bus)
  │     └── initChannels()                   // 仅按 config 创建 DingTalkChannel（若启用）
  └── 启动并发任务（ExecutorService，Java 8）:
        ├── cron.start()
        ├── heartbeat.start()
        ├── executor.submit(() -> agent.run())           // 消费 inbound，处理消息，写 outbound
        ├── executor.submit(() -> channelManager.dispatchOutbound())
        └── 若启用钉钉: executor.submit(() -> dingTalkChannel.start())
```

**ChannelManager.startAll() 内部：**

```
ChannelManager.startAll()
  ├── 启动 dispatchOutbound() 的线程/任务   // 从 bus 取 outbound 分发给已注册 channel（当前仅钉钉）
  └── 若启用钉钉: executor.submit(() -> dingTalkChannel.start())
        └── dingTalkChannel.start()   // 钉钉 Stream/长连 逻辑，收到用户消息后调用 handleMessage
```

---

## 三、单条消息完整调用链（渠道 → Agent → 回复）

### 3.1 渠道侧：收消息 → 入队

当前仅 **钉钉** 渠道。

```
[钉钉开放平台回调] Stream 模式 / HTTP 回调
  └── DingTalkChannel 的 handler
        └── BaseChannel.handleMessage(senderId, chatId, content, media, metadata)
              ├── 可选：isAllowed(senderId) 校验
              └── bus.publishInbound(new InboundMessage("dingtalk", senderId, chatId, content, ...))
```

即：**Channel 收到消息 → handleMessage → bus.publishInbound(InboundMessage)**。

### 3.2 Agent 侧：出队 → 处理 → 回复入队

```
AgentLoop.run()   // 循环，通常 while (running)
  └── msg = bus.consumeInbound()   // 阻塞取一条 InboundMessage
  └── response = processMessage(msg)
        ├── [system 渠道] processSystemMessage(msg) → 同下文的「组消息 → 循环 → 写 session → OutboundMessage」
        ├── session = sessions.getOrCreate(msg.getSessionKey())
        ├── 若 /new：session.clear() + 后台 consolidateMemory(session, true) + 返回 "New session..."
        ├── 若 /help：直接返回帮助文案
        ├── 若 session.getMessages().size() > memoryWindow：executor.submit(() -> consolidateMemory(session))
        ├── setToolContext(msg.getChannel(), msg.getChatId())
        ├── initialMessages = context.buildMessages(history, msg.getContent(), ...)
        ├── RunResult result = runAgentLoop(initialMessages)
        ├── session.addMessage("user", ...); session.addMessage("assistant", ...); sessions.save(session)
        └── return new OutboundMessage(msg.getChannel(), msg.getChatId(), result.getContent(), ...)
  └── bus.publishOutbound(response)
```

### 3.3 上下文与 Agent 循环内部

**构建发给 LLM 的消息列表：**

```
ContextBuilder.buildMessages(history, currentMessage, channel, chatId, media)
  ├── buildSystemPrompt(skillNames)
  │     ├── getIdentity()           // 身份、时间、工作区、记忆路径等
  │     ├── loadBootstrapFiles()    // AGENTS.md, SOUL.md, USER.md, TOOLS.md, IDENTITY.md
  │     ├── memory.getMemoryContext()  // MEMORY.md 内容
  │     └── skills 相关（常驻 skill 全文 + 可选 skill 摘要）
  └── messages = [system, ...history, userMessage]
```

**一轮 Agent 循环（可能多轮 tool 调用）：**

```
AgentLoop.runAgentLoop(initialMessages)
  loop:
    ├── response = provider.chat(messages, tools.getDefinitions(), model, temperature, maxTokens)
    ├── 若 response.hasToolCalls():
    │     ├── context.addAssistantMessage(messages, response.getContent(), toolCallDicts, response.getReasoningContent())
    │     ├── 对每个 toolCall:
    │     │     └── result = tools.execute(toolCall.getName(), toolCall.getArguments())
    │     │           └── ToolRegistry.execute() → 具体 Tool.execute(params)
    │     ├── context.addToolResult(messages, toolCall.getId(), toolName, result)
    │     └── messages.add(userMessage("Reflect on the results and decide next steps."))
    │     → 继续下一轮 loop
    └── 若无 tool_calls: finalContent = response.getContent(); break
  return RunResult(finalContent, toolsUsed)
```

### 3.4 出队到渠道发送

```
ChannelManager.dispatchOutbound()   // 独立线程/任务循环（ExecutorService）
  └── loop:
        ├── msg = bus.consumeOutbound()
        ├── channel = channels.get(msg.getChannel())   // 当前仅 "dingtalk"
        └── dingTalkChannel.send(msg)                  // 钉钉 API 发回
```

---

## 四、直接调用路径（CLI / Cron / Heartbeat）

### 4.1 CLI 单条或交互

```
AgentCommand.run()（-m "xxx" 或交互模式）
  ├── ConfigLoader.loadConfig(); new MessageBus(); ProviderFactory...(); new AgentLoop(...)
  ├── 单条: agentLoop.processDirect(message, sessionId)
  ├── 交互: 循环里 userInput = readLine()
  │         → agentLoop.processDirect(userInput, sessionId)
  └── printResponse(response)
```

**processDirect 与总线的关系：**

```
AgentLoop.processDirect(content, sessionKey, channel, chatId)
  ├── connectMcp()   // 若配置了 MCP 则懒连接
  ├── msg = new InboundMessage(channel, "user", chatId, content)
  └── processMessage(msg, sessionKey)
        // 与「渠道来的消息」走同一套：session、buildMessages、runAgentLoop、写 session
        → 返回 OutboundMessage，调用方取 response.getContent() 即可（CLI 不经过 bus 分发）
```

### 4.2 定时任务（Cron）

```
CronService 到期触发 onJob(job)
  └── onCronJob(job)
        ├── response = agent.processDirect(job.getPayload().getMessage(), "cron:" + job.getId(), channel, chatId)
        └── 若 job.getPayload().isDeliver() 且 to 非空: bus.publishOutbound(new OutboundMessage(...))
              → 由 ChannelManager.dispatchOutbound 投递到对应 channel
```

### 4.3 心跳（Heartbeat）

```
HeartbeatService 定时触发 onHeartbeat(prompt)
  └── agent.processDirect(prompt, "heartbeat")
```

---

## 五、记忆合并调用链

当会话条数超过 memoryWindow 或用户执行 `/new` 时：

```
// 后台任务（不阻塞当条回复）
consolidateMemory(session, archiveAll)
  ├── new MemoryStore(workspace)
  ├── 构建 prompt：当前 MEMORY.md + 待合并的会话片段
  ├── response = provider.chat([system, user], model, ...)   // 专用「记忆合并」调用，无 tools
  ├── 解析 JSON → historyEntry, memoryUpdate
  ├── memory.appendHistory(historyEntry)       // 写入 HISTORY.md
  ├── memory.writeLongTerm(memoryUpdate)      // 写回 MEMORY.md
  └── 更新 session.lastConsolidated 或清空（/new 时）
```

---

## 六、小结：关键交汇点

| 交汇点 | 说明 |
|--------|------|
| **MessageBus** | 所有「渠道 → Agent」经 `publishInbound` / `consumeInbound`；所有「Agent → 渠道」经 `publishOutbound` / `consumeOutbound`（或直接拿 processDirect 返回值）。 |
| **AgentLoop.processMessage** | 无论是渠道消息还是 processDirect，最终都走这里：session、命令、context.buildMessages、runAgentLoop、写 session、返回 OutboundMessage。 |
| **ContextBuilder.buildMessages** | 所有需要 LLM 的请求都会先组好 system + history + 当前 user 消息，再交给 provider.chat。 |
| **ToolRegistry.execute** | 所有 tool 调用（包括 message、spawn、cron 等）都经 ToolRegistry 统一执行。 |

各模块的 Java 接口签名与扩展方式见 [03-接口文档-Java版](./03-接口文档-Java版.md)。
