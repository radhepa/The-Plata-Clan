package dev.plataclan.entity;

import dev.plataclan.PlataClan;
import dev.plataclan.Rank;
import dev.plataclan.mother.SilverMother;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The three ranks (one entity type each, so stats and the RPG Bestiary can tell them apart), the thrown shard, and
 * the Silver Mother who sleeps in every Warren's vault.
 */
public final class PlataEntities {
    private PlataEntities() {}

    public static final EntityType<PlataMember> SCUTTLER = member("plata_scuttler");
    public static final EntityType<PlataMember> SHARDCASTER = member("plata_shardcaster");
    public static final EntityType<PlataMember> WARDEN = member("plata_warden");
    public static final EntityType<SilverShard> SILVER_SHARD = register("silver_shard",
            EntityType.Builder.<SilverShard>of(SilverShard::new, MobCategory.MISC).sized(.25F, .25F).clientTrackingRange(4).updateInterval(10));
    /** The Silver Mother: the same mesh as her children, half again as tall. */
    public static final EntityType<SilverMother> SILVER_MOTHER = register("silver_mother",
            EntityType.Builder.<SilverMother>of(SilverMother::new, MobCategory.MONSTER).sized(.9F, 2.7F).eyeHeight(2.43F).clientTrackingRange(10));

    private static EntityType<PlataMember> member(String id) {
        // The player's own mesh: same size, same eyes. They are people.
        return register(id, EntityType.Builder.<PlataMember>of(PlataMember::new, MobCategory.MONSTER)
                .sized(.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10));
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, PlataClan.id(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static EntityType<PlataMember> of(Rank rank) {
        return switch (rank) { case SCUTTLER -> SCUTTLER; case SHARDCASTER -> SHARDCASTER; case WARDEN -> WARDEN; };
    }

    public static Rank rankOf(EntityType<?> type) {
        if (type == SHARDCASTER) return Rank.SHARDCASTER;
        if (type == WARDEN) return Rank.WARDEN;
        return Rank.SCUTTLER;
    }

    public static void register() {
        for (Rank r : Rank.values()) FabricDefaultAttributeRegistry.register(of(r), PlataMember.createAttributes());
        FabricDefaultAttributeRegistry.register(SILVER_MOTHER, SilverMother.createAttributes());
    }
}
