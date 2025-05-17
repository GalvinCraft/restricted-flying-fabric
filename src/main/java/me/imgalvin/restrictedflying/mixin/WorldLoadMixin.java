package me.imgalvin.restrictedflying.mixin;

import me.imgalvin.restrictedflying.RestrictedFlying;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class WorldLoadMixin {
    @Inject(at = @At("HEAD"), method = "loadWorld")
    private void init(CallbackInfo info) {
        RestrictedFlying.loadConfig(MinecraftServer.class.cast(this).getSavePath(WorldSavePath.ROOT).resolve("allowed_flight.txt"));
    }
}