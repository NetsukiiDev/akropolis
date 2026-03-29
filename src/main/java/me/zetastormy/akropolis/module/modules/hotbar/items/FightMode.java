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

package me.zetastormy.akropolis.module.modules.hotbar.items;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import me.zetastormy.akropolis.config.ConfigType;
import me.zetastormy.akropolis.module.ModuleType;
import me.zetastormy.akropolis.module.modules.hotbar.HotbarItem;
import me.zetastormy.akropolis.module.modules.hotbar.HotbarManager;
import me.zetastormy.akropolis.module.modules.player.FightModeManager;
import me.zetastormy.akropolis.util.text.PlaceholderUtil;
import net.kyori.adventure.text.Component;

public class FightMode extends HotbarItem {
    private static final String ABILITY_COOLDOWN_KEY = "fight_mode_ability";

    private final FightModeManager fightModeManager;
    private final boolean abilityEnabled;
    private final long abilityCooldown;
    private final double abilityDamage;
    private final double abilityTravelDistance;
    private final int abilityTravelDurationTicks;
    private final double abilityHeightOffset;
    private final double abilityStartRadius;
    private final double abilityEndRadius;
    private final double abilityHitboxRadius;
    private final double abilityCollisionHeight;
    private final ParticleProfile abilityOuterParticle;
    private final ParticleProfile abilityInnerParticle;
    private final int abilityTrailSteps;
    private final double abilityTrailSpacing;
    private final double abilityTrailRadiusMultiplier;
    private final boolean abilitySoundEnabled;
    private final Sound abilitySound;
    private final float abilitySoundVolume;
    private final float abilitySoundPitch;
    private final boolean abilityCooldownBarEnabled;
    private final int abilityCooldownBarUpdateTicks;
    private final boolean abilityCooldownBarShowSeconds;
    private final boolean abilityActionBarEnabled;
    private final String abilityActionBarText;
    private final String abilityActionBarReadyText;
    private final String abilityActionBarCooldownText;
    private final boolean abilityReadySoundEnabled;
    private final Sound abilityReadySound;
    private final float abilityReadySoundVolume;
    private final float abilityReadySoundPitch;
    private final Map<UUID, ExperienceSnapshot> abilityExperienceSnapshots;
    private final Set<Particle> warnedUnsupportedParticles;
    private final Set<UUID> abilityPlayersOnCooldown;

    public FightMode(HotbarManager hotbarManager, ItemStack item, int slot, String keyValue) {
        super(hotbarManager, item, slot, keyValue);

        this.fightModeManager = (FightModeManager) getPlugin().getModuleManager().getModule(ModuleType.FIGHT_MODE);
        this.abilityExperienceSnapshots = new HashMap<>();
        this.warnedUnsupportedParticles = new HashSet<>();
        this.abilityPlayersOnCooldown = new HashSet<>();

        ConfigurationSection abilitySection = getPlugin()
                .getConfigManager()
                .getFile(ConfigType.SETTINGS)
                .get()
                .getConfigurationSection("fight_mode.ability");

        if (abilitySection == null) {
            abilityEnabled = false;
            abilityCooldown = 10L;
            abilityDamage = 6.0D;
            abilityTravelDistance = 14.0D;
            abilityTravelDurationTicks = 16;
            abilityHeightOffset = 1.0D;
            abilityStartRadius = 0.95D;
            abilityEndRadius = 0.3D;
            abilityHitboxRadius = 0.45D;
            abilityCollisionHeight = 1.1D;
            abilityOuterParticle = new ParticleProfile(Particle.SMOKE, 1, 0.01D, 0.01D, 0.01D, 0.0D, 14, 1, 0.0D, 1.0D);
            abilityInnerParticle = new ParticleProfile(Particle.DRAGON_BREATH, 1, 0.03D, 0.02D, 0.03D, 0.01D, 10, 1, 0.0D, 0.55D);
            abilityTrailSteps = 5;
            abilityTrailSpacing = 0.4D;
            abilityTrailRadiusMultiplier = 0.82D;
            abilitySoundEnabled = true;
            abilitySound = Sound.ENTITY_WARDEN_SONIC_BOOM;
            abilitySoundVolume = 1.0F;
            abilitySoundPitch = 1.2F;
            abilityCooldownBarEnabled = true;
            abilityCooldownBarUpdateTicks = 2;
            abilityCooldownBarShowSeconds = true;
            abilityActionBarEnabled = true;
            abilityActionBarText = "<dark_gray>» <gray>ᴀʙɪʟɪᴛᴀ<dark_gray>: <status>";
            abilityActionBarReadyText = "<green>ᴘʀᴏɴᴛᴀ";
            abilityActionBarCooldownText = "<red>ɪɴ ʀɪᴄᴀʀɪᴄᴀ <dark_gray>(<yellow><seconds>s<dark_gray>)";
            abilityReadySoundEnabled = true;
            abilityReadySound = Sound.BLOCK_NOTE_BLOCK_PLING;
            abilityReadySoundVolume = 1.0F;
            abilityReadySoundPitch = 1.6F;
            return;
        }

        ConfigurationSection travelSection = abilitySection.getConfigurationSection("travel");
        ConfigurationSection circleSection = abilitySection.getConfigurationSection("circle");
        ConfigurationSection particleSection = abilitySection.getConfigurationSection("particle");
        ConfigurationSection outerParticleSection = particleSection == null ? null : particleSection.getConfigurationSection("outer");
        ConfigurationSection innerParticleSection = particleSection == null ? null : particleSection.getConfigurationSection("inner");
        ConfigurationSection trailSection = particleSection == null ? null : particleSection.getConfigurationSection("trail");
        ConfigurationSection soundSection = abilitySection.getConfigurationSection("sound");
        ConfigurationSection cooldownBarSection = abilitySection.getConfigurationSection("cooldown_bar");
        ConfigurationSection actionBarSection = abilitySection.getConfigurationSection("action_bar");
        ConfigurationSection readySoundSection = abilitySection.getConfigurationSection("ready_sound");

        abilityEnabled = abilitySection.getBoolean("enabled", true);
        abilityCooldown = Math.max(0L, abilitySection.getLong("cooldown", 10L));
        abilityDamage = Math.max(0.0D, abilitySection.getDouble("damage", 6.0D));
        abilityTravelDistance = Math.max(0.5D, travelSection == null ? 14.0D : travelSection.getDouble("distance", 14.0D));
        abilityTravelDurationTicks = Math.max(1, travelSection == null ? 16 : travelSection.getInt("duration_ticks", 16));
        abilityHeightOffset = travelSection == null ? 0.15D : travelSection.getDouble("height_offset", 0.15D);
        abilityStartRadius = Math.max(0.15D, circleSection == null ? 0.95D : circleSection.getDouble("start_radius", 0.95D));
        abilityEndRadius = Math.max(0.1D, circleSection == null ? 0.3D : circleSection.getDouble("end_radius", 0.3D));
        abilityHitboxRadius = Math.max(0.1D, circleSection == null ? 0.45D : circleSection.getDouble("hitbox_radius", 0.45D));
        abilityCollisionHeight = Math.max(0.1D,
                circleSection == null ? 1.1D : circleSection.getDouble("collision_height", 1.1D));
        abilityOuterParticle = loadParticleProfile(
                outerParticleSection == null ? particleSection : outerParticleSection,
                new ParticleProfile(Particle.SMOKE, 1, 0.01D, 0.01D, 0.01D, 0.0D, 14, 1, 0.0D, 1.0D),
                "fight_mode.ability.particle.outer");
        abilityInnerParticle = loadParticleProfile(
                innerParticleSection,
                new ParticleProfile(Particle.DRAGON_BREATH, 1, 0.03D, 0.02D, 0.03D, 0.01D, 10, 1, 0.0D, 0.55D),
                "fight_mode.ability.particle.inner");
        abilityTrailSteps = Math.max(0, trailSection == null ? 5 : trailSection.getInt("steps", 5));
        abilityTrailSpacing = Math.max(0.1D, trailSection == null ? 0.4D : trailSection.getDouble("spacing", 0.4D));
        abilityTrailRadiusMultiplier = Math.max(0.1D,
                trailSection == null ? 0.82D : trailSection.getDouble("radius_multiplier", 0.82D));
        abilitySoundEnabled = soundSection == null || soundSection.getBoolean("enabled", true);
        abilitySound = parseSound(soundSection == null ? "ENTITY_WARDEN_SONIC_BOOM" : soundSection.getString("value", "ENTITY_WARDEN_SONIC_BOOM"));
        abilitySoundVolume = (float) Math.max(0.0D, soundSection == null ? 1.0D : soundSection.getDouble("volume", 1.0D));
        abilitySoundPitch = (float) Math.max(0.0D, soundSection == null ? 1.2D : soundSection.getDouble("pitch", 1.2D));
        abilityCooldownBarEnabled = cooldownBarSection == null || cooldownBarSection.getBoolean("enabled", true);
        abilityCooldownBarUpdateTicks = Math.max(1,
                cooldownBarSection == null ? 2 : cooldownBarSection.getInt("update_ticks", 2));
        abilityCooldownBarShowSeconds = cooldownBarSection == null || cooldownBarSection.getBoolean("show_seconds", true);
        abilityActionBarEnabled = actionBarSection == null || actionBarSection.getBoolean("enabled", true);
        abilityActionBarText = actionBarSection == null
                ? "<dark_gray>» <gray>ᴀʙɪʟɪᴛᴀ<dark_gray>: <status>"
                : actionBarSection.getString("text", "<dark_gray>» <gray>ᴀʙɪʟɪᴛᴀ<dark_gray>: <status>");
        abilityActionBarReadyText = actionBarSection == null
                ? "<green>ᴘʀᴏɴᴛᴀ"
                : actionBarSection.getString("ready_text", "<green>ᴘʀᴏɴᴛᴀ");
        abilityActionBarCooldownText = actionBarSection == null
                ? "<red>ɪɴ ʀɪᴄᴀʀɪᴄᴀ <dark_gray>(<yellow><seconds>s<dark_gray>)"
                : actionBarSection.getString("cooldown_text",
                "<red>ɪɴ ʀɪᴄᴀʀɪᴄᴀ <dark_gray>(<yellow><seconds>s<dark_gray>)");
        abilityReadySoundEnabled = readySoundSection == null || readySoundSection.getBoolean("enabled", true);
        abilityReadySound = parseSound(readySoundSection == null
                ? "BLOCK_NOTE_BLOCK_PLING"
                : readySoundSection.getString("value", "BLOCK_NOTE_BLOCK_PLING"));
        abilityReadySoundVolume = (float) Math.max(0.0D,
                readySoundSection == null ? 1.0D : readySoundSection.getDouble("volume", 1.0D));
        abilityReadySoundPitch = (float) Math.max(0.0D,
                readySoundSection == null ? 1.6D : readySoundSection.getDouble("pitch", 1.6D));

        if (abilityEnabled && (abilityCooldownBarEnabled || abilityActionBarEnabled || abilityReadySoundEnabled)) {
            startAbilityStatusTask();
        }
    }

    @Override
    protected void onInteract(Player player) {
        // Not used.
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!abilityEnabled) return;

        Player player = event.getPlayer();

        if (getHotbarManager().inDisabledWorld(player.getLocation())) return;
        if (!fightModeManager.isInFightMode(player.getUniqueId())) return;
        if (!fightModeManager.isValidItem(player.getInventory().getItemInMainHand())) return;

        if (abilityCooldown > 0L && getAbilityCooldownRemaining(player.getUniqueId()) > 0L) {
            syncAbilityStatus(player);
            return;
        }

        if (abilityCooldown > 0L) {
            getPlugin().getCooldownManager().setCooldown(player.getUniqueId(), ABILITY_COOLDOWN_KEY, abilityCooldown);
            syncAbilityStatus(player);
        }

        castAbility(player);
    }

    @Override
    public void removeItem(Player player) {
        super.removeItem(player);
        fightModeManager.disableFightMode(player);
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        if (fightModeManager.isInFightMode(playerUuid)) {
            if (fightModeManager.isValidItem(newItem)) {
                fightModeManager.cancelHoldTask(playerUuid);
            } else if (!fightModeManager.hasHoldTask(playerUuid)) {
                fightModeManager.startDeactivationTimer(player);
            }

            return;
        }

        if (fightModeManager.isValidItem(newItem)) {
            fightModeManager.cancelHoldTask(playerUuid);
            fightModeManager.startActivationTimer(player);
        } else {
            fightModeManager.cancelHoldTask(playerUuid);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAbilityStatus(event.getPlayer());
        fightModeManager.disableFightMode(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearAbilityStatus(event.getPlayer());
        fightModeManager.disableFightMode(event.getPlayer());
    }

    @EventHandler()
    public void onRespawnEvent(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (!fightModeManager.isInFightMode(player.getUniqueId())) return;

        Bukkit.getScheduler().runTask(getPlugin(), () -> {
            if (!player.isOnline() || getHotbarManager().inDisabledWorld(player.getLocation())) return;

            player.getInventory().setHeldItemSlot(getSlot());
            fightModeManager.restoreFightMode(player);
        });
    }

    private void castAbility(Player player) {
        Vector lookDirection = player.getLocation().getDirection().clone();
        final Vector castDirection;

        if (lookDirection.lengthSquared() == 0.0D) {
            castDirection = new Vector(1.0D, 0.0D, 0.0D);
        } else {
            castDirection = lookDirection.normalize();
        }

        Location origin = player.getLocation().clone()
                .add(0.0D, abilityHeightOffset, 0.0D)
                .add(castDirection.clone().multiply(0.9D));

        if (abilitySoundEnabled && abilitySound != null) {
            player.getWorld().playSound(player.getLocation(), abilitySound, abilitySoundVolume, abilitySoundPitch);
        }

        Set<UUID> hitTargets = new HashSet<>();
        int[] tick = {0};
        int[] taskId = {0};

        taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(getPlugin(), () -> {
            if (!player.isOnline() || getHotbarManager().inDisabledWorld(player.getLocation())
                    || !fightModeManager.isInFightMode(player.getUniqueId())) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                return;
            }

            double progress = abilityTravelDurationTicks <= 1
                    ? 1.0D
                    : (double) tick[0] / (abilityTravelDurationTicks - 1);
            Location center = origin.clone().add(castDirection.clone().multiply(abilityTravelDistance * progress));
            double radius = interpolateRadius(progress);

            spawnAbilityVisuals(center, castDirection, radius, tick[0]);
            damageTargets(player, center, radius, hitTargets);

            tick[0]++;

            if (tick[0] >= abilityTravelDurationTicks) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
            }
        }, 0L, 1L);
    }

    private double interpolateRadius(double progress) {
        return abilityStartRadius - ((abilityStartRadius - abilityEndRadius) * progress);
    }

    private void spawnAbilityVisuals(Location center, Vector direction, double radius, int tick) {
        double rotation = tick * 0.28D;

        spawnProjectileSegment(center, direction, radius, rotation, 1.0D);
        spawnAbilityTrail(center, direction, radius, tick);
    }

    private void spawnAbilityTrail(Location center, Vector direction, double radius, int tick) {
        if (abilityTrailSteps <= 0) return;

        Vector backward = direction.clone().multiply(-abilityTrailSpacing);

        for (int step = 1; step <= abilityTrailSteps; step++) {
            double strength = 1.0D - (step / (double) (abilityTrailSteps + 1));
            Location trailCenter = center.clone().add(backward.clone().multiply(step));
            double trailRadius = Math.max(abilityEndRadius, radius * Math.pow(abilityTrailRadiusMultiplier, step));
            double rotation = (tick * 0.24D) + (step * 0.33D);

            spawnProjectileSegment(trailCenter, direction, trailRadius, rotation, 0.35D + (strength * 0.4D));
        }
    }

    private void spawnProjectileSegment(Location center, Vector direction, double radius, double rotation, double intensity) {
        spawnParticleCircle(center, direction, radius, rotation, abilityOuterParticle, intensity, 1.0D);
        spawnParticleCircle(center, direction, radius, -rotation * 1.25D, abilityInnerParticle, intensity, 1.0D);
        spawnParticleCloud(center, abilityInnerParticle, 0.2D + (intensity * 0.25D), 0.05D, 0.05D);
    }

    private void spawnParticleCircle(Location center, Vector direction, double radius, double rotation, ParticleProfile profile,
                                     double intensity, double pointMultiplier) {
        if (profile.particle() == null) return;

        Vector forward = direction.clone().normalize();
        Vector reference = Math.abs(forward.getY()) > 0.9D ? new Vector(1.0D, 0.0D, 0.0D) : new Vector(0.0D, 1.0D, 0.0D);
        Vector axisA = forward.clone().crossProduct(reference).normalize();

        if (axisA.lengthSquared() == 0.0D) {
            axisA = new Vector(1.0D, 0.0D, 0.0D);
        }

        Vector axisB = forward.clone().crossProduct(axisA).normalize();
        int points = Math.max(6, (int) Math.round(profile.points() * pointMultiplier));
        int count = Math.max(1, (int) Math.round(profile.count() * intensity));
        double scaledRadius = Math.max(0.1D, radius * profile.radiusMultiplier());

        for (int i = 0; i < points; i++) {
            double angle = rotation + ((2.0D * Math.PI * i) / points);
            Vector offset = axisA.clone().multiply(Math.cos(angle) * scaledRadius)
                    .add(axisB.clone().multiply(Math.sin(angle) * scaledRadius));

            for (int layer = 0; layer < profile.verticalLayers(); layer++) {
                Location particleLocation = center.clone()
                        .add(offset)
                        .add(forward.clone().multiply(layer * profile.verticalSpacing()));

                spawnConfiguredParticle(particleLocation, profile, count, profile.offsetX(), profile.offsetY(),
                        profile.offsetZ(), profile.extra());
            }
        }
    }

    private void spawnParticleCloud(Location center, ParticleProfile profile, double intensity, double horizontalSpread,
                                    double verticalSpread) {
        if (profile.particle() == null) return;

        int count = Math.max(1, (int) Math.round(profile.count() * 8 * intensity));

        spawnConfiguredParticle(center, profile, count, horizontalSpread, verticalSpread, horizontalSpread,
                profile.extra());
    }

    private ParticleProfile loadParticleProfile(ConfigurationSection section, ParticleProfile defaults, String path) {
        if (section == null) {
            return defaults;
        }

        return new ParticleProfile(
                parseParticle(section.getString("type", defaults.particle().name()), path, defaults.particle()),
                Math.max(1, section.getInt("count", defaults.count())),
                section.getDouble("offset_x", defaults.offsetX()),
                section.getDouble("offset_y", defaults.offsetY()),
                section.getDouble("offset_z", defaults.offsetZ()),
                section.getDouble("extra", defaults.extra()),
                Math.max(6, section.getInt("points", defaults.points())),
                Math.max(1, section.getInt("vertical_layers", defaults.verticalLayers())),
                Math.max(0.0D, section.getDouble("vertical_spacing", defaults.verticalSpacing())),
                Math.max(0.1D, section.getDouble("radius_multiplier", defaults.radiusMultiplier()))
        );
    }

    private void spawnConfiguredParticle(Location location, ParticleProfile profile, int count, double offsetX,
                                         double offsetY, double offsetZ, double extra) {
        Particle particle = profile.particle();

        if (particle == null || location.getWorld() == null) return;

        Class<?> dataType = particle.getDataType();

        try {
            if (Void.class.equals(dataType)) {
                location.getWorld().spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count,
                        offsetX, offsetY, offsetZ, extra);
                return;
            }

            if (Float.class.equals(dataType)) {
                float scale = (float) (extra > 0.0D ? extra : 1.0D);
                location.getWorld().spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count,
                        offsetX, offsetY, offsetZ, 0.0D, Float.valueOf(scale));
                return;
            }

            if (Integer.class.equals(dataType)) {
                int value = Math.max(0, (int) Math.round(extra));
                location.getWorld().spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count,
                        offsetX, offsetY, offsetZ, 0.0D, Integer.valueOf(value));
                return;
            }
        } catch (IllegalArgumentException exception) {
            if (warnedUnsupportedParticles.add(particle)) {
                getPlugin().getLogger().warning("[Akropolis] Failed to spawn particle " + particle.name()
                        + " for fight_mode ability: " + exception.getMessage());
            }
            return;
        }

        if (warnedUnsupportedParticles.add(particle)) {
            getPlugin().getLogger().warning("[Akropolis] Unsupported fight_mode ability particle type "
                    + particle.name() + " requiring data class " + dataType.getSimpleName()
                    + ". Choose a particle without custom data or extend the renderer.");
        }
    }

    private void damageTargets(Player caster, Location center, double radius, Set<UUID> hitTargets) {
        if (abilityDamage <= 0.0D) return;

        double maxDistance = radius + abilityHitboxRadius;

        for (Entity entity : center.getWorld().getNearbyEntities(center, maxDistance, abilityCollisionHeight, maxDistance)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(caster) || target.isDead()) continue;
            if (hitTargets.contains(target.getUniqueId())) continue;
            if (!fightModeManager.isInFightMode(target.getUniqueId())) continue;

            Location targetLocation = target.getLocation().clone().add(0.0D, target.getHeight() * 0.5D, 0.0D);

            if (Math.abs(targetLocation.getY() - center.getY()) > abilityCollisionHeight) continue;
            if (targetLocation.distanceSquared(center) > maxDistance * maxDistance) continue;

            hitTargets.add(target.getUniqueId());
            target.damage(abilityDamage, caster);
        }
    }

    private Particle parseParticle(String rawParticle, String path, Particle fallback) {
        try {
            return Particle.valueOf(rawParticle.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            getPlugin().getLogger().warning("[Akropolis] Invalid " + path + ".type: "
                    + rawParticle.toUpperCase(Locale.ROOT) + ". Falling back to " + fallback.name() + ".");
            return fallback;
        }
    }

    private Sound parseSound(String rawSound) {
        try {
            return Sound.valueOf(rawSound.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            getPlugin().getLogger().warning("[Akropolis] Invalid fight_mode.ability.sound.value: "
                    + rawSound.toUpperCase(Locale.ROOT) + ". Sound will be disabled.");
            return null;
        }
    }

    private void startAbilityStatusTask() {
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (shouldTrackAbilityStatus(onlinePlayer)) {
                    syncAbilityStatus(onlinePlayer);
                } else {
                    clearAbilityStatus(onlinePlayer);
                }
            }
        }, 1L, abilityCooldownBarUpdateTicks);
    }

    private boolean shouldTrackAbilityStatus(Player player) {
        return abilityEnabled
                && !getHotbarManager().inDisabledWorld(player.getLocation())
                && fightModeManager.isInFightMode(player.getUniqueId());
    }

    private void syncAbilityStatus(Player player) {
        UUID playerUuid = player.getUniqueId();
        long remaining = abilityCooldown <= 0L ? 0L : Math.max(0L, getAbilityCooldownRemaining(playerUuid));

        if (abilityCooldownBarEnabled) {
            syncAbilityCooldownBar(player, remaining);
        }

        if (abilityActionBarEnabled) {
            sendAbilityActionBar(player, remaining);
        }

        if (remaining > 0L) {
            abilityPlayersOnCooldown.add(playerUuid);
        } else if (abilityPlayersOnCooldown.remove(playerUuid)) {
            playAbilityReadySound(player);
        }
    }

    private void syncAbilityCooldownBar(Player player, long remaining) {
        UUID playerUuid = player.getUniqueId();

        abilityExperienceSnapshots.computeIfAbsent(playerUuid,
                ignored -> new ExperienceSnapshot(player.getExp(), player.getLevel(), player.getTotalExperience()));

        if (abilityCooldown <= 0L) {
            player.setExp(1.0F);

            if (abilityCooldownBarShowSeconds) {
                player.setLevel(0);
            }

            return;
        }

        long totalCooldownMillis = abilityCooldown * 1000L;
        float progress = 1.0F - Math.min(remaining, totalCooldownMillis) / (float) totalCooldownMillis;

        player.setExp(Math.max(0.0F, Math.min(1.0F, progress)));

        if (abilityCooldownBarShowSeconds) {
            player.setLevel((int) Math.ceil(remaining / 1000.0D));
        }
    }

    private void sendAbilityActionBar(Player player, long remaining) {
        long remainingSeconds = remaining <= 0L ? 0L : (long) Math.ceil(remaining / 1000.0D);
        String status = remainingSeconds <= 0L
                ? abilityActionBarReadyText
                : abilityActionBarCooldownText.replace("<seconds>", String.valueOf(remainingSeconds));
        String actionBar = abilityActionBarText.replace("<status>", status).replace("<seconds>",
                String.valueOf(remainingSeconds));

        player.sendActionBar(PlaceholderUtil.setPlaceholders(actionBar, player));
    }

    private void playAbilityReadySound(Player player) {
        if (!abilityReadySoundEnabled || abilityReadySound == null) return;

        player.playSound(player.getLocation(), abilityReadySound, abilityReadySoundVolume, abilityReadySoundPitch);
    }

    private void clearAbilityStatus(Player player) {
        abilityPlayersOnCooldown.remove(player.getUniqueId());
        player.sendActionBar(Component.empty());
        clearAbilityCooldownBar(player);
    }

    private void clearAbilityCooldownBar(Player player) {
        ExperienceSnapshot snapshot = abilityExperienceSnapshots.remove(player.getUniqueId());

        if (snapshot == null) return;

        player.setTotalExperience(snapshot.totalExperience());
        player.setLevel(snapshot.level());
        player.setExp(snapshot.exp());
    }

    private long getAbilityCooldownRemaining(UUID playerUuid) {
        return getPlugin().getCooldownManager().getCooldown(playerUuid, ABILITY_COOLDOWN_KEY);
    }

    private record ExperienceSnapshot(float exp, int level, int totalExperience) {
    }

    private record ParticleProfile(Particle particle, int count, double offsetX, double offsetY, double offsetZ,
                                   double extra, int points, int verticalLayers, double verticalSpacing,
                                   double radiusMultiplier) {
    }
}
