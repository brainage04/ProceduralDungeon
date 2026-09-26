package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.reward.RelicEssence;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets an anvil carry an enchantment above its vanilla maximum, such as a dungeon reward's Sharpness VI, onto another
 * item. The cap becomes the highest level already on either input, so combining two over-max items never climbs further.
 * Also applies a {@link RelicEssence} placed in the right-hand slot.
 */
@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {
    @Shadow
    @Final
    private DataSlot cost;

    @Shadow
    private int repairItemCountCost;

    // NeoForge moves the vanilla body of createResult into createResultInternal.
    @WrapOperation(
            method = {"createResult", "createResultInternal"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I")
    )
    private int proceduralDungeon$keepOverMaxLevels(Enchantment enchantment, Operation<Integer> original) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        return Math.max(
                original.call(enchantment),
                Math.max(levelOn(menu.getSlot(0).getItem(), enchantment), levelOn(menu.getSlot(1).getItem(), enchantment))
        );
    }

    private static int levelOn(ItemStack stack, Enchantment enchantment) {
        int level = 0;
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            if (entry.getKey().value() == enchantment) {
                level = entry.getIntValue();
            }
        }
        return level;
    }

    // Vanilla returns early, with no result, for anything that is not a book or a matching repair item.
    @Inject(method = {"createResult", "createResultInternal"}, at = @At("RETURN"))
    private void proceduralDungeon$applyEssence(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        RelicEssence.applyTo(menu.getSlot(0).getItem(), menu.getSlot(1).getItem()).ifPresent(result -> {
            menu.getSlot(2).set(result);
            this.cost.set(RelicEssence.APPLY_COST);
            this.repairItemCountCost = 0;
        });
    }
}
