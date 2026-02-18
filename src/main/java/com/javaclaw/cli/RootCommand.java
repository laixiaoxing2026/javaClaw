package com.javaclaw.cli;

import picocli.CommandLine;

/**
 * 根命令：无参数时打印帮助；子命令由 Main 注册。
 */
@CommandLine.Command(name = "javaClaw", mixinStandardHelpOptions = true,
        description = "超轻量个人 AI 助手（Java 8，仅钉钉渠道）")
public class RootCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
