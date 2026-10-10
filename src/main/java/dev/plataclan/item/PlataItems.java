package dev.plataclan.item;

import java.util.Map;
import java.util.function.Function;

import dev.plataclan.PlataClan;
import dev.plataclan.block.PlataBlocks;
import dev.plataclan.entity.PlataEntities;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.entity.BannerPattern;

/** Silver Scales, the thrown shard, the Plata Fang knife, the Plata Mask and a spawn egg per rank. */
public final class PlataItems {
    private PlataItems() {}

    public static final TagKey<Item> REPAIRS_MASK = TagKey.create(Registries.ITEM, PlataClan.id("repairs_plata_mask"));
    public static final ResourceKey<EquipmentAsset> MASK_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, PlataClan.id("plata_mask"));
    /** A face plate: no more than leather on the head, but it fools the clan. */
    public static final ArmorMaterial MASK_MATERIAL = new ArmorMaterial(15, Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 0,
            ArmorType.LEGGINGS, 0, ArmorType.BOOTS, 0, ArmorType.BODY, 0), 12, SoundEvents.ARMOR_EQUIP_CHAIN, 0, 0, REPAIRS_MASK, MASK_ASSET);

    public static final Item SILVER_SCALE = register("silver_scale", Item::new, new Item.Properties());
    public static final Item SILVER_SHARD = register("silver_shard", Item::new, new Item.Properties());
    /** A reverse-grip knife: just under an iron sword's bite, twice as quick. */
    public static final Item PLATA_FANG = register("plata_fang", Item::new, new Item.Properties().sword(ToolMaterial.IRON, 2.0F, -2.0F));
    public static final Item PLATA_MASK = register("plata_mask", Item::new,
            new Item.Properties().humanoidArmor(MASK_MATERIAL, ArmorType.HELMET).rarity(Rarity.UNCOMMON));
    public static final Item SCUTTLER_EGG = register("plata_scuttler_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(PlataEntities.SCUTTLER));
    public static final Item SHARDCASTER_EGG = register("plata_shardcaster_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(PlataEntities.SHARDCASTER));
    public static final Item WARDEN_EGG = register("plata_warden_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(PlataEntities.WARDEN));
    public static final TagKey<BannerPattern> MARK_PATTERN = TagKey.create(Registries.BANNER_PATTERN, PlataClan.id("pattern_item/plata_mark"));
    /** The clan's mark for a loom: found in hideouts. */
    public static final Item PLATA_MARK_PATTERN = register("plata_mark_banner_pattern", Item::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).delayedComponent(DataComponents.PROVIDES_BANNER_PATTERNS, lookup -> lookup.getOrThrow(MARK_PATTERN)));

    private static Item register(String id, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PlataClan.id(id));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
    }

    /** Someone wearing the clan's face. Disguised Plata take them for kin. */
    public static boolean wearsMask(Player p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(PLATA_MASK);
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(out -> {
            out.accept(SILVER_SCALE); out.accept(SILVER_SHARD); out.accept(PLATA_MARK_PATTERN);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> out.accept(PlataBlocks.SILVER_HUSK_ITEM));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(out -> { out.accept(PLATA_FANG); out.accept(PLATA_MASK); });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(out -> {
            out.accept(SCUTTLER_EGG); out.accept(SHARDCASTER_EGG); out.accept(WARDEN_EGG);
        });
    }
}
