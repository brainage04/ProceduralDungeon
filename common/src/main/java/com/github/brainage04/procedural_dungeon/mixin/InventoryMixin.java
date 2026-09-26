package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.enchantment.DungeonSoulbound;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds soulbound items back from the death drop; they stay in the dead player's inventory until respawn.
 */
@Mixin(Inventory.class)
public class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Unique
    private Map<Integer, ItemStack> proceduralDungeon$soulbound = Map.of();

    @Inject(method = "dropAll", at = @At("HEAD"))
    private void proceduralDungeon$holdBackSoulbound(CallbackInfo ci) {
        if (this.player.isDeadOrDying()) {
            this.proceduralDungeon$soulbound = DungeonSoulbound.holdBack((Inventory) (Object) this);
        }
    }

    @Inject(method = "dropAll", at = @At("TAIL"))
    private void proceduralDungeon$putBackSoulbound(CallbackInfo ci) {
        DungeonSoulbound.putBack((Inventory) (Object) this, this.proceduralDungeon$soulbound);
        this.proceduralDungeon$soulbound = Map.of();
    }
}
