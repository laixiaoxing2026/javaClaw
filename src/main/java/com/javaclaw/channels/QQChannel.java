package com.javaclaw.channels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javaclaw.bus.MessageBus;
import com.javaclaw.bus.OutboundMessage;
import com.javaclaw.config.GatewayConfig;
import com.javaclaw.config.QQConfig;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * QQ 渠道实现：通过 WebSocket 连接 QQ 机器人网关。
 * 流程：获取 WSS 接入点 → 建立连接 → 收 Hello(op 10) → 发 Identify(op 2) → 收 Ready → 按心跳周期发 Heartbeat(op 1)，
 * 收到消息事件(op 0，如 GROUP_AT_MESSAGE_CREATE、C2C_MESSAGE_CREATE) 后解析并入队；断线后可 Resume(op 6) 恢复。
 */
public class QQChannel extends BaseChannel {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final int OP_DISPATCH = 0;
    private static final int OP_HEARTBEAT = 1;
    private static final int OP_IDENTIFY = 2;
    private static final int OP_RESUME = 6;
    private static final int OP_RECONNECT = 7;
    private static final int OP_INVALID_SESSION = 9;
    private static final int OP_HELLO = 10;
    private static final int OP_HEARTBEAT_ACK = 11;

    private final QQConfig config;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
    private final AtomicLong lastSeq = new AtomicLong(-1);
    private final AtomicReference<String> sessionIdRef = new AtomicReference<>();
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile boolean needIdentify = true;
    /** 当前使用的 access_token，用于 Identify/Resume；重连时在 connect() 内重新获取 */
    private volatile String cachedAccessToken;

    public QQChannel(QQConfig qqConfig, GatewayConfig gatewayConfig, MessageBus bus) {
        super("qq", bus);
        this.config = qqConfig != null ? qqConfig : new QQConfig();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "qq-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public boolean isAllowed(String senderId) {
        List<String> allow = config.getAllowFrom();
        if (allow == null || allow.isEmpty()) {
            return true;
        }
        return allow.contains(senderId);
    }

    @Override
    public void start() {
        if (running.getAndSet(true)) {
            return;
        }
        connect();
    }

    @Override
    public void stop() {
        running.set(false);
        cancelHeartbeat();
        WebSocket ws = webSocketRef.getAndSet(null);
        if (ws != null) {
            ws.close(1000, "stop");
        }
        scheduler.shutdown();
    }

    /** 文本消息类型，见 QQ 开放平台发送消息接口 */
    private static final int MSG_TYPE_TEXT = 0;

    @Override
    public void send(OutboundMessage msg) {
        if (msg == null) {
            return;
        }
        String chatId = msg.getChatId();
        String content = msg.getContent();
        if (chatId == null || content == null) {
            return;
        }
        Map<String, Object> metadata = msg.getMetadata();
        String msgId = metadata != null && metadata.get("qq_msg_id") != null ? metadata.get("qq_msg_id").toString() : null;
        String token;
        try {
            token = cachedAccessToken != null ? cachedAccessToken : getAccessToken();
        } catch (IOException e) {
            System.err.println("[QQ] send getAccessToken failed: " + e.getMessage());
            return;
        }
        String base = config.getGatewayApiBase().replaceAll("/$", "");
        String path = "/v2/users/" + chatId + "/messages";
        ObjectNode body = MAPPER.createObjectNode();
        body.put("content", content);
        body.put("msg_type", MSG_TYPE_TEXT);
        if (msgId != null && !msgId.isEmpty()) {
            body.put("msg_id", msgId);
        }
        RequestBody requestBody;
        try {
            requestBody = RequestBody.create(MAPPER.writeValueAsString(body), JSON);
        } catch (Exception e) {
            System.err.println("[QQ] send build body failed: " + e.getMessage());
            return;
        }
        Request request = new Request.Builder()
                .url(base + path)
                .addHeader("Authorization", "QQBot " + token)
                .post(requestBody)
                .build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                String errBody = resp.body() != null ? resp.body().string() : "";
                System.err.println("[QQ] send failed: " + resp.code() + " " + errBody);
                if (resp.code() == 401 && cachedAccessToken != null) {
                    cachedAccessToken = null;
                    send(msg);
                }
                return;
            }
        } catch (IOException e) {
            System.err.println("[QQ] send request failed: " + e.getMessage());
        }
    }

    /**
     * 获取 access_token：若配置了 clientSecret 则 POST getAppAccessToken；否则使用 config.token 作为 access_token。
     */
    private String getAccessToken() throws IOException {
        String secret = config.getClientSecret();
        if (secret != null && !secret.isEmpty() && config.getAppId() != null && !config.getAppId().isEmpty()) {
            String body = "{\"appId\":\"" + escapeJson(config.getAppId()) + "\",\"clientSecret\":\"" + escapeJson(secret) + "\"}";
            Request req = new Request.Builder()
                    .url("https://bots.qq.com/app/getAppAccessToken")
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    throw new IOException("getAppAccessToken failed: " + resp.code());
                }
                JsonNode node = MAPPER.readTree(resp.body().string());
                if (node.has("access_token")) {
                    return node.get("access_token").asText();
                }
                throw new IOException("getAppAccessToken response missing access_token");
            }
        }
        if (config.getToken() != null && !config.getToken().isEmpty()) {
            return config.getToken();
        }
        throw new IllegalStateException("QQ channel: need clientSecret+appId or token (access_token) in config");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * GET /gateway 获取 WSS 地址，需带 Authorization: QQBot {access_token}。
     */
    private String fetchGatewayUrl(String accessToken) throws IOException {
        String base = config.getGatewayApiBase().replaceAll("/$", "");
        Request req = new Request.Builder()
                .url(base + "/gateway")
                .addHeader("Authorization", "QQBot " + accessToken)
                .get()
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("GET /gateway failed: " + resp.code() + " " + (resp.body() != null ? resp.body().string() : ""));
            }
            JsonNode node = MAPPER.readTree(resp.body().string());
            if (node.has("url")) {
                return node.get("url").asText();
            }
            throw new IOException("GET /gateway response missing url");
        }
    }

    private void connect() {
        if (!running.get()) {
            return;
        }
        try {
            cachedAccessToken = getAccessToken();
            String wssUrl = fetchGatewayUrl(cachedAccessToken);
            Request request = new Request.Builder().url(wssUrl).build();
            httpClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                    webSocketRef.set(webSocket);
                    // 等待服务端下发 Hello(op 10)，在 onMessage 里处理
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    handleFrame(webSocket, text);
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    webSocketRef.compareAndSet(webSocket, null);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                    webSocketRef.compareAndSet(webSocket, null);
                    cancelHeartbeat();
                    if (running.get()) {
                        System.err.println("[QQ] WebSocket failure, reconnecting: " + t.getMessage());
                        scheduler.schedule(() -> connect(), 3, TimeUnit.SECONDS);
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[QQ] connect error: " + e.getMessage());
            if (running.get()) {
                scheduler.schedule(() -> connect(), 5, TimeUnit.SECONDS);
            }
        }
    }

    private void handleFrame(WebSocket webSocket, String text) {
        try {
            JsonNode root = MAPPER.readTree(text);
            int op = root.has("op") ? root.get("op").asInt() : -1;
            JsonNode d = root.get("d");
            int s = root.has("s") && !root.get("s").isNull() ? root.get("s").asInt() : -1;
            if (s >= 0) {
                lastSeq.set(s);
            }

            String token = cachedAccessToken;
            switch (op) {
                case OP_HELLO:
                    int interval = (d != null && d.has("heartbeat_interval")) ? d.get("heartbeat_interval").asInt(45000) : 45000;
                    startHeartbeat(webSocket, interval);
                    if (needIdentify) {
                        sendIdentify(webSocket, token);
                    } else {
                        sendResume(webSocket, token);
                    }
                    break;
                case OP_DISPATCH:
                    String t = root.has("t") ? root.get("t").asText() : "";
                    if ("READY".equals(t)) {
                        if (d != null && d.has("session_id")) {
                            sessionIdRef.set(d.get("session_id").asText());
                            needIdentify = false;
                        }
                    } else if ("RESUMED".equals(t)) {
                        needIdentify = false;
                    } else {
                        dispatchEvent(t, d);
                    }
                    break;
                case OP_HEARTBEAT_ACK:
                    break;
                case OP_RECONNECT:
                    cancelHeartbeat();
                    webSocket.close(1000, "reconnect");
                    connect();
                    break;
                case OP_INVALID_SESSION:
                    needIdentify = true;
                    sessionIdRef.set(null);
                    cancelHeartbeat();
                    webSocket.close(1000, "invalid session");
                    scheduler.schedule(() -> connect(), 2, TimeUnit.SECONDS);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.err.println("[QQ] handle frame error: " + e.getMessage());
        }
    }

    private void startHeartbeat(WebSocket webSocket, int intervalMs) {
        cancelHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            WebSocket ws = webSocketRef.get();
            if (ws == null) return;
            Long seq = lastSeq.get() >= 0 ? lastSeq.get() : null;
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("op", OP_HEARTBEAT);
            payload.set("d", seq != null ? JsonNodeFactory.instance.numberNode(seq) : JsonNodeFactory.instance.nullNode());
            try {
                ws.send(MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                System.err.println("[QQ] heartbeat send error: " + e.getMessage());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void sendIdentify(WebSocket webSocket, String accessToken) {
        ObjectNode d = MAPPER.createObjectNode();
        d.put("token", "QQBot " + accessToken);
        d.put("intents", config.getIntents());
        d.putArray("shard").add(0).add(1);
        ObjectNode props = d.putObject("properties");
        props.put("$os", "java");
        props.put("$browser", "javaclaw");
        props.put("$device", "javaclaw");
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("op", OP_IDENTIFY);
        payload.set("d", d);
        try {
            webSocket.send(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            System.err.println("[QQ] identify send error: " + e.getMessage());
        }
    }

    private void sendResume(WebSocket webSocket, String accessToken) {
        String sid = sessionIdRef.get();
        long seq = lastSeq.get();
        if (sid == null || seq < 0) {
            needIdentify = true;
            sendIdentify(webSocket, accessToken);
            return;
        }
        ObjectNode d = MAPPER.createObjectNode();
        d.put("token", "QQBot " + accessToken);
        d.put("session_id", sid);
        d.put("seq", (int) seq);
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("op", OP_RESUME);
        payload.set("d", d);
        try {
            webSocket.send(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            System.err.println("[QQ] resume send error: " + e.getMessage());
        }
    }

    /**
     * 根据事件类型 t 解析 d，提取 senderId、chatId、content 并调用 handleMessage。
     * 仅支持单聊：C2C_MESSAGE_CREATE、DIRECT_MESSAGE_CREATE；群聊事件 GROUP_AT_MESSAGE_CREATE 不处理。
     */
    private void dispatchEvent(String t, JsonNode d) {
        if (d == null) return;
        if ("GROUP_AT_MESSAGE_CREATE".equals(t)) {
            return;
        }
        String senderId = null;
        String chatId = null;
        String content = null;
        if (d.has("author")) {
            JsonNode author = d.get("author");
            if (author.has("user_openid")) senderId = author.get("user_openid").asText();
            else if (author.has("member_openid")) senderId = author.get("member_openid").asText();
            else if (author.has("id")) senderId = author.get("id").asText();
        }
        if (d.has("channel_id")) {
            chatId = d.get("channel_id").asText();
        } else if (senderId != null) {
            chatId = senderId;
        }
        if (d.has("content")) {
            content = d.get("content").asText();
        }
        if (senderId != null && content != null) {
            Map<String, Object> metadata = new HashMap<>();
            if (d.has("id")) {
                metadata.put("qq_msg_id", d.get("id").asText());
            }
            handleMessage(senderId, chatId != null ? chatId : senderId, content, Collections.<String>emptyList(), metadata);
        }
    }
}
