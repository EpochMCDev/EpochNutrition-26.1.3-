package net.miolc.epochnutrition.guide;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.registry.FoodCategory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 图鉴点击处理：全程取消拾取，分类跳转与返回。
 */
public final class GuideListener implements Listener {

    private final EpochNutritionPlugin plugin;

    public GuideListener(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuideMenu.Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getInventory().getSize()) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        if (holder.page == GuideMenu.Page.CATEGORIES) {
            int idx = e.getRawSlot() - 10;
            var cats = plugin.registry().categories();
            if (idx >= 0 && idx < cats.size()) {
                FoodCategory c = cats.get(idx);
                // 未解锁时给出提示（openCategory 内部有解锁校验）
                plugin.menus().openCategory(p, c);
            }
        } else if (clicked.getType() == Material.BARRIER) {
            plugin.menus().openCategories(p);
        }
    }
}
