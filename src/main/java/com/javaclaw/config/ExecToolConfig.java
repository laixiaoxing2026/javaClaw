package com.javaclaw.config;

/**
 * 执行类工具（如 shell）的配置：超时等。对应 config.json 中 tools.exec。
 */
public class ExecToolConfig {

    /** 单次执行超时秒数 */
    private int timeoutSeconds = 60;

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
