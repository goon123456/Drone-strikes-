package com.steelrain.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Sound helpers. Wherever Superb Warfare already has a recording (explosions, gunfire, launches, reloads,
 * warnings) Steel Rain plays that by id rather than shipping a copy.
 */
public final class Sfx {
    private static final Map<String, SoundEvent> CACHE = new HashMap<>();

    /** A Superb Warfare sound event by name, or a vanilla stand-in if that version of SBW does not have it. */
    public static SoundEvent sbw(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            SoundEvent e = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("superbwarfare", n));
            return e != null ? e : SoundEvents.GENERIC_EXPLODE.value();
        });
    }

    public static void play(ServerLevel level, Vec3 pos, String sbwName, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sbw(sbwName), SoundSource.NEUTRAL, volume, pitch);
    }

    /**
     * Plays one of three recordings to each player depending on how far away they are, the way a real blast
     * sounds sharp nearby and a dull thump from a distance.
     */
    public static void tiered(ServerLevel level, Vec3 pos, String close, String far, String veryFar,
                              double closeRange, double farRange, double maxRange) {
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(pos));
            if (d > maxRange) continue;
            String name = d <= closeRange ? close : d <= farRange ? far : veryFar;
            // Volume above 1 only extends the audible radius (16 blocks per unit); make sure it reaches this player.
            float volume = (float) Math.max(2.0, d / 16.0 + 2.0);
            p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sbw(name)),
                    SoundSource.NEUTRAL, pos.x, pos.y, pos.z, volume, 0.9f + level.random.nextFloat() * 0.2f, level.random.nextLong()));
        }
    }

    /** @param size 0 small (grenade, cannon shell), 1 ordinary warhead, 2 big missile */
    public static void explosion(ServerLevel level, Vec3 pos, int size) {
        if (size <= 0) {
            tiered(level, pos, "mini_explosion", "explosion_far", "explosion_very_far", 48, 160, 320);
        } else if (size == 1) {
            tiered(level, pos, "explosion_close", "explosion_far", "explosion_very_far", 64, 256, 640);
        } else {
            tiered(level, pos, "huge_explosion_close", "huge_explosion_far", "huge_explosion_very_far", 96, 384, 1024);
        }
    }

    /** A weapon firing: a near, a far and a very far recording. */
    public static void gunfire(ServerLevel level, Vec3 pos, String near, String far, String veryFar) {
        tiered(level, pos, near, far, veryFar, 48, 200, 480);
    }

    private Sfx() {}
}
