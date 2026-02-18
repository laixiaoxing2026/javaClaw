package com.javaclaw.cli;

import picocli.CommandLine;

/**
 * cron 子命令：占位，后续可扩展为列出/添加/删除定时任务。
 */
@CommandLine.Command(name = "cron", description = "定时任务管理（占位）")
public class CronCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Cron command: not implemented yet. Use gateway for now.");
    }
}
