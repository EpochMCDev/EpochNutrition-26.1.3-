package net.miolc.epochnutrition;

import net.miolc.epochnutrition.calc.NutritionCalculator;
import net.miolc.epochnutrition.command.EnuCommand;
import net.miolc.epochnutrition.config.Settings;
import net.miolc.epochnutrition.guide.GuideListener;
import net.miolc.epochnutrition.guide.GuideMenu;
import net.miolc.epochnutrition.listener.ConnectionListener;
import net.miolc.epochnutrition.listener.ConsumeListener;
import net.miolc.epochnutrition.listener.CraftListener;
import net.miolc.epochnutrition.listener.DeathListener;
import net.miolc.epochnutrition.registry.FoodRegistry;
import net.miolc.epochnutrition.store.NutritionManager;
import net.miolc.epochnutrition.task.DecayTask;
import net.miolc.epochnutrition.task.EffectTask;
import net.miolc.epochnutrition.task.TabTask;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EpochNutrition —— 四项独立营养值系统（碳水/维生素/蛋白质/脂肪）。
 * 适配 CraftEngine 26.1.3（CE 物品识别 + 真实食物组件读取，无硬依赖）。
 */
public final class EpochNutritionPlugin extends JavaPlugin {

    private static EpochNutritionPlugin instance;

    private Settings settings;
    private FoodRegistry registry;
    private NutritionManager nutrition;
    private NutritionCalculator calculator;
    private GuideMenu menus;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<NutritionType, NamespacedKey> pdcKeys = new EnumMap<>(NutritionType.class);

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        for (NutritionType t : NutritionType.ALL) {
            pdcKeys.put(t, new NamespacedKey(this, t.id()));
        }
        reloadAll();

        Bukkit.getPluginManager().registerEvents(new ConnectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ConsumeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CraftListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GuideListener(this), this);

        EnuCommand command = new EnuCommand(this);
        PluginCommand pc = getCommand("enu");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        getLogger().info("EpochNutrition v" + getDescription().getVersion()
                + " 已启用（适配 CraftEngine 26.1.3 / Paper 1.21.x）");
    }

    @Override
    public void onDisable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.setPlayerListHeaderFooter((String) null, (String) null);
            } catch (Exception ignored) {
            }
        }
        if (nutrition != null) {
            nutrition.saveSync();
        }
    }

    /** 重载配置并重启任务（玩家数据保留）。 */
    public void reloadAll() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        reloadConfig();

        settings = new Settings(this);
        settings.load();
        registry = new FoodRegistry(this);
        registry.load(getConfig());
        calculator = new NutritionCalculator(this);
        if (nutrition == null) {
            nutrition = new NutritionManager(this);
        }
        menus = new GuideMenu(this);
        startTasks();
    }

    private void startTasks() {
        long decayTicks = Math.max(20L, (long) (settings.decayIntervalMinutes() * 60 * 20));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new DecayTask(this), decayTicks, decayTicks));

        long effectTicks = Math.max(10L, settings.effectRefreshSeconds() * 20L);
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new EffectTask(this), effectTicks, effectTicks));

        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new TabTask(this), 20L, 20L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> nutrition.save(), 20L * 60 * 5, 20L * 60 * 5));
    }

    /** 营养清零致死检查（碳水/蛋白质）。 */
    public void checkLethal(Player p) {
        if (p.isDead()) return;
        var lethal = settings.lethalTypes();
        if (lethal.isEmpty()) return;
        var d = nutrition.get(p.getUniqueId());
        for (NutritionType t : lethal) {
            if (d.values[t.ordinal()] <= settings.min()) {
                if (d.deathByNutrient == null) {
                    d.deathByNutrient = t.id();
                    d.dirty = true;
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    Player target = Bukkit.getPlayer(p.getUniqueId());
                    if (target != null && !target.isDead()) {
                        target.setHealth(0.0);
                    }
                });
                return;
            }
        }
    }

    // ---------------- 对外 API（供 森罗厨房 等插件调用） ----------------

    public double getNutrition(UUID uuid, NutritionType t) {
        return nutrition.value(uuid, t);
    }

    public void addNutrition(UUID uuid, NutritionType t, double amount) {
        nutrition.addValue(uuid, t, amount);
    }

    public void setNutrition(UUID uuid, NutritionType t, double value) {
        nutrition.setValue(uuid, t, value);
    }

    public Tier getTier(UUID uuid, NutritionType t) {
        return Tier.of(getNutrition(uuid, t));
    }

    public void unlockGuide(UUID uuid, String categoryId) {
        var d = nutrition.get(uuid);
        d.unlocked.add(categoryId.toLowerCase(java.util.Locale.ROOT));
        d.dirty = true;
    }

    // ---------------- getters ----------------

    public Settings settings() {
        return settings;
    }

    public FoodRegistry registry() {
        return registry;
    }

    public NutritionManager nutrition() {
        return nutrition;
    }

    public NutritionCalculator calculator() {
        return calculator;
    }

    public GuideMenu menus() {
        return menus;
    }

    public NamespacedKey pdcKey(NutritionType t) {
        return pdcKeys.get(t);
    }

    public static EpochNutritionPlugin get() {
        return instance;
    }
}
