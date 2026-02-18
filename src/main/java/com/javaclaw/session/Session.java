package com.javaclaw.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单次对话会话，消息为仅追加的列表。key 通常为 channel:chatId。
 */
public class Session {

    private String key;
    private List<Map<String, Object>> messages;
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> metadata;
    private Instant lastConsolidated;

    public Session() {
        this.messages = new ArrayList<>();
    }

    public Session(String key) {
        this();
        this.key = key;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.metadata = Collections.emptyMap();
    }

    /** 追加一条消息（可带 toolsUsed 等 extra） */
    public void addMessage(String role, String content, Map<String, Object> extra) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("role", role);
        msg.put("content", content != null ? content : "");
        if (extra != null && !extra.isEmpty()) {
            msg.putAll(extra);
        }
        messages.add(msg);
        updatedAt = Instant.now();
    }

    /** 最近 maxMessages 条消息的 LLM 格式（role + content） */
    public List<Map<String, Object>> getHistory(int maxMessages) {
        if (messages.isEmpty() || maxMessages <= 0) {
            return Collections.emptyList();
        }
        int from = Math.max(0, messages.size() - maxMessages);
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    /** 清空消息并重置 lastConsolidated */
    public void clear() {
        messages.clear();
        lastConsolidated = null;
        updatedAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<Map<String, Object>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, Object>> messages) {
        this.messages = messages != null ? messages : new ArrayList<Map<String, Object>>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getLastConsolidated() {
        return lastConsolidated;
    }

    public void setLastConsolidated(Instant lastConsolidated) {
        this.lastConsolidated = lastConsolidated;
    }
}
