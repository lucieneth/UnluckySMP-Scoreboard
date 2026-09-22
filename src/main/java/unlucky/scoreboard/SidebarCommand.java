package unlucky.scoreboard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
							ctx.getSource().sendSuccess(() -> top(StatBoard.PLAYTIME), false);
							return 1;
						})
						.then(Commands.argument("board", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(StatBoard.ids(), builder))
								.executes(ctx -> {
									String id = StringArgumentType.getString(ctx, "board");
									StatBoard board = StatBoard.byId(id);
									if (board == null) {
										ctx.getSource().sendFailure(Component.literal("Unknown board \"" + id + "\". Try /sidebar boards."));
										return 0;
									}
									ctx.getSource().sendSuccess(() -> top(board), false);
									return 1;
								})))
				.then(Commands.literal("boards")
						.executes(ctx -> {
							ctx.getSource().sendSuccess(SidebarCommand::boards, false);
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
		String ping = String.valueOf(player != null ? player.connection.latency() : 0);
		SidebarUpdater.Rendered rendered = SidebarUpdater.renderLines(config, name, ping, stats,
				StatsManager.topPlaytime(topCount),
				String.valueOf(server.getPlayerCount()),
				String.valueOf(server.getPlayerList().getMaxPlayers()));
		// Whole lines, never mid-typewriter — the point is to check the config —
		// but at the live animation phase so waves and caps look like they do in game.
		float ph = SidebarUpdater.livePhase(server, config);
		MutableComponent out = Component.empty().append(ScoreboardConfig.parseLine(config.title, ph));
		for (String line : rendered.lines()) {
			out.append(Component.literal("\n")).append(ScoreboardConfig.parseLine(line, ph));
		}
		return out;
	}

	private static Component top(StatBoard board) {
		MutableComponent out = Component.literal("Top " + board.label()).withStyle(ChatFormatting.GOLD);
		List<StatsManager.TopEntry> entries = StatsManager.top(board, 10);
		if (entries.isEmpty()) {
			return out.append(Component.literal("\nNobody has any yet.").withStyle(ChatFormatting.GRAY));
		}
		int rank = 1;
		for (StatsManager.TopEntry entry : entries) {
			out.append(Component.literal("\n#" + rank++ + " ").withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(entry.name()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" " + board.format(entry.value())).withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	private static Component boards() {
		return Component.literal("Leaderboard boards").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("\n" + String.join(", ", StatBoard.ids())).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("\nList the ones you want in ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("leaderboard_boards").withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(", then ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("/sidebar reload").withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(".").withStyle(ChatFormatting.GRAY));
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
