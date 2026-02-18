package com.javaclaw.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 key 管理会话的获取、保存与缓存。会话文件存放在 dataDir/sessions/ 下（与 workspace 解耦）。
 * 构造时传入数据目录，会话存于 dataDir/sessions/；若需与 Config 一致则传入 ConfigLoader.getSessionsDir() 的父目录并内部 resolve("sessions")，或直接传入 ConfigLoader.getSessionsDir() 作为会话根。
 */
public class SessionManager {

    private final Path sessionsDir;
    private final Map<String, Session> cache = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * @param sessionsRoot 会话根目录（如 ~/.javaclawbot/sessions），会话文件为 sessionsRoot/{sanitizedKey}.json
     */
    public SessionManager(Path sessionsRoot) {
        this.sessionsDir = sessionsRoot;
    }

    /** 按 key 取缓存或从磁盘加载，不存在则新建 */
    public Session getOrCreate(String key) {
        Session s = cache.get(key);
        if (s != null) {
            return s;
        }
        synchronized (this) {
            s = cache.get(key);
            if (s != null) {
                return s;
            }
            Path f = sessionFile(key);
            if (Files.isRegularFile(f)) {
                try {
                    String json = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                    s = MAPPER.readValue(json, Session.class);
                } catch (IOException e) {
                    s = new Session(key);
                }
            } else {
                s = new Session(key);
            }
            cache.put(key, s);
            return s;
        }
    }

    /** 将会话持久化到磁盘 */
    public void save(Session session) {
        if (session == null) {
            return;
        }
        try {
            Files.createDirectories(sessionsDir);
            Path f = sessionFile(session.getKey());
            String json = MAPPER.writeValueAsString(session);
            Files.write(f, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save session " + session.getKey(), e);
        }
    }

    /** 从缓存移除，下次 getOrCreate 会重新加载 */
    public void invalidate(String key) {
        cache.remove(key);
    }

    private Path sessionFile(String key) {
        String safe = key.replaceAll("[^a-zA-Z0-9:_-]", "_");
        return sessionsDir.resolve(safe + ".json");
    }
}
