package com.javaclaw.cron;

/**
 * CronJob 与 CronJobPayload 的简单实现。
 */
public class CronJobImpl implements CronService.CronJob {

    private final String id;
    private final CronService.CronJobPayload payload;

    public CronJobImpl(String id, CronService.CronJobPayload payload) {
        this.id = id;
        this.payload = payload;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public CronService.CronJobPayload getPayload() {
        return payload;
    }
}
