package com.javaclaw.bus;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步消息总线：解耦渠道与 Agent。使用 BlockingQueue 实现入站/出站队列。
 * 渠道 publishInbound，Agent consumeInbound；Agent/Cron publishOutbound，分发循环 consumeOutbound。
 */
public class MessageBus {

    private final BlockingQueue<InboundMessage> inbound;
    private final BlockingQueue<OutboundMessage> outbound;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public MessageBus() {
        this.inbound = new LinkedBlockingQueue<>();
        this.outbound = new LinkedBlockingQueue<>();
    }

    /** 渠道调用：将用户消息入队 */
    public void publishInbound(InboundMessage msg) {
        if (msg != null) {
            inbound.offer(msg);
        }
    }

    /** Agent 调用：阻塞取一条入站消息 */
    public InboundMessage consumeInbound() throws InterruptedException {
        return inbound.take();
    }

    /** Agent 或 Cron 调用：将回复入队 */
    public void publishOutbound(OutboundMessage msg) {
        if (msg != null) {
            outbound.offer(msg);
        }
    }

    /** 分发循环调用：阻塞取一条出站消息 */
    public OutboundMessage consumeOutbound() throws InterruptedException {
        return outbound.take();
    }

    /** 带超时取一条出站消息，便于循环中检查停止标志 */
    public OutboundMessage consumeOutbound(long timeout, TimeUnit unit) throws InterruptedException {
        return outbound.poll(timeout, unit);
    }

    /** 停止 dispatch 循环（设置 running 标志） */
    public void stop() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getInboundSize() {
        return inbound.size();
    }

    public int getOutboundSize() {
        return outbound.size();
    }

    public BlockingQueue<InboundMessage> getInboundQueue() {
        return inbound;
    }

    public BlockingQueue<OutboundMessage> getOutboundQueue() {
        return outbound;
    }
}
