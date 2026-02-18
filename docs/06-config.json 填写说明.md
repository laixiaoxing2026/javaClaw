# config.json 填写说明

配置文件路径：**`~/.javaclawbot/config.json`**（Windows 为 `%USERPROFILE%\.javaclawbot\config.json`）。  
所有键名使用 **camelCase**（小驼峰），与下方示例一致。

---

## 一、完整示例

```json
{
  "agents": {
    "workspace": "~/.javaclawbot/workspace",
    "model": "gpt-3.5-turbo",
    "maxTokens": 4096,
    "temperature": 0.7,
    "maxToolIterations": 10,
    "memoryWindow": 20,
    "defaultProvider": "openai"
  },
  "providers": {
    "openai": {
      "apiKey": "sk-xxx",
      "apiBase": "https://api.openai.com/v1"
    }
  },
  "channels": {
    "dingtalk": {
      "enabled": false,
      "allowFrom": [],
      "appKey": "",
      "appSecret": "",
      "callbackUrl": ""
    },
    "qq": {
      "enabled": false,
      "allowFrom": [],
      "appId": "",
      "clientSecret": "",
      "token": "",
      "gatewayApiBase": "https://api.sgroup.qq.com",
      "intents": 33558528
    }
  },
  "gateway": {
    "host": "0.0.0.0",
    "port": 8765
  },
  "tools": {
    "exec": {
      "timeoutSeconds": 60
    },
    "restrictToWorkspace": true,
    "webSearchApiKey": "",
    "mcpServers": {}
  }
}
```

---

## 二、各节点说明

### 1. `agents`（必填，Agent 行为）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `workspace` | string | 否 | 工作区路径，不填则用 `~/.javaclawbot/workspace` | `"~/.javaclawbot/workspace"` |
| `model` | string | **是** | 使用的模型名，由对应 provider 支持 | `"gpt-3.5-turbo"`、`"gpt-4"`、`"deepseek-chat"` |
| `defaultProvider` | string | 否 | 使用的 provider 名，需与 `providers` 里某个 key 一致；不填则用第一个 | `"openai"` |
| `maxTokens` | number | 否 | 单次回复最大 token 数，默认 4096 | `4096` |
| `temperature` | number | 否 | 采样温度，默认 0.7 | `0.7` |
| `maxToolIterations` | number | 否 | 单轮最大工具调用次数，默认 10 | `10` |
| `memoryWindow` | number | 否 | 会话条数超过此值会触发记忆合并，默认 20 | `20` |
| `providerModels` | array | 否 | 可选，按模型指定 provider，一般可不填 | `[]` |

- **最小可运行**：至少填 `model`，且 `defaultProvider` 与 `providers` 中某个 key 对应（或只配一个 provider 不填 defaultProvider）。

---

### 2. `providers`（必填，LLM 接口）

结构为：**`providers` 是一个对象**，key 为 provider 名称（如 `openai`、`azure`、`deepseek`），value 为该 provider 的配置。

每个 provider 配置（`ProviderConfig`）：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `apiKey` | string | **是** | 调用 API 的密钥 | `"sk-xxx"` |
| `apiBase` | string | 否 | API 根地址，OpenAI 兼容；不填默认 `https://api.openai.com/v1` | `"https://api.openai.com/v1"` |
| `extraHeaders` | object | 否 | 额外 HTTP 头，key/value 均为 string | `{"X-Custom": "value"}` |

**示例：只用 OpenAI**

```json
"providers": {
  "openai": {
    "apiKey": "sk-your-openai-key",
    "apiBase": "https://api.openai.com/v1"
  }
}
```

**示例：多个 provider（如 OpenAI + DeepSeek）**

```json
"providers": {
  "openai": {
    "apiKey": "sk-openai-key",
    "apiBase": "https://api.openai.com/v1"
  },
  "deepseek": {
    "apiKey": "your-deepseek-key",
    "apiBase": "https://api.deepseek.com/v1"
  }
}
```

在 `agents` 里通过 `defaultProvider": "deepseek"` 和 `model": "deepseek-chat"` 即可切到 DeepSeek。

---

### 3. `channels`（可选，当前仅钉钉）

| 字段 | 类型 | 说明 |
|------|------|------|
| `dingtalk` | object | 钉钉渠道配置 |

`channels.dingtalk` 各字段：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `enabled` | boolean | 否 | 是否启用钉钉，默认 false |
| `allowFrom` | array of string | 否 | 允许的发送者 ID 列表，空或不存在表示允许所有人 |
| `appKey` | string | 启用时建议填 | 钉钉应用 AppKey |
| `appSecret` | string | 启用时建议填 | 钉钉应用 AppSecret |
| `callbackUrl` | string | 否 | 回调或 Stream 相关 URL，按钉钉开放平台要求配置 |

不启用钉钉时，可省略 `channels` 或 `dingtalk.enabled: false`。

**QQ 渠道**（`channels.qq`）：通过 **WebSocket** 连接 QQ 机器人网关，无需 HTTP 回调端口。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `enabled` | boolean | 否 | 是否启用 QQ 渠道，默认 false |
| `allowFrom` | array of string | 否 | 允许的发送者 ID（如 user_openid）列表，空表示允许所有人 |
| `appId` | string | 与 clientSecret 二选一 | 开放平台 appId，用于 getAppAccessToken |
| `clientSecret` | string | 与 token 二选一 | 开放平台 clientSecret，用于换取 access_token（推荐） |
| `token` | string | 与 clientSecret 二选一 | 直接使用 access_token 时填写，需自行刷新 |
| `callbackUrl` | string | 否 | 保留兼容，WebSocket 方式下可选 |
| `secret` | string | 否 | 可选，如 Webhook 校验用 |
| `gatewayApiBase` | string | 否 | 获取 WSS 的 API 根地址 | `"https://api.sgroup.qq.com"` |
| `intents` | number | 否 | 事件订阅位掩码 | 群@+单聊+私信：`(1<<25)\|(1<<12)` |

鉴权方式二选一：**推荐** 配置 `appId` + `clientSecret`，程序会请求 `https://bots.qq.com/app/getAppAccessToken` 换取 access_token 并用于 GET /gateway 与 Identify；或仅配置 `token` 为 access_token（需自行在过期前刷新）。连接流程：GET /gateway → 建立 WSS → 收 Hello(op 10) → 发 Identify(op 2) → 收 Ready → 按 heartbeat_interval 发心跳(op 1)；断线后发 Resume(op 6) 恢复。支持事件：GROUP_AT_MESSAGE_CREATE、C2C_MESSAGE_CREATE、DIRECT_MESSAGE_CREATE、AT_MESSAGE_CREATE，解析后入队供 Agent 消费。

---

### 4. `gateway`（可选）

| 字段 | 类型 | 必填 | 说明 | 默认 |
|------|------|------|------|------|
| `host` | string | 否 | 监听地址 | `"0.0.0.0"` |
| `port` | number | 否 | 监听端口（钉钉 HTTP 回调等） | `8765` |

---

### 5. `tools`（可选）

| 字段 | 类型 | 必填 | 说明 | 默认 |
|------|------|------|------|------|
| `exec` | object | 否 | 执行类工具（如 shell）配置 | 见下 |
| `exec.timeoutSeconds` | number | 否 | 单次执行超时秒数 | `60` |
| `restrictToWorkspace` | boolean | 否 | 是否把文件类工具限制在工作区内 | `true` |
| `webSearchApiKey` | string | 否 | 网页搜索 API Key（如 Brave），未实现时可留空 | - |
| `mcpServers` | object | 否 | MCP 服务名到配置的映射，暂无需求可 `{}` | `{}` |

`mcpServers` 中每个服务可选字段：`command`、`args`（数组）、`url`。

---

## 三、常见场景

### 只用 OpenAI（本地或代理）

- `agents.model`：如 `gpt-3.5-turbo`、`gpt-4`。
- `agents.defaultProvider`：`"openai"`（或只配一个 provider 不填）。
- `providers.openai`：填 `apiKey`，`apiBase` 不填或用 `https://api.openai.com/v1`；若走代理则 `apiBase` 填代理地址。

### 只用 DeepSeek / 其他兼容 API

- `agents.model`：如 `deepseek-chat`。
- `agents.defaultProvider`：与下面 key 一致，如 `"deepseek"`。
- `providers` 里增加一项，例如：
  - key：`"deepseek"`
  - value：`"apiKey"`、`"apiBase": "https://api.deepseek.com/v1"`。

### 启用钉钉

- `channels.dingtalk.enabled`: `true`。
- 填好 `appKey`、`appSecret`，并按钉钉文档配置 `callbackUrl`（若需要）。
- `gateway.port` 与钉钉回调 URL 的端口一致（如 8765）。

---

## 四、注意事项

1. **键名**：必须 camelCase（如 `defaultProvider`、`apiBase`），不要用下划线。
2. **路径**：`workspace` 支持 `~`，程序会展开为当前用户目录。
3. **敏感信息**：`apiKey`、`appSecret` 等不要提交到版本库；可用环境变量或密钥管理，再在部署时写入或生成 config。
4. **首次生成**：若还没有 config 文件，可先执行 `java -jar javaclaw.jar onboard`，会按环境变量或默认值生成一份 `~/.javaclawbot/config.json`，再按本文修改即可。
