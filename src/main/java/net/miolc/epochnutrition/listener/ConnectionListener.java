package net.miolc.epochnutrition.listener;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.store.NutritionPlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 进服：应用离线衰减；退服：结算 lastSeen 并延迟保存。
 */
public final class ConnectionListener implements Listener {

    private final EpochNutritionPlugin plugin;

    public ConnectionListener(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        NutritionPlayerData d = plugin.nutrition().get(p.getUniqueId());
        d.name = p.getName();
        plugin.nutrition().indexName(p.getName(), p.getUniqueId());

        if (plugin.settings().offlineDecay()) {
            long now = System.currentTimeMillis();
            double days = (now - d.lastSeen) / (plugin.settings().gameDayMinutes() * 60_000.0);
            double cap = plugin.settings().offlineMaxDays();
            if (days > cap) days = cap;
            if (days > 0) {
                for (NutritionType t : NutritionType.ALL) {
                    double v = d.values[t.ordinal()] - plugin.settings().decayPerDay()[t.ordinal()] * days;
                    d.values[t.ordinal()] = Math.max(plugin.settings().min(), v);
                }
                d.dirty = true;
                // 离线衰减导致的 0 值不判死（避免久离线玩家上线即死），由在线消耗继续触发
            }
        }
        d.lastSeen = System.currentTimeMillis();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        NutritionPlayerData d = plugin.nutrition().peek(p.getUniqueId());
        if (d != null) {
            d.lastSeen = System.currentTimeMillis();
            d.name = p.getName();
        }
        plugin.nutrition().saveLater();
    }
}
