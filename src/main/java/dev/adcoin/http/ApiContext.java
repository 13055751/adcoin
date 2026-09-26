package dev.adcoin.http;

import dev.adcoin.AdCoinPlugin;
import dev.adcoin.config.PluginConfig;
import dev.adcoin.econ.CurrencyService;
import dev.adcoin.link.LinkCodeService;
import dev.adcoin.security.ReplayGuard;
import dev.adcoin.security.Signer;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * 一次 API 服务启动的共享上下文（每次 start/reload 重建，handler 之间传递）。
 */
public final class ApiContext {

    private final AdCoinPlugin plugin;
    private final PluginConfig cfg;
    private final Signer signer;
    private final ReplayGuard replayGuard;

    public ApiContext(AdCoinPlugin plugin, PluginConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.signer = new Signer(cfg.apiKey());
        this.replayGuard = new ReplayGuard(cfg.tsWindowSeconds());
    }

    public AdCoinPlugin plugin() {
        return plugin;
    }

    public PluginConfig cfg() {
        return cfg;
    }

    public Signer signer() {
        return signer;
    }

    public ReplayGuard replayGuard() {
        return replayGuard;
    }

    public CurrencyService currency() {
        return plugin.currency();
    }

    public LinkCodeService linkService() {
        return plugin.linkService();
    }

    public Logger log() {
        return plugin.getLogger();
    }

    /** IP 白名单校验；白名单为空 = 不限制。 */
    public boolean isIpAllowed(InetSocketAddress remote) {
        if (cfg.allowedIps().isEmpty()) {
            return true;
        }
        if (remote == null || remote.getAddress() == null) {
            return false;
        }
        return cfg.allowedIps().contains(remote.getAddress().getHostAddress());
    }
}