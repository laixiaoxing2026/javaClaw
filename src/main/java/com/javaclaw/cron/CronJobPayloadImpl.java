package com.javaclaw.cron;

/**
 * CronJobPayload 的简单实现。
 */
public class CronJobPayloadImpl implements CronService.CronJobPayload {

    private final String message;
    private final boolean deliver;
    private final String channel;
    private final String chatId;

    public CronJobPayloadImpl(String message, boolean deliver, String channel, String chatId) {
        this.message = message;
        this.deliver = deliver;
        this.channel = channel;
        this.chatId = chatId;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public boolean isDeliver() {
        return deliver;
    }

    @Override
    public String getChannel() {
        return channel;
    }

    @Override
    public String getChatId() {
        return chatId;
    }
}
