package dev.adcoin.http;

import com.sun.net.httpserver.HttpServer;
import dev.adcoin.AdCoinPlugin;
import dev.adcoin.config.PluginConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内置 HTTP API 服务（JDK 自带 HttpServer，零额外依赖）。
 * <p>
 * 端点：
 * <ul>
 *   <li>POST /api/v1/reward   — 广告奖励上报（签名 + 幂等 + 限额）</li>
 *   <li>POST /api/v1/link     — 绑定码兑换，绑定 App 账号与游戏账号</li>
 *   <li>POST /api/v1/unlink   — 解绑</li>
 *   <li>POST /api/v1/friend   — 好友请求/接受/拒绝/删除/列表</li>
 *   <li>POST /api/v1/transfer — App 端好友转币（幂等）</li>
 *   <li>POST /api/v1/balance  — 查询绑定与余额</li>
 *   <li>GET  /health          — 存活检查</li>
 * </ul>
 */
public final class ApiServer {

    private final AdCoinPlugin plugin;

    private HttpServer server;
    private ExecutorService executor;

    public ApiServer(AdCoinPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(PluginConfig cfg) throws IOException {
        ApiContext context = new ApiContext(plugin, cfg);
        InetSocketAddress addr = new InetSocketAddress(cfg.host(), cfg.port());
        server = HttpServer.create(addr, 0);
        server.createContext("/api/v1/reward", new RewardHandler(context));
        server.createContext("/api/v1/link", new LinkHandler(context));
        server.createContext("/api/v1/unlink", new UnlinkHandler(context));
        server.createContext("/api/v1/friend", new FriendHandler(context));
        server.createContext("/api/v1/transfer", new TransferHandler(context));
        server.createContext("/api/v1/balance", new BalanceHandler(context));
        server.createContext("/api/v1/top", new TopHandler(context));
        server.createContext("/health", new HealthHandler(context));
        executor = Executors.newFixedThreadPool(cfg.threads());
        server.setExecutor(executor);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean running() {
        return server != null;
    }
}