package net.miolc.epochnutrition.listener;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.store.NutritionPlayerData;
import net.miolc.epochnutrition.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 进食 → 按分类系数 × 饱食度（或合成/手动配方覆盖值）增加营养。
 */
public final class ConsumeListener implements Listener {

    private final EpochNutritionPlugin plugin;

    public ConsumeListener(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        double[] add = plugin.calculator().forConsume(item);
        if (add == null) return;
        boolean any = false;
        for (double v : add) {
            if (v > 1e-9) {
                any = true;
                break;
            }
        }
        if (!any) return;

        Player p = e.getPlayer();
        NutritionPlayerData d = plugin.nutrition().get(p.getUniqueId());
        for (NutritionType t : NutritionType.ALL) {
            d.values[t.ordinal()] = plugin.settings().clamp(d.values[t.ordinal()] + add[t.ordinal()]);
        }
        d.dirty = true;

        if (plugin.settings().actionbarEnabled()) {
            String msg = plugin.settings().actionbarFormat();
            if (msg != null && !msg.isEmpty()) {
                for (NutritionType t : NutritionType.ALL) {
                    msg = msg.replace("{" + t.id() + "}", Text.fmt(add[t.ordinal()]));
                }
                p.sendActionBar(Text.color(msg));
            }
        }
        plugin.checkLethal(p);
    }
}
