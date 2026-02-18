package com.javaclaw.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 配置加载与保存。路径统一使用 javaclawbot：~/.javaclawbot/config.json、~/.javaclawbot、~/.javaclawbot/workspace。
 */
public final class ConfigLoader {

    private static final String JAVACLAWBOT_DIR = ".javaclawbot";
    private static final String CONFIG_FILE = "config.json";
    private static final String DEFAULT_WORKSPACE = "workspace";
    private static final String SESSIONS_DIR = "sessions";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ConfigLoader() {
    }

    /**
     * 默认配置文件路径：~/.javaclawbot/config.json
     */
    public static Path getConfigPath() {
        return getDataDir().resolve(CONFIG_FILE);
    }

    /**
     * 数据目录（cron、sessions 的父目录）：~/.javaclawbot
     */
    public static Path getDataDir() {
        String home = System.getProperty("user.home");
        return Paths.get(home).resolve(JAVACLAWBOT_DIR);
    }

    /**
     * 默认工作区路径：~/.javaclawbot/workspace
     */
    public static Path getDefaultWorkspacePath() {
        return getDataDir().resolve(DEFAULT_WORKSPACE);
    }

    /**
     * 会话存储目录：~/.javaclawbot/sessions
     */
    public static Path getSessionsDir() {
        return getDataDir().resolve(SESSIONS_DIR);
    }

    /**
     * 从默认路径加载配置；若文件不存在或解析失败则返回带默认路径的 Config。
     */
    public static Config loadConfig() {
        return loadConfig(getConfigPath());
    }

    /**
     * 从指定路径加载配置；失败时返回默认 Config（resolved 路径仍为 javaclawbot 约定）。
     */
    public static Config loadConfig(Path configPath) {
        Config config;
        if (Files.isRegularFile(configPath)) {
            try {
                config = MAPPER.readValue(configPath.toFile(), Config.class);
            } catch (IOException e) {
                config = new Config();
            }
        } else {
            config = new Config();
        }
        Path dataDir = getDataDir();
        config.setResolvedDataDir(dataDir);
        Path workspace = config.getAgents().getWorkspace() != null && !config.getAgents().getWorkspace().isEmpty()
                ? resolvePath(config.getAgents().getWorkspace())
                : getDefaultWorkspacePath();
        config.setResolvedWorkspacePath(workspace);
        return config;
    }

    /**
     * 将 Config 写回 JSON（默认路径）；键名 camelCase。
     */
    public static void saveConfig(Config config) {
        saveConfig(config, getConfigPath());
    }

    /**
     * 将 Config 写入指定路径。
     */
    public static void saveConfig(Config config, Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            MAPPER.writeValue(configPath.toFile(), config);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config to " + configPath, e);
        }
    }

    private static Path resolvePath(String raw) {
        if (raw.startsWith("~")) {
            String rest = raw.length() > 1 && raw.charAt(1) == '/' ? raw.substring(2) : raw.substring(1);
            return Paths.get(System.getProperty("user.home")).resolve(rest);
        }
        return Paths.get(raw);
    }
}
