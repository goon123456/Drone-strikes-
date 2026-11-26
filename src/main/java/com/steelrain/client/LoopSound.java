package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.entity.AircraftEntity;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.entity.FpvDroneEntity;
import com.steelrain.entity.InterceptorEntity;
import com.steelrain.entity.MissileEntity;
import com.steelrain.util.Sfx;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Engine or motor noise that follows a flying thing for as long as it exists. */
public class LoopSound extends AbstractTickableSoundInstance {
    private final FlyingWeaponEntity entity;
    private final float baseVolume, basePitch;
    /** For rotor drones: which speed band this layer belongs to (0 slow, 1 medium, 2 fast), else -1. */
    private final int band;
    private boolean wasActive;

    private LoopSound(FlyingWeaponEntity entity, SoundEvent sound, float volume, float pitch, int band) {
        super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.baseVolume = volume;
        this.basePitch = pitch;
        this.band = band;
        this.looping = true;
        this.delay = 0;
        this.volume = band >= 0 ? 0f : volume;
        this.pitch = pitch;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    private static void play(FlyingWeaponEntity e, SoundEvent sound, float volume, float pitch, int band) {
        Minecraft.getInstance().getSoundManager().play(new LoopSound(e, sound, volume, pitch, band));
    }

    /** Picks the right loop for an entity and starts it. Does nothing for things that are silent in flight. */
    static void start(FlyingWeaponEntity e) {
        if (e instanceof FpvDroneEntity d) {
            // Three recordings of the rotors at rising speed, blended by how fast the drone is going.
            boolean mavic = d.mavic();
            play(e, (mavic ? ModRegistry.MAVIC_LOW : ModRegistry.FPV_LOW).get(), 1f, 1f, 0);
            play(e, (mavic ? ModRegistry.MAVIC_MEDIUM : ModRegistry.FPV_MEDIUM).get(), 1f, 1f, 1);
            play(e, (mavic ? ModRegistry.MAVIC_HIGH : ModRegistry.FPV_HIGH).get(), 1f, 1f, 2);
        } else if (e instanceof AircraftEntity a) {
            switch (a.type()) {
                case REAPER -> play(e, ModRegistry.REAPER_ENGINE.get(), 1f, 1f, -1);
                case TB2 -> play(e, ModRegistry.TB2_ENGINE.get(), 0.9f, 1f, -1);
                case ORLAN -> play(e, ModRegistry.ORLAN_ENGINE.get(), 0.6f, 1.25f, -1);
                case GLOBAL_HAWK -> play(e, ModRegistry.GLOBAL_HAWK_JET.get(), 1f, 1f, -1);
            }
        } else if (e instanceof InterceptorEntity) {
            play(e, Sfx.sbw("rocket_engine"), 3f, 1f, -1);
        } else if (e instanceof MissileEntity m) {
            if (m.profile() == MissileProfile.SHAHED) play(e, ModRegistry.SHAHED_ENGINE.get(), 1f, 0.9f, -1);
            else if (m.profile().smokeTrail()) play(e, Sfx.sbw("rocket_engine"), 4f, 1f, -1);
        }
    }

    @Override
    public void tick() {
        if (entity.isRemoved()) {
            stop();
            return;
        }
        x = entity.getX();
        y = entity.getY();
        z = entity.getZ();
        double speed = Math.sqrt(Mth.square(entity.getX() - entity.xo) + Mth.square(entity.getY() - entity.yo) + Mth.square(entity.getZ() - entity.zo));
        if (entity instanceof FpvDroneEntity d) {
            // Motors only run while it is being flown. Each layer is loudest at its own speed and fades
            // into its neighbours: hovering, cruising, flat out.
            float throttle = (float) Mth.clamp(speed / (d.mavic() ? 0.5 : 1.0), 0, 1) * 2f;
            float target = d.active() ? Math.max(0f, 1f - Math.abs(throttle - band)) * baseVolume : 0f;
            volume += (target - volume) * 0.25f;
            pitch = basePitch * (0.97f + 0.06f * (throttle - band));
            if (band == 0 && d.active() && !wasActive) {
                Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                        (d.mavic() ? ModRegistry.MAVIC_ACCELERATE : ModRegistry.FPV_ACCELERATE).get(), SoundSource.NEUTRAL,
                        0.8f, 1f, SoundInstance.createUnseededRandom(), x, y, z));
            }
            wasActive = d.active();
        } else if (entity instanceof AircraftEntity) {
            pitch = basePitch * (0.85f + (float) Math.min(0.3, speed * 0.15));
        }
    }

    @Override
    public boolean canStartSilent() { return true; }

    /** Hiss of a jammed video link heard by the operator: light, medium and heavy layers faded by the jam level. */
    static final class Static extends AbstractTickableSoundInstance {
        private final int layer;
        private int quiet;

        Static(int layer) {
            super((layer == 0 ? ModRegistry.STATIC_LIGHT : layer == 1 ? ModRegistry.STATIC_MEDIUM : ModRegistry.STATIC_HEAVY).get(),
                    SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.layer = layer;
            this.looping = true;
            this.delay = 0;
            this.volume = 0f;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            // jam 0.1 .. 1 spread across the three layers
            float pos = Mth.clamp((ClientHooks.jam - 0.1f) / 0.9f, 0f, 1f) * 2f;
            float target = ClientHooks.jam > 0.08f ? Math.max(0f, 1f - Math.abs(pos - layer)) : 0f;
            if (layer == 0 && ClientHooks.jam > 0.08f) target = Math.max(target, 0.5f);
            volume += (target - volume) * 0.2f;
            quiet = ClientHooks.jam > 0.08f ? 0 : quiet + 1;
            if (quiet > 100) {
                ClientHooks.staticStopped();
                stop();
            }
        }

        @Override
        public boolean canStartSilent() { return true; }
    }
}
