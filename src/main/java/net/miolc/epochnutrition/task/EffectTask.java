package net.miolc.epochnutrition.task;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.Tier;
import net.miolc.epochnutrition.config.Settings.EffectSpec;
import net.miolc.epochnutrition.store.NutritionPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/**
 * 按档位循环施加药水效果（短时长 + 高频刷新，档位变化后旧效果自然过期）。
 */
public final class EffectTask implements Runnable {

    private final EpochNutritionPlugin plugin;

    public EffectTask(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        int duration = plugin.settings().effectDurationTicks();
        for (Player p : Bukkit.getOnlinePlayers()) {
            NutritionPlayerData d = plugin.nutrition().peek(p.getUniqueId());
            if (d == null) continue;
            for (NutritionType t : NutritionType.ALL) {
                Tier tier = Tier.of(d.values[t.ordinal()]);
                for (EffectSpec spec : plugin.settings().effects(t, tier)) {
                    p.addPotionEffect(new PotionEffect(spec.type, duration, spec.amplifier, false, false, true));
                }
            }
        }
    }
}
