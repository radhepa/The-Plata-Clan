package dev.plataclan.block;

import java.util.Set;
import java.util.function.Function;

import dev.plataclan.PlataClan;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The hideouts' two blocks: the silver husk a Plata sleeps in, and the invisible mark where a lookout waits. */
public final class PlataBlocks {
    private PlataBlocks() {}

    public static final SilverHuskBlock SILVER_HUSK = register("silver_husk", SilverHuskBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(1.5F, 3.0F).sound(SoundType.DEEPSLATE_TILES).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    public static final LookoutBlock LOOKOUT = register("lookout", LookoutBlock::new, BlockBehaviour.Properties.of()
            .noCollision().noOcclusion().noLootTable().strength(-1.0F, 3600000.0F).replaceable().noTerrainParticles()
            .pushReaction(PushReaction.POPPED));

    public static final BlockEntityType<SilverHuskBlockEntity> SILVER_HUSK_ENTITY = entity("silver_husk", SilverHuskBlockEntity::new, SILVER_HUSK);
    public static final BlockEntityType<LookoutBlockEntity> LOOKOUT_ENTITY = entity("lookout", LookoutBlockEntity::new, LOOKOUT);

    public static final Item SILVER_HUSK_ITEM = Registry.register(BuiltInRegistries.ITEM, PlataClan.id("silver_husk"), new BlockItem(SILVER_HUSK,
            new Item.Properties().setId(ResourceKey.create(Registries.ITEM, PlataClan.id("silver_husk"))).useBlockDescriptionPrefix()));

    private static <B extends Block> B register(String id, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PlataClan.id(id));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
    }

    private static <T extends BlockEntity> BlockEntityType<T> entity(String id, BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PlataClan.id(id), new BlockEntityType<>(factory, Set.of(block)));
    }

    public static void register() {}
}
