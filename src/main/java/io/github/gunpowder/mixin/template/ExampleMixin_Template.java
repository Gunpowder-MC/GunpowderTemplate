package io.github.gunpowder.mixin.template;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ExampleMixin_Template {
    @Inject(method="runServer", at=@At("HEAD"))
    void hook(CallbackInfo ci) {
        System.out.println("This is a message from a gunpowder module!");
    }
}
