package id.cadera.cdrcarpenter.command;

import id.cadera.cdrcarpenter.CdrCarpenter;
import id.cadera.cdrcarpenter.blueprint.BlueprintDefinition;
import id.cadera.cdrcarpenter.blueprint.BlueprintManager;
import id.cadera.cdrcarpenter.furniture.FurnitureDefinition;
import id.cadera.cdrcarpenter.furniture.FurnitureItemFactory;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.furniture.FurnitureRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CarpenterCommand implements CommandExecutor, TabCompleter {
    private final CdrCarpenter plugin;
    private final FurnitureRegistry registry;
    @SuppressWarnings("unused")
    private final FurnitureManager manager;
    private final BlueprintManager blueprints;

    public CarpenterCommand(
            CdrCarpenter plugin,
            FurnitureRegistry registry,
            FurnitureManager manager,
            BlueprintManager blueprints
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.manager = manager;
        this.blueprints = blueprints;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "give" -> give(sender, args);
            case "blueprint", "bp" -> blueprint(sender, args);
            case "reload" -> reload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cdrcarpenter.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /carpenter give <player> <id> [amount]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[1]);
            return;
        }

        FurnitureDefinition definition = registry.get(args[2]);
        if (definition == null) {
            sender.sendMessage("§cUnknown furniture id: " + args[2]);
            return;
        }

        int amount = parseAmount(sender, args, 3);
        if (amount < 1) {
            return;
        }

        ItemStack item = FurnitureItemFactory.create(plugin, definition, amount);
        var leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        sender.sendMessage("§aGave §f" + amount + "x " + definition.displayName() + " §ato §f" + target.getName() + "§a.");
    }

    private void blueprint(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendBlueprintHelp(sender);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> blueprintList(sender);
            case "give" -> blueprintGive(sender, args);
            case "known" -> blueprintKnown(sender, args);
            default -> sendBlueprintHelp(sender);
        }
    }

    private void blueprintList(CommandSender sender) {
        sender.sendMessage("§bCdrCarpenter Blueprints:");
        for (BlueprintDefinition definition : blueprints.all()) {
            sender.sendMessage("§7- §f" + definition.id() + " §8→ recipe: " + definition.recipeId());
        }
    }

    private void blueprintGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cdrcarpenter.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§eUsage: /carpenter blueprint give <player> <id> [amount]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[2]);
            return;
        }

        BlueprintDefinition definition = blueprints.get(args[3]);
        if (definition == null) {
            sender.sendMessage("§cUnknown blueprint id: " + args[3]);
            return;
        }

        int amount = parseAmount(sender, args, 4);
        if (amount < 1) {
            return;
        }

        ItemStack item = blueprints.createBlueprint(definition.id(), amount);
        if (item == null) {
            sender.sendMessage("§cCould not create that Blueprint.");
            return;
        }
        var leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        sender.sendMessage("§aGave §f" + amount + "x " + definition.displayName() + " §ato §f" + target.getName() + "§a.");
    }

    private void blueprintKnown(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 3) {
            if (!sender.hasPermission("cdrcarpenter.admin")) {
                sender.sendMessage("§cYou do not have permission to inspect another player.");
                return;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: " + args[2]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§eUsage: /carpenter blueprint known <player>");
            return;
        }

        Set<String> known = blueprints.unlockedRecipes(target.getUniqueId());
        sender.sendMessage("§bKnown Carpenter recipes for §f" + target.getName() + "§b:");
        if (known.isEmpty()) {
            sender.sendMessage("§7- none");
            return;
        }
        known.stream().sorted().forEach(recipe -> sender.sendMessage("§7- §f" + recipe));
    }

    private int parseAmount(CommandSender sender, String[] args, int index) {
        int amount = 1;
        if (args.length > index) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[index])));
            } catch (NumberFormatException ignored) {
                sender.sendMessage("§cAmount must be a number from 1 to 64.");
                return -1;
            }
        }
        return amount;
    }

    private void list(CommandSender sender) {
        sender.sendMessage("§6CdrCarpenter Furniture:");
        for (FurnitureDefinition definition : registry.all()) {
            sender.sendMessage("§7- §f" + definition.id() + " §8(" + definition.displayName() + ")");
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("cdrcarpenter.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return;
        }
        plugin.reloadPlugin();
        sender.sendMessage("§aCdrCarpenter configuration, blueprints and furniture registry reloaded.");
    }

    private void sendBlueprintHelp(CommandSender sender) {
        sender.sendMessage("§bCdrCarpenter Blueprint Commands");
        sender.sendMessage("§e/carpenter blueprint list");
        sender.sendMessage("§e/carpenter blueprint known [player]");
        if (sender.hasPermission("cdrcarpenter.admin")) {
            sender.sendMessage("§e/carpenter blueprint give <player> <id> [amount]");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6CdrCarpenter v" + plugin.getPluginMeta().getVersion());
        sender.sendMessage("§e/carpenter list");
        sender.sendMessage("§e/carpenter blueprint <list|known>");
        if (sender.hasPermission("cdrcarpenter.admin")) {
            sender.sendMessage("§e/carpenter give <player> <id> [amount]");
            sender.sendMessage("§e/carpenter blueprint give <player> <id> [amount]");
            sender.sendMessage("§e/carpenter reload");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> roots = new ArrayList<>(List.of("list", "blueprint"));
            if (sender.hasPermission("cdrcarpenter.admin")) {
                roots.add("give");
                roots.add("reload");
            }
            return filter(roots, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(registry.all().stream().map(FurnitureDefinition::id).toList(), args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("blueprint") || args[0].equalsIgnoreCase("bp"))) {
            List<String> subs = new ArrayList<>(List.of("list", "known"));
            if (sender.hasPermission("cdrcarpenter.admin")) {
                subs.add("give");
            }
            return filter(subs, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("blueprint") || args[0].equalsIgnoreCase("bp"))) {
            if (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("known")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            }
        }
        if (args.length == 4
                && (args[0].equalsIgnoreCase("blueprint") || args[0].equalsIgnoreCase("bp"))
                && args[1].equalsIgnoreCase("give")) {
            return filter(blueprints.all().stream().map(BlueprintDefinition::id).toList(), args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
