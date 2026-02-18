package com.javaclaw.channels;

import com.javaclaw.bus.MessageBus;
import com.javaclaw.bus.OutboundMessage;
import com.javaclaw.config.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 按配置初始化并启动渠道（当前仅钉钉）；启动 outbound 分发循环，从 bus 取消息并交给对应 channel.send。
 */
public class ChannelManager {

    private final Config config;
    private final MessageBus bus;
    private final Map<String, BaseChannel> channels = new HashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    public ChannelManager(Config config, MessageBus bus) {
        this.config = config;
        this.bus = bus;
        initChannels();
    }

    /** 按 config 创建各渠道（钉钉、QQ），若启用则加入 channels */
    public void initChannels() {
        if (config.getChannels().getDingtalk().isEnabled()) {
            DingTalkChannel dingTalk = new DingTalkChannel(
                    config.getChannels().getDingtalk(),
                    config.getGateway(),
                    bus);
            channels.put("dingtalk", dingTalk);
        }
        if (config.getChannels().getQq().isEnabled()) {
            QQChannel qq = new QQChannel(
                    config.getChannels().getQq(),
                    config.getGateway(),
                    bus);
            channels.put("qq", qq);
        }
    }

    /** 启动 dispatchOutbound 循环与各 channel.start() */
    public void startAll() {
        executor.submit(this::dispatchOutbound);
        for (BaseChannel ch : channels.values()) {
            executor.submit(ch::start);
        }
    }

    /** 从 bus 取 outbound 分发给已注册 channel */
    public void dispatchOutbound() {
        while (running && bus.isRunning()) {
            try {
                OutboundMessage msg = bus.consumeOutbound(2, TimeUnit.SECONDS);
                if (msg == null) {
                    continue;
                }
                BaseChannel ch = channels.get(msg.getChannel());
                if (ch != null) {
                    ch.send(msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running = false;
        for (BaseChannel ch : channels.values()) {
            ch.stop();
        }
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Map<String, BaseChannel> getChannels() {
        return new HashMap<>(channels);
    }
}
