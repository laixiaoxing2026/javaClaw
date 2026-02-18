package com.javaclaw.cron;

/**
 * 定时任务服务：到期触发 onJob。由 Gateway 创建并 setOnJob，回调里 agent.processDirect + 可选 bus.publishOutbound。
 */
public interface CronService {

    /**
     * 设置任务到期回调。job 包含 payload（如 message、deliver、to 等）。
     */
    void setOnJob(CronJobCallback callback);

    /** 启动 cron 调度 */
    void start();

    /** 停止 */
    void stop();

    /** 任务到期时调用 */
    @FunctionalInterface
    interface CronJobCallback {
        void onJob(CronJob job);
    }

    /** 单次定时任务描述 */
    interface CronJob {
        String getId();
        CronJobPayload getPayload();
    }

    interface CronJobPayload {
        String getMessage();
        boolean isDeliver();
        String getChannel();
        String getChatId();
    }
}
