package com.javaclaw.config;

import java.util.Collections;
import java.util.Map;

/**
 * 所有 LLM 提供商配置，key 为 provider 名称（如 "openai"）。
 * 对应 config.json 中 providers 节点。
 */
public class ProvidersConfig {

    private Map<String, ProviderConfig> providers;

    public Map<String, ProviderConfig> getProviders() {
        return providers == null ? Collections.<String, ProviderConfig>emptyMap() : providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }
}
