package com.steelrain.compat;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.projectile.DestroyableProjectile;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.steelrain.Config;
import com.steelrain.util.Teams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Superb Warfare side of {@link Sbw}. Never load this class unless Superb Warfare is installed. */
final class SbwHooks {
    static boolean isAirThreat(Level level, Entity e, boolean highValueOnly, @Nullable UUID owner, @Nullable String ownerName) {
        if (e instanceof VehicleEntity v) {
            VehicleType type = v.getVehicleType();
            if (v.isWreck() || (type != VehicleType.AIRPLANE && type != VehicleType.HELICOPTER && type != VehicleType.AIRSHIP
                    && !(!highValueOnly && type == VehicleType.DRONE))) return false;
            if (v.onGround()) return false;
            if (!(v.getFirstPassenger() instanceof Player p)) return false;   // leave empty aircraft alone
            return !Teams.friendly(level, owner, ownerName, p.getUUID(), p.getGameProfile().getName());
        }
        if (e instanceof DestroyableProjectile && e instanceof Projectile p && !highValueOnly) {
            return p.getOwner() instanceof Player shooter
                    && !Teams.friendly(level, owner, ownerName, shooter.getUUID(), shooter.getGameProfile().getName());
        }
        return false;
    }

    static boolean isVehicle(Entity e) { return e instanceof VehicleEntity; }

    static List<Entity> vehicles(Level level, AABB box) {
        return new ArrayList<>(level.getEntitiesOfClass(VehicleEntity.class, box, v -> v.isAlive() && !v.isWreck()));
    }

    static boolean domePower(Entity e, int fe, boolean draw) {
        if (!(e instanceof VehicleEntity v)) return true;
        if (v.isWreck()) return false;
        if (!Config.REQUIRE_ENERGY.get() || !v.hasEnergyStorage()) return true;
        if (v.getEnergy() < fe) return false;
        if (draw) v.consumeEnergy(fe);
        return true;
    }

    private SbwHooks() {}
}
