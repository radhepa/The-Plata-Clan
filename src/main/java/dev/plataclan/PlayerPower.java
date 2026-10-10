package dev.plataclan;

import java.lang.reflect.Method;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Sizes up a player the way the clan does at the reveal. The level is the Village Friends RPG
 * character level when that add-on is installed (read by reflection, so it stays optional), and
 * otherwise a power level estimated from gear ({@link Balance#estimateLevel}).
 */
public final class PlayerPower {
    private PlayerPower() {}

    private static final boolean RPG = FabricLoader.getInstance().isModLoaded("villagefriends_rpg");
    private static Method sheet, level, attrs, meleeBase;
    private static boolean broken;

    public static boolean rpgInstalled() { return RPG && !broken; }

    /** The player's level, 1–100. */
    public static int level(ServerPlayer p) {
        int rpg = rpgLevel(p);
        if (rpg > 0) return Balance.clampLevel(rpg);
        return Balance.estimateLevel((float) p.getAttributeValue(Attributes.ARMOR), (float) p.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                rawMelee(p), p.getMaxHealth(), p.experienceLevel);
    }

    /** Everything the balance needs, measured against {@code against} (for Sharpness, Smite and Protection). */
    public static Balance.Foe read(ServerPlayer p, LivingEntity against) {
        ServerLevel level = (ServerLevel) p.level();
        float melee = rawMelee(p);
        try {
            melee = EnchantmentHelper.modifyDamage(level, p.getMainHandItem(), against, p.damageSources().playerAttack(p), melee);
        } catch (RuntimeException ignored) {
            // an odd modded weapon: the attribute value will do
        }
        melee *= rpgMeleeMultiplier(p);
        float protection = 0;
        try {
            protection = EnchantmentHelper.getDamageProtection(level, p, against.damageSources().mobAttack(against));
        } catch (RuntimeException ignored) {
        }
        return new Balance.Foe(level(p), p.getMaxHealth(), (float) p.getAttributeValue(Attributes.ARMOR),
                (float) p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), protection, melee);
    }

    private static float rawMelee(Player p) { return (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE); }

    /** The RPG character level, or -1 without the RPG. */
    public static int rpgLevel(Player p) {
        Object s = rpgSheet(p);
        if (s == null) return -1;
        try {
            return (int) level.invoke(s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return -1;
        }
    }

    /** The RPG scales a young character's melee down (75% at level 1) and Strength up. */
    private static float rpgMeleeMultiplier(Player p) {
        Object s = rpgSheet(p);
        if (s == null) return 1;
        try {
            double base = (double) meleeBase.invoke(null, (int) level.invoke(s));
            Object a = attrs.invoke(s);
            int strength = a instanceof Map<?, ?> m && m.get("strength") instanceof Integer i ? i : 0;
            return (float) (base + .01 * strength);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return 1;
        }
    }

    private static Object rpgSheet(Player p) {
        if (!rpgInstalled()) return null;
        try {
            if (sheet == null) {
                Class<?> rpg = Class.forName("dev.villagefriends.rpg.Rpg");
                sheet = rpg.getMethod("sheet", Player.class);
                Class<?> sheetType = sheet.getReturnType();
                level = sheetType.getMethod("level");
                attrs = sheetType.getMethod("attrs");
                meleeBase = Class.forName("dev.villagefriends.rpg.Balance").getMethod("meleeBase", int.class);
            }
            return sheet.invoke(null, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    private static void fail(Exception e) {
        if (!broken) PlataClan.LOG.warn("Couldn't read Village Friends RPG levels, estimating from gear instead: {}", e.toString());
        broken = true;
    }
}
