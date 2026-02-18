package com.javaclaw.config;

/**
 * 单个 MCP 服务配置。对应 config.json 中 tools.mcpServers.&lt;name&gt;。
 */
public class MCPServerConfig {

    private String command;
    private String[] args;
    private String url;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String[] getArgs() {
        return args;
    }

    public void setArgs(String[] args) {
        this.args = args;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
