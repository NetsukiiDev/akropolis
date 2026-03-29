/*
 * This file is part of Akropolis
 *
 * Copyright (c) 2025 DevBlook Team and others
 *
 * Akropolis free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Akropolis is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Akropolis. If not, see <http://www.gnu.org/licenses/>.
 */

package me.zetastormy.akropolis.command.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import me.zetastormy.akropolis.AkropolisPlugin;
import me.zetastormy.akropolis.Permissions;
import me.zetastormy.akropolis.command.CommandManager;
import me.zetastormy.akropolis.command.InjectableCommand;
import me.zetastormy.akropolis.config.Message;
import me.zetastormy.akropolis.inventory.AbstractInventory;
import me.zetastormy.akropolis.inventory.InventoryManager;
import me.zetastormy.akropolis.module.ModuleManager;
import me.zetastormy.akropolis.module.ModuleType;
import me.zetastormy.akropolis.module.modules.hologram.Hologram;
import me.zetastormy.akropolis.module.modules.hotbar.HotbarItem;
import me.zetastormy.akropolis.module.modules.hotbar.HotbarManager;
import me.zetastormy.akropolis.module.modules.player.PvpLeaderboardManager;
import me.zetastormy.akropolis.module.modules.player.PvpLeaderboardManager.LeaderboardInstance;
import me.zetastormy.akropolis.module.modules.player.PvpLeaderboardManager.LeaderboardMode;
import me.zetastormy.akropolis.module.modules.visual.scoreboard.ScoreboardManager;
import me.zetastormy.akropolis.module.modules.world.LobbySpawn;
import me.zetastormy.akropolis.module.modules.world.SongPlayerManager;
import me.zetastormy.akropolis.util.text.TextUtil;
import net.kyori.adventure.text.Component;

public class AkropolisCommand extends InjectableCommand {
    private final AkropolisPlugin plugin;

    public AkropolisCommand(AkropolisPlugin plugin) {
        super(plugin, "akropolis", "View plugin information and additional commands", Collections.singletonList("akro"));
        this.plugin = plugin;
    }

    @Override
    public void onCommand(CommandSender sender, String label, String[] args) {
        /*
         * Command: help Description: displays help message
         */
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {

            if (!sender.hasPermission(Permissions.COMMAND_AKROPOLIS_HELP.getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }

            Message.HELP_PLUGIN.sendAsList(sender);
            return;
        }

        /*
         * Command: reload Description: reloads the entire plugin
         */
        else if (args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission(Permissions.COMMAND_AKROPOLIS_RELOAD.getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }

            long start = System.currentTimeMillis();
            plugin.reload();
            Message.CONFIG_RELOAD.sendWithReplacement(sender, "time", TextUtil.parse(String.valueOf(System.currentTimeMillis() - start)));
        }

        /*
         * Command: scoreboard Description: toggles the scoreboard on/off
         */
        else if (args[0].equalsIgnoreCase("scoreboard")) {

            if (!(sender instanceof Player player)) {
                Message.CONSOLE_NOT_ALLOWED.send(sender);
                return;
            }

            if (!sender.hasPermission(Permissions.COMMAND_SCOREBOARD_TOGGLE.getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }

            if (!plugin.getModuleManager().isEnabled(ModuleType.SCOREBOARD)) {
                sender.sendMessage(TextUtil.parse("<red>The scoreboard module is not enabled in the configuration."));
                return;
            }

            ScoreboardManager scoreboardManager = ((ScoreboardManager) plugin.getModuleManager()
                    .getModule(ModuleType.SCOREBOARD));

            if (scoreboardManager.hasScore(player.getUniqueId())) {
                scoreboardManager.removeScoreboard(player);
                Message.SCOREBOARD_DISABLE.send(player);
            } else {
                scoreboardManager.createScoreboard(player);
                Message.SCOREBOARD_ENABLE.send(player);
            }
        }

        /*
         * Command: hotbar Description: toggles the hotbar on/off
         */
        else if (args[0].equalsIgnoreCase("hotbar")) {

            if (!(sender instanceof Player player)) {
                Message.CONSOLE_NOT_ALLOWED.send(sender);
                return;
            }

            if (!sender.hasPermission(Permissions.COMMAND_HOTBAR_TOGGLE.getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }

            if (!plugin.getModuleManager().isEnabled(ModuleType.HOTBAR_ITEMS)) {
                sender.sendMessage(TextUtil.parse("<red>The hotbar module is not enabled in the configuration."));
                return;
            }

            HotbarManager hotbarManager = ((HotbarManager) plugin.getModuleManager()
                    .getModule(ModuleType.HOTBAR_ITEMS));

            if (hotbarManager.hasHotbar(player.getUniqueId())) {
                hotbarManager.removeItemsFromPlayer(player);
                Message.HOTBAR_DISABLE.send(player);
            } else {
                hotbarManager.giveItemsToPlayer(player);
                Message.HOTBAR_ENABLE.send(player);
            }
        }

        /*
         * Command: info Description: displays useful information about the
         * configuration
         */
        else if (args[0].equalsIgnoreCase("info")) {

            if (!sender.hasPermission(Permissions.COMMAND_AKROPOLIS_HELP.getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }

            sender.sendMessage(TextUtil.parse("<gold><b>Akropolis <reset><dark_gray>|| <gray>Plugin information<dark_gray>:"));

            Location location = ((LobbySpawn) plugin.getModuleManager().getModule(ModuleType.LOBBY)).getLocation();
            sender.sendMessage(
                    TextUtil.parse("<dark_gray>» <gray>Spawn set <dark_gray>- " + (location != null ? "<green>yes" : "<red>no <gray><i>(/setlobby)")));

            ModuleManager moduleManager = plugin.getModuleManager();
            sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>Disabled Worlds (" + moduleManager.getDisabledWorlds().size()
                    + ") <dark_gray>- <green>" + (String.join(", ", moduleManager.getDisabledWorlds()))));

            InventoryManager inventoryManager = plugin.getInventoryManager();
            sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>Custom menus (" + inventoryManager.getInventories().size() + ")"
                    + " <dark_gray>- <green>" + (String.join(", ", inventoryManager.getInventories().keySet()))));

            HotbarManager hotbarManager = ((HotbarManager) plugin.getModuleManager()
                    .getModule(ModuleType.HOTBAR_ITEMS));
            sender.sendMessage(TextUtil
                    .parse("<dark_gray>» <gray>Hotbar items (" + hotbarManager.getHotbarItems().size() + ")" + " <dark_gray>- <green>" + (hotbarManager
                            .getHotbarItems().stream().map(HotbarItem::getKeyValue).collect(Collectors.joining(", ")))));

            CommandManager commandManager = plugin.getCommandManager();
            sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>Custom commands (" + commandManager.getCustomCommands().size() + ")"
                    + " <dark_gray>- <green>" + (commandManager.getCustomCommands().stream()
                    .map(command -> command.getAliases().getFirst()).collect(Collectors.joining(", ")))));

            sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>PlaceholderAPI hook<dark_gray>: "
                    + (plugin.getHookManager().isHookEnabled("PLACEHOLDER_API") ? "<green>yes" : "<red>no")));
            sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>HeadDatabase hook<dark_gray>: "
                    + (plugin.getHookManager().isHookEnabled("HEAD_DATABASE") ? "<green>yes" : "<red>no")));
            sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>MiniPlaceholders hook<dark_gray>: "
                    + (plugin.getHookManager().isHookEnabled("MINIPLACEHOLDERS") ? "<green>yes" : "<red>no")));
            sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>NoteBlockAPI hook<dark_gray>: "
                    + (plugin.getHookManager().isHookEnabled("NOTEBLOCK_API") ? "<green>yes" : "<red>no")));
        }

        /*
         * Command: open Description: opens a custom menu
         */
        else if (args[0].equalsIgnoreCase("open")) {
            if (!(sender instanceof Player)) {
                Message.CONSOLE_NOT_ALLOWED.send(sender);
                return;
            }

            if (!sender.hasPermission(Permissions.COMMAND_OPEN_MENUS.getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }

            if (args.length == 1) {
                Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis open <menu>"));
                return;
            }

            AbstractInventory inventory = plugin.getInventoryManager().getInventory(args[1]);
            if (inventory == null) {
                sender.sendMessage(TextUtil.parse("<red>" + args[1] + " is not a valid menu ID."));
                return;
            }
            inventory.openInventory((Player) sender);
        }

        /*
         * PvP leaderboards
         */
        if (args[0].equalsIgnoreCase("leaderboard") || args[0].equalsIgnoreCase("leaderboards")
                || args[0].equalsIgnoreCase("lb")) {

            if (!sender.hasPermission(Permissions.COMMAND_LEADERBOARDS.getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }

            if (!plugin.getModuleManager().isEnabled(ModuleType.PVP_LEADERBOARDS) || plugin.getPvpLeaderboardManager() == null) {
                sender.sendMessage(prefixed("<red>Le leaderboard PvP non sono disponibili. Verifica FancyHolograms e la config."));
                return;
            }

            PvpLeaderboardManager leaderboardManager = plugin.getPvpLeaderboardManager();

            if (args.length == 1) {
                sendLeaderboardHelp(sender);
                return;
            }

            if (args[1].equalsIgnoreCase("list")) {
                if (leaderboardManager.getLeaderboards().isEmpty()) {
                    sender.sendMessage(prefixed("<red>Nessuna leaderboard PvP trovata."));
                    return;
                }

                sender.sendMessage(prefixed("<gray>Leaderboard PvP disponibili<dark_gray>:"));
                for (LeaderboardInstance instance : leaderboardManager.getLeaderboards()) {
                    Location location = instance.getLocation();
                    sender.sendMessage(TextUtil.parse("<dark_gray>- <yellow>" + instance.getId()
                            + " <dark_gray>(" + leaderboardManager.getModeLabel(instance.getMode())
                            + "<dark_gray>) <gray>@ <yellow>" + location.getWorld().getName()
                            + " <dark_gray>[<gray>" + location.getBlockX() + ", " + location.getBlockY() + ", "
                            + location.getBlockZ() + "<dark_gray>]"));
                }
                return;
            }

            if (args[1].equalsIgnoreCase("create")) {
                if (!(sender instanceof Player player)) {
                    Message.CONSOLE_NOT_ALLOWED.send(sender);
                    return;
                }

                if (args.length < 3) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis leaderboard create <id>"));
                    return;
                }

                if (!leaderboardManager.createLeaderboard(args[2], player.getLocation())) {
                    sender.sendMessage(prefixed("<red>Impossibile creare la leaderboard. L'id esiste già oppure la posizione non è valida."));
                    return;
                }

                sender.sendMessage(prefixed("<green>Leaderboard PvP <yellow>" + args[2] + " <green>creata."));
                return;
            }

            if (args[1].equalsIgnoreCase("move")) {
                if (!(sender instanceof Player player)) {
                    Message.CONSOLE_NOT_ALLOWED.send(sender);
                    return;
                }

                if (args.length < 3) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis leaderboard move <id>"));
                    return;
                }

                if (!leaderboardManager.moveLeaderboard(args[2], player.getLocation())) {
                    sender.sendMessage(prefixed("<red>Non esiste nessuna leaderboard PvP con id <yellow>" + args[2] + "<red>."));
                    return;
                }

                sender.sendMessage(prefixed("<green>Leaderboard PvP <yellow>" + args[2] + " <green>spostata."));
                return;
            }

            if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("delete")) {
                if (args.length < 3) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis leaderboard remove <id>"));
                    return;
                }

                if (!leaderboardManager.removeLeaderboard(args[2])) {
                    sender.sendMessage(prefixed("<red>Non esiste nessuna leaderboard PvP con id <yellow>" + args[2] + "<red>."));
                    return;
                }

                sender.sendMessage(prefixed("<green>Leaderboard PvP <yellow>" + args[2] + " <green>rimossa."));
                return;
            }

            if (args[1].equalsIgnoreCase("mode")) {
                if (args.length < 4) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis leaderboard mode <id> <kills|deaths|assists|kd>"));
                    return;
                }

                LeaderboardMode mode = leaderboardManager.parseMode(args[3]);
                if (mode == null) {
                    sender.sendMessage(prefixed("<red>Modalità non valida. Usa <yellow>kills<red>, <yellow>deaths<red>, <yellow>assists <red>o <yellow>kd<red>."));
                    return;
                }

                if (leaderboardManager.setMode(args[2], mode) == null) {
                    sender.sendMessage(prefixed("<red>Non esiste nessuna leaderboard PvP con id <yellow>" + args[2] + "<red>."));
                    return;
                }

                sender.sendMessage(prefixed("<green>Leaderboard PvP <yellow>" + args[2] + " <green>impostata su "
                        + leaderboardManager.getModeLabel(mode) + "<green>."));
                return;
            }

            if (args[1].equalsIgnoreCase("rotate")) {
                if (args.length < 4) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis leaderboard rotate <id> <yaw> [pitch]"));
                    return;
                }

                LeaderboardInstance instance = leaderboardManager.getLeaderboard(args[2]);
                if (instance == null) {
                    sender.sendMessage(prefixed("<red>Non esiste nessuna leaderboard PvP con id <yellow>" + args[2] + "<red>."));
                    return;
                }

                float yaw;
                float pitch = instance.getLocation().getPitch();

                try {
                    yaw = Float.parseFloat(args[3]);
                    if (args.length >= 5) {
                        pitch = Float.parseFloat(args[4]);
                    }
                } catch (NumberFormatException exception) {
                    sender.sendMessage(prefixed("<red>Yaw e pitch devono essere numeri validi."));
                    return;
                }

                leaderboardManager.setRotation(args[2], yaw, pitch);
                sender.sendMessage(prefixed("<green>Rotazione aggiornata <dark_gray>(<gray>yaw <yellow>"
                        + formatDecimal(yaw) + "<gray>, pitch <yellow>" + formatDecimal(pitch) + "<dark_gray>)<green>."));
                return;
            }

            if (args[1].equalsIgnoreCase("billboard")) {
                if (args.length < 4) {
                    Message.USAGE.sendWithReplacement(sender, "command",
                            Component.text("akropolis leaderboard billboard <id> <fixed|center|vertical|horizontal>"));
                    return;
                }

                Display.Billboard billboard = leaderboardManager.parseBillboard(args[3]);
                if (billboard == null) {
                    sender.sendMessage(prefixed("<red>Billboard non valido. Usa <yellow>fixed<red>, <yellow>center<red>, <yellow>vertical <red>o <yellow>horizontal<red>."));
                    return;
                }

                if (leaderboardManager.setBillboard(args[2], billboard) == null) {
                    sender.sendMessage(prefixed("<red>Non esiste nessuna leaderboard PvP con id <yellow>" + args[2] + "<red>."));
                    return;
                }

                sender.sendMessage(prefixed("<green>Billboard impostato su <yellow>" + billboard.name().toLowerCase(Locale.ROOT) + "<green>."));
                return;
            }

            if (args[1].equalsIgnoreCase("scale")) {
                if (args.length < 4) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis leaderboard scale <id> <value>"));
                    return;
                }

                float scale;
                try {
                    scale = Float.parseFloat(args[3]);
                } catch (NumberFormatException exception) {
                    sender.sendMessage(prefixed("<red>La scala deve essere un numero valido."));
                    return;
                }

                Float appliedScale = leaderboardManager.setScale(args[2], scale);
                if (appliedScale == null) {
                    sender.sendMessage(prefixed("<red>Non esiste nessuna leaderboard PvP con id <yellow>" + args[2] + "<red>."));
                    return;
                }

                sender.sendMessage(prefixed("<green>Scala impostata a <yellow>" + formatDecimal(appliedScale) + "<green>."));
                return;
            }

            if (args[1].equalsIgnoreCase("background")) {
                if (args.length < 4) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis leaderboard background <id> <none|#RRGGBB>"));
                    return;
                }

                var background = leaderboardManager.parseBackground(args[3]);
                if (background == null) {
                    sender.sendMessage(prefixed("<red>Background non valido. Usa <yellow>none <red>oppure un colore HEX tipo <yellow>#000000<red>."));
                    return;
                }

                if (leaderboardManager.setBackground(args[2], background) == null) {
                    sender.sendMessage(prefixed("<red>Non esiste nessuna leaderboard PvP con id <yellow>" + args[2] + "<red>."));
                    return;
                }

                sender.sendMessage(prefixed("<green>Background impostato su <yellow>" + leaderboardManager.formatBackground(background) + "<green>."));
                return;
            }

            if (args[1].equalsIgnoreCase("settings")) {
                if (args.length < 3) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis leaderboard settings <id>"));
                    return;
                }

                LeaderboardInstance instance = leaderboardManager.getLeaderboard(args[2]);
                if (instance == null) {
                    sender.sendMessage(prefixed("<red>Non esiste nessuna leaderboard PvP con id <yellow>" + args[2] + "<red>."));
                    return;
                }

                Location location = instance.getLocation();
                sender.sendMessage(prefixed("<gray>Impostazioni leaderboard <yellow>" + instance.getId() + "<dark_gray>:"));
                sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>Modalità<dark_gray>: " + leaderboardManager.getModeLabel(instance.getMode())));
                sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>Billboard<dark_gray>: <yellow>"
                        + instance.getBillboard().name().toLowerCase(Locale.ROOT)));
                sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>Scala<dark_gray>: <yellow>" + formatDecimal(instance.getScale())));
                sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>Background<dark_gray>: <yellow>"
                        + leaderboardManager.formatBackground(instance.getBackground())));
                sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>Rotazione<dark_gray>: <yellow>yaw "
                        + formatDecimal(location.getYaw()) + "<gray>, pitch <yellow>" + formatDecimal(location.getPitch())));
                sender.sendMessage(TextUtil.parse("<dark_gray>» <gray>Posizione<dark_gray>: <yellow>" + location.getWorld().getName()
                        + " <dark_gray>[<gray>" + location.getBlockX() + ", " + location.getBlockY() + ", "
                        + location.getBlockZ() + "<dark_gray>]"));
                return;
            }

            sendLeaderboardHelp(sender);
            return;
        }

        /*
         * Holograms
         */
        if (args[0].equalsIgnoreCase("hologram") || args[0].equalsIgnoreCase("holo")) {

            if (!(sender instanceof Player player)) {
                Message.CONSOLE_NOT_ALLOWED.send(sender);
                return;
            }

            if (!sender.hasPermission(Permissions.COMMAND_HOLOGRAMS.getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }

            if (args.length == 1) {
                Message.HELP_HOLOGRAM.toComponentList().forEach(sender::sendMessage);
                return;
            }

            if (args[1].equalsIgnoreCase("list")) {

                if (plugin.getHologramManager().getHolograms().isEmpty()) {
                    Message.HOLOGRAMS_EMPTY.send(sender);
                    return;
                }

                sender.sendMessage(TextUtil.parse("<gold><b>Akropolis <reset><dark_gray>|| <gray>Hologram list<dark_gray>:"));
                for (Hologram entry : plugin.getHologramManager().getHolograms()) {
                    sender.sendMessage(TextUtil.parse("<dark_gray>- <gray>" + entry.getName()));
                }
            }

            if (args[1].equalsIgnoreCase("create")) {
                if (args.length == 2) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis hologram create <id>"));
                    return;
                }

                if (plugin.getHologramManager().hasHologram(args[2])) {
                    sender.sendMessage(
                            TextUtil.replace(Message.HOLOGRAMS_ALREADY_EXISTS.toComponent(), "name", TextUtil.parse(args[2])));
                    return;
                }

                Hologram holo = plugin.getHologramManager().createHologram(args[2], player.getLocation());
                List<Component> defaultMsg = new ArrayList<>();
                defaultMsg.add(TextUtil.parse("<gray>Created new Hologram called <aqua>" + args[2]));
                defaultMsg.add(TextUtil.parse("<gray>Use <aqua>/akropolis holo <gray>to customise"));
                holo.setLines(defaultMsg);
                Message.HOLOGRAMS_SPAWNED.sendWithReplacement(sender, "name", TextUtil.parse(args[2]));
                return;
            }

            if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("delete")) {
                if (args.length == 2) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis hologram remove <id>"));
                    return;
                }

                if (!plugin.getHologramManager().hasHologram(args[2])) {
                    sender.sendMessage(
                            TextUtil.replace(Message.HOLOGRAMS_INVALID_HOLOGRAM.toComponent(), "name", TextUtil.parse(args[2])));
                    return;
                }

                plugin.getHologramManager().deleteHologram(args[2]);
                Message.HOLOGRAMS_DESPAWNED.sendWithReplacement(sender, "name", TextUtil.parse(args[2]));
                return;
            }

            if (args[1].equalsIgnoreCase("setline")) {
                if (args.length < 5) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis hologram setline <id> <line> <text>"));
                    return;
                }

                if (!plugin.getHologramManager().hasHologram(args[2])) {
                    sender.sendMessage(
                            TextUtil.replace(Message.HOLOGRAMS_INVALID_HOLOGRAM.toComponent(), "name", TextUtil.parse(args[2])));
                    return;
                }

                Hologram holo = plugin.getHologramManager().getHologram(args[2]);
                int line = Integer.parseInt(args[3]);
                String text = TextUtil.joinString(4, args);

                if (holo.hasInvalidLine(line)) {
                    sender.sendMessage(
                            TextUtil.replace(Message.HOLOGRAMS_INVALID_LINE.toComponent(), "line", TextUtil.parse(String.valueOf(line))));
                    return;
                }

                holo.setLine(line, TextUtil.parse(text));
                Message.HOLOGRAMS_LINE_SET.sendWithReplacement(sender, "line", TextUtil.parse(String.valueOf(line)));
                return;
            }

            if (args[1].equalsIgnoreCase("addline")) {
                if (args.length <= 3) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis hologram addline <id> <text>"));
                    return;
                }

                if (!plugin.getHologramManager().hasHologram(args[2])) {
                    sender.sendMessage(
                            TextUtil.replace(Message.HOLOGRAMS_INVALID_HOLOGRAM.toComponent(), "name", TextUtil.parse(args[2])));
                    return;
                }

                Hologram holo = plugin.getHologramManager().getHologram(args[2]);
                Component text = TextUtil.parse(TextUtil.joinString(3, args));

                holo.addLine(text);
                Message.HOLOGRAMS_ADDED_LINE.sendWithReplacement(sender, "name", TextUtil.parse(args[2]));
            }

            if (args[1].equalsIgnoreCase("removeline")) {
                if (args.length != 4) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis hologram removeline <id> <line>"));
                    return;
                }

                if (!plugin.getHologramManager().hasHologram(args[2])) {
                    sender.sendMessage(
                            TextUtil.replace(Message.HOLOGRAMS_INVALID_HOLOGRAM.toComponent(), "name", TextUtil.parse(args[2])));
                    return;
                }

                Hologram holo = plugin.getHologramManager().getHologram(args[2]);
                int line = Integer.parseInt(args[3]);

                if (holo.hasInvalidLine(line)) {
                    sender.sendMessage(
                            TextUtil.replace(Message.HOLOGRAMS_INVALID_LINE.toComponent(), "line", TextUtil.parse(String.valueOf(line))));
                    return;
                }

                if (holo.removeLine(line) == null) {
                    plugin.getHologramManager().deleteHologram(args[2]);
                    sender.sendMessage(
                            TextUtil.replace(Message.HOLOGRAMS_REMOVED_LINE.toComponent(), "name", TextUtil.parse(args[2])));
                }

                return;
            }

            if (args[1].equalsIgnoreCase("move")) {
                if (args.length == 2) {
                    Message.USAGE.sendWithReplacement(sender, "command", Component.text("akropolis hologram move <id>"));
                    return;
                }

                if (!plugin.getHologramManager().hasHologram(args[2])) {
                    sender.sendMessage(
                            TextUtil.replace(Message.HOLOGRAMS_INVALID_HOLOGRAM.toComponent(), "name", TextUtil.parse(args[2])));
                    return;
                }

                Hologram holo = plugin.getHologramManager().getHologram(args[2]);

                holo.setLocation(player.getLocation());
                Message.HOLOGRAMS_MOVED.sendWithReplacement(sender, "name", TextUtil.parse(args[2]));
            }
        }

        /*
         * Song player
         */
        if (args[0].equalsIgnoreCase("songplayer") || args[0].equalsIgnoreCase("sp")) {
            if (!(sender instanceof Player player)) {
                Message.CONSOLE_NOT_ALLOWED.send(sender);
                return;
            }

            if (!sender.hasPermission(Permissions.COMMAND_SONG_PLAYER.getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }

            if (args.length == 1) {
                Message.HELP_SONG_PLAYER.toComponentList().forEach(sender::sendMessage);
                return;
            }

            SongPlayerManager songPlayerManager = plugin.getSongPlayerManager();

            if (songPlayerManager.getSongPlayer() == null) {
                Message.SONG_PLAYER_NOT_LOADED.send(sender);
                return;
            }

            if (args[1].equalsIgnoreCase("setpos")) {
                songPlayerManager.setLocation(player.getLocation());
                Message.SONG_PLAYER_SET_LOCATION.send(sender);
            }

            if (args[1].equalsIgnoreCase("skip")) {
                songPlayerManager.skip();

                // Delay it, because the change is not immediate in the song player.
                Bukkit.getScheduler().runTaskLaterAsynchronously(getPlugin(), () -> {
                    Message.SONG_PLAYER_SKIPPED.sendWithReplacement(
                        sender,
                        "current_song",
                        TextUtil.parse(songPlayerManager.getCurrentSong()));
                }, 20L);
            }
        }
    }

    private void sendLeaderboardHelp(CommandSender sender) {
        sender.sendMessage(prefixed("<gray>Comandi leaderboard PvP<dark_gray>:"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard list <dark_gray>- <gray>Lista tutte le leaderboard PvP"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard create <id> <dark_gray>- <gray>Crea una leaderboard FancyHolograms"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard move <id> <dark_gray>- <gray>Sposta una leaderboard alla tua posizione"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard remove <id> <dark_gray>- <gray>Rimuove una leaderboard"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard mode <id> <kills|deaths|assists|kd> <dark_gray>- <gray>Cambia la statistica mostrata"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard rotate <id> <yaw> [pitch] <dark_gray>- <gray>Ruota la leaderboard"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard billboard <id> <type> <dark_gray>- <gray>Cambia il billboard"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard scale <id> <value> <dark_gray>- <gray>Cambia la scala del testo"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard background <id> <none|#RRGGBB> <dark_gray>- <gray>Cambia lo sfondo del testo"));
        sender.sendMessage(TextUtil.parse("<dark_gray>/<gold>akropolis leaderboard settings <id> <dark_gray>- <gray>Mostra le impostazioni correnti"));
    }

    private Component prefixed(String message) {
        return TextUtil.parse(Message.PREFIX.raw() + " " + message);
    }

    private String formatDecimal(float value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
