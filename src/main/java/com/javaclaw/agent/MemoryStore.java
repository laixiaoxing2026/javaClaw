package com.javaclaw.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 持久记忆：workspace/memory/MEMORY.md（长期事实）+ HISTORY.md（可 grep 的日志）。
 */
public class MemoryStore {

    private static final String MEMORY_DIR = "memory";
    private static final String MEMORY_FILE = "MEMORY.md";
    private static final String HISTORY_FILE = "HISTORY.md";
    private static final String MEMORY_HEADER = "## Long-term memory\n\n";

    private final Path memoryDir;
    private final Path memoryPath;
    private final Path historyPath;

    public MemoryStore(Path workspace) {
        this.memoryDir = workspace.resolve(MEMORY_DIR);
        this.memoryPath = memoryDir.resolve(MEMORY_FILE);
        this.historyPath = memoryDir.resolve(HISTORY_FILE);
    }

    /** 读取 MEMORY.md 内容 */
    public String readLongTerm() {
        if (!Files.isRegularFile(memoryPath)) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(memoryPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** 写入 MEMORY.md */
    public void writeLongTerm(String content) {
        try {
            Files.createDirectories(memoryDir);
            Files.write(memoryPath, (content != null ? content : "").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write MEMORY.md", e);
        }
    }

    /** 向 HISTORY.md 追加一行记录 */
    public void appendHistory(String entry) {
        try {
            Files.createDirectories(memoryDir);
            String line = (entry != null ? entry : "") + "\n";
            Files.write(historyPath, line.getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to append HISTORY.md", e);
        }
    }

    /** 返回带标题的长期记忆片段，供 system prompt 使用 */
    public String getMemoryContext() {
        String raw = readLongTerm();
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        return MEMORY_HEADER + raw.trim() + "\n\n";
    }
}
