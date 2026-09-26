package dev.adcoin.papi;

import dev.adcoin.AdCoinPlugin;
import dev.adcoin.econ.CurrencyService;
import dev.adcoin.security.Signer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI 扩展：%adcoin_balance% → 玩家 adcoins 余额。
 * 仅在 PlaceholderAPI 存在时注册；本类只在那个分支被加载。
 */
public final class AdCoinExpansion extends PlaceholderExpansion {

    private final AdCoinPlugin plugin;
    private final CurrencyService currency;

    public AdCoinExpansion(AdCoinPlugin plugin, CurrencyService currency) {
        this.plugin = plugin;
        this.currency = currency;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "adcoin";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AdCoin";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }
        if (params.equalsIgnoreCase("balance")) {
            return Signer.formatAmount(currency.balance(player.getUniqueId()));
        }
        return null;
    }
}