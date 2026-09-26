package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.enchantment.DungeonSoulbound;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    /**
     * A respawn after death (not an End exit, which keeps everything) takes back the soulbound items the dead player kept.
     */
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void proceduralDungeon$restoreSoulbound(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
        if (!restoreAll) {
            DungeonSoulbound.transfer(oldPlayer.getInventory(), ((ServerPlayer) (Object) this).getInventory());
        }
    }
}
