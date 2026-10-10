package dev.plataclan.mother;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.plataclan.Balance;
import dev.plataclan.Balance.MotherHit;
import dev.plataclan.PlataClan;
import dev.plataclan.PlataConfig;
import dev.plataclan.PlataLines;
import dev.plataclan.PlataNet;
import dev.plataclan.PlayerPower;
import dev.plataclan.block.PlataBlocks;
import dev.plataclan.block.SilverHuskBlock;
import dev.plataclan.entity.PlataMember;
import dev.plataclan.entity.SilverShard;
import dev.plataclan.hideout.Sleepers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Silver Mother of a Warren: the oldest of the clan, asleep curled up on the dais of its vault over the Clan
 * Coffer. Every Warren has one, under her own name ("Mother Ysolde"). She wakes when a stranger comes close or strikes
 * her, sized up to whoever woke her like any Plata but on a Warden's scale (see {@link Balance#motherHealth}), and
 * fights in three phases: at two thirds of her health she bursts out of her old shell and quickens, at one third she
 * makes her last molt and the very walls of the vault wake. Whoever helped bring her down is remembered by every Plata
 * in the wild ({@link Mothers}). If everyone she fights leaves or dies, she walks back to her dais and sleeps again,
 * whole.
 */
public class SilverMother extends Monster implements Enemy {
    private static final EntityDataAccessor<Integer> DATA_MOVE = SynchedEntityData.defineId(SilverMother.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(SilverMother.class, EntityDataSerializers.INT);
    /** The beats her clips are keyed to (ticks into the move). */
    static final int RISE_CARD = 32, CRUSH_AT = 20, STUCK_UNTIL = 40, SWEEP_AT = 17, KEEN_AT = 24, RAIN_AT = 12, BROOD_AT = 16,
            BURROW_AT = 18, ERUPT_AT = 6, SHED_BURST = 32;
    private static final int MAX_SUMMONED = 10, LEASH = 34, WAKE_SIGHT = 12, WAKE_NEAR = 5, DEATH_TICKS = 60;
    public static final String TITLE = "The Silver Mother";

    private String name = "";
    private @Nullable BlockPos home;
    private float homeYaw;
    private int level = 30, phase = 1, pendingPhase;
    private final float[] damage = new float[MotherHit.values().length];
    private boolean configured;

    private MotherMove move = MotherMove.DORMANT;
    private int moveTicks, serial, clientMoveStart;
    private int decideIn = 10, crushCd, sweepCd = 20, keenCd = 60, rainCd = 40, broodCd = 100, burrowCd = 140, sayCd, staggerCd,
            lostTicks, erupts;
    private float poise;
    private int poiseSince;
    private boolean goingHome;
    private @Nullable Vec3 tremorAt;
    private final Set<UUID> fighters = new HashSet<>();
    private final List<UUID> summoned = new ArrayList<>();
    private final List<Drop> rain = new ArrayList<>();
    private @Nullable ServerBossEvent bar;

    /** A scale she flung, falling on {@code at} at game time {@code time}. */
    private record Drop(Vec3 at, long time) {}

    public SilverMother(EntityType<? extends SilverMother> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        this.setPersistenceRequired();
        java.util.Arrays.fill(this.damage, 6);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, Balance.MOTHER_MIN_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 3.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.SAFE_FALL_DISTANCE, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOVE, 0);
        builder.define(DATA_PHASE, 1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, PlataMember.class, SilverMother.class));
    }

    // -- who she is ---------------------------------------------------------------------------------

    /** Her own name, without the title ("Ysolde"). */
    public String motherName() { return this.name; }
    /** "Mother Ysolde". */
    public String title() { return "Mother " + (this.name.isEmpty() ? "Nameless" : this.name); }
    public int plataLevel() { return this.level; }
    public int phase() { return this.entityData.get(DATA_PHASE); }
    public @Nullable BlockPos home() { return this.home; }
    public float damageOf(MotherHit hit) { return this.damage[hit.ordinal()]; }

    /** A fresh Mother on her dais at {@code home}, facing {@code yaw}, asleep. */
    public void settleAt(BlockPos home, float yaw, String name) {
        this.home = home.immutable();
        this.homeYaw = yaw;
        this.name = name;
        this.snapTo(home.getX() + .5, home.getY(), home.getZ() + .5, yaw, 0);
        this.yBodyRot = this.yHeadRot = yaw;
        this.start(MotherMove.DORMANT);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(accessor, difficulty, reason, data);
        if (this.name.isEmpty()) this.name = Mothers.newName(accessor.getRandom(), accessor.getLevel());
        if (this.home == null) { this.home = this.blockPosition(); this.homeYaw = this.getYRot(); }
        return out;
    }

    /** Health, armor and every blow, sized up from the player who woke her. */
    public void configure(@Nullable ServerPlayer p) {
        PlataConfig cfg = PlataConfig.get();
        int players = 0;
        if (this.level() instanceof ServerLevel world) {
            for (ServerPlayer near : world.players()) if (!near.isSpectator() && !near.isCreative() && near.distanceToSqr(this) < 32 * 32) players++;
        }
        Balance.Foe foe = p != null ? PlayerPower.read(p, this) : new Balance.Foe(30, 20, 8, 0, 0, 6);
        this.level = Balance.clampLevel((p != null ? PlayerPower.level(p) : 30) + 5);
        float hp = Balance.motherHealth(this.level, foe.meleeHit(), Math.max(1, players), cfg.healthScale);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        this.setHealth(hp);
        this.getAttribute(Attributes.ARMOR).setBaseValue(Balance.motherArmor(this.level));
        for (MotherHit h : MotherHit.values()) this.damage[h.ordinal()] = Balance.motherDamage(h, foe, this.level, cfg.damageScale);
        this.xpReward = 200 + this.level * 2;
        this.configured = true;
    }

    // -- moves ----------------------------------------------------------------------------------------

    public MotherMove move() { return MotherMove.byOrdinal(this.entityData.get(DATA_MOVE) & 0xFF); }
    /** Client: seconds since the current move began. */
    public float moveTime(float partialTick) { return (this.tickCount - this.clientMoveStart + partialTick) / 20F; }
    public boolean hidden() { return this.move().hidden(); }
    public boolean dormant() { return this.move == MotherMove.DORMANT; }

    public void start(MotherMove m) {
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
            this.noPhysics = this.move().hidden();
        }
    }

    /** She wakes, sized up to {@code p}, and rises off her dais. */
    public void awaken(@Nullable ServerPlayer p) {
        if (this.move != MotherMove.DORMANT || !(this.level() instanceof ServerLevel level)) return;
        this.configure(p);
        this.goingHome = false;
        this.lostTicks = 0;
        if (p != null && !p.isCreative() && !p.isSpectator()) this.setTarget(p);
        this.start(MotherMove.RISE);
        this.sound(SoundEvents.SILVERFISH_AMBIENT, 1.6F, .4F);
        this.sound(SoundEvents.DEEPSLATE_TILES_BREAK, 1.2F, .6F);
        String sister = p == null ? null : Mothers.latest(p);
        this.say(level, sister != null ? "boss.slayer" : "boss.wake", 0);
    }

    // -- the fight --------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.moveTicks++;
        this.cooldowns();
        this.updateBar(level);
        this.fallingScales(level);
        LivingEntity t = this.foe(level);
        switch (this.move) {
            case DORMANT -> {
                this.getNavigation().stop();
                if (this.tickCount % 5 == 0) { ServerPlayer p = this.waker(level); if (p != null) this.awaken(p); }
            }
            case RISE -> {
                if (this.moveTicks == 10) this.dust(level, 60, 1.2, .2);
                if (this.moveTicks == RISE_CARD) this.card(level, this.title().toUpperCase(java.util.Locale.ROOT), TITLE);
                if (this.moveTicks >= MotherMove.RISE.ticks) this.idle(4);
            }
            case STANCE -> this.think(level, t);
            case CRUSH -> this.crush(level, t);
            case SWEEP -> {
                if (this.moveTicks < 8) this.faceTo(t, 20);
                if (this.moveTicks == 10) this.sound(SoundEvents.BREEZE_WHIRL, 1.4F, .6F);
                if (this.moveTicks == SWEEP_AT) this.sweep(level);
                if (this.moveTicks >= MotherMove.SWEEP.ticks) this.idle(this.reaction());
            }
            case KEEN -> this.keen(level, t);
            case RAIN -> {
                this.faceTo(t, 20);
                if (this.moveTicks == RAIN_AT && t != null) this.fling(level, t);
                if (this.moveTicks >= MotherMove.RAIN.ticks) this.idle(this.reaction());
            }
            case BROOD -> {
                if (this.moveTicks == BROOD_AT) this.brood(level, t);
                if (this.moveTicks >= MotherMove.BROOD.ticks) this.idle(this.reaction());
            }
            case BURROW -> {
                if (this.moveTicks == 6) this.dust(level, 30, .9, .2);
                if (this.moveTicks >= BURROW_AT) this.goUnder(level);
            }
            case UNDER -> this.travel(level, t);
            case TREMOR -> this.tremor(level);
            case ERUPT -> {
                if (this.moveTicks == ERUPT_AT) this.erupted(level);
                if (this.moveTicks >= MotherMove.ERUPT.ticks) {
                    if (--this.erupts > 0 && t != null) this.burrow(level, t);
                    else this.idle(this.reaction());
                }
            }
            case SHED -> {
                if (this.moveTicks % 3 == 0 && this.moveTicks < SHED_BURST) this.dust(level, 6, .5, .05);
                if (this.moveTicks == SHED_BURST) this.burstShell(level);
                if (this.moveTicks >= MotherMove.SHED.ticks) { this.broodCd = 0; this.idle(4); }
            }
            case STAGGER -> { if (this.moveTicks >= MotherMove.STAGGER.ticks) this.idle(2); }
            case DEFEAT -> {}
        }
    }

    private void cooldowns() {
        if (this.crushCd > 0) this.crushCd--;
        if (this.sweepCd > 0) this.sweepCd--;
        if (this.keenCd > 0) this.keenCd--;
        if (this.rainCd > 0) this.rainCd--;
        if (this.broodCd > 0) this.broodCd--;
        if (this.burrowCd > 0) this.burrowCd--;
        if (this.sayCd > 0) this.sayCd--;
        if (this.staggerCd > 0) this.staggerCd--;
    }

    private float pace() { return Balance.phasePace(this.phase); }
    private int reaction() { return Math.round(14 / this.pace()); }
    private int cooldown(int ticks) { return Math.round(ticks / this.pace()); }

    private void idle(int delay) {
        this.start(MotherMove.STANCE);
        this.decideIn = Math.max(this.decideIn, delay);
    }

    /** A survival player she notices from her dais: in sight within a dozen blocks, or anyone right beside her. */
    private @Nullable ServerPlayer waker(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return null;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            double d = p.distanceTo(this);
            if (d <= WAKE_NEAR || d <= WAKE_SIGHT && this.sees(level, p)) return p;
        }
        return null;
    }

    private boolean sees(ServerLevel level, Entity e) {
        return level.clip(new ClipContext(this.getEyePosition(), e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    /** Her current foe, if still fair game and still within reach of her dais. */
    private @Nullable LivingEntity foe(ServerLevel level) {
        LivingEntity t = this.getTarget();
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) t = null;
        if (t != null && (!t.isAlive() || this.home != null && t.distanceToSqr(Vec3.atCenterOf(this.home)) > LEASH * LEASH)) {
            if (t instanceof Player && !t.isAlive()) this.say(level, "boss.win", 0);
            t = null;
        }
        if (t == null && this.getTarget() != null) this.setTarget(null);
        if (t == null && this.move == MotherMove.STANCE && level.getDifficulty() != Difficulty.PEACEFUL) {
            // anyone else in the vault will do
            Player next = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), 20,
                    e -> e instanceof Player p && !p.isCreative() && !p.isSpectator() && this.sees(level, p));
            if (next != null) { this.setTarget(next); t = next; }
        }
        return t;
    }

    private void think(ServerLevel level, @Nullable LivingEntity t) {
        if (t == null) {
            if (++this.lostTicks > 300) this.goHome(level);
            else this.getNavigation().stop();
            return;
        }
        this.lostTicks = 0;
        this.goingHome = false;
        this.faceTo(t, 25);
        double d = this.distanceTo(t);
        double flat = Math.sqrt(this.distanceToSqr(t.getX(), this.getY(), t.getZ()));
        boolean above = t.getY() - this.getY() > 2.5 && flat < 8;
        if (this.decideIn > 0) {
            this.decideIn--;
            this.approach(t, d);
            return;
        }
        this.decideIn = this.reaction();
        if (d < 3.8 && this.crushCd == 0 && !above) {
            this.getNavigation().stop();
            this.start(MotherMove.CRUSH);
            this.crushCd = this.cooldown(90);
            if (this.chance(.4)) this.say(level, "boss.crush", 80);
        } else if (d < 5.2 && this.sweepCd == 0) {
            this.getNavigation().stop();
            this.start(MotherMove.SWEEP);
            this.sweepCd = this.cooldown(70);
            if (this.chance(.4)) this.say(level, "boss.sweep", 80);
        } else if (this.broodCd == 0 && this.chance(.35)) {
            this.getNavigation().stop();
            this.start(MotherMove.BROOD);
            this.broodCd = this.cooldown(500);
            this.say(level, "boss.brood", 0);
        } else if (d > 6.5 && this.keenCd == 0 && this.sees(level, t)) {
            this.getNavigation().stop();
            this.start(MotherMove.KEEN);
            this.keenCd = this.cooldown(160);
            if (this.chance(.6)) this.say(level, "boss.keen", 60);
        } else if (this.phase >= 2 && this.rainCd == 0 && (d > 5 || above)) {
            this.getNavigation().stop();
            this.start(MotherMove.RAIN);
            this.rainCd = this.cooldown(140);
            if (this.chance(.5)) this.say(level, "boss.rain", 80);
        } else if (this.burrowCd == 0 && (d > 10 || above || d > 6 && this.chance(.2))) {
            this.erupts = this.phase >= 3 ? 2 : 1;
            this.burrow(level, t);
        } else {
            this.approach(t, d);
        }
    }

    private boolean chance(double p) { return this.random.nextDouble() < p; }

    private void approach(LivingEntity t, double d) {
        if (d > 3.0) this.getNavigation().moveTo(t, 1.0);   // her stride already quickens with each phase (setPhase)
        else this.getNavigation().stop();
    }

    /** Nobody left: back to her dais, where she curls up and sleeps again, whole. */
    private void goHome(ServerLevel level) {
        if (this.home == null) this.home = this.blockPosition();
        if (!this.goingHome) {
            this.goingHome = true;
            this.say(level, "boss.rest", 0);
        }
        Vec3 at = Vec3.atBottomCenterOf(this.home);
        if (this.position().distanceToSqr(at) > 2.25 && this.lostTicks < 900) {
            if (this.tickCount % 20 == 0) this.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
            return;
        }
        this.getNavigation().stop();
        this.snapTo(at.x, at.y, at.z, this.homeYaw, 0);
        this.yBodyRot = this.yHeadRot = this.homeYaw;
        this.setHealth(this.getMaxHealth());
        this.setPhase(1);
        this.goingHome = false;
        this.lostTicks = 0;
        this.fighters.clear();
        this.dismissSummoned(level);
        if (this.bar != null) this.bar.removeAllPlayers();
        this.start(MotherMove.DORMANT);
    }

    private void setPhase(int phase) {
        this.phase = phase;
        this.entityData.set(DATA_PHASE, phase);
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (phase <= 1) speed.removeModifier(PlataClan.id("mother_phase"));
        else speed.addOrReplacePermanentModifier(new AttributeModifier(PlataClan.id("mother_phase"), this.pace() - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    // -- her blows (each lands on its clip's beat) ---------------------------------------------------------

    private void crush(ServerLevel level, @Nullable LivingEntity t) {
        if (this.moveTicks < CRUSH_AT - 4) this.faceTo(t, 15);
        if (this.moveTicks == 8) this.sound(SoundEvents.SILVERFISH_AMBIENT, 1.2F, .5F);
        if (this.moveTicks == CRUSH_AT) {
            Vec3 at = this.position().add(Vec3.directionFromRotation(0, this.getYRot()).scale(1.7));
            this.sound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.6F, .7F);
            this.sound(SoundEvents.DEEPSLATE_BREAK, 1.6F, .5F);
            this.ring(level, at, 3.0, 40);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + .3, at.z, 1, 0, 0, 0, 0);
            this.area(level, at, 3.0, MotherHit.CRUSH, .5, .55);
        }
        if (this.moveTicks > CRUSH_AT && this.moveTicks < STUCK_UNTIL && this.moveTicks % 4 == 0) this.dust(level, 8, .6, .05);
        if (this.moveTicks >= MotherMove.CRUSH.ticks) this.idle(this.reaction());
    }

    /** Gauntlets in the floor: for a second after the Crush she can't defend herself. */
    public boolean stuck() { return this.move == MotherMove.CRUSH && this.moveTicks >= CRUSH_AT && this.moveTicks < STUCK_UNTIL; }

    private void sweep(ServerLevel level) {
        this.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, .5F);
        this.sound(SoundEvents.CHAIN_BREAK, 1.2F, .6F);
        for (int i = 0; i < 16; i++) {
            double a = i * Mth.TWO_PI / 16;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + Math.cos(a) * 3.6, this.getY() + .5, this.getZ() + Math.sin(a) * 3.6, 1, 0, 0, 0, 0);
        }
        this.area(level, this.position(), 5.5, MotherHit.SWEEP, 1.4, .35);
    }

    private void keen(ServerLevel level, @Nullable LivingEntity t) {
        if (this.moveTicks < KEEN_AT - 2) this.faceTo(t, 12);
        if (this.moveTicks < KEEN_AT && this.moveTicks % 4 == 0) {
            this.sound(SoundEvents.SILVERFISH_AMBIENT, 1.2F, .5F + this.moveTicks / 22F);
            Vec3 mouth = this.getEyePosition();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), mouth.x, mouth.y, mouth.z, 6, .6, .4, .6, -.05);
        }
        if (this.moveTicks == KEEN_AT) {
            this.sound(SoundEvents.SILVERFISH_HURT, 3F, .45F);
            this.sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3F, .5F);
            this.sound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 2F, 1.4F);
            this.shriek(level);
        }
        if (this.moveTicks > KEEN_AT && this.moveTicks <= KEEN_AT + 8) this.keenWave(level, (this.moveTicks - KEEN_AT) * 2.0);
        if (this.moveTicks >= MotherMove.KEEN.ticks) this.idle(this.reaction());
    }

    /** Half the width of the Keen's cone, in degrees: wider after her last molt. */
    private double keenHalfAngle() { return this.phase >= 3 ? 50 : 38; }

    private void shriek(ServerLevel level) {
        Vec3 eye = this.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(0, this.getYRot());
        double cos = Math.cos(Math.toRadians(this.keenHalfAngle()));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(16, 6, 16), this::victim)) {
            Vec3 to = e.getEyePosition().subtract(eye);
            Vec3 flat = to.multiply(1, 0, 1);
            if (to.length() > 16 || flat.lengthSqr() > .5 && flat.normalize().dot(look) < cos) continue;
            if (!this.sees(level, e)) continue;   // a pillar between you and her keeps you safe
            if (e.hurtServer(level, this.damageSources().sonicBoom(this), this.damageOf(MotherHit.KEEN))) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this);
            }
        }
    }

    private void keenWave(ServerLevel level, double r) {
        float yaw = this.getYRot();
        double half = this.keenHalfAngle();
        for (double a = -half; a <= half; a += 8) {
            Vec3 dir = Vec3.directionFromRotation(0, (float) (yaw + a));
            Vec3 p = this.getEyePosition().add(dir.scale(r)).add(0, -.6, 0);
            level.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 1, .1, .3, .1, .01);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), p.x, p.y, p.z, 2, .2, .4, .2, .05);
        }
    }

    /** The Silver Rain: scales thrown high come down round the foe a beat apart; dust falls first where each will land. */
    private void fling(ServerLevel level, LivingEntity t) {
        this.sound(SoundEvents.TRIDENT_THROW.value(), 1.2F, .6F);
        this.sound(SoundEvents.CHAIN_BREAK, 1F, 1.6F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()),
                this.getX(), this.getY() + 4, this.getZ(), 30, .4, 1.2, .4, .3);
        int n = 5 + 2 * this.phase;
        long now = level.getGameTime();
        for (int i = 0; i < n; i++) {
            double a = this.random.nextDouble() * Mth.TWO_PI, r = i == 0 ? 0 : 1.2 + this.random.nextDouble() * 3.4;
            BlockPos g = PlataMember.groundAt(level, t.getX() + Math.cos(a) * r, t.getY(), t.getZ() + Math.sin(a) * r);
            if (g == null) continue;
            this.rain.add(new Drop(new Vec3(t.getX() + Math.cos(a) * r, g.getY() + 1, t.getZ() + Math.sin(a) * r), now + 18 + i * 3L));
        }
    }

    private void fallingScales(ServerLevel level) {
        if (this.rain.isEmpty()) return;
        long now = level.getGameTime();
        for (var it = this.rain.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            if (now < d.time()) {
                if (d.time() - now <= 14 && now % 2 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.CALCITE.defaultBlockState()),
                            d.at().x, d.at().y + 5, d.at().z, 2, .25, .5, .25, 0);
                    level.sendParticles(ParticleTypes.CRIT, d.at().x, d.at().y + .05, d.at().z, 1, .3, 0, .3, 0);
                }
                continue;
            }
            it.remove();
            SilverShard shard = new SilverShard(level, this, this.damageOf(MotherHit.SHARD));
            shard.setPos(d.at().x, d.at().y + 7, d.at().z);
            shard.setDeltaMovement(0, -1.7, 0);
            level.addFreshEntity(shard);
        }
    }

    /** Her children: up to two sealed husks near her split, and silverfish come up out of the floor. */
    private void brood(ServerLevel level, @Nullable LivingEntity t) {
        this.sound(SoundEvents.SNIFFER_EGG_HATCH, 1.4F, .5F);
        this.ring(level, this.position(), 5, 30);
        List<BlockPos> husks = new ArrayList<>();
        BlockPos c = this.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-24, -6, -24), c.offset(24, 6, 24))) {
            BlockState s = level.getBlockState(q);
            if (s.is(PlataBlocks.SILVER_HUSK) && SilverHuskBlock.sealed(s)
                    && s.getValue(SilverHuskBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) husks.add(q.immutable());
        }
        husks.sort(java.util.Comparator.comparingDouble(q -> q.distSqr(c)));
        int woke = 0;
        Player who = t instanceof Player p ? p : null;
        for (BlockPos q : husks) {
            if (woke >= 2 || Sleepers.awake(level, q) >= Balance.AWAKE_CAP) break;
            if (Sleepers.hatch(level, q, level.getBlockState(q), who, "stir") != null) woke++;
        }
        this.summon(level, 2 + this.phase, 4.5, t);
    }

    private void burrow(ServerLevel level, LivingEntity t) {
        this.getNavigation().stop();
        this.burrowCd = this.cooldown(220);
        this.start(MotherMove.BURROW);
        this.tremorAt = null;
        if (this.random.nextFloat() < .5F) this.say(level, "boss.burrow", 60);
    }

    private void goUnder(ServerLevel level) {
        this.setInvisible(true);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.dust(level, 60, .9, .3);
        this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.6F, .6F);
        this.sound(SoundEvents.WARDEN_DIG, 1.2F, 1.3F);
        this.start(MotherMove.UNDER);
    }

    private void travel(ServerLevel level, @Nullable LivingEntity t) {
        if (t == null || this.moveTicks > MotherMove.UNDER.ticks) { this.surface(level, false); return; }
        double dx = t.getX() - this.getX(), dz = t.getZ() - this.getZ(), flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.0) {
            this.tremorAt = this.position();
            this.start(MotherMove.TREMOR);
            this.sound(SoundEvents.WARDEN_DIG, 1.4F, .9F);
            return;
        }
        double step = Math.min(.5 * this.pace(), flat);
        double nx = this.getX() + dx / flat * step, nz = this.getZ() + dz / flat * step;
        BlockPos ground = PlataMember.groundAt(level, nx, this.getY(), nz);
        if (ground == null || !level.getFluidState(ground.above()).isEmpty()) { this.surface(level, false); return; }
        this.setPos(nx, ground.getY() + 1, nz);
        this.dust(level, 4, .3, .05);
        if (this.moveTicks % 5 == 0) this.sound(SoundEvents.SILVERFISH_STEP, 1.3F, .5F);
        if (this.moveTicks % 8 == 0) this.sound(SoundEvents.GRAVEL_BREAK, 1F, .5F);
    }

    /** The floor shakes in a wide ring round where she'll come up: get out of it. */
    private void tremor(ServerLevel level) {
        Vec3 at = this.tremorAt != null ? this.tremorAt : this.position();
        if (this.moveTicks % 2 == 0) this.ring(level, at, 3.4, 20);
        if (this.moveTicks % 5 == 0) this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.5F, .5F);
        if (this.moveTicks >= MotherMove.TREMOR.ticks) this.surface(level, true);
    }

    private void surface(ServerLevel level, boolean strike) {
        this.setInvisible(false);
        this.noPhysics = false;
        this.setNoGravity(false);
        BlockPos ground = PlataMember.groundAt(level, this.getX(), this.getY(), this.getZ());
        if (ground != null) this.setPos(this.getX(), ground.getY() + 1, this.getZ());
        if (!strike) this.erupts = 0;
        this.dust(level, 70, 1.0, .35);
        this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.8F, .6F);
        this.sound(SoundEvents.SILVERFISH_AMBIENT, 1.4F, .45F);
        this.setDeltaMovement(0, .2, 0);
        this.start(MotherMove.ERUPT);
    }

    private void erupted(ServerLevel level) {
        if (this.erupts <= 0) return;
        this.sound(SoundEvents.WARDEN_EMERGE, 1.2F, 1.4F);
        this.sound(SoundEvents.DEEPSLATE_BREAK, 1.6F, .6F);
        this.area(level, this.position(), 3.4, MotherHit.ERUPT, .6, 1.0);
    }

    /** Between phases: the old shell bursts off her; whoever is close is thrown back (unhurt). */
    private void burstShell(ServerLevel level) {
        this.setPhase(this.pendingPhase);
        double x = this.getX(), y = this.getY() + 1.6, z = this.getZ();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), x, y, z, 140, .7, 1.2, .7, .35);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.POLISHED_DIORITE.defaultBlockState()), x, y, z, 60, .6, 1, .6, .3);
        level.sendParticles(ParticleTypes.POOF, x, y, z, 30, .6, .9, .6, .06);
        this.sound(SoundEvents.SNIFFER_EGG_HATCH, 2F, .4F);
        this.sound(SoundEvents.DEEPSLATE_TILES_BREAK, 2F, .5F);
        this.sound(SoundEvents.SILVERFISH_DEATH, 1.6F, .4F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(5), this::victim)) {
            Vec3 push = e.position().subtract(this.position()).multiply(1, 0, 1);
            if (push.lengthSqr() < 1.0E-4) continue;
            push = push.normalize().scale(1.3);
            e.push(push.x, .5, push.z);
            e.needsSync = true;
        }
        if (this.phase >= 3) this.wakeWalls(level);
    }

    /** Her last molt: the vault's infested stones crack and their silverfish pour out. */
    private void wakeWalls(ServerLevel level) {
        List<BlockPos> stones = new ArrayList<>();
        BlockPos c = this.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-14, -2, -14), c.offset(14, 12, 14))) {
            if (level.getBlockState(q).getBlock() instanceof InfestedBlock) stones.add(q.immutable());
        }
        java.util.Collections.shuffle(stones, new java.util.Random(this.random.nextLong()));
        int woke = 0;
        LivingEntity t = this.getTarget();
        for (BlockPos q : stones) {
            if (woke >= 6) break;
            BlockState s = level.getBlockState(q);
            Direction open = null;
            for (Direction dir : Direction.values()) if (level.getBlockState(q.relative(dir)).isAir()) { open = dir; break; }
            if (open == null) continue;
            level.setBlock(q, ((InfestedBlock) s.getBlock()).hostStateByInfested(s), 3);
            level.levelEvent(2001, q, net.minecraft.world.level.block.Block.getId(s));
            Silverfish fish = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (fish == null) continue;
            BlockPos out = q.relative(open);
            fish.snapTo(out.getX() + .5, out.getY(), out.getZ() + .5, this.random.nextFloat() * 360, 0);
            fish.setTarget(t);
            fish.addTag(PlataMember.SUMMONED_TAG);
            level.addFreshEntity(fish);
            this.summoned.add(fish.getUUID());
            woke++;
        }
    }

    // -- hitting ------------------------------------------------------------------------------------------

    private void faceTo(@Nullable Entity t, float maxTurn) {
        if (t == null) return;
        float want = (float) (Mth.atan2(t.getZ() - this.getZ(), t.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90F;
        float yaw = Mth.approachDegrees(this.getYRot(), want, maxTurn);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.getLookControl().setLookAt(t, 30, 30);
    }

    /** Fair game: players (not in creative), and whatever fights her. Never her children, never villagers. */
    private boolean victim(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof PlataMember || e instanceof SilverMother || e instanceof Villager || e instanceof Silverfish) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e == this.getTarget() || e instanceof Mob m && m.getTarget() == this;
    }

    private void area(ServerLevel level, Vec3 at, double radius, MotherHit hit, double knock, double lift) {
        AABB box = new AABB(at.x - radius, at.y - 1, at.z - radius, at.x + radius, at.y + 3.5, at.z + radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::victim)) {
            double dx = e.getX() - at.x, dz = e.getZ() - at.z;
            if (dx * dx + dz * dz > radius * radius) continue;
            if (!e.hurtServer(level, this.damageSources().mobAttack(this), this.damageOf(hit))) continue;
            Vec3 push = new Vec3(dx, 0, dz);
            push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(knock);
            e.push(push.x, lift, push.z);
            e.needsSync = true;
        }
    }

    /** A ring of cracking floor round a point. */
    private void ring(ServerLevel level, Vec3 at, double radius, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Mth.TWO_PI / n + this.moveTicks * .1, r = radius * (.4 + .6 * this.random.nextDouble());
            double x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r;
            BlockPos g = PlataMember.groundAt(level, x, at.y, z);
            if (g == null) continue;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(g)), x, g.getY() + 1.05, z, 2, .1, 0, .1, .1);
        }
    }

    private void dust(ServerLevel level, int count, double spread, double speed) {
        BlockState under = level.getBlockState(BlockPos.containing(this.getX(), this.getY() - .5, this.getZ()));
        if (under.isAir() || !under.getFluidState().isEmpty()) under = Blocks.STONE.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), this.getX(), this.getY() + .3, this.getZ(), count, spread, .2, spread, speed);
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    private void summon(ServerLevel level, int n, double radius, @Nullable LivingEntity t) {
        this.summoned.removeIf(id -> !(level.getEntity(id) instanceof Silverfish s) || !s.isAlive());
        n = Math.min(n, MAX_SUMMONED - this.summoned.size());
        for (int i = 0; i < n; i++) {
            double a = this.random.nextDouble() * Mth.TWO_PI, r = radius * (.5 + .5 * this.random.nextDouble());
            double x = this.getX() + Math.cos(a) * r, z = this.getZ() + Math.sin(a) * r;
            BlockPos g = PlataMember.groundAt(level, x, this.getY(), z);
            if (g == null) continue;
            Silverfish s = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (s == null) continue;
            s.snapTo(x, g.getY() + 1, z, this.random.nextFloat() * 360, 0);
            s.setTarget(t);
            s.addTag(PlataMember.SUMMONED_TAG);
            level.addFreshEntity(s);
            this.summoned.add(s.getUUID());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(g)), x, g.getY() + 1.1, z, 14, .25, .1, .25, .15);
        }
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

    // -- speech ------------------------------------------------------------------------------------------

    /** Say a line from a pool to everyone near; {@code cooldown} keeps her from chattering. */
    public void say(ServerLevel level, String pool, int cooldown) {
        if (cooldown > 0 && this.sayCd > 0) return;
        this.sayCd = Math.max(this.sayCd, cooldown);
        Player p = this.getTarget() instanceof Player t ? t : level.getNearestPlayer(this, 24);
        String text = PlataLines.pick(pool, null, this.random, p == null ? null : p.getName().getString(), this.title());
        text = Mothers.fill(text, p);
        for (ServerPlayer near : level.players()) {
            if (near.distanceToSqr(this) < 48 * 48) PlataNet.say(near, this, this.title(), TITLE, text, "");
        }
    }

    private void card(ServerLevel level, String title, String line) {
        for (ServerPlayer near : level.players()) {
            if (near.distanceToSqr(this) < 48 * 48) PlataNet.say(near, this, this.title(), TITLE, line, title);
        }
    }

    // -- being hit -----------------------------------------------------------------------------------------

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.isInvulnerableTo(level, source);
        if (this.move == MotherMove.DORMANT || this.move == MotherMove.RISE || this.move == MotherMove.SHED || this.move.hidden()
                || this.move == MotherMove.BURROW && this.moveTicks >= 14) return true;
        if (source.getEntity() instanceof PlataMember || source.getEntity() instanceof Silverfish || source.getEntity() == this) return true;
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isDeadOrDying()) return false;
        if (this.move == MotherMove.DORMANT && source.getEntity() instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()) {
            this.awaken(p);
            return false;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) amount *= .6F;      // arrows skip off her shell
        if (this.stuck()) amount *= 1.5F;                                 // and her guard is down after a Crush
        boolean hurt = super.hurtServer(level, source, amount);
        if (!hurt || this.isDeadOrDying()) return hurt;
        if (source.getEntity() instanceof ServerPlayer p) this.fighters.add(p.getUUID());
        if (amount >= this.getMaxHealth() * .06F) this.say(level, "boss.hurt", 100);
        // enough damage in four seconds rocks her back (now and then; never mid-blow)
        if (this.tickCount - this.poiseSince > 80) { this.poise = 0; this.poiseSince = this.tickCount; }
        this.poise += this.stuck() ? amount * 2 : amount;
        boolean between = this.move == MotherMove.STANCE || this.move == MotherMove.BROOD || this.move == MotherMove.RAIN
                || this.move == MotherMove.KEEN && this.moveTicks < KEEN_AT - 4 || this.stuck();
        if (this.poise >= Balance.poise(this.getMaxHealth()) && this.staggerCd == 0 && between) {
            this.poise = 0;
            this.staggerCd = 300;
            this.getNavigation().stop();
            this.start(MotherMove.STAGGER);
            this.sound(SoundEvents.ANVIL_LAND, .6F, .5F);
        }
        float left = this.getHealth() / this.getMaxHealth();
        if (this.phase == 1 && left < Balance.SHED_AT && this.move != MotherMove.SHED) this.shed(level, 2);
        else if (this.phase == 2 && left < Balance.LAST_AT && this.move != MotherMove.SHED) this.shed(level, 3);
        return true;
    }

    private void shed(ServerLevel level, int next) {
        this.pendingPhase = next;
        this.getNavigation().stop();
        this.rain.clear();
        if (this.move.hidden()) this.surface(level, false);
        this.start(MotherMove.SHED);
        this.say(level, next >= 3 ? "boss.last" : "boss.shed", 0);
    }

    // -- the end -------------------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        boolean first = !this.isRemoved() && !this.dead;
        if (first && source.getEntity() instanceof ServerPlayer p) this.fighters.add(p.getUUID());
        super.die(source);
        if (first && this.level() instanceof ServerLevel level) {
            this.start(MotherMove.DEFEAT);
            this.say(level, "boss.defeat", 0);
            this.dismissSummoned(level);
            this.rain.clear();
            if (this.bar != null) this.bar.removeAllPlayers();
            Mothers.fallen(level, this, this.fighters);
        }
    }

    /** She sinks to her knees and curls up where she slept; then a burst of silver dust, and she's gone. */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            if (this.deathTime % 6 == 0 && this.deathTime < 48) this.dust(level, 10, .6, .05);
            if (this.deathTime == 52) {
                double x = this.getX(), y = this.getY() + .8, z = this.getZ();
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), x, y, z, 160, .8, .8, .8, .3);
                level.sendParticles(ParticleTypes.POOF, x, y, z, 40, .7, .6, .7, .05);
                level.sendParticles(ParticleTypes.END_ROD, x, y + .5, z, 30, .5, 1, .5, .04);
                this.sound(SoundEvents.SILVERFISH_DEATH, 2F, .4F);
                this.sound(SoundEvents.DEEPSLATE_TILES_BREAK, 2F, .5F);
                this.sound(SoundEvents.BELL_RESONATE, 1.2F, .6F);
            }
            if (this.deathTime >= DEATH_TICKS) this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        this.spawnAtLocation(level, Mothers.crest(this.title()));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            if (this.bar != null) this.bar.removeAllPlayers();
            if (reason.shouldDestroy()) this.dismissSummoned(level);
        }
        super.remove(reason);
    }

    // -- the boss bar ------------------------------------------------------------------------------------

    public Component barName() {
        return Component.literal(this.title() + " · " + TITLE + " · Lv " + this.level);
    }

    private void updateBar(ServerLevel level) {
        if (this.bar == null) {
            this.bar = new ServerBossEvent(this.getUUID(), this.barName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_6);
        }
        this.bar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0, 1));
        if (this.tickCount % 10 != 0) return;
        this.bar.setName(this.barName());
        boolean awake = this.move != MotherMove.DORMANT;
        for (ServerPlayer p : level.players()) {
            boolean near = awake && p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < 48 * 48;
            if (near) this.bar.addPlayer(p);
            else this.bar.removePlayer(p);
        }
    }

    // -- her aura -------------------------------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && !this.hidden() && this.deathTime == 0) this.aura();
    }

    /** Client: motes of cold silver light drifting up round her, thicker as the fight wears on her. */
    private void aura() {
        boolean asleep = this.move() == MotherMove.DORMANT;
        int phase = this.phase();
        float motes = asleep ? .18F : .4F + .3F * (phase - 1);
        while (motes > 0) {
            if (this.random.nextFloat() < motes) {
                this.level().addParticle(ParticleTypes.END_ROD, this.getRandomX(1.1), this.getY() + this.random.nextDouble() * 2.8,
                        this.getRandomZ(1.1), (this.random.nextDouble() - .5) * .02, .012 + this.random.nextDouble() * .02,
                        (this.random.nextDouble() - .5) * .02);
            }
            motes -= 1;
        }
        if (!asleep && this.random.nextFloat() < .2F * phase) {
            this.level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.CALCITE.defaultBlockState()),
                    this.getRandomX(.8), this.getY() + 2.2 + this.random.nextDouble() * .6, this.getRandomZ(.8), 0, 0, 0);
        }
    }

    // -- odds and ends -------------------------------------------------------------------------------------

    @Override public boolean isPickable() { return !this.hidden() && super.isPickable(); }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) { if (!this.hidden() && !this.dormant()) super.doPush(entity); }
    @Override public boolean isInWall() { return !this.hidden() && super.isInWall(); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
    @Override public boolean shouldShowName() { return false; }
    @Override protected @Nullable SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SILVERFISH_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SILVERFISH_DEATH; }
    @Override public float getVoicePitch() { return .45F; }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("MotherName", this.name);
        if (this.home != null) out.store("Home", BlockPos.CODEC, this.home);
        out.putFloat("HomeYaw", this.homeYaw);
        out.putInt("PlataLevel", this.level);
        out.putInt("Phase", this.phase);
        out.putBoolean("Configured", this.configured);
        out.putBoolean("Asleep", this.move == MotherMove.DORMANT);
        for (MotherHit h : MotherHit.values()) out.putFloat("Damage" + h.name(), this.damage[h.ordinal()]);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.name = in.getStringOr("MotherName", "");
        this.home = in.read("Home", BlockPos.CODEC).orElse(null);
        this.homeYaw = in.getFloatOr("HomeYaw", 0);
        this.level = in.getIntOr("PlataLevel", 30);
        this.configured = in.getBooleanOr("Configured", false);
        for (MotherHit h : MotherHit.values()) this.damage[h.ordinal()] = in.getFloatOr("Damage" + h.name(), 6);
        this.phase = in.getIntOr("Phase", 1);
        this.entityData.set(DATA_PHASE, this.phase);
        this.start(in.getBooleanOr("Asleep", true) ? MotherMove.DORMANT : MotherMove.STANCE);
    }
}
