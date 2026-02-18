package com.javaclaw.config;

import java.util.Collections;
import java.util.Map;

/**
 * 单个 LLM 提供商配置：apiKey、apiBase、可选 extraHeaders。
 * 对应 config.json 中 providers.&lt;name&gt; 节点。
 */
public class ProviderConfig {

    private String apiKey;
    private String apiBase;
    private Map<String, String> extraHeaders;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }

    public Map<String, String> getExtraHeaders() {
        return extraHeaders == null ? Collections.<String, String>emptyMap() : extraHeaders;
    }

    public void setExtraHeaders(Map<String, String> extraHeaders) {
        this.extraHeaders = extraHeaders;
    }
}
