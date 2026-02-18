package com.javaclaw.bus;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 渠道发往 Agent 的入站消息。Java 8 POJO，不含 Record。
 * 会话唯一键为 channel + ":" + chatId。
 */
public class InboundMessage {

    private String channel;
    private String senderId;
    private String chatId;
    private String content;
    private ZonedDateTime timestamp;
    private List<String> media;
    private Map<String, Object> metadata;

    public InboundMessage() {
        this.media = new ArrayList<>();
        this.metadata = Collections.emptyMap();
    }

    public InboundMessage(String channel, String senderId, String chatId, String content) {
        this();
        this.channel = channel;
        this.senderId = senderId;
        this.chatId = chatId;
        this.content = content;
        this.timestamp = ZonedDateTime.now();
    }

    public InboundMessage(String channel, String senderId, String chatId, String content,
                          ZonedDateTime timestamp, List<String> media, Map<String, Object> metadata) {
        this.channel = channel;
        this.senderId = senderId;
        this.chatId = chatId;
        this.content = content;
        this.timestamp = timestamp != null ? timestamp : ZonedDateTime.now();
        this.media = media != null ? media : new ArrayList<String>();
        this.metadata = metadata != null ? metadata : Collections.<String, Object>emptyMap();
    }

    /** 会话唯一键：channel + ":" + chatId */
    public String getSessionKey() {
        return channel + ":" + chatId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<String> getMedia() {
        return media;
    }

    public void setMedia(List<String> media) {
        this.media = media;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
