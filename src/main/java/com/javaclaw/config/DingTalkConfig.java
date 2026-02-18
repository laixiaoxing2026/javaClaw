package com.javaclaw.config;

import java.util.Collections;
import java.util.List;

/**
 * 钉钉渠道配置。对应 config.json 中 channels.dingtalk。
 * allowFrom 为空表示允许所有人；非空则仅允许列表中的 senderId。
 */
public class DingTalkConfig {

    /** 是否启用钉钉渠道 */
    private boolean enabled = false;
    /** 允许接收消息的发送者 ID 列表，空表示所有人 */
    private List<String> allowFrom;
    /** 钉钉 AppKey（Stream/HTTP 回调所需） */
    private String appKey;
    /** 钉钉 AppSecret */
    private String appSecret;
    /** 回调或 Stream 相关配置，按钉钉开放平台约定扩展 */
    private String callbackUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowFrom() {
        return allowFrom == null ? Collections.<String>emptyList() : allowFrom;
    }

    public void setAllowFrom(List<String> allowFrom) {
        this.allowFrom = allowFrom;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }
}
