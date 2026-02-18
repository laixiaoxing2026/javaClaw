# javaClaw · 项目梳理

---

## 一、项目定位

- **javaClaw** 与 Python 版 nanobot 架构一致：**超轻量个人 AI 助手**，核心逻辑保持精简，便于阅读与扩展。
- **技术栈**：**Java 8**，不使用虚拟线程；CLI 用 Picocli（或 JCommander），配置用 JSON + POJO（Jackson），LLM 通过 HTTP 调用 OpenAI 兼容 API，由统一 `LLMProvider` 抽象封装。
- **能力范围**：
    - **聊天渠道**：仅支持 **钉钉（DingTalk）**；不实现 Telegram、Discord、飞书、Slack、Email、QQ、Mochat、WhatsApp 等。
    - 多 LLM 提供商、MCP 工具、定时任务、记忆与技能；与 Python 版共享同一架构与工作区约定。

---

## 二、整体架构（核心数据流）

与 Python 版一致：

```
用户/渠道 → Channel → MessageBus(inbound) → AgentLoop → LLM + Tools
                                                      ↓
用户/渠道 ← Channel ← MessageBus(outbound) ← 回复/OutboundMessage
```

- **Channel**：当前仅 **钉钉** 渠道；Java 中为 `BaseChannel` 抽象及唯一实现 `DingTalkChannel`。若后续扩展，仍通过 BaseChannel + MessageBus 解耦。
- **MessageBus**：解耦渠道与 Agent 的异步消息队列（inbound / outbound），使用 `BlockingQueue<InboundMessage>` 与 `BlockingQueue<OutboundMessage>`；**Java 8** 下使用 `ExecutorService` 驱动 Agent 循环与 outbound 分发，不使用虚拟线程。
- **AgentLoop**：核心处理引擎，消费 inbound、组装 context、调用 LLM、执行 tool、写入 outbound。

---

## 三、目录与模块职责（Java 包/类映射）

| Java 包/模块 | 职责简述 | 对应 Python |
|--------------|----------|-------------|
| **`agent`** | 核心 Agent：循环、上下文、记忆、技能、子 Agent、工具 | `nanobot/agent/` |
| **`agent.AgentLoop`** | Agent 主循环：取消息 → 建会话/上下文 → 调 LLM → 执行 tool_call → 写回复/会话 | `agent/loop.py` |
| **`agent.ContextBuilder`** | 组装 system prompt、多轮 messages（含 tool 结果、assistant 消息） | `agent/context.py` |
| **`agent.MemoryStore`** | 持久记忆：`MEMORY.md`（长期事实）+ `HISTORY.md`（可 grep 的日志） | `agent/memory.py` |
| **`agent.SkillsLoader`** | 技能加载：workspace 与内置 skills 目录下的 `SKILL.md`，按需/常驻加载 | `agent/skills.py` |
| **`agent.SubagentManager`** | 后台子任务：spawn 子 Agent 执行复杂任务 | `agent/subagent.py` |
| **`agent.tools`** | 工具抽象与实现：文件读写/编辑/列表、shell、网页搜索/抓取、message、spawn、cron、MCP 等 | `agent/tools/` |
| **`bus`** | 消息总线：`InboundMessage` / `OutboundMessage`，`MessageBus`（队列 + 出站分发） | `nanobot/bus/` |
| **`channels`** | **仅钉钉**：`BaseChannel` 抽象 + `DingTalkChannel` 实现；收消息时 `bus.publishInbound` | `nanobot/channels/`（仅保留钉钉） |
| **`channels.ChannelManager`** | 按配置初始化并启动 **仅钉钉** channel、启动 outbound 分发循环 | `channels/manager.py`（仅钉钉） |
| **`providers`** | LLM：`LLMProvider` 接口、实现类（如 `OpenAICompatibleProvider`）、Registry 按 model 选 provider | `nanobot/providers/` |
| **`config`** | 配置 Schema（POJO）：仅保留 agents、channels.dingtalk、providers、gateway、tools 等所需字段；Loader 读 `~/.nanobot/config.json` | `nanobot/config/` |
| **`session`** | 会话：按 `channel:chatId` 的 sessionKey 管理多轮对话历史与持久化 | `nanobot/session/` |
| **`cron`** | 定时任务：Cron 配置与存储，到期通过 Agent 执行 | `nanobot/cron/` |
| **`heartbeat`** | 心跳/主动唤醒（如定时拉活） | `nanobot/heartbeat/` |
| **`cli`** | CLI：`javaClaw gateway`、`agent`、`onboard`、`status`、`cron` 等子命令 | `nanobot/cli/commands.py` |
| **`skills`（资源）** | 内置技能 `SKILL.md` 文件，与 Python 版共用或复制一份 | `nanobot/skills/` |

---

## 四、核心流程简述

### 1. Gateway 启动（钉钉 + Agent）

- 加载 `Config`（`ConfigLoader.loadConfig()`）→ 创建 `MessageBus` → 通过 `ProviderRegistry` 或工厂得到 `LLMProvider`。
- 创建 `CronService`、`SessionManager`、`AgentLoop`（注入 bus、provider、workspace、cron、session、mcpServers 等）。
- 将 cron 的回调设为「通过 agent.processDirect(...) 执行任务，必要时 bus.publishOutbound(...)」。
- `ChannelManager` 根据 config **仅初始化钉钉** channel（若启用），启动 outbound 分发循环与 `DingTalkChannel.start()`（在 `ExecutorService` 线程中运行）。
- 并发运行：`AgentLoop.run()`（消费 inbound）、ChannelManager 的 outbound 分发循环、钉钉 channel 的 `start()`；**均使用 Java 8 的 `ExecutorService` 提交任务，不使用虚拟线程**。

### 2. 单条消息在 Agent 内的处理（processMessage）

- 若是 `system` 渠道，走系统消息逻辑（processSystemMessage）。
- 用 `msg.getSessionKey()`（或传入的 sessionKey）取/建 `Session`。
- 处理 `/new`、`/help` 等命令；若历史条数超过 `memoryWindow`，可触发后台记忆 consolidate。
- `setToolContext(channel, chatId)`，供 message/spawn/cron 等工具回发到正确会话。
- `ContextBuilder.buildMessages(...)` 得到带 system + 历史 + 当前用户消息的 `initialMessages`。
- `runAgentLoop(initialMessages)`：在循环内反复「LLM chat → 若有 tool_calls 则执行工具并追加结果到 messages」，直到 LLM 不再调工具。
- 将最终回复写入 session，并 `bus.publishOutbound(OutboundMessage(...))`。
- Channel 侧由 ChannelManager 的 outbound 分发循环从 bus 取消息，按 `msg.getChannel()` 找到对应 channel（当前仅钉钉）并 `channel.send(msg)`。

### 3. Agent 循环（runAgentLoop）

- 每轮：`provider.chat(messages, tools.getDefinitions(), model, temperature, maxTokens)`。
- 若 `response.hasToolCalls()`：将 assistant 消息（含 tool_calls）和每个 tool 的结果依次追加到 messages，再追加一句让模型反思的 user 消息，继续下一轮。
- 若无 tool_calls，则 `response.getContent()` 为最终回复，退出循环。

### 4. 上下文与工具

- **ContextBuilder**：从 workspace 读 `AGENTS.md`、`SOUL.md`、`USER.md`、`TOOLS.md`、`IDENTITY.md` 等做 system；加上 `MemoryStore.getMemoryContext()` 和 skills 摘要（或常驻 skill 全文）。
- **ToolRegistry**：所有工具注册到 `AgentLoop` 的 tools；`getDefinitions()` 给 LLM，`execute(name, params)` 执行；支持 MCP 动态注册。

---

## 五、配置与入口

- **配置路径**：`~/.nanobot/config.json`，由 `config.ConfigLoader` 读取并反序列化为 `Config`（POJO）；键名与 Python 版一致（camelCase）。**配置中渠道仅需支持钉钉**。
- **工作区**：默认 `~/.nanobot/workspace`，其下 memory、skills、会话等与 Python 版约定一致。
- **入口**：可执行 JAR 或 `java -cp ... Main`，子命令对应：`gateway`、`agent`、`onboard`、`status`、`cron` 等；对外可统称为 **javaClaw**（如 `javaClaw gateway`、`javaClaw agent`）。

---

## 六、设计特点小结

1. **渠道与 Agent 解耦**：所有 I/O 经 MessageBus；当前仅钉钉一种渠道，若后续扩展仍通过实现 `BaseChannel` 并在 ChannelManager 中注册。
2. **LLM 与配置解耦**：Provider 接口 + Registry/工厂，新 provider 实现接口并在配置与注册表中增加即可。
3. **工具可扩展**：内置工具 + MCP 服务，工具统一由 `ToolRegistry` 注册与执行。
4. **会话与记忆分离**：Session 管对话窗口与历史条数；Memory 管长期事实与历史日志，consolidate 时把会话压缩进记忆。
5. **体量可控**：核心逻辑集中在 agent、bus、channels（仅钉钉）、config，保持与 Python 版相近的清晰度。
6. **Java 8 与无虚拟线程**：全程使用 `ExecutorService` + `BlockingQueue`，不依赖 Java 21+ 或虚拟线程。

---

## 相关文档

- [02-调用链-Java版](./02-调用链-Java版.md) — javaClaw 程序入口、Gateway 启动、单条消息处理、直接调用、记忆合并等调用链。
- [03-接口文档-Java版](./03-接口文档-Java版.md) — 消息总线与事件类型、渠道（钉钉）/LLM/工具/Agent/会话/技能/配置等 Java 接口说明。
