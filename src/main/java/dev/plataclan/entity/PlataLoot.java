package dev.plataclan.entity;

import dev.plataclan.Balance;
import dev.plataclan.Rank;
import dev.plataclan.item.PlataItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What a beaten Plata leaves behind, by level (see Balance). */
public final class PlataLoot {
    private PlataLoot() {}

    static void drop(PlataMember m, ServerLevel level, Rank rank, int lvl, boolean killedByPlayer) {
        RandomSource r = m.getRandom();
        m.spawnAtLocation(level, new ItemStack(Items.EMERALD, Balance.emeralds(lvl, r.nextInt(3))));
        m.spawnAtLocation(level, new ItemStack(PlataItems.SILVER_SCALE, Balance.scales(lvl, r.nextInt(3))));
        if (!killedByPlayer) return;
        if (r.nextDouble() < Balance.fangChance(lvl)) m.spawnAtLocation(level, new ItemStack(PlataItems.PLATA_FANG));
        if (r.nextDouble() < Balance.maskChance(rank, lvl)) m.spawnAtLocation(level, new ItemStack(PlataItems.PLATA_MASK));
    }
}
