package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.entity.AircraftEntity;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.entity.FpvDroneEntity;
import com.steelrain.entity.InterceptorEntity;
import com.steelrain.entity.MissileEntity;
import com.steelrain.util.Sfx;
import com.steelrain.weapon.AircraftType;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Engine or motor noise that follows a flying thing for as long as it exists. */
public class LoopSound extends AbstractTickableSoundInstance {
    private final FlyingWeaponEntity entity;
    private final float baseVolume;

    private LoopSound(FlyingWeaponEntity entity, SoundEvent sound, float volume) {
        super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.baseVolume = volume;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    /** Picks the right loop for an entity and starts it. Does nothing for things that are silent in flight. */
    static void start(FlyingWeaponEntity e) {
        SoundEvent sound = null;
        float volume = 1f;
        if (e instanceof FpvDroneEntity d) {
            sound = d.mavic() ? ModRegistry.MAVIC_MOTOR.get() : ModRegistry.FPV_MOTOR.get();
        } else if (e instanceof AircraftEntity a) {
            sound = a.type() == AircraftType.GLOBAL_HAWK ? ModRegistry.JET.get() : ModRegistry.TURBOPROP.get();
            volume = a.type() == AircraftType.ORLAN ? 0.5f : 1f;
        } else if (e instanceof InterceptorEntity) {
            sound = Sfx.sbw("rocket_engine");
            volume = 3f;
        } else if (e instanceof MissileEntity m) {
            if (m.profile() == MissileProfile.SHAHED) sound = ModRegistry.SHAHED_ENGINE.get();
            else if (m.profile().smokeTrail()) {
                sound = Sfx.sbw("rocket_engine");
                volume = 4f;
            }
        }
        if (sound != null) Minecraft.getInstance().getSoundManager().play(new LoopSound(e, sound, volume));
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
            // Motors only run while it is being flown, and scream harder the faster it goes.
            volume = d.active() ? baseVolume : 0f;
            pitch = 0.85f + (float) Math.min(0.6, speed * 0.6);
        } else if (entity instanceof AircraftEntity) {
            pitch = 0.8f + (float) Math.min(0.4, speed * 0.2);
        }
    }

    @Override
    public boolean canStartSilent() { return true; }
}
