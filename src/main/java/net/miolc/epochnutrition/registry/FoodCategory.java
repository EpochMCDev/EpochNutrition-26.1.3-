package net.miolc.epochnutrition.registry;

import net.miolc.epochnutrition.NutritionType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 食物营养分类：分类名(碳水:x;蛋白质:x;维生素:x;脂肪:x;)
 * 系数 = 每 1 点饱食度提供的营养值。
 */
public final class FoodCategory {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final double[] coefficients;
    private final List<ItemPattern> itemPatterns;
    private final List<GuideEntry> guideEntries;

    public FoodCategory(String id, String displayName, Material icon, double[] coefficients,
                        List<ItemPattern> itemPatterns, List<GuideEntry> guideEntries) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.coefficients = coefficients;
        this.itemPatterns = itemPatterns;
        this.guideEntries = guideEntries;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public List<ItemPattern> itemPatterns() {
        return itemPatterns;
    }

    public List<GuideEntry> guideEntries() {
        return Collections.unmodifiableList(guideEntries);
    }

    public double coefficient(NutritionType t) {
        return coefficients[t.ordinal()];
    }

    /** hunger 点饱食度可提供的四项营养。 */
    public double[] nutritionFor(double hunger) {
        double[] out = new double[NutritionType.ALL.length];
        for (NutritionType t : NutritionType.ALL) {
            out[t.ordinal()] = coefficients[t.ordinal()] * hunger;
        }
        return out;
    }

    /** 图鉴条目。 */
    public static final class GuideEntry {
        public final String label;
        public final ItemStack icon;      // 可能为 null（无效条目）
        public final double hunger;       // -1 = 未知
        public final double[] nutrition;  // 长度 4

        public GuideEntry(String label, ItemStack icon, double hunger, double[] nutrition) {
            this.label = label;
            this.icon = icon;
            this.hunger = hunger;
            this.nutrition = nutrition;
        }
    }
}
