# javaClaw

超轻量个人 AI 助手（Java 8，仅钉钉渠道）。与 [nanobot](https://github.com/nanobot) 架构一致，核心逻辑保持精简。

## 技术栈

- **Java 8**，不使用虚拟线程
- **CLI**：Picocli
- **配置**：JSON + POJO（Jackson），路径统一为 **javaclawbot**（`~/.javaclawbot/config.json`、`~/.javaclawbot/workspace` 等）
- **LLM**：HTTP 调用 OpenAI 兼容 API（`LLMProvider` 抽象）
- **渠道**：仅钉钉（DingTalk）

## 构建与运行

```bash
# 编译
mvn compile

# 打包可执行 JAR（含依赖）
mvn package

# 初始化配置与工作区
java -jar target/javaclaw.jar onboard

# 编辑 ~/.javaclawbot/config.json，设置 providers.openai.apiKey 等

# 单条消息
java -jar target/javaclaw.jar agent -m "你好"

# 交互模式
java -jar target/javaclaw.jar agent

# 启动 Gateway（钉钉 + Agent）
java -jar target/javaclaw.jar gateway
```

## 子命令

| 命令 | 说明 |
|------|------|
| `gateway` | 启动 Gateway（钉钉渠道 + Agent 循环 + outbound 分发） |
| `agent` | CLI 运行 Agent（`-m` 单条或交互） |
| `onboard` | 初始化 ~/.javaclawbot 与默认 config.json |
| `status` | 查看配置与工作区状态 |
| `cron` | 定时任务管理（占位） |

## 配置路径（javaclawbot）

- 配置文件：`~/.javaclawbot/config.json`
- 数据目录：`~/.javaclawbot`
- 工作区：`~/.javaclawbot/workspace`
- 会话：`~/.javaclawbot/sessions/`

## 文档

- [01-项目梳理-Java版](docs/01-项目梳理-Java版.md)
- [02-调用链-Java版](docs/02-调用链-Java版.md)
- [03-接口文档-Java版](docs/03-接口文档-Java版.md)
- [04-待办清单-Java版](docs/04-待办清单-Java版.md)
- [05-最终技术方案-Java版](docs/05-最终技术方案-Java版.md)
