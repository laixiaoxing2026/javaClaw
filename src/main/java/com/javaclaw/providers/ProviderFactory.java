package com.javaclaw.providers;

import com.javaclaw.config.Config;
import com.javaclaw.config.ProviderConfig;

import java.util.Optional;

/**
 * 根据 Config 创建 LLMProvider。按 model 或 defaultProvider 选取配置并实例化 OpenAI 兼容实现。
 */
public final class ProviderFactory {

    private ProviderFactory() {
    }

    /**
     * 从 config 得到 LLMProvider。使用 config 中第一个或 defaultProvider 对应的 apiKey/apiBase 创建 OpenAICompatibleProvider。
     */
    public static LLMProvider fromConfig(Config config) {
        Optional<ProviderConfig> opt = config.getProvider(config.getAgents().getModel());
        if (!opt.isPresent()) {
            throw new IllegalStateException("No LLM provider configured. Add providers in ~/.javaclawbot/config.json");
        }
        ProviderConfig p = opt.get();
        String apiKey = p.getApiKey();
        String apiBase = p.getApiBase();
        if (apiKey == null) {
            apiKey = "";
        }
        if (apiBase == null) {
            apiBase = "https://api.openai.com/v1";
        }
        String model = config.getAgents().getModel();
        if (model == null || model.isEmpty()) {
            model = "gpt-3.5-turbo";
        }
        return new OpenAICompatibleProvider(apiKey, apiBase, p.getExtraHeaders(), model);
    }
}
