package unlucky.scoreboard;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Renders the sidebar per player with packets only — no objective ever touches
 * the real server scoreboard. Line texts ride on per-score display names with a
 * blank number format, so no red numbers show. Lines containing a wave gradient
 * are resent every few ticks with a shifted phase, which animates the colors,
 * and the leaderboard rows are resent as the typewriter reveals or erases them.
 */
public final class SidebarUpdater {
	private static final String OBJECTIVE_NAME = "unlucky_sidebar";
	private static final int MAX_LINES = 15;

	/** A player's board as the client currently has it, so we can send diffs. */
	private record BoardState(ServerGamePacketListenerImpl connection, int generation, List<String> lines,
			boolean[] animated, boolean[] typed, int[] length) {
	}

	/** Lines of one render pass, flagged with the ones the typewriter owns. */
	record Rendered(List<String> lines, boolean[] typed, int[] length) {
	}

	private static final Map<UUID, BoardState> STATES = new HashMap<>();
	private static final LeaderboardCycle CYCLE = new LeaderboardCycle();
	private static int generation;

	/** The leaderboard block is the same for everyone, so it is built once per tick, not per player. */
	private static List<String> boardRows = List.of();
	private static int[] boardRowLengths = new int[0];
	private static int boardMaxLength;
	private static String boardLabel = "";
	private static boolean boardRowsStale = true;

	private SidebarUpdater() {
	}

	/** Called from the MinecraftServer tick mixin. */
	public static void tick(MinecraftServer server) {
		ScoreboardConfig config = UnluckyScoreboard.CONFIG;
		int tickCount = server.getTickCount();
		boolean statsTick = config.update_interval_ticks >= 1 && tickCount % config.update_interval_ticks == 0;
		boolean animTick = tickCount % Math.max(1, config.animation_frame_ticks) == 0;

		StatsManager.ensureInitialized(server);
		if (statsTick) {
			StatsManager.refreshOnline(server);
		}
		List<ScoreboardConfig.BoardSpec> boards = config.boards();
		if (statsTick || boardRowsStale) {
			rebuildBoardRows(config, boards);
		}
		// Advances against the board on screen, so the one being erased keeps its own width.
		CYCLE.advance(config, boardMaxLength, boards.size());
		if (CYCLE.boardChanged()) {
			rebuildBoardRows(config, boards);
		}
		boolean typeTick = CYCLE.revealChanged() || CYCLE.boardChanged();
		if (!statsTick && !animTick && !typeTick) {
			return;
		}
		float phase = phase(tickCount, config);
		if (statsTick || CYCLE.boardChanged()) {
			refreshBoards(server, config, phase);
		}
		if (animTick || typeTick) {
			animateBoards(server, config, phase, animTick, typeTick);
		}
	}

	/** Makes the next stats tick resend every sidebar from scratch (used by /sidebar reload). */
	public static void invalidateAll() {
		generation++;
		boardRowsStale = true;
		CYCLE.reset();
	}

	/** The heading of the board currently on screen, for %board%. */
	static String boardLabel() {
		return boardLabel;
	}

	/** Expands the current board into its heading and player rows, and measures them. */
	private static void rebuildBoardRows(ScoreboardConfig config, List<ScoreboardConfig.BoardSpec> boards) {
		boardRowsStale = false;
		if (boards.isEmpty()) {
			boardRows = List.of();
			boardRowLengths = new int[0];
			boardMaxLength = 0;
			boardLabel = "";
			return;
		}
		ScoreboardConfig.BoardSpec spec = boards.get(Math.floorMod(CYCLE.index(), boards.size()));
		boardLabel = spec.label();
		List<StatsManager.TopEntry> top = StatsManager.top(spec.board(), Math.max(1, Math.min(10, config.leaderboard_count)));
		List<String> rows = new ArrayList<>();
		if (!config.leaderboard_title.isEmpty()) {
			rows.add(config.leaderboard_title.replace("%board%", spec.label()));
		}
		for (int i = 0; i < top.size(); i++) {
			StatsManager.TopEntry entry = top.get(i);
			rows.add(config.leaderboard_entry
					.replace("%rank%", String.valueOf(i + 1))
					.replace("%name%", entry.name())
					.replace("%value%", spec.board().format(entry.value()))
					.replace("%board%", spec.label()));
		}
		boardRows = rows;
		boardRowLengths = new int[rows.size()];
		int max = 0;
		for (int i = 0; i < rows.size(); i++) {
			boardRowLengths[i] = ScoreboardConfig.visibleLength(rows.get(i));
			max = Math.max(max, boardRowLengths[i]);
		}
		boardMaxLength = max;
	}

	/** Recomputes line contents, sending full boards or diffs as needed. */
	private static void refreshBoards(MinecraftServer server, ScoreboardConfig config, float phase) {
		int topCount = Math.max(1, Math.min(10, config.top_playtime_count));
		List<StatsManager.TopEntry> top = StatsManager.topPlaytime(topCount);
		String online = String.valueOf(server.getPlayerCount());
		String max = String.valueOf(server.getPlayerList().getMaxPlayers());

		List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
		Set<UUID> onlineIds = new HashSet<>();
		for (ServerPlayer player : players) {
			onlineIds.add(player.getUUID());
		}
		STATES.keySet().retainAll(onlineIds);

		for (ServerPlayer player : players) {
			Rendered rendered = renderLines(config, player.getScoreboardName(), String.valueOf(player.connection.latency()),
					StatsManager.get(player.getUUID()), top, online, max);
			boolean[] animated = new boolean[rendered.lines().size()];
			for (int i = 0; i < animated.length; i++) {
				animated[i] = ScoreboardConfig.isAnimated(rendered.lines().get(i));
			}
			BoardState state = STATES.get(player.getUUID());
			BoardState next = new BoardState(player.connection, generation, rendered.lines(), animated,
					rendered.typed(), rendered.length());
			boolean current = state != null && state.connection() == player.connection && state.generation() == generation;
			if (current) {
				sendDiff(player, config, state.lines(), next, phase);
			} else {
				// Same connection means the client still shows the old board (config reload).
				boolean removeFirst = state != null && state.connection() == player.connection;
				sendFull(player, config, next, removeFirst, phase);
			}
			STATES.put(player.getUUID(), next);
		}
	}

	/** Resends wave-gradient lines (and title) with the current phase, and leaderboard rows as they type. */
	private static void animateBoards(MinecraftServer server, ScoreboardConfig config, float phase,
			boolean animTick, boolean typeTick) {
		boolean titleAnimated = animTick && ScoreboardConfig.isAnimated(config.title);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			BoardState state = STATES.get(player.getUUID());
			if (state == null || state.connection() != player.connection || state.generation() != generation) {
				continue;
			}
			if (titleAnimated) {
				player.connection.send(new ClientboundSetObjectivePacket(buildObjective(config, phase), ClientboundSetObjectivePacket.METHOD_CHANGE));
			}
			for (int i = 0; i < state.lines().size(); i++) {
				if ((animTick && state.animated()[i]) || (typeTick && state.typed()[i])) {
					player.connection.send(scorePacket(state, i, config, phase));
				}
			}
		}
	}

	static Rendered renderLines(ScoreboardConfig config, String playerName, String ping, StatsManager.PlayerStats stats,
			List<StatsManager.TopEntry> top, String online, String max) {
		List<String> out = new ArrayList<>();
		List<Boolean> typed = new ArrayList<>();
		List<Integer> length = new ArrayList<>();
		for (String template : config.lines) {
			String trimmed = template.trim();
			if (trimmed.equals("%leaderboard%")) {
				for (int i = 0; i < boardRows.size(); i++) {
					out.add(boardRows.get(i));
					typed.add(true);
					length.add(boardRowLengths[i]);
				}
				continue;
			}
			if (trimmed.equals("%top_playtime%")) {
				for (int i = 0; i < top.size(); i++) {
					StatsManager.TopEntry entry = top.get(i);
					out.add(config.top_playtime_entry
							.replace("%rank%", String.valueOf(i + 1))
							.replace("%name%", entry.name())
							.replace("%playtime%", formatPlaytime(entry.value())));
					typed.add(false);
					length.add(0);
				}
				continue;
			}
			out.add(template
					.replace("%player%", playerName)
					.replace("%ping%", ping)
					.replace("%kills%", formatCount(stats.get(StatBoard.KILLS)))
					.replace("%deaths%", formatCount(stats.get(StatBoard.DEATHS)))
					.replace("%mined%", formatCount(stats.get(StatBoard.MINED)))
					.replace("%placed%", formatCount(stats.get(StatBoard.PLACED)))
					.replace("%playtime%", formatPlaytime(stats.get(StatBoard.PLAYTIME)))
					.replace("%board%", boardLabel)
					.replace("%online%", online)
					.replace("%max%", max));
			typed.add(false);
			length.add(0);
		}
		int count = Math.min(out.size(), MAX_LINES);
		boolean[] typedFlags = new boolean[count];
		int[] lengths = new int[count];
		for (int i = 0; i < count; i++) {
			typedFlags[i] = typed.get(i);
			lengths[i] = length.get(i);
		}
		return new Rendered(new ArrayList<>(out.subList(0, count)), typedFlags, lengths);
	}

	/** Where the wave and caps animations currently sit, so /sidebar preview matches the board. */
	static float livePhase(MinecraftServer server, ScoreboardConfig config) {
		return phase(server.getTickCount(), config);
	}

	private static Objective buildObjective(ScoreboardConfig config, float phase) {
		return new Objective(new Scoreboard(), OBJECTIVE_NAME, ObjectiveCriteria.DUMMY,
				ScoreboardConfig.parseLine(config.title, phase), ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
	}

	private static void sendFull(ServerPlayer player, ScoreboardConfig config, BoardState state, boolean removeFirst, float phase) {
		Objective objective = buildObjective(config, phase);
		if (removeFirst) {
			player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE));
		}
		player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
		player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
		for (int i = 0; i < state.lines().size(); i++) {
			player.connection.send(scorePacket(state, i, config, phase));
		}
	}

	private static void sendDiff(ServerPlayer player, ScoreboardConfig config, List<String> old, BoardState state, float phase) {
		List<String> lines = state.lines();
		if (old.size() != lines.size()) {
			// Scores shift when the line count changes, so resend everything.
			for (int i = lines.size(); i < old.size(); i++) {
				player.connection.send(new ClientboundResetScorePacket(owner(i), OBJECTIVE_NAME));
			}
			for (int i = 0; i < lines.size(); i++) {
				player.connection.send(scorePacket(state, i, config, phase));
			}
			return;
		}
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).equals(old.get(i))) {
				player.connection.send(scorePacket(state, i, config, phase));
			}
		}
	}

	private static ClientboundSetScorePacket scorePacket(BoardState state, int index, ScoreboardConfig config, float phase) {
		String line = state.lines().get(index);
		Component text = state.typed()[index]
				? ScoreboardConfig.parseClipped(line, phase, CYCLE.revealed(), state.length()[index], config.leaderboard_keep_width)
				: ScoreboardConfig.parseLine(line, phase);
		return new ClientboundSetScorePacket(owner(index), OBJECTIVE_NAME, state.lines().size() - index,
				Optional.of(text), Optional.empty());
	}

	private static float phase(int tickCount, ScoreboardConfig config) {
		int period = Math.max(2, config.animation_period_ticks);
		return (tickCount % period) / (float) period;
	}

	private static String owner(int index) {
		return "usb_line_" + index;
	}

	static String formatCount(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	static String formatPlaytime(long ticks) {
		long totalMinutes = ticks / (20 * 60);
		long days = totalMinutes / (60 * 24);
		long hours = totalMinutes % (60 * 24) / 60;
		long minutes = totalMinutes % 60;
		if (days > 0) {
			return days + "d " + hours + "h " + minutes + "m";
		}
		if (hours > 0) {
			return hours + "h " + minutes + "m";
		}
		return minutes + "m";
	}
}
