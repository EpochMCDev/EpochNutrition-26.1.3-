package net.miolc.epochnutrition.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 合成料理：营养直接从配方原料计算。
 * 最终 = 原料营养之和 × (1 + step×(n-1)) / m
 * n = 原料数量（工具不计入），m = 单次合成产物数量。
 * 计算结果写入产物 PDC，进食时优先采用。
 */
public final class CraftListener implements Listener {

    private final EpochNutritionPlugin plugin;

    public CraftListener(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        CraftingInventory inv = e.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || result.getType().isAir()) return;

        ItemStack[] matrix = inv.getMatrix();
        double[] base = new double[NutritionType.ALL.length];
        int n = 0;
        double hungerSum = 0;
        double satSum = 0;

        for (ItemStack ing : matrix) {
            if (ing == null || ing.getType().isAir()) continue;
            if (plugin.settings().isCraftTool(ing)) continue; // 菜刀/瓶子/碗等不计入
            n++;
            double[] part = plugin.calculator().forIngredient(ing);
            for (int i = 0; i < base.length; i++) base[i] += part[i];
            hungerSum += plugin.registry().hungerOf(ing);
            FoodProperties fp = plugin.registry().effectiveFood(ing);
            if (fp != null) {
                satSum += fp.nutrition() * fp.saturation() * 2.0; // 有效饱和度
            }
        }
        if (n == 0) return;

        int m = singleCraftAmount(e, result);
        if (m <= 0) m = 1;
        double bonus = 1 + plugin.settings().craftBonusStep() * (n - 1);

        ItemStack tagged = result.clone();
        ItemMeta meta = tagged.getItemMeta();
        if (meta == null) return;
        boolean any = false;
        for (NutritionType t : NutritionType.ALL) {
            double v = base[t.ordinal()] * bonus / m;
            if (v > 1e-9) {
                meta.getPersistentDataContainer().set(plugin.pdcKey(t), PersistentDataType.DOUBLE,
                        Math.round(v * 100.0) / 100.0);
                any = true;
            }
        }
        if (!any) return;
        tagged.setItemMeta(meta);

        // 可选：饱食度/饱和度按原料累加并均摊到产物（不含额外收益，与营养同一 m）
        if (plugin.settings().foodAccumulate() && hungerSum > 0) {
            int perHunger = (int) Math.max(1, Math.round(hungerSum / m));
            float modifier = (float) ((satSum / m) / (2.0 * perHunger));
            tagged.setData(DataComponentTypes.FOOD,
                    FoodProperties.food().nutrition(perHunger).saturation(modifier).canAlwaysEat(false).build());
        }
        e.setCurrentItem(tagged);
    }

    /** 单次合成的产物数量（原版配方取配方结果，避免 shift 批量合成时被整叠数量干扰）。 */
    private int singleCraftAmount(CraftItemEvent e, ItemStack result) {
        Recipe r = e.getRecipe();
        try {
            if (r instanceof ShapedRecipe shaped) return Math.max(1, shaped.getResult().getAmount());
            if (r instanceof ShapelessRecipe shapeless) return Math.max(1, shapeless.getResult().getAmount());
            if (r instanceof CookingRecipe<?> cooking) return Math.max(1, cooking.getResult().getAmount());
        } catch (Exception ignored) {
        }
        return Math.max(1, result.getAmount());
    }
}
