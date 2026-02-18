package com.javaclaw.cli;

import com.javaclaw.config.ConfigLoader;
import picocli.CommandLine;

import java.nio.file.Files;

/**
 * status 子命令：打印配置路径、工作区、数据目录是否存在等状态。
 */
@CommandLine.Command(name = "status", description = "查看配置与工作区状态")
public class StatusCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Config:  " + ConfigLoader.getConfigPath() + " (exists: " + Files.exists(ConfigLoader.getConfigPath()) + ")");
        System.out.println("DataDir:  " + ConfigLoader.getDataDir() + " (exists: " + Files.exists(ConfigLoader.getDataDir()) + ")");
        System.out.println("Workspace: " + ConfigLoader.getDefaultWorkspacePath() + " (exists: " + Files.exists(ConfigLoader.getDefaultWorkspacePath()) + ")");
        System.out.println("Sessions: " + ConfigLoader.getSessionsDir() + " (exists: " + Files.exists(ConfigLoader.getSessionsDir()) + ")");
    }
}
