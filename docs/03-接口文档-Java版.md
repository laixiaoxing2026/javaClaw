# javaClaw · 接口文档

---

## 一、消息总线与事件类型

**包**：`com.javaclaw.bus`

### 1.1 InboundMessage

渠道发往 Agent 的消息结构。**Java 8**：使用 POJO（不可变或常规 getter/setter），不使用 Record。

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `channel` | `String` | 渠道名：`dingtalk`、`cli` 等 |
| `senderId` | `String` | 发送者 ID |
| `chatId` | `String` | 会话/群组 ID |
| `content` | `String` | 消息正文 |
| `timestamp` | `ZonedDateTime` 或 `long`（Java 8 兼容） | 时间戳 |
| `media` | `List<String>` | 媒体 URL 列表，默认空列表 |
| `metadata` | `Map<String, Object>` | 渠道自定义数据，默认空 Map |

**方法**：

- `getSessionKey()` → `String`：会话唯一键，为 `channel + ":" + chatId`。

### 1.2 OutboundMessage

Agent 发往渠道的回复结构。

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `channel` | `String` | 目标渠道名 |
| `chatId` | `String` | 目标会话/群组 ID |
| `content` | `String` | 回复正文 |
| `replyTo` | `String` 或 `null` | 可选，回复某条消息的 ID |
| `media` | `List<String>` | 媒体列表，默认空列表 |
| `metadata` | `Map<String, Object>` | 渠道自定义数据（如 Slack thread_ts），默认空 Map |

### 1.3 MessageBus

异步消息总线，解耦渠道与 Agent。使用 `BlockingQueue` 实现与 Python 版 `asyncio.Queue` 对应的语义。

**构造**：`new MessageBus()`，无参数；内部创建 `BlockingQueue<InboundMessage>` 与 `BlockingQueue<OutboundMessage>`。

**队列**（可选暴露，或仅通过方法访问）：

- `getInboundQueue()` → `BlockingQueue<InboundMessage>`
- `getOutboundQueue()` → `BlockingQueue<OutboundMessage>`

**方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `publishInbound` | `void publishInbound(InboundMessage msg)` | 渠道调用，将用户消息入队（`inbound.put(msg)`） |
| `consumeInbound` | `InboundMessage consumeInbound()` throws InterruptedException | Agent 调用，阻塞取一条入站消息（`inbound.take()`） |
| `publishOutbound` | `void publishOutbound(OutboundMessage msg)` | Agent 或 Cron 等调用，将回复入队 |
| `consumeOutbound` | `OutboundMessage consumeOutbound()` throws InterruptedException | 分发循环调用，阻塞取一条出站消息 |
| `consumeOutbound(timeout)` | `OutboundMessage consumeOutbound(long timeout, TimeUnit unit)` | 带超时的取消息，便于循环中检查停止标志 |
| `stop` | `void stop()` | 停止 dispatch 循环（设置 running 标志） |

**属性/方法**：

- `getInboundSize()` → `int`：入站队列当前长度（`inbound.size()`）
- `getOutboundSize()` → `int`：出站队列当前长度

---

## 二、渠道接口（BaseChannel）与当前实现

**包**：`com.javaclaw.channels`

**javaClaw 当前仅支持钉钉**：仅实现 `DingTalkChannel`（继承 `BaseChannel`），在 `ChannelManager.initChannels()` 中按配置实例化并加入 `channels` Map。

### 2.1 类与构造

- **name**：`String`，渠道标识；钉钉为 `"dingtalk"`，需与 `OutboundMessage.getChannel()` 一致以便分发。
- **构造**：`BaseChannel(ChannelConfig config, MessageBus bus)`
    - `config`：该渠道在 `Config.getChannels()` 下对应的配置 POJO（当前仅需 `DingTalkConfig`）；
    - `bus`：共享的 `MessageBus`。

### 2.2 抽象方法（必须实现）

| 方法 | 签名 | 说明 |
|------|------|------|
| `start` | `void start()` | 启动渠道：建连、监听平台消息（钉钉为 Stream/HTTP 回调），收到后通过 `handleMessage` 转发到 `bus.publishInbound`；阻塞式运行，由 ExecutorService 线程调用 |
| `stop` | `void stop()` | 停止渠道并释放资源 |
| `send` | `void send(OutboundMessage msg)` | 将一条出站消息通过平台 API 发送到 `msg.getChatId()` |

### 2.3 可复用方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `isAllowed` | `boolean isAllowed(String senderId)` | 按 `config.getAllowFrom()` 判断是否允许该发送者；空列表表示允许所有人 |
| `handleMessage` | `void handleMessage(String senderId, String chatId, String content, List<String> media, Map<String, Object> metadata)` | 先 `isAllowed`，再构造 `InboundMessage` 并 `bus.publishInbound(msg)` |

**属性**：`isRunning()` → `boolean`。

---

## 三、LLM 提供商接口

**包**：`com.javaclaw.providers`

### 3.1 ToolCallRequest（或 ToolCall）

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `id` | `String` | 工具调用 ID |
| `name` | `String` | 工具名 |
| `arguments` | `Map<String, Object>` | 参数字典（JSON 解析后） |

### 3.2 LLMResponse

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `content` | `String` 或 `null` | 文本回复 |
| `toolCalls` | `List<ToolCallRequest>` | 工具调用列表，默认空列表 |
| `finishReason` | `String` | 结束原因，默认 `"stop"` |
| `usage` | `Map<String, Integer>` | token 用量等，默认空 Map |
| `reasoningContent` | `String` 或 `null` | 思考链内容（如 Kimi、DeepSeek-R1），默认 `null` |

**方法**：`boolean hasToolCalls()`，即 `toolCalls != null && !toolCalls.isEmpty()`。

### 3.3 LLMProvider（接口）

**构造**：实现类可由工厂根据 `Config` 创建（如 `ProviderFactory.fromConfig(config)`），构造参数可包含 `apiKey`、`apiBase` 等。

**接口方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `chat` | `LLMResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools, String model, int maxTokens, double temperature)` 或返回 `CompletableFuture<LLMResponse>` | 发起对话补全；messages 含 `role`、`content`，可选 `tool_calls` 等；tools 为 OpenAI 风格 function 定义列表 |
| `getDefaultModel` | `String getDefaultModel()` | 返回该 provider 的默认模型名 |

新增 provider 时，实现 `LLMProvider` 并在 `ProviderRegistry` 或工厂中按 model 关键词或配置选择实现类；Config 的 `providers` 下增加对应 POJO 字段。

---

## 四、工具接口（Tool / ToolRegistry）

**包**：`com.javaclaw.agent.tools`

### 4.1 Tool（接口或抽象类）

自定义工具需实现 `Tool` 接口（或继承抽象类）。

**抽象/必须实现**：

| 成员 | 类型 | 说明 |
|------|------|------|
| `getName()` | `String` | 工具名，LLM 通过此名调用 |
| `getDescription()` | `String` | 工具说明，会传给 LLM |
| `getParameters()` | `Map<String, Object>` 或 JSON Schema 结构 | JSON Schema（object），描述参数 |
| `execute` | `String execute(Map<String, Object> params)` | 执行工具，返回结果字符串（会直接作为 tool result 给 LLM）；Java 8 下同步即可，如需异步可用 `ExecutorService` 封装 |

**可复用方法（抽象类中提供）**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `validateParams` | `List<String> validateParams(Map<String, Object> params)` | 按 parameters schema 校验参数，返回错误列表，空表示合法 |
| `toSchema` | `Map<String, Object> toSchema()` | 返回 OpenAI function 格式：`{"type":"function","function":{"name","description","parameters"}}` |

注册方式：在 `AgentLoop.registerDefaultTools()` 中或后续对 `toolRegistry.register(tool)` 注册；MCP 工具由 MCP 模块动态注册。

### 4.2 ToolRegistry

**构造**：`new ToolRegistry()`。

**方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `register` | `void register(Tool tool)` | 注册工具，以 `tool.getName()` 为键 |
| `unregister` | `void unregister(String name)` | 按名称移除 |
| `get` | `Tool get(String name)` 或 `Optional<Tool> get(String name)` | 按名称获取 |
| `has` | `boolean has(String name)` | 是否已注册 |
| `getDefinitions` | `List<Map<String, Object>> getDefinitions()` | 所有工具的 OpenAI function 定义，供 `provider.chat(tools=...)` 使用 |
| `execute` | `String execute(String name, Map<String, Object> params)` | 校验后执行指定工具，返回结果字符串；未找到或校验失败返回错误信息字符串 |

**属性**：`getToolNames()` → `List<String>`。

---

## 五、Agent 与上下文

**包**：`com.javaclaw.agent`

### 5.1 AgentLoop（公开接口）

**构造**：  
`AgentLoop(MessageBus bus, LLMProvider provider, Path workspace, String model, int maxIterations, double temperature, int maxTokens, int memoryWindow, String braveApiKey, ExecToolConfig execConfig, CronService cronService, boolean restrictToWorkspace, SessionManager sessionManager, Map<String, MCPServerConfig> mcpServers)`  
（部分参数可为 Optional 或 null，用默认值。）

**公开方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `run` | `void run()` | 主循环：不断 `consumeInbound`，对每条消息 `processMessage`，将返回的 `OutboundMessage` 写入 `bus.publishOutbound`；用于 Gateway 中与 channel 任务并发运行 |
| `processDirect` | `String processDirect(String content, String sessionKey, String channel, String chatId)` | 直接处理一条用户消息（不经过总线入队），返回 Agent 回复文本；用于 CLI、Cron、Heartbeat；重载可默认 sessionKey=`"cli:direct"`, channel=`"cli"`, chatId=`"direct"` |
| `stop` | `void stop()` | 将主循环标志置为停止 |
| `closeMcp` | `void closeMcp()` 或 `CompletableFuture<Void> closeMcpAsync()` | 关闭 MCP 连接，退出前调用 |

### 5.2 ContextBuilder

**包**：`com.javaclaw.agent`  
**构造**：`ContextBuilder(Path workspace)`。

**主要方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `buildSystemPrompt` | `String buildSystemPrompt(List<String> skillNames)` 或 `String buildSystemPrompt()` | 组装系统提示：身份、bootstrap 文件、记忆、技能（常驻 + 摘要） |
| `buildMessages` | `List<Map<String, Object>> buildMessages(List<Map<String, Object>> history, String currentMessage, List<String> skillNames, List<String> media, String channel, String chatId)` | 返回给 LLM 的消息列表：`[system, ...history, userMessage]` |
| `addToolResult` | `void addToolResult(List<Map<String, Object>> messages, String toolCallId, String toolName, String result)` | 向消息列表追加一条 tool 结果 |
| `addAssistantMessage` | `void addAssistantMessage(List<Map<String, Object>> messages, String content, List<Map<String, Object>> toolCalls, String reasoningContent)` | 向消息列表追加一条 assistant 消息（可含 tool_calls、reasoning_content） |

### 5.3 MemoryStore

**包**：`com.javaclaw.agent`  
**构造**：`MemoryStore(Path workspace)`。  
在 `workspace/memory/` 下使用 `MEMORY.md`（长期记忆）和 `HISTORY.md`（可 grep 的日志）。

**方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `readLongTerm` | `String readLongTerm()` | 读取 MEMORY.md 内容 |
| `writeLongTerm` | `void writeLongTerm(String content)` | 写入 MEMORY.md |
| `appendHistory` | `void appendHistory(String entry)` | 向 HISTORY.md 追加一行记录 |
| `getMemoryContext` | `String getMemoryContext()` | 返回带标题的长期记忆片段，供 system prompt 使用 |

---

## 六、会话（Session / SessionManager）

**包**：`com.javaclaw.session`

### 6.1 Session

单次对话会话，消息为仅追加的列表。

**字段**：`key`（如 `channel:chatId`）、`messages`（`List<Map<String, Object>>`）、`createdAt`、`updatedAt`、`metadata`、`lastConsolidated`。

**方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `addMessage` | `void addMessage(String role, String content, Map<String, Object> extra)` | 追加一条消息（可带 toolsUsed 等） |
| `getHistory` | `List<Map<String, Object>> getHistory(int maxMessages)` | 最近 N 条消息的 LLM 格式（role + content） |
| `clear` | `void clear()` | 清空消息并重置 lastConsolidated |

### 6.2 SessionManager

**构造**：`SessionManager(Path workspace)`。会话文件存放在 `~/.nanobot/sessions/`（或通过 Config 配置），与 workspace 解耦。

**方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `getOrCreate` | `Session getOrCreate(String key)` | 按 key 取缓存或从磁盘加载，不存在则新建 |
| `save` | `void save(Session session)` | 将会话持久化到磁盘 |
| `invalidate` | `void invalidate(String key)` | 从缓存移除，下次 getOrCreate 会重新加载 |

---

## 七、技能（SkillsLoader）

**包**：`com.javaclaw.agent`

**构造**：`SkillsLoader(Path workspace, Path builtinSkillsDir)`。  
技能目录：`workspace/skills/<name>/SKILL.md` 与内置 `builtinSkillsDir/<name>/SKILL.md`。

**主要方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `listSkills` | `List<Map<String, String>> listSkills(boolean filterUnavailable)` | 列出技能，每项含 name、path、source（workspace/builtin）；filterUnavailable 为 true 时过滤掉依赖未满足的 |
| `loadSkill` | `String loadSkill(String name)` 或 `Optional<String> loadSkill(String name)` | 按名称读取 SKILL.md 内容 |
| `loadSkillsForContext` | `String loadSkillsForContext(List<String> skillNames)` | 将多个技能内容拼接成一段文本，供 system prompt 使用 |
| `buildSkillsSummary` | `String buildSkillsSummary()` | 生成技能摘要（名称与描述），供 system prompt 中“可用技能列表”使用 |
| `getAlwaysSkills` | `List<String> getAlwaysSkills()` | 返回需常驻加载的技能名列表（由 SKILL 元数据决定） |
| `getSkillMetadata` | `Map<String, Object> getSkillMetadata(String name)` 或 `Optional<Map<String, Object>>` | 返回技能元数据（如依赖、available 等） |

---

## 八、配置

**包**：`com.javaclaw.config`

### 8.1 Config 根结构

POJO，与 `~/.nanobot/config.json` 中 **javaClaw 所需部分** 一致，键名 camelCase。**渠道仅保留钉钉**。

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `agents` | `AgentsConfig` | 默认 workspace、model、maxTokens、temperature、maxToolIterations、memoryWindow |
| `channels` | `ChannelsConfig` | **仅需** `dingtalk`（DingTalkConfig）；可不包含 telegram、discord、feishu 等 |
| `providers` | `ProvidersConfig` | 各 LLM provider 的 apiKey、apiBase、extraHeaders |
| `gateway` | `GatewayConfig` | host、port |
| `tools` | `ToolsConfig` | web.search、exec（timeout）、restrictToWorkspace、mcpServers |

**Config 常用方法**：

- `getWorkspacePath()` → `Path`：展开后的工作区路径
- `getProvider(model)` → `ProviderConfig` 或 `Optional<ProviderConfig>`
- `getProviderName(model)` → `String` 或 `Optional<String>`
- `getApiKey(model)`、`getApiBase(model)`

### 8.2 配置加载与保存

**类**：`ConfigLoader`（或 `ConfigLoader` 静态方法）

| 方法 | 签名 | 说明 |
|------|------|------|
| `getConfigPath` | `static Path getConfigPath()` | 默认配置文件路径：`~/.nanobot/config.json` |
| `getDataDir` | `static Path getDataDir()` | 数据目录（如 cron、sessions 的父目录） |
| `loadConfig` | `static Config loadConfig()` 或 `loadConfig(Path configPath)` | 从文件加载并反序列化，失败则返回默认 Config |
| `saveConfig` | `static void saveConfig(Config config)` 或 `saveConfig(Config config, Path configPath)` | 将 Config 写回 JSON（键为 camelCase） |

---

## 九、相关文档

- [01-项目梳理-Java版](./01-项目梳理-Java版.md) — javaClaw 架构与模块职责（Java 8、仅钉钉）
- [02-调用链-Java版](./02-调用链-Java版.md) — javaClaw 从入口到消息处理、Cron/Heartbeat 的调用关系  
