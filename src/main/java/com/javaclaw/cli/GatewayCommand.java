package com.javaclaw.cli;

import com.javaclaw.agent.AgentLoop;
import com.javaclaw.bus.MessageBus;
import com.javaclaw.bus.OutboundMessage;
import com.javaclaw.channels.ChannelManager;
import com.javaclaw.config.Config;
import com.javaclaw.config.ConfigLoader;
import com.javaclaw.cron.CronServiceImpl;
import com.javaclaw.heartbeat.HeartbeatService;
import com.javaclaw.providers.ProviderFactory;
import com.javaclaw.session.SessionManager;
import picocli.CommandLine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * gateway 子命令：加载配置、创建 MessageBus/Provider/SessionManager/CronService/AgentLoop/Heartbeat/ChannelManager，
 * 使用 ExecutorService 并发运行 agent.run()、dispatchOutbound()、钉钉 channel.start()、cron.start()、heartbeat.start()。
 */
@CommandLine.Command(name = "gateway", description = "启动 Gateway（钉钉 + Agent）")
public class GatewayCommand implements Runnable {

    @Override
    public void run() {
        Config config = ConfigLoader.loadConfig();
        MessageBus bus = new MessageBus();
        SessionManager sessionManager = new SessionManager(ConfigLoader.getSessionsDir());
        CronServiceImpl cronService = new CronServiceImpl(ConfigLoader.getDataDir());
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
                cronService,
                config.getTools().isRestrictToWorkspace(),
                sessionManager,
                config.getTools().getMcpServers());
        cronService.setOnJob(job -> {
            String msg = job.getPayload() != null ? job.getPayload().getMessage() : "";
            String sk = "cron:" + job.getId();
            String ch = job.getPayload() != null ? job.getPayload().getChannel() : "cli";
            String cid = job.getPayload() != null ? job.getPayload().getChatId() : "direct";
            String response = agent.processDirect(msg, sk, ch, cid);
            if (job.getPayload() != null && job.getPayload().isDeliver()
                    && job.getPayload().getChannel() != null && job.getPayload().getChatId() != null) {
                bus.publishOutbound(new OutboundMessage(
                        job.getPayload().getChannel(), job.getPayload().getChatId(), response));
            }
        });
        HeartbeatService heartbeat = new HeartbeatService(agent::processDirect);
        ChannelManager channelManager = new ChannelManager(config, bus);

        ExecutorService executor = Executors.newCachedThreadPool();
        cronService.start();
        heartbeat.start();
        executor.submit(agent::run);
        channelManager.startAll();

        System.out.println("javaClaw gateway started. Press Ctrl+C to stop.");
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        agent.stop();
        bus.stop();
        channelManager.stop();
        cronService.stop();
        heartbeat.stop();
        agent.closeMcp();
        executor.shutdown();
    }
}
