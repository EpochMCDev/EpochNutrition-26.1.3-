package net.miolc.epochnutrition.calc;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.config.Settings;
import net.miolc.epochnutrition.registry.FoodCategory;
import net.miolc.epochnutrition.registry.FoodRegistry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 营养计算核心：
 *  1. 进食营养：PDC 覆盖（合成料理） → 分类×饱食度 → 手动配方（CE 料理）
 *  2. 合成料理公式：最终 = 原料营养之和 × (1 + step×(n-1)) / m
 *     n = 计营养原料数量（工具不计入），m = 产物数量
 */
public final class NutritionCalculator {

    private final EpochNutritionPlugin plugin;

    public NutritionCalculator(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    /** 玩家吃下物品获得的四项营养（全零返回 null）。 */
    public double[] forConsume(ItemStack item) {
        double[] pdc = pdcNutrition(item);
        if (pdc != null) return pdc;
        double[] byCategory = forCategoryFood(item);
        if (byCategory != null) return byCategory;
        return forManualRecipe(item);
    }

    /** 原版合成格内单个原料的营养（分类路径，全零返回长度4的零数组）。 */
    public double[] forIngredient(ItemStack item) {
        double[] v = forCategoryFood(item);
        return v == null ? new double[NutritionType.ALL.length] : v;
    }

    /** 分类食物：分类系数 × 饱食度。 */
    public double[] forCategoryFood(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        FoodRegistry registry = plugin.registry();
        FoodCategory c = registry.resolve(item);
        if (c == null) return null;
        double hunger = registry.hungerOf(item);
        if (hunger <= 0) return null;
        return c.nutritionFor(hunger);
    }

    /** 手动配方（CE 料理）营养。 */
    public double[] forManualRecipe(ItemStack item) {
        Settings settings = plugin.settings();
        String id = itemIdOf(item);
        Settings.ManualRecipe recipe = settings.manualRecipe(id);
        if (recipe == null) return null;

        double[] base = new double[NutritionType.ALL.length];
        int n = 0;
        for (String raw : recipe.ingredients) {
            Ingredient ing = parseIngredient(raw);
            if (ing == null) continue;
            if (ing.counted) n++;
            for (NutritionType t : NutritionType.ALL) {
                base[t.ordinal()] += ing.nutrition[t.ordinal()];
            }
        }
        if (n == 0) return null;
        double bonus = 1 + settings.craftBonusStep() * (n - 1);
        double[] out = new double[NutritionType.ALL.length];
        for (NutritionType t : NutritionType.ALL) {
            out[t.ordinal()] = base[t.ordinal()] * bonus / recipe.outputAmount;
        }
        return out;
    }

    private static final class Ingredient {
        final double[] nutrition;
        final boolean counted;

        Ingredient(double[] nutrition, boolean counted) {
            this.nutrition = nutrition;
            this.counted = counted;
        }
    }

    /** 配方原料："BREAD|面包" / "BREAD" / "customcrops:tomato" / "customcrops:item/..."（含路径自动推导） */
    private Ingredient parseIngredient(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String spec = raw.trim();
        int pipe = spec.lastIndexOf('|');
        if (pipe >= 0) spec = spec.substring(0, pipe).trim();
        if (spec.isEmpty()) return null;

        FoodRegistry registry = plugin.registry();
        double[] nutrition;
        boolean counted;

        if (spec.contains(":") || spec.contains("/")) {
            String ceId = spec.contains("/") ? FoodRegistry.deriveId(spec) : spec;
            FoodCategory c = registry.resolveCe(ceId);
            double hunger = registry.hungerByCeId(ceId);
            nutrition = c != null ? c.nutritionFor(hunger) : new double[NutritionType.ALL.length];
            counted = hunger > 0 || c != null;
        } else {
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(spec);
            if (m == null) return null;
            ItemStack temp = new ItemStack(m);
            FoodCategory c = registry.resolve(temp);
            double hunger = registry.defaultHunger(m);
            nutrition = c != null ? c.nutritionFor(hunger) : new double[NutritionType.ALL.length];
            counted = hunger > 0 || c != null;
        }
        return new Ingredient(nutrition, counted);
    }

    /** 物品的"配方id"：CE id 优先，否则原版材质小写名。 */
    public String itemIdOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        String ceId = plugin.registry().ceIdOf(item);
        return ceId != null ? ceId.toLowerCase(java.util.Locale.ROOT)
                : item.getType().name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 读取合成时写入的 PDC 营养覆盖。 */
    public double[] pdcNutrition(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        double[] out = new double[NutritionType.ALL.length];
        boolean any = false;
        for (NutritionType t : NutritionType.ALL) {
            Double v = pdc.get(plugin.pdcKey(t), PersistentDataType.DOUBLE);
            if (v != null) {
                out[t.ordinal()] = v;
                any = true;
            }
        }
        return any ? out : null;
    }
}
