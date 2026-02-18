# javaClaw · 待办清单（Java 版）

> 基于 [01-项目梳理-Java版](./01-项目梳理-Java版.md)、[02-调用链-Java版](./02-调用链-Java版.md)、[03-接口文档-Java版](./03-接口文档-Java版.md) 整理的实现待办，按模块与依赖顺序排列。配置与数据路径统一使用 **javaclawbot**（见 [05-最终技术方案-Java版](./05-最终技术方案-Java版.md)）。

---

## 一、基础与配置（优先）

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 1.1 | 配置 POJO | `config` | Config、AgentsConfig、ChannelsConfig、DingTalkConfig、ProvidersConfig、GatewayConfig、ToolsConfig、ProviderConfig、ExecToolConfig、MCPServerConfig 等；键名 camelCase | - |
| 1.2 | 配置路径与加载 | `config.ConfigLoader` | `getConfigPath()` → `~/.javaclawbot/config.json`；`getDataDir()` → `~/.javaclawbot`；`loadConfig()` / `saveConfig()`，Jackson 反序列化 | 1.1 |
| 1.3 | 工作区与数据目录约定 | - | 工作区默认 `~/.javaclawbot/workspace`；sessions 存 `~/.javaclawbot/sessions/`；cron 存 `~/.javaclawbot` 下；memory 在 workspace/memory/ | 1.2 |

---

## 二、消息总线（bus）

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 2.1 | InboundMessage | `bus` | POJO：channel, senderId, chatId, content, timestamp, media, metadata；`getSessionKey()` = channel + ":" + chatId | - |
| 2.2 | OutboundMessage | `bus` | POJO：channel, chatId, content, replyTo, media, metadata | - |
| 2.3 | MessageBus | `bus` | 无参构造；BlockingQueue&lt;InboundMessage&gt; / BlockingQueue&lt;OutboundMessage&gt;；publishInbound/consumeInbound、publishOutbound/consumeOutbound(timeout)、stop；getInboundSize/getOutboundSize | 2.1, 2.2 |

---

## 三、LLM 提供商（providers）

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 3.1 | ToolCallRequest | `providers` | id, name, arguments（Map） | - |
| 3.2 | LLMResponse | `providers` | content, toolCalls, finishReason, usage, reasoningContent；hasToolCalls() | 3.1 |
| 3.3 | LLMProvider 接口 | `providers` | chat(messages, tools, model, maxTokens, temperature) → LLMResponse；getDefaultModel() | 3.2 |
| 3.4 | OpenAI 兼容实现 | `providers` | OpenAICompatibleProvider：HTTP 调用 OpenAI 兼容 API | 3.3, config |
| 3.5 | ProviderFactory / Registry | `providers` | fromConfig(config) 得到 LLMProvider；按 model 选实现 | 3.4, 1.2 |

---

## 四、会话（session）

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 4.1 | Session | `session` | key, messages, createdAt, updatedAt, metadata, lastConsolidated；addMessage, getHistory(maxMessages), clear | - |
| 4.2 | SessionManager | `session` | 构造(workspace 或 dataDir)；getOrCreate(key)、save(session)、invalidate(key)；会话文件存 ~/.javaclawbot/sessions/ | 4.1, 1.3 |

---

## 五、Agent 子模块（agent）

### 5.1 记忆与技能

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 5.1.1 | MemoryStore | `agent` | 构造(workspace)；workspace/memory/MEMORY.md、HISTORY.md；readLongTerm、writeLongTerm、appendHistory、getMemoryContext | - |
| 5.1.2 | SkillsLoader | `agent` | 构造(workspace, builtinSkillsDir)；listSkills、loadSkill、loadSkillsForContext、buildSkillsSummary、getAlwaysSkills、getSkillMetadata | - |

### 5.2 工具（tools）

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 5.2.1 | Tool 接口/抽象类 | `agent.tools` | getName、getDescription、getParameters、execute(params)；validateParams、toSchema（OpenAI function 格式） | - |
| 5.2.2 | ToolRegistry | `agent.tools` | register、unregister、get、has、getDefinitions、execute(name, params)；getToolNames | 5.2.1 |
| 5.2.3 | 默认工具实现 | `agent.tools` | 文件读写/编辑/列表、shell(exec)、网页搜索/抓取、message、spawn、cron、MCP 等；在 AgentLoop 中 registerDefaultTools | 5.2.2, config |

### 5.3 上下文与循环

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 5.3.1 | ContextBuilder | `agent` | 构造(workspace)；buildSystemPrompt(skillNames)、buildMessages(history, currentMessage, skillNames, media, channel, chatId)；addToolResult、addAssistantMessage；读 AGENTS.md、SOUL.md、USER.md、TOOLS.md、IDENTITY.md 等 | 5.1.1, 5.1.2 |
| 5.3.2 | SubagentManager | `agent` | 后台子任务：spawn 子 Agent 执行复杂任务（可选先做占位） | agent, bus |
| 5.3.3 | AgentLoop | `agent` | 构造(bus, provider, workspace, model, maxIterations, temperature, maxTokens, memoryWindow, ...)；run() 主循环 consumeInbound→processMessage→publishOutbound；processMessage（session、/new、/help、memoryWindow 触发 consolidate、setToolContext、buildMessages、runAgentLoop、写 session）；runAgentLoop(initialMessages) 多轮 chat+tool 直到无 tool_calls；processDirect(content, sessionKey, channel, chatId)；stop、closeMcp；connectMcp 懒连接 | 2.3, 3.x, 4.2, 5.1.1, 5.1.2, 5.2.2, 5.3.1, 5.3.2 |
| 5.3.4 | 记忆合并 | `agent` | consolidateMemory(session, archiveAll)：MemoryStore、构建 prompt、provider.chat 无 tools、解析 JSON、appendHistory、writeLongTerm、更新 session | 5.1.1, 3.3 |

---

## 六、渠道（channels，仅钉钉）

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 6.1 | BaseChannel | `channels` | 抽象：name；构造(config, bus)；start()、stop()、send(OutboundMessage)；isAllowed(senderId)、handleMessage(senderId, chatId, content, media, metadata)→publishInbound | 2.1, 2.3 |
| 6.2 | DingTalkChannel | `channels` | 继承 BaseChannel；start() 钉钉 Stream/HTTP 回调，收到消息调用 handleMessage；send() 调钉钉 API | 6.1, config |
| 6.3 | ChannelManager | `channels` | 构造(config, bus)；initChannels() 仅创建 DingTalkChannel（若启用）；startAll()：dispatchOutbound 循环 + dingTalkChannel.start()；dispatchOutbound() 循环 consumeOutbound→channels.get(channel)→channel.send(msg) | 2.3, 6.2 |

---

## 七、Cron 与 Heartbeat

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 7.1 | CronService | `cron` | 构造(cronStorePath)；setOnJob(callback)；start()；到期触发 onCronJob：agent.processDirect + 若 deliver 则 publishOutbound | config, bus, agent |
| 7.2 | HeartbeatService | `heartbeat` | 构造(..., agent::processDirect)；start()；定时 onHeartbeat(prompt)→processDirect | agent |

---

## 八、CLI 入口

| 序号 | 待办项 | 包/类 | 说明 | 依赖 |
|------|--------|--------|------|------|
| 8.1 | Main | `cli` | main(String[])；Picocli 或 JCommander 解析子命令，派发到对应 Command | 8.2 |
| 8.2 | 子命令 | `cli` | GatewayCommand（gateway）、AgentCommand（agent）、OnboardCommand（onboard）、StatusCommand（status）、CronCommand（cron）等 | 1.2, 2.3, 3.5, 4.2, 5.3.3, 6.3, 7.1, 7.2 |
| 8.3 | GatewayCommand | `cli` | run()：ConfigLoader.loadConfig、MessageBus、ProviderFactory、SessionManager、CronService、AgentLoop、cron.setOnJob、HeartbeatService、ChannelManager、ExecutorService 提交 agent.run、dispatchOutbound、dingTalkChannel.start、cron.start、heartbeat.start | 见 02-调用链 第二节 |
| 8.4 | AgentCommand | `cli` | run()：加载 config/bus/provider/AgentLoop；单条 -m 或交互循环；processDirect；printResponse | 5.3.3 |

---

## 九、资源与约定

| 序号 | 待办项 | 说明 | 依赖 |
|------|--------|------|------|
| 9.1 | 内置 skills | 内置技能目录下 SKILL.md，与 ContextBuilder/SkillsLoader 约定一致 | 5.1.2 |
| 9.2 | Bootstrap 文件 | workspace 下 AGENTS.md、SOUL.md、USER.md、TOOLS.md、IDENTITY.md 等，与 ContextBuilder 约定一致 | 5.3.1 |
| 9.3 | 打包与入口 | 可执行 JAR：java -jar javaclaw.jar [command] [options]；对外统称 javaClaw（如 javaClaw gateway） | 8.1 |

---

## 十、实现顺序建议

1. **阶段一**：配置(1.1–1.3) → 总线(2.1–2.3) → 会话(4.1–4.2) → LLM(3.1–3.5)。
2. **阶段二**：MemoryStore、SkillsLoader(5.1) → Tool 接口与 ToolRegistry(5.2.1–5.2.2) → ContextBuilder(5.3.1) → 默认工具(5.2.3) → AgentLoop + 记忆合并(5.3.3–5.3.4)。
3. **阶段三**：BaseChannel + DingTalkChannel + ChannelManager(6.1–6.3) → CronService、HeartbeatService(7.1–7.2) → CLI Main + 各 Command(8.1–8.4)。
4. **阶段四**：内置 skills、bootstrap 约定、打包与联调(9.1–9.3)。

---

## 相关文档

- [01-项目梳理-Java版](./01-项目梳理-Java版.md) — 架构与模块职责  
- [02-调用链-Java版](./02-调用链-Java版.md) — 调用关系  
- [03-接口文档-Java版](./03-接口文档-Java版.md) — 接口签名与扩展  
- [05-最终技术方案-Java版](./05-最终技术方案-Java版.md) — 已确认的最终技术方案（含 javaclawbot 路径）
