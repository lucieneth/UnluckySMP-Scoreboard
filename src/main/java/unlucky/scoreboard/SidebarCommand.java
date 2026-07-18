package unlucky.scoreboard;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class SidebarCommand {
	private SidebarCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("sidebar")
				.executes(ctx -> {
					ctx.getSource().sendSuccess(SidebarCommand::info, false);
					return 1;
				})
				.then(Commands.literal("preview")
						.executes(ctx -> {
							ctx.getSource().sendSuccess(() -> preview(ctx.getSource().getServer(),
									ctx.getSource().getEntity() instanceof ServerPlayer player ? player : null), false);
							return 1;
						}))
				.then(Commands.literal("top")
						.executes(ctx -> {
							ctx.getSource().sendSuccess(SidebarCommand::topPlaytime, false);
							return 1;
						}))
				.then(Commands.literal("reload")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> {
							UnluckyScoreboard.CONFIG = ScoreboardConfig.load();
							StatsManager.rescan(ctx.getSource().getServer());
							SidebarUpdater.invalidateAll();
							ctx.getSource().sendSuccess(
									() -> Component.literal("Sidebar config reloaded.").withStyle(ChatFormatting.GREEN), true);
							return 1;
						})));
	}

	/** Renders the current board (title + lines) into chat so config edits can be checked. */
	private static Component preview(MinecraftServer server, ServerPlayer player) {
		StatsManager.ensureInitialized(server);
		ScoreboardConfig config = UnluckyScoreboard.CONFIG;
		String name = player != null ? player.getScoreboardName() : "Player";
		StatsManager.PlayerStats stats = player != null ? StatsManager.get(player.getUUID()) : StatsManager.PlayerStats.ZERO;
		int topCount = Math.max(1, Math.min(10, config.top_playtime_count));
		List<String> lines = SidebarUpdater.renderLines(config, name, stats,
				StatsManager.topPlaytime(topCount),
				String.valueOf(server.getPlayerCount()),
				String.valueOf(server.getPlayerList().getMaxPlayers()));
		MutableComponent out = Component.empty().append(ScoreboardConfig.parseLine(config.title));
		for (String line : lines) {
			out.append(Component.literal("\n")).append(ScoreboardConfig.parseLine(line));
		}
		return out;
	}

	private static Component topPlaytime() {
		MutableComponent out = Component.literal("Top playtime").withStyle(ChatFormatting.GOLD);
		int rank = 1;
		for (StatsManager.TopEntry entry : StatsManager.topPlaytime(10)) {
			out.append(Component.literal("\n#" + rank++ + " ").withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(entry.name()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" " + SidebarUpdater.formatPlaytime(entry.playtimeTicks())).withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	private static Component info() {
		String version = FabricLoader.getInstance().getModContainer(UnluckyScoreboard.MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
		return Component.literal("Unlucky Scoreboard v" + version).withStyle(ChatFormatting.GOLD)
				.append(Component.literal("\nStats sidebar for the Unlucky SMP.").withStyle(ChatFormatting.WHITE))
				.append(Component.literal("\nTracking stats for ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(String.valueOf(StatsManager.trackedPlayers())).withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" players.").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("\nMade by ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("Lucien").withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" & ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("Claude").withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal("\nConfig: ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("config/unlucky-scoreboard.json").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" — apply changes with ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("/sidebar reload").withStyle(ChatFormatting.YELLOW));
	}
}
