package dev.plataclan.entity;

import dev.plataclan.item.PlataItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A Shardcaster's thrown scale: flies nearly flat, stings, and slows whoever it hits for a moment. */
public class SilverShard extends ThrowableItemProjectile {
    private float damage = 3;

    public SilverShard(EntityType<? extends SilverShard> type, Level level) {
        super(type, level);
    }

    public SilverShard(ServerLevel level, LivingEntity thrower, float damage) {
        super(PlataEntities.SILVER_SHARD, thrower, level, new ItemStack(PlataItems.SILVER_SHARD));
        this.damage = damage;
    }

    @Override protected Item getDefaultItem() { return PlataItems.SILVER_SHARD; }
    @Override protected double getDefaultGravity() { return .012; }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (target instanceof PlataMember || target instanceof Silverfish || target instanceof Villager) return false;
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(this.level() instanceof ServerLevel level) || !(hit.getEntity() instanceof LivingEntity target)) return;
        LivingEntity owner = this.getOwner() instanceof LivingEntity l ? l : null;
        if (target.hurtServer(level, this.damageSources().mobProjectile(this, owner), this.damage)) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40), this.getOwner());
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, .5F, 1.8F);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(this.getItem())),
                    this.getX(), this.getY(), this.getZ(), 8, .1, .1, .1, .06);
            this.discard();
        }
    }
}
