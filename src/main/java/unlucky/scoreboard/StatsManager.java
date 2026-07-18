package unlucky.scoreboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Aggregates vanilla statistics for every player who ever joined: live counters
 * for online players, the world/stats/&lt;uuid&gt;.json files for everyone else.
 * The mod tracks nothing itself — vanilla already records every statistic.
 */
public final class StatsManager {
	public record PlayerStats(long kills, long deaths, long mined, long playtimeTicks) {
		public static final PlayerStats ZERO = new PlayerStats(0, 0, 0, 0);
	}

	public record TopEntry(String name, long playtimeTicks) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path NAMES_PATH = FabricLoader.getInstance().getConfigDir().resolve("unlucky-scoreboard-names.json");

	private static final Map<UUID, PlayerStats> STATS = new HashMap<>();
	private static final Map<UUID, String> NAMES = new HashMap<>();
	private static boolean initialized;
	private static boolean namesDirty;

	private StatsManager() {
	}

	/** Lazily initialized on the first server tick so the world is fully loaded. */
	public static void ensureInitialized(MinecraftServer server) {
		if (!initialized) {
			initialized = true;
			rescan(server);
		}
	}

	/** Full reload: name caches plus every stats file on disk. Used at startup and by /sidebar reload. */
	public static void rescan(MinecraftServer server) {
		STATS.clear();
		NAMES.clear();
		loadNamesFile();
		loadUserCache(server);
		int loaded = scanDir(server, server.getWorldPath(LevelResource.PLAYER_STATS_DIR));
		// Pre-26.x worlds kept stats in world/stats; players who never rejoined
		// after the upgrade may still only have a file there.
		loaded += scanDir(server, server.getWorldPath(new LevelResource("stats")));
		UnluckyScoreboard.LOGGER.info("Loaded statistics for {} players.", loaded);
	}

	private static int scanDir(MinecraftServer server, Path dir) {
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		int loaded = 0;
		try (var files = Files.list(dir)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				String fileName = file.getFileName().toString();
				if (!fileName.endsWith(".json")) {
					continue;
				}
				UUID id;
				try {
					id = UUID.fromString(fileName.substring(0, fileName.length() - 5));
				} catch (IllegalArgumentException e) {
					continue;
				}
				if (STATS.containsKey(id)) {
					continue;
				}
				try {
					// The vanilla counter parses (and datafixes) the file in its constructor.
					STATS.put(id, aggregate(new ServerStatsCounter(server, file)));
					loaded++;
				} catch (Exception e) {
					UnluckyScoreboard.LOGGER.warn("Skipping unreadable stats file {}", file, e);
				}
			}
		} catch (IOException e) {
			UnluckyScoreboard.LOGGER.error("Failed to scan stats directory {}", dir, e);
		}
		return loaded;
	}

	/** Overwrites cached aggregates with live counters; keeps the uuid→name cache current. */
	public static void refreshOnline(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			STATS.put(player.getUUID(), aggregate(player.getStats()));
			String name = player.getScoreboardName();
			if (!name.equals(NAMES.put(player.getUUID(), name))) {
				namesDirty = true;
			}
		}
		if (namesDirty) {
			namesDirty = false;
			saveNamesFile();
		}
	}

	public static PlayerStats get(UUID id) {
		return STATS.getOrDefault(id, PlayerStats.ZERO);
	}

	public static int trackedPlayers() {
		return STATS.size();
	}

	public static List<TopEntry> topPlaytime(int count) {
		return STATS.entrySet().stream()
				.sorted(Comparator.comparingLong((Map.Entry<UUID, PlayerStats> e) -> e.getValue().playtimeTicks()).reversed())
				.limit(count)
				.map(e -> new TopEntry(NAMES.getOrDefault(e.getKey(), e.getKey().toString().substring(0, 8)), e.getValue().playtimeTicks()))
				.toList();
	}

	private static PlayerStats aggregate(StatsCounter counter) {
		long mined = 0;
		for (Stat<Block> stat : Stats.BLOCK_MINED) {
			mined += counter.getValue(stat);
		}
		return new PlayerStats(
				counter.getValue(Stats.CUSTOM, Stats.MOB_KILLS) + counter.getValue(Stats.CUSTOM, Stats.PLAYER_KILLS),
				counter.getValue(Stats.CUSTOM, Stats.DEATHS),
				mined,
				counter.getValue(Stats.CUSTOM, Stats.PLAY_TIME));
	}

	private static void loadNamesFile() {
		if (!Files.isRegularFile(NAMES_PATH)) {
			return;
		}
		try (var reader = Files.newBufferedReader(NAMES_PATH)) {
			Map<String, String> map = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {
			}.getType());
			if (map != null) {
				map.forEach((id, name) -> {
					try {
						NAMES.put(UUID.fromString(id), name);
					} catch (IllegalArgumentException ignored) {
					}
				});
			}
		} catch (IOException | JsonParseException e) {
			UnluckyScoreboard.LOGGER.warn("Failed to read {}", NAMES_PATH, e);
		}
	}

	private static void saveNamesFile() {
		Map<String, String> map = new TreeMap<>();
		NAMES.forEach((id, name) -> map.put(id.toString(), name));
		try {
			Files.writeString(NAMES_PATH, GSON.toJson(map));
		} catch (IOException e) {
			UnluckyScoreboard.LOGGER.error("Failed to write {}", NAMES_PATH, e);
		}
	}

	/** Seeds names from the server's usercache.json; our own cache takes precedence. */
	private static void loadUserCache(MinecraftServer server) {
		Path path = server.getServerDirectory().resolve("usercache.json");
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (var reader = Files.newBufferedReader(path)) {
			for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
				JsonObject entry = element.getAsJsonObject();
				if (entry.has("uuid") && entry.has("name")) {
					try {
						NAMES.putIfAbsent(UUID.fromString(entry.get("uuid").getAsString()), entry.get("name").getAsString());
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
		} catch (Exception e) {
			UnluckyScoreboard.LOGGER.warn("Failed to read {}", path, e);
		}
	}
}
