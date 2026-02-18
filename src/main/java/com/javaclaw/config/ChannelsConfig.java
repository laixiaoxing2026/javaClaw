package com.javaclaw.config;

/**
 * 渠道配置。对应 config.json 中 channels，支持钉钉、QQ。
 */
public class ChannelsConfig {

    private DingTalkConfig dingtalk;
    private QQConfig qq;

    public DingTalkConfig getDingtalk() {
        return dingtalk == null ? new DingTalkConfig() : dingtalk;
    }

    public void setDingtalk(DingTalkConfig dingtalk) {
        this.dingtalk = dingtalk;
    }

    public QQConfig getQq() {
        return qq == null ? new QQConfig() : qq;
    }

    public void setQq(QQConfig qq) {
        this.qq = qq;
    }
}
