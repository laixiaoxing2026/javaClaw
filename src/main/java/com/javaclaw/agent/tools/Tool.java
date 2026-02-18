package com.javaclaw.agent.tools;

import java.util.Map;

/**
 * 工具接口：LLM 通过名称与参数调用。需实现 getName、getDescription、getParameters、execute。
 */
public interface Tool {

    /** 工具名，LLM 通过此名调用 */
    String getName();

    /** 工具说明，会传给 LLM */
    String getDescription();

    /** JSON Schema（object），描述参数 */
    Map<String, Object> getParameters();

    /**
     * 执行工具，返回结果字符串（作为 tool result 给 LLM）。
     * params 为 LLM 传入参数与调用方注入的 channel、chatId、metadata 等合并后的结果。
     */
    String execute(Map<String, Object> params);
}
