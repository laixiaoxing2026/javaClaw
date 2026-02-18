package com.javaclaw.bus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Agent 发往渠道的出站回复。Java 8 POJO。
 */
public class OutboundMessage {

    private String channel;
    private String chatId;
    private String content;
    private String replyTo;
    private List<String> media;
    private Map<String, Object> metadata;

    public OutboundMessage() {
        this.media = new ArrayList<>();
        this.metadata = Collections.emptyMap();
    }

    public OutboundMessage(String channel, String chatId, String content) {
        this();
        this.channel = channel;
        this.chatId = chatId;
        this.content = content;
    }

    public OutboundMessage(String channel, String chatId, String content, String replyTo,
                           List<String> media, Map<String, Object> metadata) {
        this.channel = channel;
        this.chatId = chatId;
        this.content = content;
        this.replyTo = replyTo;
        this.media = media != null ? media : new ArrayList<String>();
        this.metadata = metadata != null ? metadata : Collections.<String, Object>emptyMap();
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
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

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
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
