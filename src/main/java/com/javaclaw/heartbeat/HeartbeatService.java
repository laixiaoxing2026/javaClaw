package com.javaclaw.heartbeat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 心跳/主动唤醒：定时调用 agent.processDirect(prompt)。当前为占位，start 后不实际调度。
 */
public class HeartbeatService {

    private final HeartbeatCallback callback;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HeartbeatService(HeartbeatCallback callback) {
        this.callback = callback;
    }

    /** 启动心跳（占位：不实际定时调用，可后续扩展） */
    public void start() {
        running.set(true);
    }

    public void stop() {
        running.set(false);
    }

    /** 主动触发一次心跳 */
    public void trigger(String prompt) {
        if (callback != null && prompt != null) {
            callback.onHeartbeat(prompt);
        }
    }

    @FunctionalInterface
    public interface HeartbeatCallback {
        void onHeartbeat(String prompt);
    }
}
