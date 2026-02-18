package com.javaclaw.cli;

import com.javaclaw.agent.AgentLoop;
import com.javaclaw.bus.MessageBus;
import com.javaclaw.config.Config;
import com.javaclaw.config.ConfigLoader;
import com.javaclaw.providers.ProviderFactory;
import com.javaclaw.session.SessionManager;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * agent 子命令：单条 -m "xxx" 或交互模式；加载 config/bus/provider/AgentLoop，processDirect，打印回复。
 */
@CommandLine.Command(name = "agent", description = "以 CLI 方式运行 Agent（单条或交互）")
public class AgentCommand implements Runnable {

    @CommandLine.Option(names = {"-m", "--message"}, description = "单条消息，不指定则进入交互模式")
    private String message;

    @Override
    public void run() {
        Config config = ConfigLoader.loadConfig();
        MessageBus bus = new MessageBus();
        SessionManager sessionManager = new SessionManager(ConfigLoader.getSessionsDir());
        AgentLoop agent = new AgentLoop(
                bus,
                ProviderFactory.fromConfig(config),
                config.getWorkspacePath(),
                config.getAgents().getModel(),
                config.getAgents().getMaxToolIterations(),
                config.getAgents().getTemperature(),
                config.getAgents().getMaxTokens(),
                config.getAgents().getMemoryWindow(),
                config.getTools().getWebSearchApiKey(),
                config.getTools().getExec(),
                null,
                config.getTools().isRestrictToWorkspace(),
                sessionManager,
                config.getTools().getMcpServers());
        agent.connectMcp();

        if (message != null && !message.isEmpty()) {
            boolean[] streamed = { false };
            String response = agent.processDirect(message, delta -> {
                streamed[0] = true;
                System.out.print(delta);
                System.out.flush();
            });
            if (!streamed[0] && response != null && !response.isEmpty()) {
                System.out.print(response);
            }
            System.out.println();
        } else {
            System.out.println("Enter message (empty line to exit):");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        break;
                    }
                    boolean[] streamed = { false };
                    String response = agent.processDirect(line, delta -> {
                        streamed[0] = true;
                        System.out.print(delta);
                        System.out.flush();
                    });
                    if (!streamed[0] && response != null && !response.isEmpty()) {
                        System.out.print(response);
                    }
                    System.out.println();
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
        agent.closeMcp();
    }
}
