package dev.adcoin.econ;

import dev.adcoin.config.PluginConfig;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

/**
 * Vault 经济桥（可选依赖）：把奖励按百分比镜像到服务器主余额。
 * <p>
 * 所有方法必须只从主线程调用（Vault 的 Economy 实现普遍非线程安全）。
 */
public final class VaultBridge {

    private Economy economy;

    /** 检测 Vault 及其经济提供者（主线程调用）。 */
    public void init() {
        economy = null;
        try {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        } catch (LinkageError ignored) {
            // Vault 未安装：降级为不可用（NoClassDefFoundError 是其子类）
        }
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public String providerName() {
        return isAvailable() ? economy.getName() : "none";
    }

    /**
     * 主线程调用：按配置百分比把 amount 镜像到玩家主余额。
     */
    public void mirror(UUID player, double amount, PluginConfig cfg) {
        if (economy == null || !cfg.vaultEnabled() || cfg.vaultPercent() <= 0 || amount <= 0) {
            return;
        }
        int pay = (int) Math.round(amount * cfg.vaultPercent() / 100.0);
        if (pay <= 0) {
            return;
        }
        economy.depositPlayer(Bukkit.getOfflinePlayer(player), pay);
    }
}