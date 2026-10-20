package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.lock.DungeonBossKeys;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /clear} deletes items from a player's inventory; a Boss Key it deletes is destroyed.
 */
@Mixin(Inventory.class)
public class BossKeyInventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Unique
    private Set<UUID> proceduralDungeon$keysBeforeClear = Set.of();

    @Inject(method = "clearOrCountMatchingItems", at = @At("HEAD"))
    private void proceduralDungeon$noteKeys(Predicate<ItemStack> predicate, int maxCount, Container craftSlots, CallbackInfoReturnable<Integer> cir) {
        // A max count of 0 only counts.
        this.proceduralDungeon$keysBeforeClear = maxCount != 0 && this.player.level() instanceof ServerLevel
                ? DungeonBossKeys.boundIds((Inventory) (Object) this)
                : Set.of();
    }

    @Inject(method = "clearOrCountMatchingItems", at = @At("RETURN"))
    private void proceduralDungeon$reissueClearedKeys(Predicate<ItemStack> predicate, int maxCount, Container craftSlots, CallbackInfoReturnable<Integer> cir) {
        if (!this.proceduralDungeon$keysBeforeClear.isEmpty() && this.player.level() instanceof ServerLevel level) {
            DungeonBossKeys.deletedFrom(level.getServer(), this.proceduralDungeon$keysBeforeClear, (Inventory) (Object) this);
        }
        this.proceduralDungeon$keysBeforeClear = Set.of();
    }
}
