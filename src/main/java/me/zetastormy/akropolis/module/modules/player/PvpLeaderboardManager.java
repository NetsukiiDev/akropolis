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

package me.zetastormy.akropolis.module.modules.player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import me.zetastormy.akropolis.AkropolisPlugin;
import me.zetastormy.akropolis.config.ConfigType;
import me.zetastormy.akropolis.module.LifeCycle;
import me.zetastormy.akropolis.module.Module;
import me.zetastormy.akropolis.module.ModuleType;
import org.joml.Vector3f;

public class PvpLeaderboardManager extends Module implements LifeCycle {
    private static final String STATS_PATH = "fight_mode_stats.players";
    private static final String LEADERBOARDS_PATH = "fight_mode_stats.leaderboards";
    private static final String HOLOGRAM_NAME_PREFIX = "akropolis_pvp_lb_";

    private final Map<UUID, PvpPlayerStats> playerStats;
    private final Map<UUID, Map<UUID, Long>> recentAttackers;
    private final Map<String, LeaderboardInstance> leaderboards;
    private final Map<UUID, String> interactionOwners;
    private final DecimalFormat compactDecimalFormat;
    private final DecimalFormat kdDecimalFormat;
    private HologramManager hologramManager;
    private long refreshTicks;
    private long assistTimeoutMillis;
    private int topSize;
    private float interactionWidth;
    private float interactionHeight;
    private double interactionYOffset;
    private LeaderboardMode defaultMode;
    private Display.Billboard defaultBillboard;
    private float defaultScale;
    private Color defaultBackground;
    private boolean defaultTextShadow;
    private boolean defaultSeeThrough;
    private String titleFormat;
    private String subtitleFormat;
    private String entryFormat;
    private String emptyFormat;
    private String footerFormat;
    private boolean titleBold;
    private boolean subtitleBold;
    private boolean entryBold;
    private boolean emptyBold;
    private boolean footerBold;
    private final Map<LeaderboardMode, String> modeNames;
    private int refreshTaskId;

    public PvpLeaderboardManager(AkropolisPlugin plugin) {
        super(plugin, ModuleType.PVP_LEADERBOARDS);
        this.playerStats = new HashMap<>();
        this.recentAttackers = new HashMap<>();
        this.leaderboards = new LinkedHashMap<>();
        this.interactionOwners = new HashMap<>();
        this.compactDecimalFormat = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));
        this.kdDecimalFormat = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
        this.modeNames = new EnumMap<>(LeaderboardMode.class);
        this.refreshTaskId = -1;
    }

    @Override
    public void onEnable() {
        loadSettings();
        loadStats();
        hologramManager = FancyHologramsPlugin.get().getHologramManager();
        loadLeaderboards();
        startRefreshTask();
    }

    @Override
    public void onDisable() {
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }

        saveAllData();
        leaderboards.values().forEach(this::despawnLeaderboard);
        leaderboards.clear();
        interactionOwners.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (inDisabledWorld(victim.getLocation())) return;

        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        FightModeManager fightModeManager = getPlugin().getFightModeManager();
        if (fightModeManager == null) return;
        if (!fightModeManager.isInFightMode(victim.getUniqueId()) || !fightModeManager.isInFightMode(attacker.getUniqueId())) {
            return;
        }

        updatePlayerName(attacker);
        updatePlayerName(victim);

        recentAttackers.computeIfAbsent(victim.getUniqueId(), ignored -> new HashMap<>())
                .put(attacker.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (inDisabledWorld(victim.getLocation())) return;

        Player killer = victim.getKiller();
        FightModeManager fightModeManager = getPlugin().getFightModeManager();

        if (killer == null || fightModeManager == null) {
            recentAttackers.remove(victim.getUniqueId());
            return;
        }

        if (!fightModeManager.isInFightMode(victim.getUniqueId()) || !fightModeManager.isInFightMode(killer.getUniqueId())) {
            recentAttackers.remove(victim.getUniqueId());
            return;
        }

        PvpPlayerStats killerStats = getOrCreateStats(killer.getUniqueId(), killer.getName());
        PvpPlayerStats victimStats = getOrCreateStats(victim.getUniqueId(), victim.getName());

        killerStats.kills++;
        victimStats.deaths++;

        long now = System.currentTimeMillis();
        Map<UUID, Long> attackers = recentAttackers.remove(victim.getUniqueId());

        if (attackers != null) {
            for (Map.Entry<UUID, Long> entry : attackers.entrySet()) {
                UUID assisterId = entry.getKey();

                if (assisterId.equals(killer.getUniqueId()) || assisterId.equals(victim.getUniqueId())) continue;
                if ((now - entry.getValue()) > assistTimeoutMillis) continue;

                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(assisterId);
                PvpPlayerStats assisterStats = getOrCreateStats(assisterId,
                        offlinePlayer.getName() == null ? assisterId.toString() : offlinePlayer.getName());
                assisterStats.assists++;
            }
        }

        purgeExpiredAttackers(now);
        saveAllData();
        refreshAllLeaderboards();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updatePlayerName(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        recentAttackers.remove(playerId);

        for (Iterator<Map<UUID, Long>> iterator = recentAttackers.values().iterator(); iterator.hasNext();) {
            Map<UUID, Long> attackers = iterator.next();
            attackers.remove(playerId);

            if (attackers.isEmpty()) {
                iterator.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeaderboardInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        String leaderboardId = interactionOwners.get(interaction.getUniqueId());
        if (leaderboardId == null) return;

        LeaderboardInstance instance = leaderboards.get(leaderboardId);
        if (instance == null) return;

        event.setCancelled(true);
        cycleMode(instance.id);
    }

    public Collection<LeaderboardInstance> getLeaderboards() {
        return Collections.unmodifiableCollection(leaderboards.values());
    }

    public boolean hasLeaderboard(String id) {
        return leaderboards.containsKey(normalizeId(id));
    }

    public boolean createLeaderboard(String id, Location location) {
        String normalizedId = normalizeId(id);
        Location normalizedLocation = normalizePlacementLocation(location);
        if (leaderboards.containsKey(normalizedId) || normalizedLocation == null || normalizedLocation.getWorld() == null) return false;

        LeaderboardInstance instance = new LeaderboardInstance(normalizedId, normalizedLocation, defaultMode, defaultBillboard,
                defaultScale, defaultBackground);
        leaderboards.put(normalizedId, instance);
        spawnLeaderboard(instance);
        saveAllData();
        return true;
    }

    public boolean moveLeaderboard(String id, Location location) {
        LeaderboardInstance instance = leaderboards.get(normalizeId(id));
        Location normalizedLocation = normalizePlacementLocation(location);
        if (instance == null || normalizedLocation == null || normalizedLocation.getWorld() == null) return false;

        normalizedLocation.setYaw(instance.location.getYaw());
        normalizedLocation.setPitch(instance.location.getPitch());
        instance.location = normalizedLocation;
        spawnLeaderboard(instance);
        saveAllData();
        return true;
    }

    public boolean removeLeaderboard(String id) {
        LeaderboardInstance instance = leaderboards.remove(normalizeId(id));
        if (instance == null) return false;

        despawnLeaderboard(instance);
        saveAllData();
        return true;
    }

    public LeaderboardMode setMode(String id, LeaderboardMode mode) {
        LeaderboardInstance instance = leaderboards.get(normalizeId(id));
        if (instance == null || mode == null) return null;

        instance.mode = mode;
        updateLeaderboard(instance);
        saveAllData();
        return mode;
    }

    public LeaderboardMode cycleMode(String id) {
        LeaderboardInstance instance = leaderboards.get(normalizeId(id));
        if (instance == null) return null;

        instance.mode = instance.mode.next();
        updateLeaderboard(instance);
        saveAllData();
        return instance.mode;
    }

    public LeaderboardInstance getLeaderboard(String id) {
        return leaderboards.get(normalizeId(id));
    }

    public LeaderboardMode parseMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) return null;

        try {
            return LeaderboardMode.valueOf(rawMode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public Display.Billboard parseBillboard(String rawBillboard) {
        if (rawBillboard == null || rawBillboard.isBlank()) return null;

        try {
            return Display.Billboard.valueOf(rawBillboard.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean setRotation(String id, float yaw, float pitch) {
        LeaderboardInstance instance = leaderboards.get(normalizeId(id));
        if (instance == null) return false;

        instance.location.setYaw(yaw);
        instance.location.setPitch(pitch);
        updateLeaderboard(instance);
        saveAllData();
        return true;
    }

    public Display.Billboard setBillboard(String id, Display.Billboard billboard) {
        LeaderboardInstance instance = leaderboards.get(normalizeId(id));
        if (instance == null || billboard == null) return null;

        instance.billboard = billboard;
        updateLeaderboard(instance);
        saveAllData();
        return billboard;
    }

    public Float setScale(String id, float scale) {
        LeaderboardInstance instance = leaderboards.get(normalizeId(id));
        if (instance == null) return null;

        instance.scale = Math.max(0.1F, scale);
        updateLeaderboard(instance);
        saveAllData();
        return instance.scale;
    }

    public Color parseBackground(String rawBackground) {
        return parseBackground(rawBackground, null);
    }

    public Color setBackground(String id, Color background) {
        LeaderboardInstance instance = leaderboards.get(normalizeId(id));
        if (instance == null || background == null) return null;

        instance.background = background;
        updateLeaderboard(instance);
        saveAllData();
        return background;
    }

    public String getModeLabel(LeaderboardMode mode) {
        return modeNames.getOrDefault(mode, mode.name());
    }

    public String formatBackground(Color background) {
        if (background == null || background.getAlpha() <= 0) {
            return "NONE";
        }

        return String.format("#%06X", background.asRGB() & 0xFFFFFF);
    }

    private void loadSettings() {
        FileConfiguration config = getConfig(ConfigType.SETTINGS);
        ConfigurationSection section = config.getConfigurationSection("fight_mode.leaderboard");

        refreshTicks = section == null ? 40L : Math.max(1L, section.getLong("update_ticks", 40L));
        assistTimeoutMillis = (section == null ? 15L : Math.max(1L, section.getLong("assist_timeout_seconds", 15L))) * 1000L;
        topSize = section == null ? 10 : Math.max(1, section.getInt("top_size", 10));
        defaultMode = LeaderboardMode.fromString(section == null ? "KILLS" : section.getString("default_mode", "KILLS"));

        ConfigurationSection interactionSection = section == null ? null : section.getConfigurationSection("interaction");
        interactionWidth = (float) Math.max(0.5D, interactionSection == null ? 2.8D : interactionSection.getDouble("width", 2.8D));
        interactionHeight = (float) Math.max(0.5D, interactionSection == null ? 4.5D : interactionSection.getDouble("height", 4.5D));
        interactionYOffset = interactionSection == null ? 0.0D : interactionSection.getDouble("y_offset", 0.0D);

        ConfigurationSection appearanceSection = section == null ? null : section.getConfigurationSection("appearance");
        defaultBillboard = parseBillboardOrDefault(appearanceSection == null ? null : appearanceSection.getString("billboard"),
                Display.Billboard.FIXED);
        defaultScale = (float) Math.max(0.1D, appearanceSection == null ? 1.0D : appearanceSection.getDouble("scale", 1.0D));
        defaultBackground = parseBackground(appearanceSection == null ? "NONE" : appearanceSection.getString("background", "NONE"),
                Color.fromARGB(0));
        defaultTextShadow = appearanceSection == null || appearanceSection.getBoolean("text_shadow", true);
        defaultSeeThrough = appearanceSection != null && appearanceSection.getBoolean("see_through", false);

        ConfigurationSection textSection = section == null ? null : section.getConfigurationSection("text");
        titleFormat = textSection == null
                ? "<gold><b>ᴄʟᴀssɪꜰɪᴄᴀ ɢɪᴏᴄᴀᴛᴏʀɪ"
                : textSection.getString("title", "<gold><b>ᴄʟᴀssɪꜰɪᴄᴀ ɢɪᴏᴄᴀᴛᴏʀɪ");
        subtitleFormat = textSection == null
                ? "<gray>ᴛᴏᴘ <yellow><limit> <dark_gray>- <mode>"
                : textSection.getString("subtitle", "<gray>ᴛᴏᴘ <yellow><limit> <dark_gray>- <mode>");
        entryFormat = textSection == null
                ? "<gray><position>. <green><player> <dark_gray>- <yellow><value>"
                : textSection.getString("entry", "<gray><position>. <green><player> <dark_gray>- <yellow><value>");
        emptyFormat = textSection == null
                ? "<dark_gray>» <gray>Nessun dato disponibile."
                : textSection.getString("empty", "<dark_gray>» <gray>Nessun dato disponibile.");
        footerFormat = textSection == null
                ? "<dark_gray>(ᴄʟɪᴄᴋ ᴅᴇꜱᴛʀᴏ ᴘᴇʀ ᴄᴀᴍʙɪᴀʀᴇ)"
                : textSection.getString("footer", "<dark_gray>(ᴄʟɪᴄᴋ ᴅᴇꜱᴛʀᴏ ᴘᴇʀ ᴄᴀᴍʙɪᴀʀᴇ)");

        titleFormat = stripBoldTags(titleFormat);
        subtitleFormat = stripBoldTags(subtitleFormat);
        entryFormat = stripBoldTags(entryFormat);
        emptyFormat = stripBoldTags(emptyFormat);
        footerFormat = stripBoldTags(footerFormat);

        titleBold = config.getBoolean("fight_mode.leaderboard.text.bold.title", true);
        subtitleBold = config.getBoolean("fight_mode.leaderboard.text.bold.subtitle", false);
        entryBold = config.getBoolean("fight_mode.leaderboard.text.bold.entries", false);
        emptyBold = config.getBoolean("fight_mode.leaderboard.text.bold.empty", false);
        footerBold = config.getBoolean("fight_mode.leaderboard.text.bold.footer", false);

        ConfigurationSection modeNamesSection = section == null ? null : section.getConfigurationSection("mode_names");
        modeNames.clear();
        modeNames.put(LeaderboardMode.KILLS, modeNamesSection == null ? "<green>ᴋɪʟʟ" : modeNamesSection.getString("kills", "<green>ᴋɪʟʟ"));
        modeNames.put(LeaderboardMode.DEATHS, modeNamesSection == null ? "<red>ᴍᴏʀᴛɪ" : modeNamesSection.getString("deaths", "<red>ᴍᴏʀᴛɪ"));
        modeNames.put(LeaderboardMode.ASSISTS, modeNamesSection == null ? "<aqua>ᴀssɪꜱᴛ" : modeNamesSection.getString("assists", "<aqua>ᴀssɪꜱᴛ"));
        modeNames.put(LeaderboardMode.KD, modeNamesSection == null ? "<gold>ᴋ/ᴅ" : modeNamesSection.getString("kd", "<gold>ᴋ/ᴅ"));
    }

    private void loadStats() {
        playerStats.clear();

        ConfigurationSection playersSection = getConfig(ConfigType.DATA).getConfigurationSection(STATS_PATH);
        if (playersSection == null) return;

        for (String rawUuid : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                ConfigurationSection playerSection = playersSection.getConfigurationSection(rawUuid);
                if (playerSection == null) continue;

                PvpPlayerStats stats = new PvpPlayerStats(uuid,
                        playerSection.getString("name", rawUuid),
                        Math.max(0, playerSection.getInt("kills", 0)),
                        Math.max(0, playerSection.getInt("deaths", 0)),
                        Math.max(0, playerSection.getInt("assists", 0)));
                playerStats.put(uuid, stats);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void loadLeaderboards() {
        leaderboards.clear();
        interactionOwners.clear();

        ConfigurationSection leaderboardsSection = getConfig(ConfigType.DATA).getConfigurationSection(LEADERBOARDS_PATH);
        if (leaderboardsSection == null) return;

        for (String id : leaderboardsSection.getKeys(false)) {
            ConfigurationSection entrySection = leaderboardsSection.getConfigurationSection(id);
            if (entrySection == null) continue;

            Location location = entrySection.getLocation("location");
            if (location == null || location.getWorld() == null) continue;

            ConfigurationSection rotationSection = entrySection.getConfigurationSection("rotation");
            if (rotationSection == null) {
                location.setPitch(0.0F);
            } else {
                location.setYaw((float) rotationSection.getDouble("yaw", location.getYaw()));
                location.setPitch((float) rotationSection.getDouble("pitch", location.getPitch()));
            }

            LeaderboardMode mode = LeaderboardMode.fromString(entrySection.getString("mode", defaultMode.name()));
            Display.Billboard billboard = parseBillboardOrDefault(entrySection.getString("billboard", defaultBillboard.name()),
                    defaultBillboard);
            float scale = (float) Math.max(0.1D, entrySection.getDouble("scale", defaultScale));
            Color background = parseBackground(entrySection.getString("background", formatBackground(defaultBackground)),
                    defaultBackground);

            LeaderboardInstance instance = new LeaderboardInstance(normalizeId(id), location, mode, billboard, scale, background);
            leaderboards.put(instance.id, instance);
            spawnLeaderboard(instance);
        }
    }

    private void startRefreshTask() {
        refreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(getPlugin(), this::refreshAllLeaderboards, refreshTicks,
                refreshTicks);
    }

    private void refreshAllLeaderboards() {
        purgeExpiredAttackers(System.currentTimeMillis());
        leaderboards.values().forEach(this::updateLeaderboard);
    }

    private void updateLeaderboard(LeaderboardInstance instance) {
        if (instance.hologram == null) {
            spawnLeaderboard(instance);
            return;
        }

        if (!(instance.hologram.getData() instanceof TextHologramData textData)) {
            spawnLeaderboard(instance);
            return;
        }

        textData.setLocation(instance.location.clone());
        textData.setText(buildLines(instance.mode));
        applyAppearance(textData, instance);
        instance.hologram.queueUpdate();
        updateInteractionLocation(instance);
    }

    private void spawnLeaderboard(LeaderboardInstance instance) {
        despawnLeaderboard(instance);

        TextHologramData data = new TextHologramData(instance.hologramName(), instance.location.clone());
        data.setPersistent(false);
        data.setText(buildLines(instance.mode));
        applyAppearance(data, instance);

        Hologram hologram = hologramManager.create(data);
        hologram.getData().setPersistent(false);
        hologramManager.addHologram(hologram);

        instance.hologram = hologram;
        spawnInteraction(instance);
    }

    private void despawnLeaderboard(LeaderboardInstance instance) {
        if (instance.interactionId != null) {
            interactionOwners.remove(instance.interactionId);

            Entity interactionEntity = Bukkit.getEntity(instance.interactionId);
            if (interactionEntity != null && interactionEntity.isValid()) {
                interactionEntity.remove();
            }

            instance.interactionId = null;
        }

        if (instance.hologram == null) {
            hologramManager.getHologram(instance.hologramName()).ifPresent(hologramManager::removeHologram);
            return;
        }

        hologramManager.removeHologram(instance.hologram);
        instance.hologram = null;
    }

    private void spawnInteraction(LeaderboardInstance instance) {
        World world = instance.location.getWorld();
        if (world == null) return;

        Location interactionLocation = instance.location.clone().add(0.0D, interactionYOffset, 0.0D);
        Interaction interaction = world.spawn(interactionLocation, Interaction.class, entity -> {
            entity.setInteractionWidth(interactionWidth);
            entity.setInteractionHeight(interactionHeight);
            entity.setResponsive(true);
            entity.setPersistent(false);
        });

        instance.interactionId = interaction.getUniqueId();
        interactionOwners.put(interaction.getUniqueId(), instance.id);
    }

    private void updateInteractionLocation(LeaderboardInstance instance) {
        if (instance.interactionId == null) return;

        Entity entity = Bukkit.getEntity(instance.interactionId);
        if (!(entity instanceof Interaction interaction) || !interaction.isValid()) {
            spawnInteraction(instance);
            return;
        }

        interaction.teleport(instance.location.clone().add(0.0D, interactionYOffset, 0.0D));
        interaction.setInteractionWidth(interactionWidth);
        interaction.setInteractionHeight(interactionHeight);
        interaction.setResponsive(true);
    }

    private List<String> buildLines(LeaderboardMode mode) {
        List<String> lines = new ArrayList<>();
        lines.add(applyBold(titleFormat, titleBold));
        lines.add(applyBold(replace(subtitleFormat,
                Map.of("mode", getModeLabel(mode), "limit", String.valueOf(topSize))), subtitleBold));

        List<PvpPlayerStats> topEntries = getTopEntries(mode);

        if (topEntries.isEmpty()) {
            lines.add(applyBold(emptyFormat, emptyBold));
        } else {
            int position = 1;

            for (PvpPlayerStats entry : topEntries) {
                lines.add(applyBold(replace(entryFormat, Map.of(
                        "position", position + ".",
                        "player", entry.name,
                        "value", formatValue(mode, entry)
                )), entryBold));
                position++;
            }
        }

        lines.add(applyBold(footerFormat, footerBold));
        return lines;
    }

    private List<PvpPlayerStats> getTopEntries(LeaderboardMode mode) {
        List<PvpPlayerStats> entries = new ArrayList<>(playerStats.values());
        entries.removeIf(stats -> mode.getSortableValue(stats) <= 0.0D);
        entries.sort((left, right) -> compareStats(mode, left, right));

        if (entries.size() <= topSize) {
            return entries;
        }

        return new ArrayList<>(entries.subList(0, topSize));
    }

    private int compareStats(LeaderboardMode mode, PvpPlayerStats left, PvpPlayerStats right) {
        int primary = Double.compare(mode.getSortableValue(right), mode.getSortableValue(left));
        if (primary != 0) return primary;

        int killsComparison = Integer.compare(right.kills, left.kills);
        if (killsComparison != 0) return killsComparison;

        int assistsComparison = Integer.compare(right.assists, left.assists);
        if (assistsComparison != 0) return assistsComparison;

        return left.name.compareToIgnoreCase(right.name);
    }

    private String formatValue(LeaderboardMode mode, PvpPlayerStats stats) {
        if (mode == LeaderboardMode.KD) {
            return kdDecimalFormat.format(mode.getSortableValue(stats));
        }

        return compactNumber((long) mode.getSortableValue(stats));
    }

    private String compactNumber(long value) {
        long absolute = Math.abs(value);

        if (absolute >= 1_000_000L) {
            return compactDecimalFormat.format(value / 1_000_000.0D) + "M";
        }

        if (absolute >= 1_000L) {
            return compactDecimalFormat.format(value / 1_000.0D) + "K";
        }

        return String.valueOf(value);
    }

    private String replace(String format, Map<String, String> replacements) {
        String result = format;

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }

        return result;
    }

    private String applyBold(String line, boolean bold) {
        if (!bold) {
            return line;
        }

        return "<b>" + line;
    }

    private String stripBoldTags(String line) {
        return line.replace("<b>", "").replace("</b>", "");
    }

    private void purgeExpiredAttackers(long now) {
        for (Iterator<Map<UUID, Long>> outer = recentAttackers.values().iterator(); outer.hasNext();) {
            Map<UUID, Long> attackers = outer.next();
            attackers.entrySet().removeIf(entry -> (now - entry.getValue()) > assistTimeoutMillis);

            if (attackers.isEmpty()) {
                outer.remove();
            }
        }
    }

    private void applyAppearance(TextHologramData data, LeaderboardInstance instance) {
        data.setBillboard(instance.billboard);
        data.setScale(new Vector3f(instance.scale));
        data.setBackground(instance.background);
        data.setTextAlignment(TextDisplay.TextAlignment.CENTER);
        data.setTextShadow(defaultTextShadow);
        data.setSeeThrough(defaultSeeThrough);
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }

        return null;
    }

    private PvpPlayerStats getOrCreateStats(UUID playerId, String playerName) {
        return playerStats.computeIfAbsent(playerId, ignored -> new PvpPlayerStats(playerId, playerName, 0, 0, 0));
    }

    private void updatePlayerName(Player player) {
        PvpPlayerStats stats = playerStats.get(player.getUniqueId());
        if (stats == null) return;

        stats.name = player.getName();
    }

    private void saveAllData() {
        FileConfiguration dataConfig = getConfig(ConfigType.DATA);
        dataConfig.set(STATS_PATH, null);
        dataConfig.set(LEADERBOARDS_PATH, null);

        for (PvpPlayerStats stats : playerStats.values()) {
            String path = STATS_PATH + "." + stats.uuid;
            dataConfig.set(path + ".name", stats.name);
            dataConfig.set(path + ".kills", stats.kills);
            dataConfig.set(path + ".deaths", stats.deaths);
            dataConfig.set(path + ".assists", stats.assists);
        }

        for (LeaderboardInstance instance : leaderboards.values()) {
            String path = LEADERBOARDS_PATH + "." + instance.id;
            dataConfig.set(path + ".location", instance.location);
            dataConfig.set(path + ".rotation.yaw", instance.location.getYaw());
            dataConfig.set(path + ".rotation.pitch", instance.location.getPitch());
            dataConfig.set(path + ".mode", instance.mode.name());
            dataConfig.set(path + ".billboard", instance.billboard.name());
            dataConfig.set(path + ".scale", instance.scale);
            dataConfig.set(path + ".background", formatBackground(instance.background));
        }

        getPlugin().getConfigManager().getFile(ConfigType.DATA).save();
    }

    private String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private Location normalizePlacementLocation(Location location) {
        if (location == null || location.getWorld() == null) return null;

        Location normalized = location.clone();
        normalized.setPitch(0.0F);
        return normalized;
    }

    private Display.Billboard parseBillboardOrDefault(String rawBillboard, Display.Billboard fallback) {
        Display.Billboard billboard = parseBillboard(rawBillboard);
        return billboard == null ? fallback : billboard;
    }

    private Color parseBackground(String rawBackground, Color fallback) {
        if (rawBackground == null || rawBackground.isBlank()) {
            return fallback;
        }

        if (rawBackground.equalsIgnoreCase("none") || rawBackground.equalsIgnoreCase("transparent")) {
            return Color.fromARGB(0);
        }

        try {
            String rawHex = rawBackground.startsWith("#") ? rawBackground.substring(1) : rawBackground;
            return Color.fromRGB(Integer.parseInt(rawHex, 16));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public enum LeaderboardMode {
        KILLS,
        DEATHS,
        ASSISTS,
        KD;

        public static LeaderboardMode fromString(String rawMode) {
            if (rawMode == null || rawMode.isBlank()) return KILLS;

            try {
                return valueOf(rawMode.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return KILLS;
            }
        }

        public LeaderboardMode next() {
            return switch (this) {
                case KILLS -> DEATHS;
                case DEATHS -> ASSISTS;
                case ASSISTS -> KD;
                case KD -> KILLS;
            };
        }

        public double getSortableValue(PvpPlayerStats stats) {
            return switch (this) {
                case KILLS -> stats.kills;
                case DEATHS -> stats.deaths;
                case ASSISTS -> stats.assists;
                case KD -> stats.deaths <= 0 ? stats.kills : stats.kills / (double) stats.deaths;
            };
        }
    }

    private static final class PvpPlayerStats {
        private final UUID uuid;
        private String name;
        private int kills;
        private int deaths;
        private int assists;

        private PvpPlayerStats(UUID uuid, String name, int kills, int deaths, int assists) {
            this.uuid = uuid;
            this.name = name;
            this.kills = kills;
            this.deaths = deaths;
            this.assists = assists;
        }
    }

    public static final class LeaderboardInstance {
        private final String id;
        private Location location;
        private LeaderboardMode mode;
        private Display.Billboard billboard;
        private float scale;
        private Color background;
        private Hologram hologram;
        private UUID interactionId;

        private LeaderboardInstance(String id, Location location, LeaderboardMode mode, Display.Billboard billboard, float scale,
                Color background) {
            this.id = id;
            this.location = location;
            this.mode = mode;
            this.billboard = billboard;
            this.scale = scale;
            this.background = background;
        }

        public String getId() {
            return id;
        }

        public Location getLocation() {
            return location.clone();
        }

        public LeaderboardMode getMode() {
            return mode;
        }

        public Display.Billboard getBillboard() {
            return billboard;
        }

        public float getScale() {
            return scale;
        }

        public Color getBackground() {
            return background;
        }

        private String hologramName() {
            return HOLOGRAM_NAME_PREFIX + id;
        }
    }
}
