package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.reward.DungeonSalvage;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds {@link DungeonSalvage} to the grindstone.
 */
@Mixin(GrindstoneMenu.class)
public class GrindstoneMenuMixin {
    @Shadow
    @Final
    private Container repairSlots;

    @Inject(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("TAIL")
    )
    private void proceduralDungeon$wrapSlots(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        DungeonSalvage.wrapGrindstoneSlots(((AbstractContainerMenu) (Object) this).slots, this.repairSlots, access);
    }

    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void proceduralDungeon$salvage(ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir) {
        DungeonSalvage.grind(input, additional).ifPresent(grind -> cir.setReturnValue(grind.result()));
    }
}
