package com.github.brainage04.procedural_dungeon.enchantment;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Who a dungeon enchantment's area effects leave alone: the wielder, anyone the wielder is allied with (scoreboard
 * teammates, tamed pets), and players the wielder cannot harm (a shared team without friendly fire, or PvP disabled).
 */
public final class DungeonAllies {
    /**
     * The wielder of the enchantment explosion currently detonating. Explosions resolve synchronously, so a
     * thread-local set around the effect is visible to {@code ServerExplosion} while it picks its victims.
     */
    private static final ThreadLocal<LivingEntity> BLAST_OWNER = new ThreadLocal<>();

    private DungeonAllies() {}

    public static boolean spares(LivingEntity owner, Entity target) {
        if (target == owner || target.isAlliedTo(owner) || owner.isAlliedTo(target)) {
            return true;
        }
        return owner instanceof Player attacker && target instanceof Player victim && !attacker.canHarmPlayer(victim);
    }

    public static void beginBlast(@Nullable LivingEntity owner) {
        BLAST_OWNER.set(owner);
    }

    public static void endBlast() {
        BLAST_OWNER.remove();
    }

    public static boolean sparedByCurrentBlast(Entity target) {
        LivingEntity owner = BLAST_OWNER.get();
        return owner != null && spares(owner, target);
    }
}
