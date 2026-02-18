package com.javaclaw.config;

import java.util.Collections;
import java.util.List;

/**
 * QQ 渠道配置。对应 config.json 中 channels.qq。
 * 连接方式：WebSocket。先 GET /gateway 取 WSS 地址，再鉴权、心跳、收事件。
 * allowFrom 为空表示允许所有人；非空则仅允许列表中的 senderId（如 openid）。
 */
public class QQConfig {

    /** 是否启用 QQ 渠道 */
    private boolean enabled = false;
    /** 允许接收消息的发送者 ID 列表（如 user_openid），空表示所有人 */
    private List<String> allowFrom;
    /** QQ 机器人 appId（开放平台获取，用于 getAppAccessToken） */
    private String appId;
    /** 机器人 clientSecret（开放平台获取），用于 POST getAppAccessToken 换取 access_token；若未配置则可将 token 当作 access_token 使用（需自行刷新） */
    private String clientSecret;
    /** 机器人 token：若未配置 clientSecret，则视为 access_token，Identify 时格式为 "QQBot {AccessToken}" */
    private String token;
    /** 回调 URL（WebSocket 方式下可选，保留兼容） */
    private String callbackUrl;
    /** 可选：QQ 机器人 secret（如 Webhook 校验用） */
    private String secret;
    /** 获取 WSS 接入点的 API 根地址，如 https://api.sgroup.qq.com */
    private String gatewayApiBase = "https://api.sgroup.qq.com";
    /** 事件订阅 intents 位掩码，默认 GROUP_AND_C2C_EVENT(1<<25) | DIRECT_MESSAGE(1<<12)，即群@与单聊、私信 */
    private Integer intents = (1 << 25) | (1 << 12);

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

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getGatewayApiBase() {
        return gatewayApiBase == null || gatewayApiBase.isEmpty() ? "https://api.sgroup.qq.com" : gatewayApiBase;
    }

    public void setGatewayApiBase(String gatewayApiBase) {
        this.gatewayApiBase = gatewayApiBase;
    }

    /** 事件订阅 intents，默认 (1<<25)|(1<<12) */
    public int getIntents() {
        return intents != null ? intents : ((1 << 25) | (1 << 12));
    }

    public void setIntents(Integer intents) {
        this.intents = intents;
    }
}
