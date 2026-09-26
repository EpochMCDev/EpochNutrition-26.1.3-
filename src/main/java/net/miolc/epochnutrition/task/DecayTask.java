package net.miolc.epochnutrition.task;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.store.NutritionPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 营养随时间持续消耗：
 * 每轮扣减 = 每游戏日消耗 × (扣取间隔 / 游戏日分钟数)
 * 游戏内一天默认 20 现实分钟。
 */
public final class DecayTask implements Runnable {

    private final EpochNutritionPlugin plugin;

    public DecayTask(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        double factor = plugin.settings().decayIntervalMinutes() / plugin.settings().gameDayMinutes();
        if (factor <= 0) return;
        double[] perDay = plugin.settings().decayPerDay();
        double min = plugin.settings().min();
        double max = plugin.settings().max();

        for (Player p : Bukkit.getOnlinePlayers()) {
            NutritionPlayerData d = plugin.nutrition().get(p.getUniqueId());
            d.lastSeen = System.currentTimeMillis();
            boolean changed = false;
            for (NutritionType t : NutritionType.ALL) {
                double v = d.values[t.ordinal()] - perDay[t.ordinal()] * factor;
                v = Math.max(min, Math.min(max, v));
                if (v != d.values[t.ordinal()]) {
                    d.values[t.ordinal()] = v;
                    changed = true;
                }
            }
            if (changed) {
                d.dirty = true;
                plugin.checkLethal(p);
            }
        }
    }
}
