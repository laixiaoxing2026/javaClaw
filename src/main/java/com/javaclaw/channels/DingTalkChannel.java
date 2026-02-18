package com.javaclaw.channels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.bus.MessageBus;
import com.javaclaw.bus.OutboundMessage;
import com.javaclaw.config.DingTalkConfig;
import com.javaclaw.config.GatewayConfig;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 钉钉渠道实现：start() 启动 HTTP 回调服务接收钉钉推送；send() 调用钉钉 OpenAPI 发消息（需 appKey/appSecret）。
 */
public class DingTalkChannel extends BaseChannel {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DingTalkConfigWrapper config;
    private final int port;
    private HttpServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DingTalkChannel(DingTalkConfig dingTalkConfig, GatewayConfig gatewayConfig, MessageBus bus) {
        super("dingtalk", bus);
        this.config = new DingTalkConfigWrapper(dingTalkConfig);
        this.port = gatewayConfig != null && gatewayConfig.getPort() > 0 ? gatewayConfig.getPort() : 8765;
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
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handleRequest);
            server.setExecutor(executor);
            server.start();
        } catch (Exception e) {
            running.set(false);
            throw new RuntimeException("DingTalk HTTP server start failed", e);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (server != null) {
            server.stop(0);
            server = null;
        }
        executor.shutdown();
    }

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
        // 钉钉机器人发消息需调用 OpenAPI（需 access_token）。此处占位：仅打印日志，实际可接入钉钉机器人发送 API。
        System.err.println("[DingTalk] send to " + chatId + ": " + (content.length() > 80 ? content.substring(0, 80) + "..." : content));
    }

    private void handleRequest(HttpExchange exchange) {
        try {
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream in = exchange.getRequestBody();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                String body = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                if (body == null || body.trim().isEmpty()) {
                    sendResponse(exchange, 400, "body required");
                    return;
                }
                JsonNode root = MAPPER.readTree(body);
                String senderId = root.has("senderId") ? root.get("senderId").asText() : "";
                String chatId = root.has("chatId") ? root.get("chatId").asText() : "";
                String content = root.has("content") ? root.get("content").asText() : "";
                List<String> media = new ArrayList<>();
                if (root.has("media") && root.get("media").isArray()) {
                    for (JsonNode node : root.get("media")) {
                        media.add(node.asText());
                    }
                }
                handleMessage(senderId, chatId, content, media, Collections.<String, Object>emptyMap());
            }
            sendResponse(exchange, 200, "ok");
        } catch (Exception e) {
            try {
                sendResponse(exchange, 500, "error: " + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws Exception {
        byte[] bytes = (body != null ? body : "").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
