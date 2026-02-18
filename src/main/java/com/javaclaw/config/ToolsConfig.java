package com.javaclaw.config;

import java.util.Collections;
import java.util.Map;

/**
 * 工具相关配置：exec 超时、restrictToWorkspace、mcpServers 等。对应 config.json 中 tools。
 */
public class ToolsConfig {

    private ExecToolConfig exec;
    /** 是否将文件类工具限制在工作区内 */
    private boolean restrictToWorkspace = true;
    /** MCP 服务名 -> 配置 */
    private Map<String, MCPServerConfig> mcpServers;
    /** 网页搜索 API key（如 Brave） */
    private String webSearchApiKey;

    public ExecToolConfig getExec() {
        return exec == null ? new ExecToolConfig() : exec;
    }

    public void setExec(ExecToolConfig exec) {
        this.exec = exec;
    }

    public boolean isRestrictToWorkspace() {
        return restrictToWorkspace;
    }

    public void setRestrictToWorkspace(boolean restrictToWorkspace) {
        this.restrictToWorkspace = restrictToWorkspace;
    }

    public Map<String, MCPServerConfig> getMcpServers() {
        return mcpServers == null ? Collections.<String, MCPServerConfig>emptyMap() : mcpServers;
    }

    public void setMcpServers(Map<String, MCPServerConfig> mcpServers) {
        this.mcpServers = mcpServers;
    }

    public String getWebSearchApiKey() {
        return webSearchApiKey;
    }

    public void setWebSearchApiKey(String webSearchApiKey) {
        this.webSearchApiKey = webSearchApiKey;
    }
}
