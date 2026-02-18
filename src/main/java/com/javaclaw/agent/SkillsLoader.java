package com.javaclaw.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 技能加载：workspace/skills/&lt;name&gt;/SKILL.md 与内置 builtinSkillsDir/&lt;name&gt;/SKILL.md。
 */
public class SkillsLoader {

    private static final String SKILL_FILE = "SKILL.md";
    private static final String SKILLS_DIR = "skills";

    private final Path workspaceSkillsDir;
    private final Path builtinSkillsDir;

    public SkillsLoader(Path workspace, Path builtinSkillsDir) {
        this.workspaceSkillsDir = workspace.resolve(SKILLS_DIR);
        this.builtinSkillsDir = builtinSkillsDir != null ? builtinSkillsDir : null;
    }

    /** 列出技能，每项含 name、path、source（workspace/builtin）；filterUnavailable 为 true 时过滤依赖未满足的 */
    public List<Map<String, String>> listSkills(boolean filterUnavailable) {
        List<Map<String, String>> out = new ArrayList<>();
        if (Files.isDirectory(workspaceSkillsDir)) {
            try {
                Files.list(workspaceSkillsDir).filter(Files::isDirectory).forEach(dir -> {
                    Path f = dir.resolve(SKILL_FILE);
                    if (Files.isRegularFile(f)) {
                        Map<String, String> m = new HashMap<>();
                        m.put("name", dir.getFileName().toString());
                        m.put("path", f.toString());
                        m.put("source", "workspace");
                        out.add(m);
                    }
                });
            } catch (IOException e) {
                // ignore
            }
        }
        if (builtinSkillsDir != null && Files.isDirectory(builtinSkillsDir)) {
            try {
                Files.list(builtinSkillsDir).filter(Files::isDirectory).forEach(dir -> {
                    Path f = dir.resolve(SKILL_FILE);
                    if (Files.isRegularFile(f)) {
                        String name = dir.getFileName().toString();
                        if (out.stream().noneMatch(m -> name.equals(m.get("name")))) {
                            Map<String, String> m = new HashMap<>();
                            m.put("name", name);
                            m.put("path", f.toString());
                            m.put("source", "builtin");
                            out.add(m);
                        }
                    }
                });
            } catch (IOException e) {
                // ignore
            }
        }
        if (filterUnavailable) {
            List<Map<String, String>> filtered = new ArrayList<>();
            for (Map<String, String> m : out) {
                Optional<Map<String, Object>> meta = getSkillMetadata(m.get("name"));
                if (!meta.isPresent() || !meta.get().containsKey("available") || Boolean.TRUE.equals(meta.get().get("available"))) {
                    filtered.add(m);
                }
            }
            return filtered;
        }
        return out;
    }

    /** 按名称读取 SKILL.md 内容；先查 workspace 再 builtin */
    public Optional<String> loadSkill(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }
        Path inWorkspace = workspaceSkillsDir.resolve(name).resolve(SKILL_FILE);
        if (Files.isRegularFile(inWorkspace)) {
            try {
                return Optional.of(new String(Files.readAllBytes(inWorkspace), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        if (builtinSkillsDir != null) {
            Path inBuiltin = builtinSkillsDir.resolve(name).resolve(SKILL_FILE);
            if (Files.isRegularFile(inBuiltin)) {
                try {
                    return Optional.of(new String(Files.readAllBytes(inBuiltin), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** 将多个技能内容拼接成一段文本，供 system prompt 使用 */
    public String loadSkillsForContext(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : skillNames) {
            loadSkill(name).ifPresent(s -> sb.append("### ").append(name).append("\n\n").append(s).append("\n\n"));
        }
        return sb.toString();
    }

    /** 生成技能摘要（名称与描述），供 system prompt 中“可用技能列表”使用 */
    public String buildSkillsSummary() {
        List<Map<String, String>> list = listSkills(false);
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Available skills: ");
        List<String> names = new ArrayList<>();
        for (Map<String, String> m : list) {
            names.add(m.get("name"));
        }
        sb.append(String.join(", ", names)).append("\n");
        return sb.toString();
    }

    /** 返回需常驻加载的技能名列表（由 SKILL 元数据决定；暂无元数据时返回空） */
    public List<String> getAlwaysSkills() {
        return Collections.emptyList();
    }

    /** 返回技能元数据（如依赖、available 等）；简单实现可只返回 empty */
    public Optional<Map<String, Object>> getSkillMetadata(String name) {
        return Optional.empty();
    }
}
