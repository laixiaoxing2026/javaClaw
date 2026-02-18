package com.javaclaw.agent.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 写入文件。参数 path、content。若 restrictToWorkspace 为 true 则限制在工作区内。
 */
public class WriteFileTool extends BaseTool {

    private final Path workspace;
    private final boolean restrictToWorkspace;

    public WriteFileTool(Path workspace, boolean restrictToWorkspace) {
        this.workspace = workspace;
        this.restrictToWorkspace = restrictToWorkspace;
    }

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "Write content to a file. Creates parent dirs if needed.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> path = new HashMap<>();
        path.put("type", "string");
        path.put("description", "File path");
        Map<String, Object> content = new HashMap<>();
        content.put("type", "string");
        content.put("description", "Content to write");
        params.put("properties", new HashMap<String, Object>() {{
            put("path", path);
            put("content", content);
        }});
        params.put("required", java.util.Arrays.asList("path", "content"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        Object p = params.get("path");
        Object c = params.get("content");
        if (p == null || !(p instanceof String)) {
            return "[Error: path is required]";
        }
        Path path = Paths.get((String) p);
        if (!path.isAbsolute()) {
            path = workspace.resolve((String) p);
        }
        if (restrictToWorkspace && !path.normalize().startsWith(workspace.normalize())) {
            return "[Error: path outside workspace not allowed]";
        }
        String content = c != null ? c.toString() : "";
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            return "Wrote " + path;
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }
}
