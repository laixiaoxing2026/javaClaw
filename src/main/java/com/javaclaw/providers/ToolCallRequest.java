package com.javaclaw.providers;

import java.util.Collections;
import java.util.Map;

/**
 * LLM 返回的单条工具调用请求。id、name、arguments（JSON 解析后的 Map）。
 */
public class ToolCallRequest {

    private String id;
    private String name;
    private Map<String, Object> arguments;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments == null ? Collections.<String, Object>emptyMap() : arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
