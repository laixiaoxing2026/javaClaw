package com.javaclaw.agent.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表：注册、按名获取、getDefinitions（OpenAI function 列表）、execute。
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public void register(Tool tool) {
        if (tool != null) {
            tools.put(tool.getName(), tool);
        }
    }

    public void unregister(String name) {
        tools.remove(name);
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** 所有工具的 OpenAI function 定义，供 provider.chat(tools=...) 使用 */
    public List<Map<String, Object>> getDefinitions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tool t : tools.values()) {
            if (t instanceof BaseTool) {
                out.add(((BaseTool) t).toSchema());
            } else {
                Map<String, Object> fn = new java.util.HashMap<>();
                fn.put("type", "function");
                Map<String, Object> f = new java.util.HashMap<>();
                f.put("name", t.getName());
                f.put("description", t.getDescription());
                f.put("parameters", t.getParameters());
                fn.put("function", f);
                out.add(fn);
            }
        }
        return out;
    }

    /** 校验后执行指定工具，返回结果字符串；未找到或校验失败返回错误信息字符串。params 可含框架注入的 channel、chatId、metadata。 */
    public String execute(String name, Map<String, Object> params) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return "[Error: tool not found: " + name + "]";
        }
        if (tool instanceof BaseTool) {
            List<String> errs = ((BaseTool) tool).validateParams(params);
            if (errs != null && !errs.isEmpty()) {
                return "[Error: invalid params: " + String.join("; ", errs) + "]";
            }
        }
        try {
            return tool.execute(params != null ? params : Collections.<String, Object>emptyMap());
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }

    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }
}
