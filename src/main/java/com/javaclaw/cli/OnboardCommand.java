package com.javaclaw.cli;

import com.javaclaw.config.ConfigLoader;
import picocli.CommandLine;

import java.nio.file.Files;

/**
 * onboard 子命令：引导创建 ~/.javaclawbot 目录与默认 config.json（若不存在）。
 */
@CommandLine.Command(name = "onboard", description = "初始化配置与工作区（~/.javaclawbot）")
public class OnboardCommand implements Runnable {

    @Override
    public void run() {
        try {
            Files.createDirectories(ConfigLoader.getDataDir());
            Files.createDirectories(ConfigLoader.getDefaultWorkspacePath());
            Files.createDirectories(ConfigLoader.getSessionsDir());
            if (!Files.exists(ConfigLoader.getConfigPath())) {
                com.javaclaw.config.Config config = new com.javaclaw.config.Config();
                com.javaclaw.config.AgentsConfig agents = new com.javaclaw.config.AgentsConfig();
                agents.setWorkspace(ConfigLoader.getDefaultWorkspacePath().toString());
                agents.setModel("gpt-3.5-turbo");
                agents.setDefaultProvider("openai");
                config.setAgents(agents);
                java.util.Map<String, com.javaclaw.config.ProviderConfig> map = new java.util.HashMap<>();
                com.javaclaw.config.ProviderConfig p = new com.javaclaw.config.ProviderConfig();
                p.setApiKey(System.getenv().getOrDefault("OPENAI_API_KEY", ""));
                p.setApiBase("https://api.openai.com/v1");
                map.put("openai", p);
                config.setProviders(map);
                config.setChannels(new com.javaclaw.config.ChannelsConfig());
                config.setGateway(new com.javaclaw.config.GatewayConfig());
                config.setTools(new com.javaclaw.config.ToolsConfig());
                ConfigLoader.saveConfig(config);
                System.out.println("Created " + ConfigLoader.getConfigPath() + ". Please set agents.model and providers.openai.apiKey.");
            } else {
                System.out.println("Config already exists: " + ConfigLoader.getConfigPath());
            }
        } catch (Exception e) {
            System.err.println("Onboard failed: " + e.getMessage());
        }
    }
}
