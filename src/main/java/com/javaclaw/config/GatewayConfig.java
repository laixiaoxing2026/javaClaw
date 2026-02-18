package com.javaclaw.config;

/**
 * Gateway 服务配置：host、port。对应 config.json 中 gateway。
 */
public class GatewayConfig {

    private String host = "0.0.0.0";
    private int port = 8765;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
