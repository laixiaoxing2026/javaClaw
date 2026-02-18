package com.javaclaw.channels;

import com.javaclaw.config.DingTalkConfig;

import java.util.List;

/**
 * 钉钉渠道配置封装，供 DingTalkChannel 使用 isAllowed 等。
 */
public class DingTalkConfigWrapper {

    private final DingTalkConfig config;

    public DingTalkConfigWrapper(DingTalkConfig config) {
        this.config = config != null ? config : new DingTalkConfig();
    }

    public List<String> getAllowFrom() {
        return config.getAllowFrom();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public String getAppKey() {
        return config.getAppKey();
    }

    public String getAppSecret() {
        return config.getAppSecret();
    }
}
