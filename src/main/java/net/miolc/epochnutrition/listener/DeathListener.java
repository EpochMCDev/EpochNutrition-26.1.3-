package net.miolc.epochnutrition.listener;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.store.NutritionPlayerData;
import net.miolc.epochnutrition.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * 碳水/蛋白质清零死亡：自定义死亡消息；复活后为清空项补充数值（默认 +30）。
 */
public final class DeathListener implements Listener {

    private final EpochNutritionPlugin plugin;

    public DeathListener(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        NutritionPlayerData d = plugin.nutrition().peek(e.getEntity().getUniqueId());
        if (d == null || d.deathByNutrient == null) return;
        String msg = plugin.settings().deathMessage(d.deathByNutrient);
        if (msg != null && !msg.isEmpty()) {
            e.setDeathMessage(Text.color(msg.replace("%player%", e.getEntity().getName())));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        NutritionPlayerData d = plugin.nutrition().peek(e.getPlayer().getUniqueId());
        if (d == null || d.deathByNutrient == null) return;
        NutritionType t = NutritionType.byId(d.deathByNutrient);
        d.deathByNutrient = null;
        d.dirty = true;
        if (t == null) return;

        double bonus = plugin.settings().respawnBonus();
        String msg = plugin.settings().respawnMessage(t.id());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = e.getPlayer();
            if (!p.isOnline()) return;
            plugin.nutrition().addValue(p.getUniqueId(), t, bonus);
            if (msg != null && !msg.isEmpty()) {
                p.sendMessage(Text.color(msg));
            }
        });
    }
}
