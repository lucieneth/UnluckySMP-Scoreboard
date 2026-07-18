package unlucky.scoreboard.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unlucky.scoreboard.SidebarUpdater;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void unluckyscoreboard$tick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
		SidebarUpdater.tick((MinecraftServer) (Object) this);
	}
}
