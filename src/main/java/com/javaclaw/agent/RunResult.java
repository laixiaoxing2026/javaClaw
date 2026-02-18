package com.javaclaw.agent;

import java.util.Collections;
import java.util.List;

/**
 * runAgentLoop 的返回：最终回复文本与使用过的工具名列表。
 */
public class RunResult {

    private final String content;
    private final List<String> toolsUsed;

    public RunResult(String content, List<String> toolsUsed) {
        this.content = content != null ? content : "";
        this.toolsUsed = toolsUsed != null ? toolsUsed : Collections.<String>emptyList();
    }

    public String getContent() {
        return content;
    }

    public List<String> getToolsUsed() {
        return toolsUsed;
    }
}
