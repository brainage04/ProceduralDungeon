package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.lock.DungeonBossKeys;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creative players can delete a Boss Key from the creative screen; the slot updates it sends are where that shows.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleSetCreativeModeSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER)
    )
    private void proceduralDungeon$watchCreativeKeys(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (!this.player.hasInfiniteMaterials()) {
            return;
        }
        int slot = packet.slotNum();
        ItemStack previous = slot >= 1 && slot < this.player.inventoryMenu.slots.size()
                ? this.player.inventoryMenu.getSlot(slot).getItem().copy()
                : ItemStack.EMPTY;
        DungeonBossKeys.onCreativeSlot(this.player, previous, packet.itemStack());
    }
}
