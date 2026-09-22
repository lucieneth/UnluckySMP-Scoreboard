package unlucky.scoreboard;

import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/**
 * One leaderboard the sidebar can rank players by. Every board reads a vanilla
 * statistic — the mod records nothing itself — and knows how to format its own
 * numbers, so counts, durations, distances and damage all read naturally.
 */
public enum StatBoard {
	PLAYTIME("playtime", "ᴘʟᴀʏᴛɪᴍᴇ", Unit.TIME, custom(Stats.PLAY_TIME)),
	KILLS("kills", "ᴋɪʟʟꜱ", Unit.COUNT,
			counter -> counter.getValue(Stats.CUSTOM, Stats.MOB_KILLS) + counter.getValue(Stats.CUSTOM, Stats.PLAYER_KILLS)),
	PVP_KILLS("pvp_kills", "ᴘᴠᴘ ᴋɪʟʟꜱ", Unit.COUNT, custom(Stats.PLAYER_KILLS)),
	DEATHS("deaths", "ᴅᴇᴀᴛʜꜱ", Unit.COUNT, custom(Stats.DEATHS)),
	MINED("mined", "ʙʟᴏᴄᴋꜱ ᴍɪɴᴇᴅ", Unit.COUNT, StatBoard::totalMined),
	PLACED("placed", "ʙʟᴏᴄᴋꜱ ᴘʟᴀᴄᴇᴅ", Unit.COUNT, StatBoard::totalPlaced),
	DIAMONDS("diamonds", "ᴅɪᴀᴍᴏɴᴅꜱ ᴍɪɴᴇᴅ", Unit.COUNT, mined(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)),
	ANCIENT_DEBRIS("ancient_debris", "ᴀɴᴄɪᴇɴᴛ ᴅᴇʙʀɪꜱ", Unit.COUNT, mined(Blocks.ANCIENT_DEBRIS)),
	EMERALDS("emeralds", "ᴇᴍᴇʀᴀʟᴅꜱ ᴍɪɴᴇᴅ", Unit.COUNT, mined(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)),
	GOLD("gold", "ɢᴏʟᴅ ᴍɪɴᴇᴅ", Unit.COUNT, mined(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE)),
	IRON("iron", "ɪʀᴏɴ ᴍɪɴᴇᴅ", Unit.COUNT, mined(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE)),
	CRAFTED("crafted", "ɪᴛᴇᴍꜱ ᴄʀᴀꜰᴛᴇᴅ", Unit.COUNT, StatBoard::totalCrafted),
	DISTANCE("distance", "ᴅɪꜱᴛᴀɴᴄᴇ", Unit.DISTANCE, StatBoard::totalDistance),
	DAMAGE_DEALT("damage_dealt", "ᴅᴀᴍᴀɢᴇ ᴅᴇᴀʟᴛ", Unit.DAMAGE, custom(Stats.DAMAGE_DEALT)),
	DAMAGE_TAKEN("damage_taken", "ᴅᴀᴍᴀɢᴇ ᴛᴀᴋᴇɴ", Unit.DAMAGE, custom(Stats.DAMAGE_TAKEN)),
	FISH_CAUGHT("fish_caught", "ꜰɪꜱʜ ᴄᴀᴜɢʜᴛ", Unit.COUNT, custom(Stats.FISH_CAUGHT)),
	ANIMALS_BRED("animals_bred", "ᴀɴɪᴍᴀʟꜱ ʙʀᴇᴅ", Unit.COUNT, custom(Stats.ANIMALS_BRED)),
	TRADES("trades", "ᴠɪʟʟᴀɢᴇʀ ᴛʀᴀᴅᴇꜱ", Unit.COUNT, custom(Stats.TRADED_WITH_VILLAGER)),
	RAIDS_WON("raids_won", "ʀᴀɪᴅꜱ ᴡᴏɴ", Unit.COUNT, custom(Stats.RAID_WIN)),
	JUMPS("jumps", "ᴊᴜᴍᴘꜱ", Unit.COUNT, custom(Stats.JUMP));

	/** How a board's raw statistic turns into text. */
	private enum Unit {
		COUNT, TIME, DISTANCE, DAMAGE
	}

	/** Every way of covering ground, so "distance" is not just walking. */
	private static final List<Identifier> TRAVEL = List.of(
			Stats.WALK_ONE_CM, Stats.SPRINT_ONE_CM, Stats.CROUCH_ONE_CM, Stats.WALK_ON_WATER_ONE_CM,
			Stats.WALK_UNDER_WATER_ONE_CM, Stats.SWIM_ONE_CM, Stats.CLIMB_ONE_CM, Stats.FLY_ONE_CM,
			Stats.AVIATE_ONE_CM, Stats.BOAT_ONE_CM, Stats.MINECART_ONE_CM, Stats.HORSE_ONE_CM,
			Stats.PIG_ONE_CM, Stats.STRIDER_ONE_CM, Stats.HAPPY_GHAST_ONE_CM, Stats.NAUTILUS_ONE_CM);

	private static final Map<String, StatBoard> BY_ID = byId();
	private static final List<String> IDS = Stream.of(values()).map(StatBoard::id).toList();

	private final String id;
	private final String label;
	private final Unit unit;
	private final ToLongFunction<StatsCounter> reader;

	StatBoard(String id, String label, Unit unit, ToLongFunction<StatsCounter> reader) {
		this.id = id;
		this.label = label;
		this.unit = unit;
		this.reader = reader;
	}

	public String id() {
		return id;
	}

	/** Default heading, overridable per board in the config with "id:label". */
	public String label() {
		return label;
	}

	public long read(StatsCounter counter) {
		return reader.applyAsLong(counter);
	}

	public String format(long value) {
		return switch (unit) {
			case COUNT -> String.format(Locale.ROOT, "%,d", value);
			case TIME -> SidebarUpdater.formatPlaytime(value);
			case DISTANCE -> formatDistance(value);
			// Vanilla stores damage in tenths of a half-heart, same as /stats shows it.
			case DAMAGE -> String.format(Locale.ROOT, "%,.1f", value / 10.0);
		};
	}

	public static StatBoard byId(String id) {
		return BY_ID.get(id.toLowerCase(Locale.ROOT));
	}

	/** Every id, in the order they are declared above — Map.copyOf would scramble them. */
	public static List<String> ids() {
		return IDS;
	}

	private static Map<String, StatBoard> byId() {
		Map<String, StatBoard> map = new LinkedHashMap<>();
		for (StatBoard board : values()) {
			map.put(board.id, board);
		}
		return Map.copyOf(map);
	}

	private static String formatDistance(long centimetres) {
		long metres = centimetres / 100;
		if (metres >= 1000) {
			return String.format(Locale.ROOT, "%,.1fkm", metres / 1000.0);
		}
		return String.format(Locale.ROOT, "%,dm", metres);
	}

	private static ToLongFunction<StatsCounter> custom(Identifier stat) {
		return counter -> counter.getValue(Stats.CUSTOM, stat);
	}

	private static ToLongFunction<StatsCounter> mined(Block... blocks) {
		return counter -> {
			long total = 0;
			for (Block block : blocks) {
				total += counter.getValue(Stats.BLOCK_MINED, block);
			}
			return total;
		};
	}

	private static long totalMined(StatsCounter counter) {
		long total = 0;
		for (Stat<Block> stat : Stats.BLOCK_MINED) {
			total += counter.getValue(stat);
		}
		return total;
	}

	/**
	 * Vanilla has no "blocks placed" stat: placing a block counts as using its
	 * item, so sum item-use counts over block items only.
	 */
	private static long totalPlaced(StatsCounter counter) {
		long total = 0;
		for (Stat<Item> stat : Stats.ITEM_USED) {
			if (stat.getValue() instanceof BlockItem) {
				total += counter.getValue(stat);
			}
		}
		return total;
	}

	private static long totalCrafted(StatsCounter counter) {
		long total = 0;
		for (Stat<Item> stat : Stats.ITEM_CRAFTED) {
			total += counter.getValue(stat);
		}
		return total;
	}

	private static long totalDistance(StatsCounter counter) {
		long total = 0;
		for (Identifier stat : TRAVEL) {
			total += counter.getValue(Stats.CUSTOM, stat);
		}
		return total;
	}
}
