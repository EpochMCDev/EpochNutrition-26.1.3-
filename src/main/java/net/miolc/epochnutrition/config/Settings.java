package net.miolc.epochnutrition.config;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.Tier;
import net.miolc.epochnutrition.registry.ItemPattern;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 配置层：一次性解析 config.yml 到强类型字段。
 */
public final class Settings {

    public static final class EffectSpec {
        public final PotionEffectType type;
        public final int amplifier; // 0 = I 级

        EffectSpec(PotionEffectType type, int amplifier) {
            this.type = type;
            this.amplifier = amplifier;
        }
    }

    public static final class ManualRecipe {
        public final int outputAmount;
        public final List<String> ingredients;

        ManualRecipe(int outputAmount, List<String> ingredients) {
            this.outputAmount = Math.max(1, outputAmount);
            this.ingredients = ingredients;
        }
    }

    private final EpochNutritionPlugin plugin;

    private double min = 0;
    private double max = 120;
    private final double[] startValues = new double[NutritionType.ALL.length];

    private double gameDayMinutes = 20;
    private double decayIntervalMinutes = 1;
    private final double[] decayPerDay = new double[NutritionType.ALL.length];
    private boolean offlineDecay = true;
    private double offlineMaxDays = 3;

    private final Map<NutritionType, Map<Tier, List<EffectSpec>>> effects = new EnumMap<>(NutritionType.class);
    private int effectRefreshSeconds = 3;
    private int effectDurationSeconds = 6;

    private final Set<NutritionType> lethal = EnumSet.noneOf(NutritionType.class);
    private double respawnBonus = 30;
    private final Map<String, String> deathMessages = new LinkedHashMap<>();
    private final Map<String, String> respawnMessages = new LinkedHashMap<>();

    private String tabMode = "footer";
    private String tabHeader = "";
    private String tabFooter = "";
    private int tabDecimals = 0;

    private boolean actionbarEnabled = true;
    private String actionbarFormat = "";

    private double craftBonusStep = 0.05;
    private boolean foodAccumulate = false;
    private final Set<Material> toolMaterials = EnumSet.noneOf(Material.class);
    private final List<ItemPattern> toolItems = new ArrayList<>();

    private final Map<String, ManualRecipe> manualRecipes = new LinkedHashMap<>();

    private String prefix = "";
    private final Map<String, String> messages = new LinkedHashMap<>();

    public Settings(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        org.bukkit.configuration.file.FileConfiguration c = plugin.getConfig();

        min = c.getDouble("range.min", 0);
        max = c.getDouble("range.max", 120);
        if (max <= min) max = min + 1;

        for (NutritionType t : NutritionType.ALL) {
            startValues[t.ordinal()] = clamp(c.getDouble("start." + t.id(), 75));
            decayPerDay[t.ordinal()] = Math.max(0, c.getDouble("decay.per-day." + t.id(), 0));
        }

        gameDayMinutes = Math.max(0.5, c.getDouble("decay.game-day-minutes", 20));
        decayIntervalMinutes = Math.max(0.05, c.getDouble("decay.interval-minutes", 1.0));
        offlineDecay = c.getBoolean("decay.offline", true);
        offlineMaxDays = Math.max(0, c.getDouble("decay.offline-max-days", 3));

        effects.clear();
        for (NutritionType t : NutritionType.ALL) {
            Map<Tier, List<EffectSpec>> perTier = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                List<String> raw = c.getStringList("effects." + t.id() + "." + tier.id());
                List<EffectSpec> specs = new ArrayList<>();
                for (String s : raw) {
                    EffectSpec spec = parseEffect(s);
                    if (spec != null) specs.add(spec);
                }
                perTier.put(tier, specs);
            }
            effects.put(t, perTier);
        }
        effectRefreshSeconds = Math.max(1, c.getInt("effect-refresh-seconds", 3));
        effectDurationSeconds = Math.max(effectRefreshSeconds + 1, c.getInt("effect-duration-seconds", 6));

        lethal.clear();
        for (String s : c.getStringList("death.lethal")) {
            NutritionType t = NutritionType.byId(s);
            if (t != null) lethal.add(t);
            else plugin.getLogger().warning("death.lethal 中存在未知营养值：" + s);
        }
        respawnBonus = c.getDouble("death.respawn-bonus", 30);
        deathMessages.clear();
        respawnMessages.clear();
        for (NutritionType t : NutritionType.ALL) {
            deathMessages.put(t.id(), c.getString("death.messages." + t.id(), ""));
            respawnMessages.put(t.id(), c.getString("death.messages.respawn-" + t.id(), ""));
        }

        tabMode = c.getString("tab.mode", "footer").trim().toLowerCase(Locale.ROOT);
        tabHeader = c.getString("tab.header", "");
        tabFooter = c.getString("tab.footer", "");
        tabDecimals = Math.max(0, Math.min(2, c.getInt("tab.decimals", 0)));

        actionbarEnabled = c.getBoolean("feedback.actionbar-enabled", true);
        actionbarFormat = c.getString("feedback.actionbar", "");

        craftBonusStep = Math.max(0, c.getDouble("crafting.bonus-step", 0.05));
        foodAccumulate = c.getBoolean("crafting.food-accumulate", false);
        toolMaterials.clear();
        for (String s : c.getStringList("crafting.tool-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) toolMaterials.add(m);
            else plugin.getLogger().warning("crafting.tool-materials 中存在未知材质：" + s);
        }
        toolItems.clear();
        for (String s : c.getStringList("crafting.tool-items")) {
            toolItems.add(ItemPattern.of(s));
        }

        manualRecipes.clear();
        ConfigurationSection mr = c.getConfigurationSection("manual-recipes");
        if (mr != null) {
            for (String id : mr.getKeys(false)) {
                ConfigurationSection sec = mr.getConfigurationSection(id);
                if (sec == null) continue;
                List<String> ingredients = sec.getStringList("ingredients");
                manualRecipes.put(id.toLowerCase(Locale.ROOT),
                        new ManualRecipe(sec.getInt("output-amount", 1), ingredients));
            }
        }

        prefix = c.getString("messages.prefix", "");
        messages.clear();
        ConfigurationSection msg = c.getConfigurationSection("messages");
        if (msg != null) {
            for (String key : msg.getKeys(false)) {
                messages.put(key, msg.getString(key, ""));
            }
        }
    }

    private EffectSpec parseEffect(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return null;
        String name = s;
        int level = 1;
        int idx = s.lastIndexOf(':');
        if (idx > 0) {
            name = s.substring(0, idx).trim();
            try {
                level = Integer.parseInt(s.substring(idx + 1).trim());
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("药水效果等级无效（按 1 处理）：" + raw);
            }
        }
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        if (type == null) {
            plugin.getLogger().warning("未知的药水效果：" + raw);
            return null;
        }
        int amp = Math.max(0, level - 1);
        return new EffectSpec(type, amp);
    }

    public double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    public List<EffectSpec> effects(NutritionType t, Tier tier) {
        Map<Tier, List<EffectSpec>> perTier = effects.get(t);
        if (perTier == null) return List.of();
        List<EffectSpec> specs = perTier.get(tier);
        return specs == null ? List.of() : specs;
    }

    public ManualRecipe manualRecipe(String id) {
        return id == null ? null : manualRecipes.get(id.toLowerCase(Locale.ROOT));
    }

    /** 工具判定：原版工具材质或 CE 工具模式（不计入 n、不提供营养）。 */
    public boolean isCraftTool(org.bukkit.inventory.ItemStack stack) {
        if (toolMaterials.contains(stack.getType())) return true;
        if (!toolItems.isEmpty()) {
            String ceId = plugin.registry().ceIdOf(stack);
            if (ceId != null) {
                for (ItemPattern p : toolItems) {
                    if (p.matchesCe(ceId)) return true;
                }
            }
        }
        return false;
    }

    public String message(String key) {
        String s = messages.get(key);
        return s == null ? "" : s;
    }

    // ---------- getters ----------

    public double min() { return min; }
    public double max() { return max; }
    public double[] startValues() { return startValues; }
    public double gameDayMinutes() { return gameDayMinutes; }
    public double decayIntervalMinutes() { return decayIntervalMinutes; }
    public double[] decayPerDay() { return decayPerDay; }
    public boolean offlineDecay() { return offlineDecay; }
    public double offlineMaxDays() { return offlineMaxDays; }
    public int effectRefreshSeconds() { return effectRefreshSeconds; }
    public int effectDurationTicks() { return effectDurationSeconds * 20; }
    public Set<NutritionType> lethalTypes() { return lethal; }
    public double respawnBonus() { return respawnBonus; }
    public String deathMessage(String nutrientId) { return deathMessages.getOrDefault(nutrientId, ""); }
    public String respawnMessage(String nutrientId) { return respawnMessages.getOrDefault(nutrientId, ""); }
    public String tabMode() { return tabMode; }
    public String tabHeader() { return tabHeader; }
    public String tabFooter() { return tabFooter; }
    public int tabDecimals() { return tabDecimals; }
    public boolean actionbarEnabled() { return actionbarEnabled; }
    public String actionbarFormat() { return actionbarFormat; }
    public double craftBonusStep() { return craftBonusStep; }
    public boolean foodAccumulate() { return foodAccumulate; }
    public Map<String, ManualRecipe> manualRecipes() { return manualRecipes; }
    public String prefix() { return prefix; }
}
