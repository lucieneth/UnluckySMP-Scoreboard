package unlucky.scoreboard.mixin;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unlucky.scoreboard.SidebarCommand;

@Mixin(Commands.class)
public class CommandsMixin {

	// Register /sidebar after vanilla commands are set up. Runs on every
	// dispatcher rebuild (server start and /reload), so it stays registered.
	@Inject(method = "<init>", at = @At("RETURN"))
	private void unluckyscoreboard$registerCommands(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo ci) {
		SidebarCommand.register(((Commands) (Object) this).getDispatcher());
	}
}
