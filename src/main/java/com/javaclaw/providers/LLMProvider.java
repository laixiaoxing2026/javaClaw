package com.javaclaw.providers;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM 提供商接口。发起对话补全，支持 tools；实现类由 ProviderFactory 根据 Config 创建。
 */
public interface LLMProvider {

    /**
     * 发起对话补全。messages 含 role、content，可选 tool_calls；tools 为 OpenAI 风格 function 定义列表。
     *
     * @param messages  消息列表
     * @param tools     工具定义（OpenAI function 格式）
     * @param model     模型名
     * @param maxTokens 最大 token
     * @param temperature 温度
     * @return 响应，含 content、toolCalls 等
     */
    LLMResponse chat(List<Map<String, Object>> messages,
                     List<Map<String, Object>> tools,
                     String model,
                     int maxTokens,
                     double temperature);

    /**
     * 同上，可选流式：当 streamConsumer 非 null 时，实现类可对 content 增量调用 streamConsumer，再返回完整 LLMResponse。
     * 默认实现忽略 streamConsumer 并调用无参重载。
     */
    default LLMResponse chat(List<Map<String, Object>> messages,
                             List<Map<String, Object>> tools,
                             String model,
                             int maxTokens,
                             double temperature,
                             Consumer<String> streamConsumer) {
        return chat(messages, tools, model, maxTokens, temperature);
    }

    /** 返回该 provider 的默认模型名 */
    String getDefaultModel();
}
