package com.javaclaw.agent.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具抽象基类：提供 validateParams、toSchema（OpenAI function 格式）的默认实现。
 */
public abstract class BaseTool implements Tool {

    public List<String> validateParams(Map<String, Object> params) {
        return Collections.emptyList();
    }

    /** 返回 OpenAI function 格式：{"type":"function","function":{"name","description","parameters"}} */
    public Map<String, Object> toSchema() {
        Map<String, Object> function = new HashMap<>();
        function.put("name", getName());
        function.put("description", getDescription());
        function.put("parameters", getParametersSchema());
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }

    /** 子类可覆盖；默认返回 getParameters() 包装为 {"type":"object", "properties": ...} */
    protected Map<String, Object> getParametersSchema() {
        Map<String, Object> params = getParameters();
        if (params == null || params.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("type", "object");
            empty.put("properties", new HashMap<String, Object>());
            return empty;
        }
        return params;
    }
}
