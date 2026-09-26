package net.miolc.epochnutrition.task;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.store.NutritionPlayerData;
import net.miolc.epochnutrition.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Tab 显示：以"碳水：xx%"形式展示四项营养（页眉/页脚/两者/关闭）。
 */
public final class TabTask implements Runnable {

    private final EpochNutritionPlugin plugin;

    public TabTask(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        String mode = plugin.settings().tabMode();
        if (mode.equals("off")) return;
        boolean headerOn = mode.equals("header") || mode.equals("both");
        boolean footerOn = mode.equals("footer") || mode.equals("both");
        String headerTpl = headerOn ? plugin.settings().tabHeader() : "";
        String footerTpl = footerOn ? plugin.settings().tabFooter() : "";
        if ((headerTpl == null || headerTpl.isEmpty()) && (footerTpl == null || footerTpl.isEmpty())) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            NutritionPlayerData d = plugin.nutrition().peek(p.getUniqueId());
            if (d == null) continue;
            p.setPlayerListHeaderFooter(render(headerTpl, d, p), render(footerTpl, d, p));
        }
    }

    private String render(String template, NutritionPlayerData d, Player p) {
        if (template == null || template.isEmpty()) return "";
        String out = template.replace("{player}", p.getName());
        for (NutritionType t : NutritionType.ALL) {
            out = out.replace("{" + t.id() + "}",
                    Text.fmt(d.values[t.ordinal()], plugin.settings().tabDecimals()));
        }
        return Text.color(out);
    }
}
