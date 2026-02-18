package com.javaclaw.channels;

import com.javaclaw.bus.InboundMessage;
import com.javaclaw.bus.MessageBus;
import com.javaclaw.bus.OutboundMessage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 渠道抽象基类：name；构造(name, bus)；start/stop/send 由子类实现；isAllowed、handleMessage 可复用。
 */
public abstract class BaseChannel {

    /** 渠道标识，如 "dingtalk"、"qq" */
    protected final String name;
    protected final MessageBus bus;
    protected final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * @param name 渠道名，需与 OutboundMessage.getChannel() 一致以便分发
     * @param bus  消息总线
     */
    protected BaseChannel(String name, MessageBus bus) {
        this.name = name != null ? name : "unknown";
        this.bus = bus;
    }

    /** 启动渠道：阻塞式运行，由 ExecutorService 线程调用 */
    public abstract void start();

    /** 停止渠道并释放资源 */
    public abstract void stop();

    /** 将一条出站消息通过平台 API 发送到 msg.getChatId() */
    public abstract void send(OutboundMessage msg);

    /** 按配置判断是否允许该发送者；空列表表示允许所有人。子类可覆盖以使用 config。 */
    public boolean isAllowed(String senderId) {
        return true;
    }

    /** 先 isAllowed，再构造 InboundMessage 并 bus.publishInbound(msg) */
    public void handleMessage(String senderId, String chatId, String content,
                              List<String> media, Map<String, Object> metadata) {
        if (!isAllowed(senderId)) {
            return;
        }
        InboundMessage msg = new InboundMessage(name, senderId, chatId, content,
                null, media != null ? media : Collections.<String>emptyList(),
                metadata != null ? metadata : Collections.<String, Object>emptyMap());
        bus.publishInbound(msg);
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getName() {
        return name;
    }
}
