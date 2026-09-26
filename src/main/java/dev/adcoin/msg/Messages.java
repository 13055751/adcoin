package dev.adcoin.msg;

import dev.adcoin.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 消息渲染：config.yml messages 段的 MiniMessage 模板 + 变量替换。
 * 变量以 &lt;key&gt; 标签形式出现；玩家输入一律经 {@link MiniMessage#escapeTags} 转义再替换，防注入标签。
 */
public final class Messages {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final PluginConfig cfg;

    public Messages(PluginConfig cfg) {
        this.cfg = cfg;
    }

    public void send(Player player, String key, Map<String, String> vars) {
        Component prefix = mm.deserialize(cfg.message("prefix"));
        player.sendMessage(prefix.append(Component.text(" ")).append(build(key, vars)));
    }

    public Component build(String key, Map<String, String> vars) {
        String template = cfg.message(key);
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                String v = e.getValue() == null ? "" : mm.escapeTags(e.getValue());
                template = template.replace("<" + e.getKey() + ">", v);
            }
        }
        return mm.deserialize(template);
    }
}