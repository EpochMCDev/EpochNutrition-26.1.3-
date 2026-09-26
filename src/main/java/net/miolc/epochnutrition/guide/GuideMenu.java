package net.miolc.epochnutrition.guide;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.registry.FoodCategory;
import net.miolc.epochnutrition.store.NutritionPlayerData;
import net.miolc.epochnutrition.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 营养图鉴：分类总览 → 分类内食物列表。
 * 食物营养信息仅在对应营养值 > 0 时显示。
 */
public final class GuideMenu {

    public enum Page { CATEGORIES, CATEGORY }

    /** 自定义 Holder 用于点击事件识别。 */
    public static final class Holder implements InventoryHolder {
        public final Page page;
        public final String categoryId;

        Holder(Page page, String categoryId) {
            this.page = page;
            this.categoryId = categoryId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final EpochNutritionPlugin plugin;

    public GuideMenu(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    public void openCategories(Player p) {
        List<FoodCategory> cats = plugin.registry().categories();
        int size = Math.max(27, ((cats.size() + 8) / 9) * 9);
        if (size > 54) size = 54;
        Holder holder = new Holder(Page.CATEGORIES, null);
        Inventory inv = Bukkit.createInventory(holder, size, Text.color("&8营养图鉴"));

        NutritionPlayerData data = plugin.nutrition().get(p.getUniqueId());
        int slot = 10;
        for (FoodCategory c : cats) {
            inv.setItem(slot++, categoryIcon(c, data.unlocked.contains(c.id())));
        }
        p.openInventory(inv);
    }

    private ItemStack categoryIcon(FoodCategory c, boolean unlocked) {
        ItemStack icon;
        if (unlocked) {
            Material m = c.icon();
            icon = m == null ? new ItemStack(Material.BOOK) : new ItemStack(m);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Text.color("&a" + c.displayName()));
                List<String> lore = new ArrayList<>();
                lore.add(Text.color("&7每 1 点饱食度提供："));
                for (NutritionType t : NutritionType.ALL) {
                    double co = c.coefficient(t);
                    if (co > 0) {
                        lore.add(Text.color("&f" + t.display() + "：&b" + Text.fmt(co)));
                    }
                }
                lore.add(Text.color("&8点击查看分类内食物"));
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
        } else {
            icon = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Text.color("&8？？？"));
                meta.setLore(List.of(Text.color("&7尚未解锁该分类的图鉴")));
                icon.setItemMeta(meta);
            }
        }
        return icon;
    }

    public void openCategory(Player p, FoodCategory c) {
        NutritionPlayerData data = plugin.nutrition().get(p.getUniqueId());
        if (!data.unlocked.contains(c.id())) {
            p.sendMessage(Text.color(plugin.settings().prefix() + "&7你尚未解锁该分类的图鉴。"));
            return;
        }
        List<FoodCategory.GuideEntry> entries = c.guideEntries();
        int size = Math.max(27, ((entries.size() + 8) / 9) * 9);
        if (size > 54) size = 54;
        Holder holder = new Holder(Page.CATEGORY, c.id());
        Inventory inv = Bukkit.createInventory(holder, size, Text.color("&8营养图鉴 - " + c.displayName()));

        int slot = 0;
        for (FoodCategory.GuideEntry entry : entries) {
            inv.setItem(slot++, entryIcon(entry));
        }
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bm = back.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(Text.color("&c返回"));
            back.setItemMeta(bm);
        }
        inv.setItem(size - 5, back);
        p.openInventory(inv);
    }

    private ItemStack entryIcon(FoodCategory.GuideEntry entry) {
        ItemStack icon = entry.icon == null ? new ItemStack(Material.BARRIER) : entry.icon.clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&f" + entry.label));
            List<String> lore = new ArrayList<>();
            if (entry.hunger >= 0) {
                lore.add(Text.color("&7饱食度：&f" + Text.fmt(entry.hunger)));
            }
            if (entry.nutrition != null) {
                for (NutritionType t : NutritionType.ALL) {
                    double v = entry.nutrition[t.ordinal()];
                    if (v > 0) { // 仅显示 > 0 的营养项
                        lore.add(Text.color("&f" + t.display() + "：&b+" + Text.fmt(v)));
                    }
                }
            }
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }
}
