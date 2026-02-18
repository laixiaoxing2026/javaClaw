package com.javaclaw.agent.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 列出目录下的文件/子目录。参数 path（可选，默认 workspace）。
 */
public class ListDirTool extends BaseTool {

    private final Path workspace;
    private final boolean restrictToWorkspace;

    public ListDirTool(Path workspace, boolean restrictToWorkspace) {
        this.workspace = workspace;
        this.restrictToWorkspace = restrictToWorkspace;
    }

    @Override
    public String getName() {
        return "list_dir";
    }

    @Override
    public String getDescription() {
        return "List files and subdirectories in a directory.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> path = new HashMap<>();
        path.put("type", "string");
        path.put("description", "Directory path (default: workspace root)");
        params.put("properties", Collections.singletonMap("path", path));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        Path path = workspace;
        Object p = params.get("path");
        if (p != null && p instanceof String && !((String) p).isEmpty()) {
            path = Paths.get((String) p);
            if (!path.isAbsolute()) {
                path = workspace.resolve((String) p);
            }
        }
        if (restrictToWorkspace && !path.normalize().startsWith(workspace.normalize())) {
            return "[Error: path outside workspace not allowed]";
        }
        if (!Files.isDirectory(path)) {
            return "[Error: not a directory: " + path + "]";
        }
        try {
            return Files.list(path)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }
}
