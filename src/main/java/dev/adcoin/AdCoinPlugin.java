package dev.adcoin;

import dev.adcoin.cmd.AdCoinCommand;
import dev.adcoin.cmd.AdLinkCommand;
import dev.adcoin.cmd.FriendCommand;
import dev.adcoin.config.PluginConfig;
import dev.adcoin.data.DataStore;
import dev.adcoin.econ.CurrencyService;
import dev.adcoin.econ.VaultBridge;
import dev.adcoin.http.ApiServer;
import dev.adcoin.link.LinkCodeService;
import dev.adcoin.msg.Messages;
import dev.adcoin.papi.AdCoinExpansion;
import dev.adcoin.social.OnlineTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AdCoin 主类：看广告（手机App）赚取游戏内独立货币 adcoins，与服务器经济互通。
 * <p>
 * 运行模型：内置 HTTP API（工作线程）处理后端回调，账本/余额走线程安全的数据层；
 * 所有 Bukkit API（消息、事件、Vault、命令）只在主线程执行。
 */
public final class AdCoinPlugin extends JavaPlugin {

    private PluginConfig config;
    private Messages messages;
    private DataStore store;
    private CurrencyService currency;
    private LinkCodeService linkService;
    private VaultBridge vaultBridge;
    private ApiServer apiServer;
    private OnlineTracker onlineTracker;
    private boolean papiRegistered;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        saveDefaultConfig();
        reloadConfig();
        this.config = PluginConfig.from(getConfig());
        this.messages = new Messages(config);
        this.store = new DataStore(getDataFolder().toPath().resolve("adcoin-data.json"), config.ledgerMaxEntries());
        store.load();
        this.currency = new CurrencyService(store);
        this.linkService = new LinkCodeService(store, config);
        this.vaultBridge = new VaultBridge();
        vaultBridge.init();
        this.onlineTracker = new OnlineTracker(store);
        onlineTracker.register(this);

        guardCommand("adlink", new AdLinkCommand(this));
        guardCommand("adcoin", new AdCoinCommand(this));
        guardCommand("friend", new FriendCommand(this));
        registerPlaceholders();

        if (config.vaultEnabled() && !vaultBridge.isAvailable()) {
            getLogger().warning("economy.vault.enabled=true，但未检测到 Vault / 经济插件，镜像不生效。");
        } else if (!config.vaultEnabled()) {
            getLogger().info("Vault 镜像未启用（economy.vault.enabled=false）。检测到经济提供者: " + vaultBridge.providerName());
        } else {
            getLogger().info("Vault 镜像已启用，经济提供者: " + vaultBridge.providerName());
        }

        startApi();
        getLogger().info("AdCoin 已启用（" + (System.currentTimeMillis() - start) + "ms）。HTTP API: "
                + config.host() + ":" + config.port());
    }

    @Override
    public void onDisable() {
        stopApi();
        if (store != null) {
            store.save();
        }
        getLogger().info("AdCoin 已禁用。");
    }

    /** /adcoin reload：重建配置与依赖服务，重启 HTTP API（数据不丢）。 */
    public synchronized void reloadAll() {
        stopApi();
        reloadConfig();
        PluginConfig next = PluginConfig.from(getConfig());
        this.config = next;
        this.messages = new Messages(next);
        this.currency = new CurrencyService(store);
        this.linkService = new LinkCodeService(store, next);
        vaultBridge.init();
        if (next.vaultEnabled() && !vaultBridge.isAvailable()) {
            getLogger().warning("economy.vault.enabled=true，但未检测到 Vault / 经济插件，镜像不生效。");
        }
        startApi();
    }

    private void guardCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("plugin.yml 中缺少命令 " + name);
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            cmd.setTabCompleter(completer);
        }
    }

    private void startApi() {
        try {
            apiServer = new ApiServer(this);
            apiServer.start(config);
            getLogger().info("HTTP API 已监听 " + config.host() + ":" + config.port()
                    + "（/health, /api/v1/reward, /api/v1/link, /api/v1/unlink）");
        } catch (Exception e) {
            getLogger().severe("HTTP API 启动失败（端口被占用？）: " + e.getMessage() + "，可 /adcoin reload 重试");
            apiServer = null;
        }
    }

    private void stopApi() {
        if (apiServer != null) {
            apiServer.stop();
            apiServer = null;
        }
    }

    private void registerPlaceholders() {
        if (papiRegistered) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new AdCoinExpansion(this, currency).register();
            papiRegistered = true;
            getLogger().info("已注册 PlaceholderAPI 扩展：%adcoin_balance%");
        } catch (Throwable t) {
            getLogger().warning("PlaceholderAPI 扩展注册失败: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------ 访问器

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public DataStore dataStore() {
        return store;
    }

    public CurrencyService currency() {
        return currency;
    }

    public LinkCodeService linkService() {
        return linkService;
    }

    public VaultBridge vaultBridge() {
        return vaultBridge;
    }

    public OnlineTracker onlineTracker() {
        return onlineTracker;
    }
}