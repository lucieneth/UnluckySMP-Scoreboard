package unlucky.scoreboard;

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
 * are resent every few ticks with a shifted phase, which animates the colors.
 */
public final class SidebarUpdater {
	private static final String OBJECTIVE_NAME = "unlucky_sidebar";
	private static final int MAX_LINES = 15;

	private record BoardState(ServerGamePacketListenerImpl connection, int generation, List<String> lines, boolean[] animated) {
	}

	private static final Map<UUID, BoardState> STATES = new HashMap<>();
	private static int generation;

	private SidebarUpdater() {
	}

	/** Called from the MinecraftServer tick mixin. */
	public static void tick(MinecraftServer server) {
		ScoreboardConfig config = UnluckyScoreboard.CONFIG;
		int tickCount = server.getTickCount();
		boolean statsTick = config.update_interval_ticks >= 1 && tickCount % config.update_interval_ticks == 0;
		boolean animTick = tickCount % Math.max(1, config.animation_frame_ticks) == 0;
		if (!statsTick && !animTick) {
			return;
		}
		float phase = phase(tickCount, config);
		if (statsTick) {
			refreshBoards(server, config, phase);
		}
		if (animTick) {
			animateBoards(server, config, phase);
		}
	}

	/** Makes the next stats tick resend every sidebar from scratch (used by /sidebar reload). */
	public static void invalidateAll() {
		generation++;
	}

	/** Recomputes stats and line contents, sending full boards or diffs as needed. */
	private static void refreshBoards(MinecraftServer server, ScoreboardConfig config, float phase) {
		StatsManager.ensureInitialized(server);
		StatsManager.refreshOnline(server);

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
			List<String> lines = renderLines(config, player.getScoreboardName(), StatsManager.get(player.getUUID()), top, online, max);
			BoardState state = STATES.get(player.getUUID());
			boolean current = state != null && state.connection() == player.connection && state.generation() == generation;
			if (current) {
				sendDiff(player, state.lines(), lines, phase);
			} else {
				// Same connection means the client still shows the old board (config reload).
				boolean removeFirst = state != null && state.connection() == player.connection;
				sendFull(player, config, lines, removeFirst, phase);
			}
			boolean[] animated = new boolean[lines.size()];
			for (int i = 0; i < lines.size(); i++) {
				animated[i] = ScoreboardConfig.isAnimated(lines.get(i));
			}
			STATES.put(player.getUUID(), new BoardState(player.connection, generation, lines, animated));
		}
	}

	/** Resends only wave-gradient lines (and title) with the current phase. */
	private static void animateBoards(MinecraftServer server, ScoreboardConfig config, float phase) {
		boolean titleAnimated = ScoreboardConfig.isAnimated(config.title);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			BoardState state = STATES.get(player.getUUID());
			if (state == null || state.connection() != player.connection || state.generation() != generation) {
				continue;
			}
			if (titleAnimated) {
				player.connection.send(new ClientboundSetObjectivePacket(buildObjective(config, phase), ClientboundSetObjectivePacket.METHOD_CHANGE));
			}
			for (int i = 0; i < state.lines().size(); i++) {
				if (state.animated()[i]) {
					player.connection.send(scorePacket(state.lines(), i, phase));
				}
			}
		}
	}

	static List<String> renderLines(ScoreboardConfig config, String playerName, StatsManager.PlayerStats stats,
			List<StatsManager.TopEntry> top, String online, String max) {
		List<String> out = new ArrayList<>();
		for (String template : config.lines) {
			if (template.trim().equals("%top_playtime%")) {
				for (int i = 0; i < top.size(); i++) {
					StatsManager.TopEntry entry = top.get(i);
					out.add(config.top_playtime_entry
							.replace("%rank%", String.valueOf(i + 1))
							.replace("%name%", entry.name())
							.replace("%playtime%", formatPlaytime(entry.playtimeTicks())));
				}
				continue;
			}
			out.add(template
					.replace("%player%", playerName)
					.replace("%kills%", formatCount(stats.kills()))
					.replace("%deaths%", formatCount(stats.deaths()))
					.replace("%mined%", formatCount(stats.mined()))
					.replace("%playtime%", formatPlaytime(stats.playtimeTicks()))
					.replace("%online%", online)
					.replace("%max%", max));
		}
		return out.size() > MAX_LINES ? new ArrayList<>(out.subList(0, MAX_LINES)) : out;
	}

	private static Objective buildObjective(ScoreboardConfig config, float phase) {
		return new Objective(new Scoreboard(), OBJECTIVE_NAME, ObjectiveCriteria.DUMMY,
				ScoreboardConfig.parseLine(config.title, phase), ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
	}

	private static void sendFull(ServerPlayer player, ScoreboardConfig config, List<String> lines, boolean removeFirst, float phase) {
		Objective objective = buildObjective(config, phase);
		if (removeFirst) {
			player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE));
		}
		player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
		player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
		for (int i = 0; i < lines.size(); i++) {
			player.connection.send(scorePacket(lines, i, phase));
		}
	}

	private static void sendDiff(ServerPlayer player, List<String> old, List<String> lines, float phase) {
		if (old.size() != lines.size()) {
			// Scores shift when the line count changes, so resend everything.
			for (int i = lines.size(); i < old.size(); i++) {
				player.connection.send(new ClientboundResetScorePacket(owner(i), OBJECTIVE_NAME));
			}
			for (int i = 0; i < lines.size(); i++) {
				player.connection.send(scorePacket(lines, i, phase));
			}
			return;
		}
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).equals(old.get(i))) {
				player.connection.send(scorePacket(lines, i, phase));
			}
		}
	}

	private static ClientboundSetScorePacket scorePacket(List<String> lines, int index, float phase) {
		return new ClientboundSetScorePacket(owner(index), OBJECTIVE_NAME, lines.size() - index,
				Optional.of(ScoreboardConfig.parseLine(lines.get(index), phase)), Optional.empty());
	}

	private static float phase(int tickCount, ScoreboardConfig config) {
		int period = Math.max(2, config.animation_period_ticks);
		return (tickCount % period) / (float) period;
	}

	private static String owner(int index) {
		return "usb_line_" + index;
	}

	private static String formatCount(long value) {
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
