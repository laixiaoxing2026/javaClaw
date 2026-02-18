package com.javaclaw.cli;

import picocli.CommandLine;

/**
 * javaClaw 入口：解析子命令 gateway、agent、onboard、status、cron，派发到对应 Command。
 * 用法：java -jar javaclaw.jar [command] [options] 或 javaClaw gateway / javaClaw agent 等。
 */
public class Main {

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new RootCommand())
                .addSubcommand("gateway", new GatewayCommand())
                .addSubcommand("agent", new AgentCommand())
                .addSubcommand("onboard", new OnboardCommand())
                .addSubcommand("status", new StatusCommand())
                .addSubcommand("cron", new CronCommand());
        int exit = cmd.execute(args);
        System.exit(exit);
    }
}
