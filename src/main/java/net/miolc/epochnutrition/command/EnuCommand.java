package net.miolc.epochnutrition.command;

import net.miolc.epochnutrition.EpochNutritionPlugin;
import net.miolc.epochnutrition.NutritionType;
import net.miolc.epochnutrition.Tier;
import net.miolc.epochnutrition.registry.FoodCategory;
import net.miolc.epochnutrition.store.NutritionPlayerData;
import net.miolc.epochnutrition.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /enu 指令：
 *   set/add/remove <玩家> <grain|vitamin|protein|fat|all> <数值>
 *   getguide <玩家> <分类|all>
 *   query [玩家] / guide / reload
 */
public final class EnuCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("set", "add", "remove", "getguide", "query", "guide", "reload");
    private static final List<String> NUTRIENTS = List.of("grain", "vitamin", "protein", "fat", "all");

    private final EpochNutritionPlugin plugin;

    public EnuCommand(EpochNutritionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set", "add", "remove" -> handleModify(sender, args, sub);
            case "getguide" -> handleGetGuide(sender, args);
            case "query" -> handleQuery(sender, args);
            case "guide" -> handleGuide(sender);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Text.color("&b----- EpochNutrition 指令 -----"));
        sender.sendMessage(Text.color("&f/" + label + " set <玩家> <grain|vitamin|protein|fat|all> <数值>"));
        sender.sendMessage(Text.color("&f/" + label + " add <玩家> <grain|vitamin|protein|fat|all> <数值>"));
        sender.sendMessage(Text.color("&f/" + label + " remove <玩家> <grain|vitamin|protein|fat|all> <数值>"));
        sender.sendMessage(Text.color("&f/" + label + " getguide <玩家> <分类|all>"));
        sender.sendMessage(Text.color("&f/" + label + " query [玩家]"));
        sender.sendMessage(Text.color("&f/" + label + " guide"));
        sender.sendMessage(Text.color("&f/" + label + " reload"));
    }

    private void handleModify(CommandSender sender, String[] args, String sub) {
        if (!sender.hasPermission("enu.admin")) {
            sender.sendMessage(msg("no-permission"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Text.color("&c用法：/enu " + sub + " <玩家> <grain|vitamin|protein|fat|all> <数值>"));
            return;
        }
        UUID target = plugin.nutrition().uuidByName(args[1]);
        if (target == null) {
            sender.sendMessage(msg("player-not-found"));
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(msg("invalid-number").replace("%input%", args[3]));
            return;
        }

        List<NutritionType> types = new ArrayList<>();
        if (args[2].equalsIgnoreCase("all")) {
            types.addAll(List.of(NutritionType.ALL));
        } else {
            NutritionType t = NutritionType.byId(args[2]);
            if (t == null) {
                sender.sendMessage(msg("unknown-nutrient").replace("%input%", args[2]));
                return;
            }
            types.add(t);
        }

        NutritionPlayerData d = plugin.nutrition().get(target);
        for (NutritionType t : types) {
            switch (sub) {
                case "set" -> plugin.nutrition().setValue(target, t, value);
                case "add" -> plugin.nutrition().addValue(target, t, value);
                default -> plugin.nutrition().addValue(target, t, -value);
            }
        }
        d.dirty = true;
        plugin.nutrition().save();

        String feedback = msg(sub);
        feedback = feedback.replace("%player%", args[1])
                .replace("%nutrient%", args[2])
                .replace("%value%", Text.fmt(value));
        sender.sendMessage(Text.color(feedback));

        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            plugin.checkLethal(online);
        }
    }

    private void handleGetGuide(CommandSender sender, String[] args) {
        if (!sender.hasPermission("enu.admin")) {
            sender.sendMessage(msg("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.color("&c用法：/enu getguide <玩家> <分类|all>"));
            return;
        }
        UUID target = plugin.nutrition().uuidByName(args[1]);
        if (target == null) {
            sender.sendMessage(msg("player-not-found"));
            return;
        }
        NutritionPlayerData d = plugin.nutrition().get(target);
        if (args[2].equalsIgnoreCase("all")) {
            for (FoodCategory c : plugin.registry().categories()) {
                d.unlocked.add(c.id());
            }
            d.dirty = true;
            plugin.nutrition().save();
            sender.sendMessage(Text.color(msg("guide-unlocked-all").replace("%player%", args[1])));
            return;
        }
        FoodCategory c = plugin.registry().category(args[2]);
        if (c == null) {
            sender.sendMessage(msg("unknown-category").replace("%input%", args[2]));
            return;
        }
        d.unlocked.add(c.id());
        d.dirty = true;
        plugin.nutrition().save();
        sender.sendMessage(Text.color(msg("guide-unlocked")
                .replace("%player%", args[1])
                .replace("%category%", c.displayName())));
    }

    private void handleQuery(CommandSender sender, String[] args) {
        UUID target;
        String name;
        if (args.length >= 2) {
            if (!sender.hasPermission("enu.admin")) {
                sender.sendMessage(msg("no-permission"));
                return;
            }
            target = plugin.nutrition().uuidByName(args[1]);
            name = args[1];
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Text.color("&c控制台请使用 /enu query <玩家>"));
                return;
            }
            target = p.getUniqueId();
            name = p.getName();
        }
        if (target == null) {
            sender.sendMessage(msg("player-not-found"));
            return;
        }
        NutritionPlayerData d = plugin.nutrition().get(target);
        sender.sendMessage(Text.color(msg("query").replace("%player%", name)));
        for (NutritionType t : NutritionType.ALL) {
            double v = d.values[t.ordinal()];
            sender.sendMessage(Text.color("&f" + t.display() + "：&b" + Text.fmt(v) + "&7（" + Tier.of(v).display() + "）"));
        }
    }

    private void handleGuide(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Text.color("&c该指令仅玩家可用。"));
            return;
        }
        if (!p.hasPermission("enu.guide")) {
            p.sendMessage(msg("no-permission"));
            return;
        }
        plugin.menus().openCategories(p);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("enu.admin")) {
            sender.sendMessage(msg("no-permission"));
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(Text.color(msg("reload")));
    }

    private String msg(String key) {
        String prefix = plugin.settings().prefix();
        String body = plugin.settings().message(key);
        return prefix + body;
    }

    // ---------------- Tab 补全 ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.equals("query") || s.equals("guide") || sender.hasPermission("enu.admin")) {
                    if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add")
                || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("getguide")
                || args[0].equalsIgnoreCase("query"))) {
            if (!sender.hasPermission("enu.admin") && !args[0].equalsIgnoreCase("query")) return out;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
            return out;
        }
        if (args.length == 3) {
            String prev = args[0].toLowerCase(Locale.ROOT);
            String cur = args[2].toLowerCase(Locale.ROOT);
            if (prev.equals("set") || prev.equals("add") || prev.equals("remove")) {
                if (!sender.hasPermission("enu.admin")) return out;
                for (String s : NUTRIENTS) {
                    if (s.startsWith(cur)) out.add(s);
                }
            } else if (prev.equals("getguide")) {
                if (!sender.hasPermission("enu.admin")) return out;
                out.add("all");
                for (FoodCategory c : plugin.registry().categories()) {
                    if (c.id().startsWith(cur)) out.add(c.id());
                }
            }
            return out;
        }
        if (args.length == 4) {
            String prev = args[0].toLowerCase(Locale.ROOT);
            if ((prev.equals("set") || prev.equals("add") || prev.equals("remove")) && sender.hasPermission("enu.admin")) {
                out.addAll(List.of("0", "30", "75", "120"));
            }
            return out;
        }
        return out;
    }
}
