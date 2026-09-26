package net.miolc.epochnutrition.registry;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * 食物注册表：
 *  - 解析分类配置（items 匹配模式 / guide-items 图鉴条目）
 *  - CE 物品识别（item_model / custom_model_data）
 *  - 饱食度读取（食物组件 → CE 兜底表 → 原版默认组件）
 */
public final class FoodRegistry {

    public enum CeDetection { AUTO, ITEM_MODEL, CUSTOM_MODEL_DATA, OFF }

    private final EpochNutritionPlugin plugin;
    private CeDetection detection = CeDetection.AUTO;
    private final List<FoodCategory> categories = new ArrayList<>();
    private final List<Map.Entry<ItemPattern, Double>> hungerOverrides = new ArrayList<>();

    public FoodRegistry(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        categories.clear();
        hungerOverrides.clear();

        String det = config.getString("ce.detection", "auto").trim().toLowerCase(Locale.ROOT);
        try {
            detection = CeDetection.valueOf(det.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            detection = CeDetection.AUTO;
        }

        ConfigurationSection ho = config.getConfigurationSection("ce.hunger-overrides");
        if (ho != null) {
            for (String key : ho.getKeys(false)) {
                hungerOverrides.add(Map.entry(ItemPattern.of(key), ho.getDouble(key, 0.0)));
            }
        }

        ConfigurationSection cats = config.getConfigurationSection("categories");
        if (cats == null) {
            plugin.getLogger().warning("配置中缺少 categories 段，营养分类为空。");
            return;
        }
        for (String id : cats.getKeys(false)) {
            ConfigurationSection cs = cats.getConfigurationSection(id);
            if (cs == null) continue;
            try {
                categories.add(parseCategory(id.toLowerCase(Locale.ROOT), cs));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "解析营养分类 " + id + " 失败：" + ex.getMessage(), ex);
            }
        }
    }

    private FoodCategory parseCategory(String id, ConfigurationSection cs) {
        String display = cs.getString("display-name", id);
        Material icon = Material.matchMaterial(cs.getString("icon", "BOOK"));

        double[] coefficients = new double[NutritionType.ALL.length];
        ConfigurationSection coeffs = cs.getConfigurationSection("coefficients");
        if (coeffs != null) {
            for (NutritionType t : NutritionType.ALL) {
                coefficients[t.ordinal()] = coeffs.getDouble(t.id(), 0.0);
            }
        }

        List<ItemPattern> patterns = new ArrayList<>();
        for (String raw : cs.getStringList("items")) {
            ItemPattern p = ItemPattern.of(raw);
            if (p.valid()) patterns.add(p);
            else plugin.getLogger().warning("分类 " + id + " 中存在无法识别的物品：" + raw);
        }

        List<FoodCategory.GuideEntry> entries = new ArrayList<>();
        for (String raw : cs.getStringList("guide-items")) {
            FoodCategory.GuideEntry entry = parseGuideEntry(raw, coefficients);
            if (entry != null) entries.add(entry);
        }

        return new FoodCategory(id, display, icon, coefficients, patterns, entries);
    }

    /** guide 条目格式："BREAD|面包" 或 "ce:customcrops:item/customcrops/crop/corn/corn|玉米" */
    private FoodCategory.GuideEntry parseGuideEntry(String raw, double[] coefficients) {
        String spec = raw.trim();
        if (spec.isEmpty()) return null;
        String label = null;
        int pipe = spec.lastIndexOf('|');
        if (pipe >= 0) {
            label = spec.substring(pipe + 1).trim();
            spec = spec.substring(0, pipe).trim();
        }
        boolean ce = spec.regionMatches(true, 0, "ce:", 0, 3);
        ItemStack icon;
        double hunger;
        String defaultLabel;
        if (ce) {
            String path = spec.substring(3);
            icon = buildCeIcon(path);
            String ceId = deriveId(path);
            hunger = hungerByCeId(ceId);
            defaultLabel = ceId;
        } else {
            Material m = Material.matchMaterial(spec);
            if (m == null) {
                plugin.getLogger().warning("图鉴条目无法识别：" + raw);
                return null;
            }
            icon = new ItemStack(m);
            hunger = defaultHunger(m);
            defaultLabel = m.name().toLowerCase(Locale.ROOT);
        }
        double[] nutrition = null;
        if (hunger > 0) {
            nutrition = new double[NutritionType.ALL.length];
            for (NutritionType t : NutritionType.ALL) {
                nutrition[t.ordinal()] = coefficients[t.ordinal()] * hunger;
            }
        }
        return new FoodCategory.GuideEntry(label != null && !label.isEmpty() ? label : defaultLabel,
                icon, hunger, nutrition);
    }

    /** 构造 CE 物品图标：PAPER + item_model 指向 CE 模型路径（客户端资源包负责渲染）。 */
    public ItemStack buildCeIcon(String path) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            NamespacedKey key = NamespacedKey.fromString(path.toLowerCase(Locale.ROOT));
            if (key != null) {
                try {
                    meta.setItemModel(key);
                } catch (Exception ignored) {
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public List<FoodCategory> categories() {
        return categories;
    }

    public FoodCategory category(String id) {
        if (id == null) return null;
        String k = id.toLowerCase(Locale.ROOT);
        for (FoodCategory c : categories) {
            if (c.id().equals(k)) return c;
        }
        return null;
    }

    /** 按物品解析所属分类（原版材质 / CE 模式）。 */
    public FoodCategory resolve(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        String ceId = ceIdOf(stack);
        for (FoodCategory c : categories) {
            for (ItemPattern p : c.itemPatterns()) {
                if (p.isCe()) {
                    if (p.matchesCe(ceId)) return c;
                } else if (p.material() == stack.getType()) {
                    return c;
                }
            }
        }
        return null;
    }

    /** 按 CE id 解析所属分类（手动配方原料用）。 */
    public FoodCategory resolveCe(String ceId) {
        if (ceId == null) return null;
        for (FoodCategory c : categories) {
            for (ItemPattern p : c.itemPatterns()) {
                if (p.isCe() && p.matchesCe(ceId)) return c;
            }
        }
        return null;
    }

    /**
     * 物品的有效饱食度：
     * 1) 显式食物组件（原版/CE 设置的真实组件）
     * 2) CE 物品 → 饱食度兜底表（不吃宿主材质默认值）
     * 3) 原版物品 → 材质默认食物组件
     */
    public double hungerOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0;
        FoodProperties fp = stack.getData(DataComponentTypes.FOOD);
        if (fp != null) return fp.nutrition();
        String ceId = ceIdOf(stack);
        if (ceId != null) return hungerByCeId(ceId);
        return defaultHunger(stack.getType());
    }

    /** 材质默认食物组件的饱食度（原版食物）。 */
    public double defaultHunger(Material material) {
        if (material == null || material.isAir()) return 0;
        ItemType t = material.asItemType();
        if (t == null) return 0;
        FoodProperties d = t.getDefaultData(DataComponentTypes.FOOD);
        return d == null ? 0 : d.nutrition();
    }

    /** 物品的有效食物组件（含材质默认回退），用于饱和度累加。 */
    public FoodProperties effectiveFood(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        FoodProperties fp = stack.getData(DataComponentTypes.FOOD);
        if (fp != null) return fp;
        String ceId = ceIdOf(stack);
        if (ceId != null) return null; // CE 物品不吃宿主材质默认值
        ItemType t = stack.getType().asItemType();
        if (t == null) return null;
        return t.getDefaultData(DataComponentTypes.FOOD);
    }

    public double hungerByCeId(String ceId) {
        if (ceId == null) return 0;
        for (Map.Entry<ItemPattern, Double> e : hungerOverrides) {
            if (e.getKey().matchesCe(ceId)) return e.getValue();
        }
        return 0;
    }

    /**
     * CE 物品 id 识别：
     *  - item_model：非 minecraft 命名空间的模型键，如 customcrops:item/customcrops/crop/corn/corn
     *  - custom_model_data：含命名空间的字符串，如 customcrops:item/customcrops/crop/tomato/tomato
     *  - id 推导：命名空间 + ":" + 路径最后一段（customcrops:corn / customcrops:tomato）
     */
    public String ceIdOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        if (detection == CeDetection.OFF) return null;

        if (detection == CeDetection.AUTO || detection == CeDetection.ITEM_MODEL) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasItemModel()) {
                NamespacedKey model = meta.getItemModel();
                if (model != null && !"minecraft".equals(model.getNamespace())) {
                    return deriveId(model.getNamespace() + ":" + model.getKey());
                }
            }
        }
        if (detection == CeDetection.AUTO || detection == CeDetection.CUSTOM_MODEL_DATA) {
            CustomModelData cmd = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (cmd != null) {
                for (String s : cmd.strings()) {
                    if (s.indexOf(':') > 0) return deriveId(s);
                }
            }
        }
        return null;
    }

    /** "customcrops:item/customcrops/crop/corn/corn" → "customcrops:corn" */
    public static String deriveId(String path) {
        if (path == null) return null;
        int colon = path.indexOf(':');
        if (colon <= 0) return null;
        String ns = path.substring(0, colon);
        String key = path.substring(colon + 1);
        int slash = key.lastIndexOf('/');
        String name = slash >= 0 ? key.substring(slash + 1) : key;
        return ns + ":" + name;
    }
}
