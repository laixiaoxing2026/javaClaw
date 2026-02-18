package com.javaclaw.agent.tools;

import com.javaclaw.bus.MessageBus;
import com.javaclaw.bus.OutboundMessage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 向当前会话发送一条消息（通过 bus.publishOutbound）。
 * params 含 LLM 传入的 content，以及框架注入的 channel、chatId、metadata。
 */
public class MessageTool extends BaseTool {

    private final MessageBus bus;

    public MessageTool(MessageBus bus) {
        this.bus = bus;
    }

    @Override
    public String getName() {
        return "message";
    }

    @Override
    public String getDescription() {
        return "Send a message back to the current chat (e.g. to notify the user).";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> content = new HashMap<>();
        content.put("type", "string");
        content.put("description", "Message content to send");
        params.put("properties", Collections.singletonMap("content", content));
        params.put("required", Collections.singletonList("content"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String channel = params.get("channel") != null ? params.get("channel").toString() : null;
        String chatId = params.get("chatId") != null ? params.get("chatId").toString() : null;
        if (channel == null || chatId == null) {
            return "[Error: no channel/chatId in params]";
        }
        Object c = params.get("content");
        String content = c != null ? c.toString() : "";
        OutboundMessage out = new OutboundMessage(channel, chatId, content);
        Object meta = params.get("metadata");
        if (meta instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadataMap = (Map<String, Object>) meta;
            out.setMetadata(metadataMap);
        }
        bus.publishOutbound(out);
        return "Message sent.";
    }
}
