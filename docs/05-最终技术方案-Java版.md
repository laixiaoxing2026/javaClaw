# javaClaw · 最终技术方案（Java 版）

---

## 一、确认结论摘要

| 项 | 结论 |
|----|------|
| **技术栈** | 按文档定死：Java 8，不使用虚拟线程；Picocli（或 JCommander）；JSON + POJO（Jackson）；LLM 通过 HTTP 调用 OpenAI 兼容 API，由 `LLMProvider` 抽象。 |
| **配置与数据路径** | 统一使用 **javaclawbot**：配置文件 `~/.javaclawbot/config.json`，数据目录 `~/.javaclawbot`，工作区默认 `~/.javaclawbot/workspace`，会话目录 `~/.javaclawbot/sessions/` 等（详见第二节）。 |
| **渠道** | **当前已实现对接的为 QQ**（QQChannel，WebSocket 单聊收 + 单聊发消息 API）；**钉钉为预留**（DingTalkChannel 存在但未与钉钉平台真正对接）。可扩展其他渠道。 |
| **入口与命令** | 与文档一致：可执行 JAR 或 `java -cp ... Main`，子命令 `gateway`、`agent`、`onboard`、`status`、`cron`；对外统称 **javaClaw**（如 `javaClaw gateway`、`javaClaw agent`）。 |
| **接口** | 与 [03-接口文档-Java版](./03-接口文档-Java版.md) 保持一致，作为实现与扩展的权威依据。 |

---

## 二、配置与路径约定（javaclawbot）

以下路径均基于用户目录下的 **javaclawbot** 命名空间，与原文档中的 nanobot 路径区分。

| 用途 | 路径 | 说明 |
|------|------|------|
| 配置文件 | `~/.javaclawbot/config.json` | ConfigLoader.getConfigPath() 默认值 |
| 数据目录 | `~/.javaclawbot` | ConfigLoader.getDataDir()；cron、sessions 等数据的父目录 |
| 工作区 | `~/.javaclawbot/workspace` | 默认 workspace；其下 memory、skills、bootstrap 文件等与文档约定一致 |
| 会话存储 | `~/.javaclawbot/sessions/` | SessionManager 持久化会话文件的目录 |
| 记忆 | `workspace/memory/MEMORY.md`、`workspace/memory/HISTORY.md` | MemoryStore 使用，workspace 为上述工作区路径 |
| 技能 | `workspace/skills/<name>/SKILL.md` 与内置 `builtinSkillsDir/<name>/SKILL.md` | SkillsLoader 约定 |
| Cron 存储 | 由 Config/ConfigLoader 约定，置于 `~/.javaclawbot` 下 | CronService 持久化 |

**实现要点**：ConfigLoader 中凡原文档写 `~/.nanobot` 之处，均改为 `~/.javaclawbot`；Config 的 getWorkspacePath()、getDataDir() 等返回的 Path 均基于上述约定。

---

## 三、技术栈（定死）

- **语言与运行时**：Java 8，不使用虚拟线程。
- **并发**：`ExecutorService` + `BlockingQueue`（inbound/outbound），不依赖 Java 21+ 或虚拟线程。
- **CLI**：Picocli 或 JCommander，解析子命令并派发到对应 Command。
- **配置**：JSON 文件 + POJO，Jackson 序列化/反序列化，键名 camelCase。
- **LLM**：HTTP 调用 OpenAI 兼容 API，统一由 `LLMProvider` 接口抽象，实现类（如 `OpenAICompatibleProvider`）由工厂/Registry 根据 Config 创建。

---

## 四、整体架构与数据流

与文档一致，不做变更：

```
用户/渠道 → Channel → MessageBus(inbound) → AgentLoop → LLM + Tools
                                                      ↓
用户/渠道 ← Channel ← MessageBus(outbound) ← 回复/OutboundMessage
```

- **Channel**：QQ 已对接、钉钉预留；`BaseChannel` 抽象 + `QQChannel`（完整实现）、`DingTalkChannel`（预留）。
- **MessageBus**：`BlockingQueue<InboundMessage>` / `BlockingQueue<OutboundMessage>`，ExecutorService 驱动 Agent 循环与 outbound 分发。
- **AgentLoop**：消费 inbound、组装 context、调用 LLM、执行 tool、写入 outbound。

---

## 五、模块与包职责（与文档一致）

| 包/模块 | 职责 |
|---------|------|
| **config** | 配置 Schema（POJO）、ConfigLoader 读 `~/.javaclawbot/config.json`，含 agents、channels.dingtalk、channels.qq、providers、gateway、tools 等。 |
| **bus** | InboundMessage、OutboundMessage、MessageBus。 |
| **providers** | LLMProvider、ToolCallRequest、LLMResponse、ProviderFactory/Registry、OpenAI 兼容实现。 |
| **session** | Session、SessionManager（按 channel:chatId 的 sessionKey 管理多轮对话与持久化，存于 ~/.javaclawbot/sessions/）。 |
| **agent** | AgentLoop、ContextBuilder、MemoryStore、SkillsLoader、SubagentManager、tools（Tool、ToolRegistry 及默认工具实现）。 |
| **channels** | BaseChannel、QQChannel（已对接）、DingTalkChannel（预留）、ChannelManager（按配置初始化、outbound 分发循环）。 |
| **cron** | Cron 配置与存储，到期通过 Agent 执行。 |
| **heartbeat** | 心跳/主动唤醒。 |
| **cli** | Main、GatewayCommand、AgentCommand、OnboardCommand、StatusCommand、CronCommand 等。 |

---

## 六、入口与命令（与文档一致）

- 入口：`java -jar javaclaw.jar [command] [options]` 或 `java -cp ... com.javaclaw.cli.Main [command] [options]`。
- 子命令：`gateway`、`agent`、`onboard`、`status`、`cron` 等；对外统称 **javaClaw**（如 `javaClaw gateway`、`javaClaw agent`）。
- Gateway 启动：ConfigLoader.loadConfig()（读 ~/.javaclawbot/config.json）→ MessageBus、ProviderFactory、SessionManager、CronService、AgentLoop、HeartbeatService、ChannelManager → ExecutorService 提交 agent.run()、dispatchOutbound()、各 channel.start()（当前仅 QQ 与平台有真实连接）、cron.start()、heartbeat.start()。

---

## 七、接口与扩展（与 03-接口文档 一致）

- **消息与总线**：InboundMessage、OutboundMessage 字段与 getSessionKey()；MessageBus 的 publishInbound/consumeInbound、publishOutbound/consumeOutbound(timeout)、stop、getInboundSize/getOutboundSize。
- **渠道**：BaseChannel 的 start、stop、send、isAllowed、handleMessage；QQChannel 已对接、DingTalkChannel 预留；ChannelManager 的 initChannels、startAll、dispatchOutbound。
- **LLM**：LLMProvider.chat、getDefaultModel；LLMResponse、ToolCallRequest 结构；ProviderFactory.fromConfig。
- **工具**：Tool（getName、getDescription、getParameters、execute(params)、validateParams、toSchema）；params 可含框架注入的 channel、chatId、metadata；ToolRegistry（register、unregister、get、has、getDefinitions、execute(name, params)、getToolNames）。
- **Agent**：AgentLoop 构造参数与 run、processDirect、stop、closeMcp；ContextBuilder.buildSystemPrompt、buildMessages、addToolResult、addAssistantMessage；MemoryStore.readLongTerm、writeLongTerm、appendHistory、getMemoryContext。
- **会话**：Session（addMessage、getHistory、clear）；SessionManager（getOrCreate、save、invalidate）。
- **技能**：SkillsLoader（listSkills、loadSkill、loadSkillsForContext、buildSkillsSummary、getAlwaysSkills、getSkillMetadata）。
- **配置**：Config 根结构及 getWorkspacePath、getProvider、getApiKey、getApiBase 等；ConfigLoader.getConfigPath（~/.javaclawbot/config.json）、getDataDir（~/.javaclawbot）、loadConfig、saveConfig。

所有新增或扩展实现均需符合 [03-接口文档-Java版](./03-接口文档-Java版.md) 中规定的签名与语义；路径相关处按本文档第二节使用 javaclawbot。

---

## 八、设计特点（与文档一致）

1. 渠道与 Agent 解耦：所有 I/O 经 MessageBus；当前已实现对接的为 QQ，钉钉为预留，后续扩展通过实现 BaseChannel 并在 ChannelManager 注册。
2. LLM 与配置解耦：Provider 接口 + 工厂/Registry，新 provider 实现接口并在配置与注册中增加即可。
3. 工具可扩展：内置工具 + MCP，统一由 ToolRegistry 注册与执行。
4. 会话与记忆分离：Session 管对话窗口与历史条数；Memory 管长期事实与历史日志；consolidate 时把会话压缩进记忆。
5. 体量可控：核心逻辑集中在 agent、bus、channels（QQ 已对接、钉钉预留）、config。
6. Java 8 与无虚拟线程：全程 ExecutorService + BlockingQueue。

---

## 九、相关文档

- [01-项目梳理-Java版](./01-项目梳理-Java版.md) — 架构与模块职责（路径以本文档为准）
- [02-调用链-Java版](./02-调用链-Java版.md) — 程序入口、Gateway 启动、单条消息处理、直接调用、记忆合并
- [03-接口文档-Java版](./03-接口文档-Java版.md) — 消息/渠道/LLM/工具/Agent/会话/技能/配置的 Java 接口
- [04-待办清单-Java版](./04-待办清单-Java版.md) — 按模块与依赖顺序整理的实现待办
