package dev.plataclan.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.plataclan.Balance;
import dev.plataclan.PlataClan;
import dev.plataclan.PlataConfig;
import dev.plataclan.PlataLines;
import dev.plataclan.PlataNet;
import dev.plataclan.PlayerPower;
import dev.plataclan.Rank;
import dev.plataclan.hideout.Sleepers;
import dev.plataclan.mother.Mothers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Plata out of disguise. One class serves all three ranks; the rank comes from the entity type.
 * The fight is a small state machine of {@link Move}s whose hits land on the beats the clips are
 * keyed for. Health and damage are set once, from the player who found them ({@link #configure}).
 */
public class PlataMember extends Monster implements Enemy {
    private static final EntityDataAccessor<Integer> DATA_MOVE = SynchedEntityData.defineId(PlataMember.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(PlataMember.class, EntityDataSerializers.INT);
    public static final String SUMMONED_TAG = "plataclan.summoned";
    private static final int MAX_SUMMONED = 6, DEATH_TICKS = 28;

    public final Rank rank;
    private int level = 10;
    private final float[] damage = new float[Balance.Hit.values().length];
    private String disguise = "";
    private boolean configured, molted, fleeing, gloated;

    // the current move (server) and its synced serial
    private Move move = Move.STANCE;
    private int moveTicks, serial;
    // client: when the synced move began
    private int clientMoveStart;

    private int decideIn = 10, burrowCooldown = 60, lungeCooldown, throwCooldown = 20, lashCooldown, slamCooldown = 60,
            callCooldown = 200, kickCooldown, sayCooldown, strafeTicks, lostTicks, moltAmbush;
    private int flurryIndex, flurryLength;
    private float strafeSide = 1;
    private boolean dodgedLast, burrowAttack, emergeStrike;
    private @Nullable Vec3 burrowGoal;
    private final int[] hitTicks = {-100, -100, -100};
    private int hitIndex;
    private @Nullable UUID lastFoe;
    private final List<UUID> summoned = new ArrayList<>();
    private @Nullable ServerBossEvent bar;
    /** A hideout sleeper's own husk, which it crawls back into when there's nobody left to fight. */
    private @Nullable BlockPos home;
    /** Letting a Mother's killer be: standing down, ready to talk, until the time runs out or someone strikes them. */
    private boolean truce, parting;
    private int truceTicks, asked;
    private @Nullable UUID truceWith;

    public PlataMember(EntityType<? extends PlataMember> type, Level level) {
        super(type, level);
        this.rank = PlataEntities.rankOf(type);
        this.xpReward = Balance.experience(this.level);
        java.util.Arrays.fill(this.damage, 3);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 8.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOVE, 0);
        builder.define(DATA_LEVEL, 10);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, PlataMember.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> !this.truce));
    }

    // -- setup ------------------------------------------------------------------------------------

    /** Health, speed, armor and every kind of hit, sized up from the player who found them. */
    public void configure(int level, Balance.Foe foe, int players, String disguise) {
        PlataConfig cfg = PlataConfig.get();
        this.level = Balance.clampLevel(level);
        this.disguise = disguise == null ? "" : disguise;
        this.configured = true;
        float hp = Balance.health(this.rank, this.level, foe.meleeHit(), players, cfg.healthScale);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        this.setHealth(hp);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Balance.speed(this.rank));
        this.getAttribute(Attributes.ARMOR).setBaseValue(Balance.armor(this.rank, this.level));
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(Balance.knockbackResistance(this.rank));
        for (Balance.Hit h : Balance.Hit.values()) this.damage[h.ordinal()] = Balance.damage(h, foe, this.level, cfg.damageScale);
        this.entityData.set(DATA_LEVEL, this.level);
        this.xpReward = Balance.experience(this.level);
        this.flurryLength = Balance.flurryLength(this.level);
    }

    /** From a spawn egg or a command: size up the nearest player, if there is one. */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(accessor, difficulty, reason, data);
        if (!this.configured) {
            Player near = accessor.getLevel().getNearestPlayer(this, 32);
            if (near instanceof ServerPlayer sp && !sp.isSpectator()) {
                this.configure(Balance.memberLevel(PlayerPower.level(sp), this.rank, this.getUUID().getLeastSignificantBits()),
                        PlayerPower.read(sp, this), 1, "");
            } else {
                this.configure(10, new Balance.Foe(10, 20, 8, 0, 0, 6), 1, "");
            }
        }
        return out;
    }

    /** A hideout sleeper: guards the husk at {@code husk}, and wakes on {@code healthScale} of its health. */
    public void guard(BlockPos husk, float healthScale) {
        this.home = husk.immutable();
        this.setPersistenceRequired();
        float hp = Math.max(Balance.MIN_HEALTH, this.getMaxHealth() * healthScale);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        this.setHealth(hp);
    }

    public boolean garrison() { return this.home != null; }
    /** Their Mother has fallen: no husk to go back to. When they're done fighting they leave for good. */
    public void orphan() { this.home = null; }
    public @Nullable BlockPos home() { return this.home; }
    public boolean fleeing() { return this.fleeing; }

    public int plataLevel() { return this.entityData.get(DATA_LEVEL); }
    public String disguise() { return this.disguise; }
    public float damageOf(Balance.Hit hit) { return this.damage[hit.ordinal()]; }

    // -- moves ------------------------------------------------------------------------------------

    public Move move() { return Move.byOrdinal(this.entityData.get(DATA_MOVE) & 0xFF); }

    /** Client: seconds (with partial ticks) since the current move began. */
    public float moveTime(float partialTick) { return (this.tickCount - this.clientMoveStart + partialTick) / 20F; }

    public boolean hidden() { return this.move().hidden(); }

    public void start(Move m) {
        this.move = m;
        this.moveTicks = 0;
        this.serial = (this.serial + 1) & 0xFFFFFF;
        this.entityData.set(DATA_MOVE, m.ordinal() | this.serial << 8);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_MOVE.equals(key)) {
            this.clientMoveStart = this.tickCount;
            this.noPhysics = this.hidden();
        }
    }

    /** The reveal's entrance: springing out of the dust in a backflip. */
    public void unfurl() { this.start(Move.UNFURL); }

    // -- the fight --------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.moveTicks++;
        this.cooldowns();
        this.updateBar(level);
        if (this.truce) { this.standDown(level); return; }
        LivingEntity t = this.foe(level);
        switch (this.move) {
            case STANCE, TAUNT -> {
                if (this.move == Move.TAUNT && this.moveTicks >= Move.TAUNT.ticks) this.start(Move.STANCE);
                this.think(level, t);
            }
            case UNFURL -> { if (this.moveTicks >= Move.UNFURL.ticks) this.idle(6); }
            case READY -> {
                this.faceTo(t, 40);
                if (this.moveTicks >= Move.READY.ticks) { this.flurryIndex = 1; this.start(Move.FLURRY_1); }
            }
            case FLURRY_1, FLURRY_2, FLURRY_3, FLURRY_4 -> this.flurry(level, t);
            case LUNGE -> this.lunge(level, t);
            case BACKFLIP, SIDEROLL, STAGGER, CALL, THROW_FAN, KICK, LASH, SLAM -> this.action(level, t);
            case BURROW_DIVE -> {
                if (this.moveTicks == 4) this.dust(level, 12, .5, .1);
                if (this.moveTicks >= 10) this.goUnder(level);
            }
            case UNDER -> this.travel(level, t);
            case TREMOR -> {
                if (this.moveTicks % 2 == 0) this.dust(level, 10, .8, .15);
                if (this.moveTicks % 4 == 0) this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.3F, .6F);
                if (this.moveTicks >= Move.TREMOR.ticks) this.surface(level, true);
            }
            case EMERGE -> {
                if (this.moveTicks == 4 && this.emergeStrike) {
                    this.area(level, this.position(), 1.9, Balance.Hit.ERUPT, .3, .85);
                    this.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, .8F);
                }
                if (this.moveTicks >= Move.EMERGE.ticks) {
                    if (this.moltAmbush > 0 && t != null) { this.moltAmbush--; this.burrow(t, true); }
                    else this.idle(Balance.reaction(this.level));
                }
            }
            case DEFEAT -> {}
        }
    }

    private void cooldowns() {
        if (this.burrowCooldown > 0) this.burrowCooldown--;
        if (this.lungeCooldown > 0) this.lungeCooldown--;
        if (this.throwCooldown > 0) this.throwCooldown--;
        if (this.lashCooldown > 0) this.lashCooldown--;
        if (this.slamCooldown > 0) this.slamCooldown--;
        if (this.callCooldown > 0) this.callCooldown--;
        if (this.kickCooldown > 0) this.kickCooldown--;
        if (this.sayCooldown > 0) this.sayCooldown--;
    }

    /** The current target if it's still fair game; notices when a player they fought has fallen. */
    private @Nullable LivingEntity foe(ServerLevel level) {
        LivingEntity t = this.getTarget();
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) { this.setTarget(null); t = null; }
        if (t != null && !t.isAlive()) {
            if (t instanceof Player && t.getUUID().equals(this.lastFoe) && !this.gloated) { this.gloated = true; this.say(level, "fight.win", 0); }
            this.setTarget(null);
            t = null;
        }
        if (t instanceof Player) { this.lastFoe = t.getUUID(); this.gloated = false; }
        return t;
    }

    private void idle(int delay) {
        this.start(Move.STANCE);
        this.decideIn = Math.max(this.decideIn, delay);
    }

    /** Between moves: keep the distance this rank likes, then pick the next move. */
    private void think(ServerLevel level, @Nullable LivingEntity t) {
        if (t == null) {
            this.getNavigation().stop();
            if (++this.lostTicks > 200 && !this.fleeing) this.flee(level);
            return;
        }
        this.lostTicks = 0;
        this.faceTo(t, 30);
        double d = this.distanceTo(t);
        if (this.decideIn > 0) {
            this.decideIn--;
            this.position(t, d);
            return;
        }
        this.decideIn = Balance.reaction(this.level) - (this.molted ? 3 : 0);
        switch (this.rank) {
            case SCUTTLER -> this.scuttler(level, t, d);
            case SHARDCASTER -> this.shardcaster(level, t, d);
            case WARDEN -> this.warden(level, t, d);
        }
    }

    private void scuttler(ServerLevel level, LivingEntity t, double d) {
        if (this.burrowCooldown == 0 && (d > 14 || d > 7 && this.random.nextFloat() < .3F)) { this.burrow(t, true); return; }
        if (Balance.callsSilverfish(this.rank, this.level) && this.callCooldown == 0 && d < 12 && this.random.nextFloat() < .2F) { this.call(level); return; }
        // the close ambush: down into the ground at your feet, and up again under you a moment later
        if (this.burrowCooldown == 0 && d <= 7 && this.random.nextFloat() < .15F) { this.burrow(t, true); return; }
        if (d > 4.5 && d < 8 && this.lungeCooldown == 0 && this.random.nextFloat() < .55F) { this.start(Move.LUNGE); this.lungeCooldown = 70; return; }
        if (d <= 3.0) { this.getNavigation().stop(); this.start(Move.READY); this.sound(SoundEvents.PLAYER_ATTACK_NODAMAGE, .8F, 1.4F); return; }
        if (d > 16 && this.random.nextFloat() < .04F) { this.start(Move.TAUNT); this.say(level, "reveal.taunt.timeout", 100); }
    }

    private void shardcaster(ServerLevel level, LivingEntity t, double d) {
        if (d < 2.6 && this.kickCooldown == 0) { this.getNavigation().stop(); this.start(Move.KICK); this.kickCooldown = 40; return; }
        if (d < 5) {
            if (this.burrowCooldown == 0) { this.burrow(t, false); return; }
            if (this.random.nextFloat() < .35F) { this.leap(t, Move.BACKFLIP); return; }
        }
        if (d <= 17 && this.throwCooldown == 0 && this.hasLineOfSight(t)) { this.getNavigation().stop(); this.start(Move.THROW_FAN); this.throwCooldown = 50 - this.level / 5; return; }
        if (Balance.callsSilverfish(Rank.SCUTTLER, this.level) && this.callCooldown == 0 && this.random.nextFloat() < .1F) this.call(level);
    }

    private void warden(ServerLevel level, LivingEntity t, double d) {
        if (d < 3.2 && this.slamCooldown == 0) { this.getNavigation().stop(); this.start(Move.SLAM); this.slamCooldown = 160 - this.level / 2; return; }
        if (d < 4.8 && this.lashCooldown == 0) { this.getNavigation().stop(); this.start(Move.LASH); this.lashCooldown = 50 - this.level / 5; return; }
        if (this.burrowCooldown == 0 && (d > 9 || d > 4.8 && this.random.nextFloat() < .25F)) { this.burrow(t, true); return; }
        if (this.callCooldown == 0 && this.random.nextFloat() < .25F) this.call(level);
    }

    /** Keep the rank's distance: Scuttlers circle close, Shardcasters keep away, Wardens walk you down. */
    private void position(LivingEntity t, double d) {
        if (--this.strafeTicks <= 0) { this.strafeTicks = 20 + this.random.nextInt(30); this.strafeSide = this.random.nextBoolean() ? 1 : -1; }
        switch (this.rank) {
            case SCUTTLER -> {
                if (d > 6) this.getNavigation().moveTo(t, 1.15);
                else { this.getNavigation().stop(); this.getMoveControl().strafe(d > 3.4 ? .55F : -.2F, .55F * this.strafeSide); }
            }
            case SHARDCASTER -> {
                if (d > 15) this.getNavigation().moveTo(t, 1.0);
                else { this.getNavigation().stop(); this.getMoveControl().strafe(d < 9 ? -.6F : d > 13 ? .4F : 0F, .5F * this.strafeSide); }
            }
            case WARDEN -> {
                if (d > 3.5) this.getNavigation().moveTo(t, 1.0);
                else this.getNavigation().stop();
            }
        }
    }

    // -- move scripts (hits land on the clip's beats) ----------------------------------------------

    private void flurry(ServerLevel level, @Nullable LivingEntity t) {
        this.faceTo(t, 45);
        if (this.moveTicks == 2 && t != null && this.distanceTo(t) > 1.8) this.nudge(t, .28);
        if (this.moveTicks == 4) {
            this.sound(SoundEvents.PLAYER_ATTACK_SWEEP, .8F, 1.3F + this.random.nextFloat() * .2F);
            this.strike(level, t, Balance.Hit.CUT, 3.0, 120, .25, .05);
        }
        if (this.moveTicks >= this.move.ticks) {
            if (this.flurryIndex < this.flurryLength && t != null && this.distanceTo(t) < 4) this.start(Move.flurry(++this.flurryIndex));
            else { this.flurryIndex = 0; this.idle(Balance.reaction(this.level) + 8); }
        }
    }

    private void lunge(ServerLevel level, @Nullable LivingEntity t) {
        if (this.moveTicks < 6) this.faceTo(t, 30);
        if (this.moveTicks == 6) this.sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1F, 1.2F);
        if (this.moveTicks >= 6 && this.moveTicks <= 9 && t != null && this.distanceTo(t) > 1.6) this.nudge(t, .95);
        if (this.moveTicks == 10) this.strike(level, t, Balance.Hit.LUNGE, 2.9, 100, .5, .1);
        if (this.moveTicks >= Move.LUNGE.ticks) this.idle(Balance.reaction(this.level) + 6);
    }

    private void action(ServerLevel level, @Nullable LivingEntity t) {
        switch (this.move) {
            case THROW_FAN -> {
                if (this.moveTicks < 8) this.faceTo(t, 30);
                if (this.moveTicks == 8 && t != null) this.throwFan(level, t);
            }
            case KICK -> {
                this.faceTo(t, 30);
                if (this.moveTicks == 5) { this.strike(level, t, Balance.Hit.KICK, 2.7, 100, 1.1, .3); this.sound(SoundEvents.PLAYER_ATTACK_STRONG, 1F, .9F); }
            }
            case LASH -> {
                if (this.moveTicks < 14) this.faceTo(t, 20);
                if (this.moveTicks == 12) this.sound(SoundEvents.BREEZE_WHIRL, .9F, 1.4F);
                if (this.moveTicks == 17) {
                    this.sound(SoundEvents.CHAIN_BREAK, 1.2F, .7F);
                    this.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, .6F);
                    this.arc(level, Balance.Hit.LASH, 4.7, 150, .8, .2);
                }
            }
            case SLAM -> {
                if (this.moveTicks < 12) this.faceTo(t, 20);
                if (this.moveTicks == 14) {
                    this.sound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.3F, .8F);
                    this.dust(level, 50, 1.2, .25);
                    this.area(level, this.position(), 2.4, Balance.Hit.SLAM, .6, .4);
                }
                if (this.moveTicks > 14 && this.moveTicks < 26 && this.moveTicks % 2 == 0) this.ring(level, 4.5);
                if (this.moveTicks == 20) this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.4F, .5F);
                if (this.moveTicks == 26) {
                    this.sound(SoundEvents.DEEPSLATE_BREAK, 1.5F, .6F);
                    this.ring(level, 4.5);
                    this.area(level, this.position(), 4.5, Balance.Hit.ERUPT, .4, .9);
                    this.summon(level, Balance.swarm(this.level), 4.0);
                }
            }
            case CALL -> {
                if (this.moveTicks == 10) {
                    this.summon(level, Balance.swarm(this.level), 3.0);
                    this.say(level, "fight.call", 60);
                }
            }
            case BACKFLIP, SIDEROLL -> { if (this.moveTicks == 1) this.sound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1F, 1.6F); }
            default -> {}
        }
        if (this.moveTicks >= this.move.ticks) this.idle(this.move == Move.STAGGER ? 2 : Balance.reaction(this.level));
    }

    private void throwFan(ServerLevel level, LivingEntity t) {
        int n = Balance.shardsPerFan(this.level);
        Vec3 eye = this.getEyePosition();
        Vec3 aim = t.getEyePosition().subtract(eye).add(0, -.3, 0);
        double flat = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        for (int i = 0; i < n; i++) {
            double yaw = Math.toRadians((i - (n - 1) / 2.0) * 9);
            double x = aim.x * Math.cos(yaw) - aim.z * Math.sin(yaw), z = aim.x * Math.sin(yaw) + aim.z * Math.cos(yaw);
            SilverShard shard = new SilverShard(level, this, this.damageOf(Balance.Hit.SHARD));
            shard.setPos(eye.x, eye.y - .2, eye.z);
            shard.shoot(x, aim.y + flat * .05, z, 1.35F, 1.5F);
            level.addFreshEntity(shard);
        }
        this.sound(SoundEvents.TRIDENT_THROW.value(), .9F, 1.5F);
        this.sound(SoundEvents.SNOWBALL_THROW, .8F, .7F);
    }

    /** A step or dash toward the target (lunges and flurries close the gap). */
    private void nudge(LivingEntity t, double speed) {
        Vec3 to = t.position().subtract(this.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0E-4) return;
        to = to.normalize().scale(speed);
        this.setDeltaMovement(to.x, Math.max(this.getDeltaMovement().y, .05), to.z);
        this.needsSync = true;
    }

    /** A backflip away from (or a roll to the side of) whatever is coming. */
    private void leap(@Nullable Entity from, Move m) {
        Vec3 away = from == null ? Vec3.directionFromRotation(0, this.getYRot() + 180) : this.position().subtract(from.position());
        away = away.multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        this.getNavigation().stop();
        if (m == Move.BACKFLIP) this.setDeltaMovement(away.x * .75, .45, away.z * .75);
        else {
            // the roll is keyed to their own left
            float yaw = this.getYRot() * Mth.DEG_TO_RAD;
            this.setDeltaMovement(Mth.cos(yaw) * .7, .22, Mth.sin(yaw) * .7);
        }
        this.needsSync = true;
        this.flurryIndex = 0;
        this.start(m);
    }

    private void call(ServerLevel level) {
        this.getNavigation().stop();
        this.start(Move.CALL);
        this.callCooldown = 400 - this.level * 2;
    }

    // -- burrowing --------------------------------------------------------------------------------

    /** Dive into the ground: to come up under the target (attack) or somewhere well away from it. */
    private void burrow(@Nullable LivingEntity t, boolean attack) {
        this.getNavigation().stop();
        this.burrowAttack = attack && t != null;
        this.burrowGoal = null;
        if (!this.burrowAttack) {
            Vec3 from = t != null ? t.position() : this.position();
            float yaw = this.random.nextFloat() * Mth.TWO_PI;
            this.burrowGoal = from.add(Mth.cos(yaw) * 11, 0, Mth.sin(yaw) * 11);
        }
        this.burrowCooldown = Balance.burrowCooldown(this.level);
        this.flurryIndex = 0;
        this.start(Move.BURROW_DIVE);
        if (this.level() instanceof ServerLevel level && this.random.nextFloat() < .5F) this.say(level, "fight.burrow", 60);
    }

    private void goUnder(ServerLevel level) {
        this.setInvisible(true);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.dust(level, 40, .6, .25);
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + .4, this.getZ(), 8, .3, .2, .3, .02);
        this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.3F, .8F);
        this.sound(SoundEvents.SILVERFISH_HURT, .9F, .6F);
        this.start(Move.UNDER);
    }

    /** Underground: run just under the surface toward the goal, leaving a trail of cracking grit. */
    private void travel(ServerLevel level, @Nullable LivingEntity t) {
        if (this.fleeing && this.moveTicks > 20) {
            if (this.home != null) Sleepers.reseal(level, this.home);
            this.discard();
            return;
        }
        Vec3 goal = this.burrowAttack ? (t != null ? t.position() : null) : this.burrowGoal;
        if (goal == null || this.moveTicks > Move.UNDER.ticks) { this.surface(level, false); return; }
        double dx = goal.x - this.getX(), dz = goal.z - this.getZ(), flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.2) {
            if (this.burrowAttack) {
                this.start(Move.TREMOR);
                this.sound(SoundEvents.WARDEN_DIG, 1.2F, 1.5F);
            } else this.surface(level, false);
            return;
        }
        double step = Math.min(.42, flat);
        double nx = this.getX() + dx / flat * step, nz = this.getZ() + dz / flat * step;
        BlockPos ground = this.groundAt(level, nx, this.getY(), nz);
        if (ground == null || !level.getFluidState(ground.above()).isEmpty() || !level.getFluidState(ground).isEmpty()) {
            this.surface(level, false);
            return;
        }
        this.setPos(nx, ground.getY() + 1, nz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
        this.dust(level, 3, .2, .05);
        if (this.moveTicks % 6 == 0) this.sound(SoundEvents.SILVERFISH_STEP, 1F, .7F);
        if (this.moveTicks % 9 == 0) this.sound(SoundEvents.GRAVEL_BREAK, .7F, .6F);
    }

    private void surface(ServerLevel level, boolean strike) {
        this.setInvisible(false);
        this.noPhysics = false;
        this.setNoGravity(false);
        BlockPos ground = this.groundAt(level, this.getX(), this.getY(), this.getZ());
        if (ground != null) this.setPos(this.getX(), ground.getY() + 1, this.getZ());
        this.emergeStrike = strike;
        this.dust(level, 45, .7, .3);
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + .6, this.getZ(), 10, .4, .4, .4, .03);
        this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.5F, .7F);
        this.sound(SoundEvents.SILVERFISH_AMBIENT, 1F, .6F);
        this.setDeltaMovement(0, .25, 0);
        this.start(Move.EMERGE);
    }

    /** The top of the ground near (x, y, z): the block something would stand on, or null. */
    public static @Nullable BlockPos groundAt(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, y + 3, z).mutable();
        for (int i = 0; i < 8; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && level.getBlockState(p.above(2)).getCollisionShape(level, p.above(2)).isEmpty()) return p.immutable();
        }
        return null;
    }

    // -- hitting ----------------------------------------------------------------------------------

    private void faceTo(@Nullable Entity t, float maxTurn) {
        if (t == null) return;
        float want = (float) (Mth.atan2(t.getZ() - this.getZ(), t.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90F;
        float yaw = Mth.approachDegrees(this.getYRot(), want, maxTurn);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.getLookControl().setLookAt(t, 30, 30);
    }

    private boolean inFront(Entity e, double arcDegrees) {
        Vec3 to = e.position().subtract(this.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < .25) return true;
        Vec3 look = Vec3.directionFromRotation(0, this.getYRot());
        double cos = to.normalize().dot(look);
        return cos >= Math.cos(Math.toRadians(arcDegrees / 2));
    }

    /** Fair game for a swing: players (not in creative), and whatever is fighting them. Never villagers, never the clan. */
    private boolean victim(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof PlataMember || e instanceof Villager || e instanceof Silverfish) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e == this.getTarget() || !(e instanceof Enemy);
    }

    private boolean hurt(ServerLevel level, LivingEntity e, Balance.Hit hit, Vec3 from, double knock, double lift) {
        if (!e.hurtServer(level, this.damageSources().mobAttack(this), this.damageOf(hit))) return false;
        Vec3 push = e.position().subtract(from).multiply(1, 0, 1);
        push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(knock);
        e.push(push.x, lift, push.z);
        e.needsSync = true;
        return true;
    }

    /** One target, within reach and in front. */
    private boolean strike(ServerLevel level, @Nullable LivingEntity t, Balance.Hit hit, double reach, double arc, double knock, double lift) {
        if (t == null || !this.victim(t)) return false;
        double dy = t.getY() - this.getY();
        if (dy > 2.5 || dy < -2 || this.distanceTo(t) > reach + t.getBbWidth() / 2 || !this.inFront(t, arc)) return false;
        return this.hurt(level, t, hit, this.position(), knock, lift);
    }

    /** Everyone fair game in a sweep in front (the Silvertail's lash). */
    private void arc(ServerLevel level, Balance.Hit hit, double reach, double arc, double knock, double lift) {
        AABB box = this.getBoundingBox().inflate(reach, 1.5, reach);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::victim)) {
            if (this.distanceTo(e) <= reach + e.getBbWidth() / 2 && this.inFront(e, arc)) this.hurt(level, e, hit, this.position(), knock, lift);
        }
    }

    /** Everyone fair game within a radius of a point (eruptions, the slam). */
    private void area(ServerLevel level, Vec3 at, double radius, Balance.Hit hit, double knock, double lift) {
        AABB box = new AABB(at.x - radius, at.y - 1, at.z - radius, at.x + radius, at.y + 3, at.z + radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::victim)) {
            double dx = e.getX() - at.x, dz = e.getZ() - at.z;
            if (dx * dx + dz * dz <= radius * radius) this.hurt(level, e, hit, at, knock, lift);
        }
    }

    /** The slam's ring of cracking ground: step out of it before it erupts. */
    private void ring(ServerLevel level, double radius) {
        for (int i = 0; i < 24; i++) {
            double a = i * Mth.TWO_PI / 24 + this.moveTicks * .1, r = radius * (.35 + .65 * this.random.nextDouble());
            double x = this.getX() + Math.cos(a) * r, z = this.getZ() + Math.sin(a) * r;
            BlockPos g = this.groundAt(level, x, this.getY(), z);
            if (g == null) continue;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(g)), x, g.getY() + 1.05, z, 2, .1, 0, .1, .1);
        }
    }

    private void dust(ServerLevel level, int count, double spread, double speed) {
        BlockState under = level.getBlockState(BlockPos.containing(this.getX(), this.getY() - .5, this.getZ()));
        if (under.isAir() || !under.getFluidState().isEmpty()) under = Blocks.STONE.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), this.getX(), this.getY() + .2, this.getZ(),
                count, spread, .15, spread, speed);
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Silverfish out of the ground around them, going for the same target. */
    private void summon(ServerLevel level, int n, double radius) {
        this.summoned.removeIf(id -> !(level.getEntity(id) instanceof Silverfish s) || !s.isAlive());
        n = Math.min(n, MAX_SUMMONED - this.summoned.size());
        for (int i = 0; i < n; i++) {
            double a = this.random.nextDouble() * Mth.TWO_PI, r = radius * (.6 + .4 * this.random.nextDouble());
            double x = this.getX() + Math.cos(a) * r, z = this.getZ() + Math.sin(a) * r;
            BlockPos g = this.groundAt(level, x, this.getY(), z);
            if (g == null) continue;
            Silverfish s = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (s == null) continue;
            s.snapTo(x, g.getY() + 1, z, this.random.nextFloat() * 360, 0);
            s.setTarget(this.getTarget());
            s.addTag(SUMMONED_TAG);
            level.addFreshEntity(s);
            this.summoned.add(s.getUUID());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(g)), x, g.getY() + 1.1, z, 14, .25, .1, .25, .15);
        }
        this.sound(SoundEvents.SILVERFISH_AMBIENT, 1.2F, .9F);
    }

    private void dismissSummoned(ServerLevel level) {
        for (UUID id : this.summoned) {
            if (level.getEntity(id) instanceof Silverfish s && s.isAlive()) {
                level.sendParticles(ParticleTypes.POOF, s.getX(), s.getY() + .2, s.getZ(), 4, .1, .1, .1, .02);
                s.discard();
            }
        }
        this.summoned.clear();
    }

    /** Say a line from a pool to everyone near. {@code cooldown} keeps them from chattering. */
    public void say(ServerLevel level, String pool, int cooldown) {
        if (cooldown > 0 && this.sayCooldown > 0) return;
        this.sayCooldown = Math.max(this.sayCooldown, cooldown);
        Player who = this.getTarget() instanceof Player p ? p
                : this.lastFoe != null && level.getPlayerByUUID(this.lastFoe) instanceof Player p ? p
                : this.truceWith != null ? level.getPlayerByUUID(this.truceWith) : null;
        String text = PlataLines.pick(pool, this.rank, this.random, who == null ? null : who.getName().getString(),
                this.disguise.isEmpty() ? null : this.disguise);
        this.speak(level, Mothers.fill(text, who), false);
    }

    /** The player this member lets be, if they are letting anyone be. */
    public @Nullable UUID truceWith() { return this.truce ? this.truceWith : null; }

    /** Send a line (already chosen) to everyone within 40 blocks; {@code card} for the first-encounter title card. */
    public void speak(ServerLevel level, String text, boolean card) {
        String speaker = this.disguise.isEmpty() ? this.getType().getDescription().getString() : this.disguise;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 40 * 40) PlataNet.say(p, this, speaker, this.rank, text, card);
        }
    }

    // -- being hit --------------------------------------------------------------------------------

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.isInvulnerableTo(level, source);
        if (this.move.hidden() || this.move == Move.UNFURL || this.move == Move.BURROW_DIVE && this.moveTicks >= 8) return true;
        if (source.getEntity() instanceof PlataMember || source.getEntity() instanceof Silverfish) return true;
        return source.is(DamageTypeTags.IS_FALL) || super.isInvulnerableTo(level, source);
    }

    private boolean canDodge() {
        return this.move == Move.STANCE || this.move == Move.TAUNT || this.move == Move.READY || this.move == Move.CALL && this.moveTicks < 6;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isDeadOrDying()) return false;
        Entity attacker = source.getEntity();
        boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
        if (this.truce && attacker instanceof Player p && !p.isCreative() && !p.isSpectator() && !this.isInvulnerableTo(level, source)) {
            this.provoke(level, p);
        }
        if (attacker instanceof Player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && this.canDodge() && !this.isInvulnerableTo(level, source)) {
            double chance = projectile ? .25 : Balance.dodgeChance(this.level);
            if (!this.dodgedLast && this.random.nextDouble() < chance) {
                this.dodgedLast = true;
                this.leap(source.getDirectEntity() != null ? source.getDirectEntity() : attacker, projectile || this.random.nextBoolean() ? Move.SIDEROLL : Move.BACKFLIP);
                if (this.random.nextFloat() < .35F) this.say(level, "fight.dodge", 80);
                return false;
            }
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (!hurt || this.isDeadOrDying()) return hurt;
        this.dodgedLast = false;
        // three hits in two seconds, a critical blow, or a hit as they come up out of the ground: they reel
        this.hitTicks[this.hitIndex = (this.hitIndex + 1) % 3] = this.tickCount;
        boolean flurried = this.tickCount - this.hitTicks[(this.hitIndex + 1) % 3] <= 40;
        boolean crit = attacker instanceof Player p && !projectile && p.fallDistance > 0 && !p.onGround() && !p.isInWater();
        boolean exposed = this.move == Move.EMERGE && this.moveTicks > 5;
        if ((flurried || crit || exposed) && this.move != Move.STAGGER && this.move != Move.SLAM && this.move != Move.LASH) this.stagger();
        if (amount >= this.getMaxHealth() * .15F) this.say(level, "fight.hurt", 60);
        if (!this.molted && Balance.molts(this.level) && this.getHealth() < this.getMaxHealth() * .35F) this.molt(level);
        return true;
    }

    private void stagger() {
        this.flurryIndex = 0;
        this.getNavigation().stop();
        this.start(Move.STAGGER);
        this.sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, .8F, .7F);
    }

    /** A shield blocked their blade: they reel. */
    @Override
    protected void blockedByItem(LivingEntity defender, DamageSource source, float damage, boolean fullyBlocked) {
        super.blockedByItem(defender, source, damage, fullyBlocked);
        if (this.move.flurry() || this.move == Move.LUNGE) this.stagger();
    }

    /** Low on health: they shed a burst of grit, burrow, come up twice in a row and fight faster. */
    private void molt(ServerLevel level) {
        this.molted = true;
        this.getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(
                new AttributeModifier(PlataClan.id("molt"), .15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        this.dust(level, 70, .8, .35);
        this.sound(SoundEvents.SILVERFISH_DEATH, 1.2F, .5F);
        this.say(level, "fight.molt", 0);
        LivingEntity t = this.getTarget();
        if (t != null) { this.moltAmbush = 1; this.burrow(t, true); }
    }

    /** Nobody left to fight: a parting line and into the ground for good. */
    private void flee(ServerLevel level) {
        this.leave(level, this.home != null ? "hideout.reseal" : "fight.flee");
    }

    /** A parting line from {@code pool}, and into the ground for good. */
    public void leave(ServerLevel level, String pool) {
        this.truce = false;
        this.fleeing = true;
        this.say(level, pool, 0);
        this.burrow(null, false);
    }

    // -- letting a Mother's killer be ---------------------------------------------------------------------

    /**
     * They know {@code p} killed a Silver Mother, and they won't fight them for it: they stand where they are,
     * revealed, and can be talked to ({@link dev.plataclan.disguise.Truce}) for {@code ticks}, then go back into the
     * stone. Strike them and the truce is over.
     */
    public void yieldTo(ServerPlayer p, int ticks) {
        this.truce = true;
        this.truceTicks = ticks;
        this.truceWith = p.getUUID();
        this.asked = 0;
        this.setTarget(null);
        this.getNavigation().stop();
    }

    public boolean truce() { return this.truce; }
    public int truceTicks() { return this.truceTicks; }
    public void truceTicks(int ticks) { this.truceTicks = ticks; }
    /** Questions answered in the truce window so far. */
    public int asked() { return this.asked; }
    public int ask() { return ++this.asked; }

    private void standDown(ServerLevel level) {
        this.getNavigation().stop();
        if (this.move == Move.UNFURL && this.moveTicks < Move.UNFURL.ticks) return;
        if (this.move != Move.STANCE) this.start(Move.STANCE);
        Player p = this.truceWith == null ? null : level.getPlayerByUUID(this.truceWith);
        if (p != null && p.distanceToSqr(this) < 16 * 16) this.faceTo(p, 12);
        if (--this.truceTicks <= 0 || p == null || !p.isAlive() || p.distanceToSqr(this) > 40 * 40) {
            this.leave(level, this.parting ? "" : "mother.leave");
        }
    }

    /** They have said goodbye already: in a moment they go, without another word. */
    public void partIn(int ticks) {
        this.parting = true;
        this.truceTicks = Math.min(this.truceTicks, ticks);
    }

    /** Struck while letting someone be: so be it. */
    private void provoke(ServerLevel level, LivingEntity by) {
        this.truce = false;
        this.setTarget(by);
        this.decideIn = 4;
        this.say(level, "mother.provoked", 0);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.truce && hand == InteractionHand.MAIN_HAND && !player.isSpectator()) {
            if (player instanceof ServerPlayer sp) dev.plataclan.disguise.Truce.open(sp, this);
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.mobInteract(player, hand);
    }

    // -- the end ----------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        boolean first = !this.isRemoved() && !this.dead;
        super.die(source);
        if (first && this.level() instanceof ServerLevel level) {
            this.say(level, "defeat", 0);
            this.dismissSummoned(level);
            if (this.bar != null) this.bar.removeAllPlayers();
        }
    }

    /** They drop to a knee, curl up and burst into dust, like a silverfish into a wall. */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            if (this.deathTime == 24) {
                this.dust(level, 60, .5, .3);
                level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + .5, this.getZ(), 14, .3, .3, .3, .04);
                this.sound(SoundEvents.SILVERFISH_DEATH, 1F, .7F);
                this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.2F, .8F);
            }
            if (this.deathTime >= DEATH_TICKS) this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        PlataLoot.drop(this, level, this.rank, this.level, killedByPlayer);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            if (this.bar != null) this.bar.removeAllPlayers();
            if (reason.shouldDestroy()) this.dismissSummoned(level);
        }
        super.remove(reason);
    }

    // -- the boss bar -----------------------------------------------------------------------------

    public Component barName() {
        Component title = this.getType().getDescription().copy().append(" · Lv " + this.plataLevel());
        return this.disguise.isEmpty() ? title : Component.literal(this.disguise + ", ").append(title);
    }

    private void updateBar(ServerLevel level) {
        if (this.bar == null) {
            this.bar = new ServerBossEvent(this.getUUID(), this.barName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        this.bar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0, 1));
        if (this.tickCount % 10 != 0) return;
        this.bar.setName(this.barName());
        for (ServerPlayer p : level.players()) {
            boolean near = !this.truce && p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < 40 * 40;
            if (near) this.bar.addPlayer(p);
            else this.bar.removePlayer(p);
        }
    }

    // -- odds and ends ----------------------------------------------------------------------------

    @Override public boolean isPickable() { return !this.hidden() && super.isPickable(); }
    @Override public boolean isPushable() { return !this.hidden() && super.isPushable(); }
    @Override protected void doPush(Entity entity) { if (!this.hidden()) super.doPush(entity); }
    @Override public boolean isInWall() { return !this.hidden() && super.isInWall(); }
    @Override public boolean shouldShowName() { return false; }

    @Override protected @Nullable SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
    @Override public float getVoicePitch() { return .75F + this.random.nextFloat() * .1F; }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("PlataLevel", this.level);
        for (Balance.Hit h : Balance.Hit.values()) out.putFloat("Damage" + h.name(), this.damage[h.ordinal()]);
        out.putString("Disguise", this.disguise);
        out.putBoolean("Configured", this.configured);
        out.putBoolean("Molted", this.molted);
        if (this.home != null) out.store("Home", BlockPos.CODEC, this.home);
        if (this.truce && this.truceWith != null) {
            out.store("TruceWith", net.minecraft.core.UUIDUtil.CODEC, this.truceWith);
            out.putInt("TruceTicks", this.truceTicks);
            out.putInt("Asked", this.asked);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.level = in.getIntOr("PlataLevel", 10);
        for (Balance.Hit h : Balance.Hit.values()) this.damage[h.ordinal()] = in.getFloatOr("Damage" + h.name(), 3);
        this.disguise = in.getStringOr("Disguise", "");
        this.configured = in.getBooleanOr("Configured", false);
        this.molted = in.getBooleanOr("Molted", false);
        this.home = in.read("Home", BlockPos.CODEC).orElse(null);
        this.truceWith = in.read("TruceWith", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        this.truce = this.truceWith != null;
        this.truceTicks = in.getIntOr("TruceTicks", 0);
        this.asked = in.getIntOr("Asked", 0);
        this.entityData.set(DATA_LEVEL, this.level);
        this.flurryLength = Balance.flurryLength(this.level);
        this.xpReward = Balance.experience(this.level);
    }
}
