package com.javaclaw.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

/**
 * 通过 HTTP 调用 OpenAI 兼容 API 的 LLMProvider 实现（Java 8）。
 * 使用 OkHttp 发送 POST /v1/chat/completions，解析 JSON 得到 content、tool_calls 等。
 */
public class OpenAICompatibleProvider implements LLMProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    /** LLM 接口通常较慢，默认连接 30s、读 120s、写 60s，避免频繁 timeout */
    private static final int CONNECT_TIMEOUT_SEC = 30;
    private static final int READ_TIMEOUT_SEC = 120;
    private static final int WRITE_TIMEOUT_SEC = 60;

    private final String apiKey;
    private final String apiBase;
    private final Map<String, String> extraHeaders;
    private final OkHttpClient client;
    private final String defaultModel;

    public OpenAICompatibleProvider(String apiKey, String apiBase, Map<String, String> extraHeaders, String defaultModel) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.apiBase = apiBase != null && !apiBase.isEmpty() ? apiBase : "https://api.openai.com/v1";
        this.extraHeaders = extraHeaders != null ? extraHeaders : Collections.<String, String>emptyMap();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
        this.defaultModel = defaultModel != null && !defaultModel.isEmpty() ? defaultModel : DEFAULT_MODEL;
    }

    @Override
    public LLMResponse chat(List<Map<String, Object>> messages,
                            List<Map<String, Object>> tools,
                            String model,
                            int maxTokens,
                            double temperature) {
        return chat(messages, tools, model, maxTokens, temperature, null);
    }

    @Override
    public LLMResponse chat(List<Map<String, Object>> messages,
                            List<Map<String, Object>> tools,
                            String model,
                            int maxTokens,
                            double temperature,
                            Consumer<String> streamConsumer) {
        boolean useStream = streamConsumer != null;
        String url = apiBase.endsWith("/") ? apiBase + "chat/completions" : apiBase + "/chat/completions";
        Map<String, Object> body = new HashMap<>();
        body.put("model", model != null && !model.isEmpty() ? model : defaultModel);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        if (useStream) {
            body.put("stream", true);
        }
        Request.Builder req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json");
        for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
            req.addHeader(e.getKey(), e.getValue());
        }
        try {
            String json = MAPPER.writeValueAsString(body);
            Response response = client.newCall(req.post(RequestBody.create(json, JSON)).build()).execute();
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                LLMResponse err = new LLMResponse();
                String detail = parseErrorBody(responseBody);
                err.setContent("[LLM error: " + response.code() + " " + response.message()
                        + (detail != null ? " | " + detail : "")
                        + "]");
                return err;
            }
            if (useStream && response.body() != null) {
                return parseStreamResponse(response.body().byteStream(), streamConsumer);
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            return parseResponse(responseBody);
        } catch (IOException e) {
            LLMResponse err = new LLMResponse();
            err.setContent("[LLM error: " + e.getMessage() + "]");
            return err;
        }
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * 解析 SSE 流式响应：逐行读 data: {...}，提取 delta.content（回调 streamConsumer）与 delta.tool_calls（按 index 累积），
     * 最后返回完整 content 与 toolCalls 的 LLMResponse。
     */
    private static LLMResponse parseStreamResponse(InputStream stream, Consumer<String> streamConsumer) throws IOException {
        LLMResponse out = new LLMResponse();
        StringBuilder fullContent = new StringBuilder();
        List<ToolCallRequest> toolCallsAccum = new ArrayList<>();
        Map<Integer, ToolCallRequest> byIndex = new HashMap<>();
        Map<Integer, StringBuilder> argsRawByIndex = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    try {
                        JsonNode root = MAPPER.readTree(data);
                        JsonNode choices = root.get("choices");
                        if (choices == null || !choices.isArray() || choices.size() == 0) {
                            continue;
                        }
                        JsonNode delta = choices.get(0).get("delta");
                        if (delta == null) {
                            continue;
                        }
                        if (delta.has("content") && !delta.get("content").isNull()) {
                            String chunk = delta.get("content").asText();
                            if (chunk != null) {
                                fullContent.append(chunk);
                                if (streamConsumer != null) {
                                    streamConsumer.accept(chunk);
                                }
                            }
                        }
                        JsonNode toolCalls = delta.get("tool_calls");
                        if (toolCalls != null && toolCalls.isArray()) {
                            for (int i = 0; i < toolCalls.size(); i++) {
                                JsonNode tc = toolCalls.get(i);
                                int idx = tc.has("index") ? tc.get("index").asInt() : i;
                                ToolCallRequest tr = byIndex.get(idx);
                                if (tr == null) {
                                    tr = new ToolCallRequest();
                                    if (tc.has("id")) {
                                        tr.setId(tc.get("id").asText());
                                    }
                                    byIndex.put(idx, tr);
                                    while (toolCallsAccum.size() <= idx) {
                                        toolCallsAccum.add(null);
                                    }
                                    toolCallsAccum.set(idx, tr);
                                    argsRawByIndex.put(idx, new StringBuilder());
                                }
                                JsonNode fn = tc.get("function");
                                if (fn != null) {
                                    if (fn.has("name")) {
                                        tr.setName(fn.get("name").asText());
                                    }
                                    if (fn.has("arguments")) {
                                        String part = fn.get("arguments").asText();
                                        if (part != null) {
                                            argsRawByIndex.get(idx).append(part);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 忽略单条解析失败，继续下一行
                    }
                }
            }
        }
        out.setContent(fullContent.toString());
        List<ToolCallRequest> finalCalls = new ArrayList<>();
        for (int idx = 0; idx < toolCallsAccum.size(); idx++) {
            ToolCallRequest t = toolCallsAccum.get(idx);
            if (t != null) {
                StringBuilder argsRaw = argsRawByIndex.get(idx);
                if (argsRaw != null && argsRaw.length() > 0) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = MAPPER.readValue(argsRaw.toString(), Map.class);
                        t.setArguments(args);
                    } catch (Exception e) {
                        t.setArguments(Collections.<String, Object>emptyMap());
                    }
                }
                finalCalls.add(t);
            }
        }
        if (!finalCalls.isEmpty()) {
            out.setToolCalls(finalCalls);
        }
        return out;
    }

    /**
     * 从 API 错误响应体中解析出可读信息（OpenAI 格式 error.message 或阿里云等通用 JSON）。
     */
    private static String parseErrorBody(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode error = root.get("error");
            if (error != null && error.isObject()) {
                if (error.has("message") && error.get("message").isTextual()) {
                    return error.get("message").asText();
                }
                if (error.has("msg") && error.get("msg").isTextual()) {
                    return error.get("msg").asText();
                }
            }
            if (root.has("message") && root.get("message").isTextual()) {
                return root.get("message").asText();
            }
            return responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
        } catch (Exception e) {
            return responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
        }
    }

    private static LLMResponse parseResponse(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        LLMResponse out = new LLMResponse();
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) {
            out.setContent("");
            return out;
        }
        JsonNode first = choices.get(0);
        JsonNode message = first.get("message");
        if (message != null) {
            JsonNode content = message.get("content");
            if (content != null && content.isTextual()) {
                out.setContent(content.asText());
            }
            JsonNode toolCalls = message.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                List<ToolCallRequest> list = new ArrayList<>();
                for (JsonNode tc : toolCalls) {
                    ToolCallRequest tr = new ToolCallRequest();
                    if (tc.has("id")) {
                        tr.setId(tc.get("id").asText());
                    }
                    JsonNode fn = tc.get("function");
                    if (fn != null) {
                        if (fn.has("name")) {
                            tr.setName(fn.get("name").asText());
                        }
                        if (fn.has("arguments")) {
                            String argsStr = fn.get("arguments").asText();
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> args = MAPPER.readValue(argsStr, Map.class);
                                tr.setArguments(args);
                            } catch (Exception e) {
                                tr.setArguments(Collections.<String, Object>emptyMap());
                            }
                        }
                    }
                    list.add(tr);
                }
                out.setToolCalls(list);
            }
            JsonNode reasoning = message.get("reasoning_content");
            if (reasoning != null && reasoning.isTextual()) {
                out.setReasoningContent(reasoning.asText());
            }
        }
        JsonNode finish = first.get("finish_reason");
        if (finish != null && finish.isTextual()) {
            out.setFinishReason(finish.asText());
        }
        JsonNode usage = root.get("usage");
        if (usage != null && usage.isObject()) {
            Map<String, Integer> u = new HashMap<>();
            if (usage.has("prompt_tokens")) {
                u.put("prompt_tokens", usage.get("prompt_tokens").asInt());
            }
            if (usage.has("completion_tokens")) {
                u.put("completion_tokens", usage.get("completion_tokens").asInt());
            }
            if (usage.has("total_tokens")) {
                u.put("total_tokens", usage.get("total_tokens").asInt());
            }
            out.setUsage(u);
        }
        return out;
    }
}
