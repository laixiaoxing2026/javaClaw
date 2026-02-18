package com.javaclaw.agent.tools;

import com.javaclaw.config.ExecToolConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 执行 shell 命令。参数 command（或 cmd）。超时由 ExecToolConfig 配置。
 */
public class ExecTool extends BaseTool {

    private final ExecToolConfig execConfig;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ExecTool(ExecToolConfig execConfig) {
        this.execConfig = execConfig != null ? execConfig : new ExecToolConfig();
    }

    @Override
    public String getName() {
        return "exec";
    }

    @Override
    public String getDescription() {
        return "Execute a shell command. Returns stdout and stderr.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("type", "string");
        cmd.put("description", "Shell command to run");
        params.put("properties", Collections.singletonMap("command", cmd));
        params.put("required", Collections.singletonList("command"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        Object c = params.get("command");
        if (c == null) {
            c = params.get("cmd");
        }
        if (c == null || !(c instanceof String)) {
            return "[Error: command is required]";
        }
        String command = (String) c;
        int timeout = execConfig.getTimeoutSeconds() > 0 ? execConfig.getTimeoutSeconds() : 60;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", command});
            Future<String> outFuture = executor.submit(new StreamReader(p.getInputStream()));
            Future<String> errFuture = executor.submit(new StreamReader(p.getErrorStream()));
            boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "[Error: command timed out after " + timeout + "s]";
            }
            String out = outFuture.get(2, TimeUnit.SECONDS);
            String err = errFuture.get(2, TimeUnit.SECONDS);
            if (err != null && !err.isEmpty()) {
                return "stdout:\n" + (out != null ? out : "") + "\nstderr:\n" + err;
            }
            return out != null ? out : "";
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }

    private static class StreamReader implements Callable<String> {
        private final InputStream in;

        StreamReader(InputStream in) {
            this.in = in;
        }

        @Override
        public String call() throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
