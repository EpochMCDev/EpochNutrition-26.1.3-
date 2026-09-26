package net.miolc.epochnutrition.store;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 营养数据管理：内存缓存 + data.yml 持久化（异步写盘）。
 */
public final class NutritionManager {

    private final EpochNutritionPlugin plugin;
    private final Map<UUID, NutritionPlayerData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final File file;
    private final Object ioLock = new Object();

    public NutritionManager(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    // ---------------- 存取 ----------------

    public NutritionPlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            NutritionPlayerData d = new NutritionPlayerData();
            double[] start = plugin.settings().startValues();
            for (NutritionType t : NutritionType.ALL) {
                d.values[t.ordinal()] = start[t.ordinal()];
            }
            return d;
        });
    }

    public NutritionPlayerData peek(UUID uuid) {
        return cache.get(uuid);
    }

    public UUID uuidByName(String name) {
        if (name == null) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        return nameIndex.get(name.toLowerCase(Locale.ROOT));
    }

    public void indexName(String name, UUID uuid) {
        if (name != null && uuid != null) {
            nameIndex.put(name.toLowerCase(Locale.ROOT), uuid);
        }
    }

    public double value(UUID uuid, NutritionType t) {
        return get(uuid).values[t.ordinal()];
    }

    public void setValue(UUID uuid, NutritionType t, double v) {
        NutritionPlayerData d = get(uuid);
        d.values[t.ordinal()] = plugin.settings().clamp(v);
        d.dirty = true;
    }

    public void addValue(UUID uuid, NutritionType t, double delta) {
        NutritionPlayerData d = get(uuid);
        d.values[t.ordinal()] = plugin.settings().clamp(d.values[t.ordinal()] + delta);
        d.dirty = true;
    }

    /** 一次性增加四项营养（进食）。 */
    public void addAll(UUID uuid, double[] deltas) {
        NutritionPlayerData d = get(uuid);
        for (NutritionType t : NutritionType.ALL) {
            d.values[t.ordinal()] = plugin.settings().clamp(d.values[t.ordinal()] + deltas[t.ordinal()]);
        }
        d.dirty = true;
    }

    // ---------------- 持久化 ----------------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) return;
        double[] start = plugin.settings().startValues();
        for (String key : players.getKeys(false)) {
            ConfigurationSection sec = players.getConfigurationSection(key);
            if (sec == null) continue;
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            NutritionPlayerData d = new NutritionPlayerData();
            for (NutritionType t : NutritionType.ALL) {
                d.values[t.ordinal()] = plugin.settings().clamp(sec.getDouble(t.id(), start[t.ordinal()]));
            }
            d.unlocked.addAll(sec.getStringList("unlocked"));
            d.lastSeen = sec.getLong("last-seen", System.currentTimeMillis());
            d.name = sec.getString("name", null);
            cache.put(uuid, d);
            if (d.name != null) indexName(d.name, uuid);
        }
    }

    /** 异步保存。 */
    public void save() {
        YamlConfiguration yml = buildYml();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(yml));
    }

    /** 同步保存（关服时）。 */
    public void saveSync() {
        write(buildYml());
    }

    /** 延迟保存（退出时，等待最后修改落缓存）。 */
    public void saveLater() {
        Bukkit.getScheduler().runTaskLater(plugin, this::save, 40L);
    }

    private void write(YamlConfiguration yml) {
        synchronized (ioLock) {
            try {
                yml.save(file);
            } catch (IOException ex) {
                plugin.getLogger().warning("保存 data.yml 失败：" + ex.getMessage());
            }
        }
    }

    private YamlConfiguration buildYml() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, NutritionPlayerData> en : cache.entrySet()) {
            String base = "players." + en.getKey();
            NutritionPlayerData d = en.getValue();
            for (NutritionType t : NutritionType.ALL) {
                yml.set(base + "." + t.id(), round2(d.values[t.ordinal()]));
            }
            if (!d.unlocked.isEmpty()) {
                yml.set(base + ".unlocked", new java.util.ArrayList<>(d.unlocked));
            }
            yml.set(base + ".last-seen", d.lastSeen);
            if (d.name != null) yml.set(base + ".name", d.name);
        }
        return yml;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
