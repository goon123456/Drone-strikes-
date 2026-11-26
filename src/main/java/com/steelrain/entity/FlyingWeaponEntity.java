package com.steelrain.entity;

import com.steelrain.Config;
import com.steelrain.util.Blast;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.UUID;

/** Shared plumbing of everything that flies: owner, hit points, chunk loading, smooth client movement. */
public abstract class FlyingWeaponEntity extends Entity {
    /** Expires on its own, so a crashed server never leaves chunks stuck loaded. */
    public static final TicketType<ChunkPos> FLIGHT_TICKET =
            TicketType.create("steelrain_flight", Comparator.comparingLong(ChunkPos::toLong), 300);

    @Nullable protected UUID ownerId;
    @Nullable protected String ownerName;
    protected float health = 5f;
    /** Game time until which an air defence system has "claimed" this target. */
    public long engagedUntil;
    protected double lastGround = Double.NaN;

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot, lerpXRot;

    protected FlyingWeaponEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setOwner(@Nullable UUID id, @Nullable String name) {
        this.ownerId = id;
        this.ownerName = name;
    }

    @Nullable public UUID ownerId() { return ownerId; }
    @Nullable public String ownerName() { return ownerName; }

    @Nullable
    public Player ownerPlayer() {
        return ownerId == null ? null : level().getPlayerByUUID(ownerId);
    }

    /** Whether air defence should shoot at this. */
    public boolean interceptable() { return true; }

    /** Worth a long-range interceptor (aircraft, cruise and ballistic missiles). */
    public boolean highValue() { return false; }

    /** Rough radius of the model, so it is not culled while its small hit box is off screen. */
    protected double modelRadius() { return 3; }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(modelRadius()); }

    /** Progress of this entity's recon sweep over the chunks below it. */
    public int scanIndex;

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> JAM = SynchedEntityData.defineId(
            FlyingWeaponEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** How badly the radio link is jammed right now: 0 clean, 1 lost. Server side. */
    protected float jam;
    private long directJamUntil;

    /** Does this depend on a radio or satellite signal at the moment? Wired and inertial things do not. */
    protected boolean radioLinked() { return false; }

    /** An anti-drone gun is being held on this. */
    public void directJam(long until) { directJamUntil = Math.max(directJamUntil, until); }

    /** Jam level as a percentage, usable on the client. */
    public int jamPercent() { return entityData.get(JAM); }

    private void updateJam(ServerLevel level) {
        float j = 0;
        if (radioLinked()) {
            j = directJamUntil > level.getGameTime() ? 1f
                    : com.steelrain.util.Jamming.level(level, position(), ownerId, ownerName);
        }
        jam = j;
        int pct = Math.round(j * 100);
        if (pct != entityData.get(JAM)) entityData.set(JAM, pct);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(JAM, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, lerpXRot);
                lerpSteps--;
            }
            clientTick();
        } else {
            if ((tickCount + getId()) % 3 == 0) updateJam((ServerLevel) level());
            serverTick((ServerLevel) level());
        }
    }

    protected abstract void serverTick(ServerLevel level);

    protected void clientTick() {}

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x; lerpY = y; lerpZ = z; lerpYRot = yRot; lerpXRot = xRot;
        lerpSteps = Math.max(1, steps);
    }

    // ---- chunk loading ----

    /**
     * Requests the chunks along the flight path and reports whether the next position can be entered yet.
     * A munition that would fly into a chunk that is still loading waits for it instead of freezing there.
     */
    protected boolean keepChunksLoaded(ServerLevel level, Vec3 next, Vec3 velocity) {
        if (!Config.CHUNK_LOADING.get()) return true;
        ticket(level, getX(), getZ());
        ticket(level, next.x, next.z);
        for (int t : LOOK_AHEAD) {
            ticket(level, next.x + velocity.x * t, next.z + velocity.z * t);
        }
        return level.isPositionEntityTicking(net.minecraft.core.BlockPos.containing(next));
    }

    private static final int[] LOOK_AHEAD = {8, 20, 40};

    private static void ticket(ServerLevel level, double x, double z) {
        ChunkPos pos = new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        level.getChunkSource().addRegionTicket(FLIGHT_TICKET, pos, 2, pos);
    }

    /** Loads (without ticking) everything within {@code chunks} of this entity, for wide-area sensors. */
    protected void loadAround(ServerLevel level, int chunks) {
        if (!Config.CHUNK_LOADING.get()) return;
        ChunkPos pos = new ChunkPos(Mth.floor(getX()) >> 4, Mth.floor(getZ()) >> 4);
        level.getChunkSource().addRegionTicket(FLIGHT_TICKET, pos, Math.max(2, chunks), pos);
    }

    public static void preload(ServerLevel level, double x, double z) {
        if (!Config.CHUNK_LOADING.get()) return;
        ChunkPos pos = new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        level.getChunkSource().addRegionTicket(FLIGHT_TICKET, pos, 2, pos);
    }

    // ---- flight helpers ----

    /** Terrain height at a column, or the last known value while that chunk is not loaded. */
    protected double groundAt(double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (level().hasChunk(bx >> 4, bz >> 4)) {
            lastGround = level().getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        } else if (Double.isNaN(lastGround)) {
            lastGround = level().getSeaLevel();
        }
        return lastGround;
    }

    /** Direction that heads for (tx, tz) while holding {@code altitude} above the terrain ahead. */
    protected Vec3 terrainFollowing(Vec3 dir, double tx, double tz, double altitude, double speed) {
        double dx = tx - getX(), dz = tz - getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h < 1e-4) return dir;
        double look = Math.max(16, speed * 25);
        // Look well ahead too, so a mountain range is climbed over in good time rather than flown into.
        double g = groundAt(getX(), getZ());
        for (double ahead : new double[]{look, look * 2.5, look * 5}) {
            double a = Math.min(ahead, h);
            g = Math.max(g, groundAt(getX() + dx / h * a, getZ() + dz / h * a));
        }
        double climb = Mth.clamp((g + altitude - getY()) / look, -0.5, 0.9);
        return new Vec3(dx / h, climb, dz / h).normalize();
    }

    protected static Vec3 steer(Vec3 dir, Vec3 desired, double k) {
        Vec3 v = dir.scale(1 - k).add(desired.scale(k));
        return v.lengthSqr() < 1e-6 ? desired : v.normalize();
    }

    protected void faceAlong(Vec3 dir) {
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG);
        setRot(yaw, pitch);
    }

    /** First block between two points, or null. */
    @Nullable
    protected Vec3 blockHit(Vec3 from, Vec3 to) {
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    // ---- damage ----

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean ignoreExplosion(Explosion explosion) { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) return false;
        health -= amount;
        if (health <= 0) shotDown();
        return true;
    }

    /** Destroyed in flight: by default it just bursts without setting off the warhead. */
    public void shotDown() {
        if (level() instanceof ServerLevel sl) Blast.airburst(sl, position());
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 400.0 * 400.0; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        if (tag.contains("OwnerName")) ownerName = tag.getString("OwnerName");
        health = tag.getFloat("Health");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (ownerName != null) tag.putString("OwnerName", ownerName);
        tag.putFloat("Health", health);
    }
}
