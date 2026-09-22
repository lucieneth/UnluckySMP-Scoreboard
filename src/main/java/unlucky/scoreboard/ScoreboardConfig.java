package unlucky.scoreboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScoreboardConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("unlucky-scoreboard.json");

	/** Written into the generated config file; see HEADER for the full placeholder list. */
	private static final String HEADER = String.join("\n",
			"  // ─── Unlucky Scoreboard ────────────────────────────────────────────",
			"  // Placeholders — usable in \"title\" and in any entry of \"lines\":",
			"  //   %player%        name of the player looking at the sidebar",
			"  //   %ping%          their latency, in milliseconds",
			"  //   %kills%         mob kills + player kills",
			"  //   %deaths%        deaths",
			"  //   %mined%         every block they have ever mined",
			"  //   %placed%        every block they have ever placed",
			"  //   %playtime%      total playtime, e.g. \"2d 5h 13m\"",
			"  //   %online%        players currently online",
			"  //   %max%           player slots on the server",
			"  //   %board%         heading of the leaderboard being shown right now",
			"  //   %leaderboard%   on a line of its own: the cycling leaderboard",
			"  //   %top_playtime%  on a line of its own: a fixed playtime leaderboard",
			"  //",
			"  // ─── Cycling leaderboard ───────────────────────────────────────────",
			"  // \"leaderboard_boards\" lists what it rotates through. Each entry is a",
			"  // board id, optionally followed by \":\" and your own heading, e.g.",
			"  // \"ancient_debris:ɴᴇᴛʜᴇʀɪᴛᴇ\". Run /sidebar boards in game for the list:",
			"  //   playtime  kills  pvp_kills  deaths  mined  placed",
			"  //   diamonds  ancient_debris  emeralds  gold  iron  crafted",
			"  //   distance  damage_dealt  damage_taken  fish_caught",
			"  //   animals_bred  trades  raids_won  jumps",
			"  //",
			"  // \"leaderboard_title\" is the heading row (drop it with \"\"), and",
			"  // \"leaderboard_entry\" one player row: %rank% %name% %value% %board%.",
			"  // Each board holds for \"leaderboard_switch_seconds\", then types itself",
			"  // out backwards and the next one types in. \"leaderboard_type_ticks\" and",
			"  // \"leaderboard_erase_ticks\" are ticks per character (1 = 20 chars/sec),",
			"  // \"leaderboard_animate\" false swaps boards instantly instead, and",
			"  // \"leaderboard_keep_width\" holds the row width steady while it types.",
			"  //",
			"  // Placeholders for \"top_playtime_entry\" (one line per leaderboard player):",
			"  //   %rank%          position, 1 and up",
			"  //   %name%          player name",
			"  //   %playtime%      their playtime",
			"  //",
			"  // Colors:    &0-&9 &a-&f, or hex &#ff5ee6",
			"  // Formats:   &l bold  &m strikethrough  &n underline  &o italic  &r reset",
			"  // Gradient:  <grad:#a855f7:#5ee6ff>text</grad>        (2 or more stops)",
			"  // Animated:  <wave:#ff5ee6:#a855f7:#5ee6ff>text</wave>  (colors flow along)",
			"  // Caps wave: <caps>ᴛᴏᴘ %board%</caps>   one letter at a time is raised to",
			"  //            a full-height capital and walks along, then the text rests.",
			"  //            Nest it with the others: <caps><grad:...>text</grad></caps>.",
			"  //            \"animation_period_ticks\" sets how long one pass takes.",
			"  //",
			"  // The sidebar shows at most 15 lines once the leaderboard has expanded.",
			"  // Apply changes in game with /sidebar reload — check them with /sidebar preview.",
			"  // ───────────────────────────────────────────────────────────────────",
			"");

	public String title = "&l<wave:#ff5ee6:#a855f7:#5ee6ff:#a855f7>ᴜɴʟᴜᴄᴋʏꜱᴍᴘ</wave>";
	public List<String> lines = List.of(
			"&8&m---------------------",
			"&f&l%player%",
			"&8| &#d97dffᴋɪʟʟꜱ: &f%kills%",
			"&8| &#d97dffᴅᴇᴀᴛʜꜱ: &f%deaths%",
			"&8| &#d97dffᴍɪɴᴇᴅ: &f%mined%",
			"&8| &#d97dffᴘʟᴀʏᴛɪᴍᴇ: &f%playtime%",
			"%leaderboard%",
			"&fꜱᴇʀᴠᴇʀ &8[&a%online%&7/&f%max%&8] &8&m---",
			"&8| &#5ee6ffᴍᴄ.ᴜɴʟᴜᴄᴋʏ.ʟɪꜰᴇ",
			"&8&m---------------------");
	// Boards the sidebar rotates through: "id", or "id:your own heading".
	public List<String> leaderboard_boards = List.of("playtime", "kills", "mined", "diamonds", "ancient_debris");
	public String leaderboard_title = "<grad:#a855f7:#5ee6ff>ᴛᴏᴘ %board%</grad> &8&m--";
	// One line per top player; placeholders: %rank%, %name%, %value%, %board%
	public String leaderboard_entry = "&8| &e#%rank% &f%name% &7%value%";
	public int leaderboard_count = 3;
	public int leaderboard_switch_seconds = 15;
	// Typewriter: ticks per character on the way in and on the way out.
	public boolean leaderboard_animate = true;
	public int leaderboard_type_ticks = 1;
	public int leaderboard_erase_ticks = 1;
	public boolean leaderboard_keep_width = true;
	// One line per top player; placeholders: %rank%, %name%, %playtime%
	public String top_playtime_entry = "&8| &e#%rank% &f%name% &7%playtime%";
	public int top_playtime_count = 3;
	public int update_interval_ticks = 20;
	// How often animated gradients advance (in ticks) and how long one full loop takes.
	public int animation_frame_ticks = 3;
	public int animation_period_ticks = 60;

	/** One board in the rotation, with the heading it is shown under. */
	public record BoardSpec(StatBoard board, String label) {
	}

	private transient List<BoardSpec> resolvedBoards;

	/** The rotation, resolved once per config load; empty when no line uses %leaderboard%. */
	public List<BoardSpec> boards() {
		if (resolvedBoards == null) {
			resolvedBoards = resolveBoards();
		}
		return resolvedBoards;
	}

	private List<BoardSpec> resolveBoards() {
		if (lines == null || lines.stream().noneMatch(line -> line.trim().equals("%leaderboard%"))) {
			return List.of();
		}
		List<BoardSpec> specs = new ArrayList<>();
		for (String entry : leaderboard_boards == null ? List.<String>of() : leaderboard_boards) {
			int colon = entry.indexOf(':');
			String id = (colon < 0 ? entry : entry.substring(0, colon)).trim();
			String label = colon < 0 ? "" : entry.substring(colon + 1).trim();
			StatBoard board = StatBoard.byId(id);
			if (board == null) {
				UnluckyScoreboard.LOGGER.warn("Unknown leaderboard board \"{}\" — /sidebar boards lists the valid ids.", id);
				continue;
			}
			specs.add(new BoardSpec(board, label.isEmpty() ? board.label() : label));
		}
		if (specs.isEmpty()) {
			specs.add(new BoardSpec(StatBoard.PLAYTIME, StatBoard.PLAYTIME.label()));
		}
		return List.copyOf(specs);
	}

	public static ScoreboardConfig load() {
		try {
			if (Files.exists(PATH)) {
				try (var reader = Files.newBufferedReader(PATH)) {
					ScoreboardConfig config = GSON.fromJson(reader, ScoreboardConfig.class);
					if (config != null) {
						return config;
					}
				}
			}
		} catch (IOException | JsonParseException e) {
			UnluckyScoreboard.LOGGER.error("Failed to read {}, keeping defaults (file left untouched)", PATH, e);
			return new ScoreboardConfig();
		}
		ScoreboardConfig config = new ScoreboardConfig();
		config.save();
		return config;
	}

	public void save() {
		try {
			// Gson reads leniently, so the // reference block survives reloads.
			String json = GSON.toJson(this);
			int brace = json.indexOf('{');
			String withHeader = brace < 0 ? json
					: json.substring(0, brace + 1) + "\n" + HEADER + json.substring(brace + 1);
			Files.writeString(PATH, withHeader);
		} catch (IOException e) {
			UnluckyScoreboard.LOGGER.error("Failed to write {}", PATH, e);
		}
	}

	private static final Pattern GRADIENT_TAG = Pattern.compile("<(grad|wave):(#[0-9a-fA-F]{6}(?::#[0-9a-fA-F]{6})+)>(.*?)</\\1>");
	private static final Pattern CAPS_TAG = Pattern.compile("<caps>(.*?)</caps>");
	/** Any of our own tags, so character sweeps can step over the markup. */
	private static final Pattern INLINE_TAG = Pattern.compile("</?(?:grad|wave|caps)(?::[^>]*)?>");

	/** Small-caps glyphs back to the capitals they stand for; X and Q have no small-cap form. */
	private static final Map<Character, Character> SMALL_CAPS = Map.ofEntries(
			Map.entry('ᴀ', 'A'), Map.entry('ʙ', 'B'), Map.entry('ᴄ', 'C'),
			Map.entry('ᴅ', 'D'), Map.entry('ᴇ', 'E'), Map.entry('ꜰ', 'F'),
			Map.entry('ɢ', 'G'), Map.entry('ʜ', 'H'), Map.entry('ɪ', 'I'),
			Map.entry('ᴊ', 'J'), Map.entry('ᴋ', 'K'), Map.entry('ʟ', 'L'),
			Map.entry('ᴍ', 'M'), Map.entry('ɴ', 'N'), Map.entry('ᴏ', 'O'),
			Map.entry('ᴘ', 'P'), Map.entry('ǫ', 'Q'), Map.entry('ʀ', 'R'),
			Map.entry('ꜱ', 'S'), Map.entry('ᴛ', 'T'), Map.entry('ᴜ', 'U'),
			Map.entry('ᴠ', 'V'), Map.entry('ᴡ', 'W'), Map.entry('ʏ', 'Y'),
			Map.entry('ᴢ', 'Z'));

	/** True if the line animates by itself and needs periodic resending. */
	public static boolean isAnimated(String line) {
		if (line.contains("<caps>")) {
			return true;
		}
		Matcher matcher = GRADIENT_TAG.matcher(line);
		while (matcher.find()) {
			if (matcher.group(1).equals("wave")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Raises one character of each &lt;caps&gt; span to its full-height capital and
	 * walks that capital along, which reads as a bump travelling through small-caps
	 * text. Runs before any colors are parsed, so it composes with grad and wave, and
	 * swaps characters one for one so the typewriter's measurements still hold.
	 */
	private static String capsSweep(String line, float phase) {
		if (!line.contains("<caps>")) {
			return line;
		}
		StringBuilder out = new StringBuilder();
		Matcher matcher = CAPS_TAG.matcher(line);
		int last = 0;
		while (matcher.find()) {
			out.append(line, last, matcher.start()).append(sweepSpan(matcher.group(1), phase));
			last = matcher.end();
		}
		return out.append(line, last, line.length()).toString();
	}

	private static String sweepSpan(String span, float phase) {
		List<Integer> letters = new ArrayList<>();
		for (int at : visiblePositions(span)) {
			// Spaces and punctuation have no capital, so skip them rather than
			// spending a frame of the sweep on a character that cannot change.
			if (raise(span.charAt(at)) != span.charAt(at)) {
				letters.add(at);
			}
		}
		if (letters.isEmpty()) {
			return span;
		}
		// One pass over the first half of the loop, plain text for the second half.
		int step = (int) (phase * letters.size() * 2);
		if (step >= letters.size()) {
			return span;
		}
		int at = letters.get(step);
		return span.substring(0, at) + raise(span.charAt(at)) + span.substring(at + 1);
	}

	private static char raise(char c) {
		Character capital = SMALL_CAPS.get(c);
		return capital != null ? capital : Character.toUpperCase(c);
	}

	/** Indices of the characters a player actually sees, skipping & codes and tags. */
	private static List<Integer> visiblePositions(String text) {
		List<Integer> out = new ArrayList<>();
		Matcher tag = INLINE_TAG.matcher(text);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				if (text.charAt(i + 1) == '#' && i + 7 < text.length() && parseHex(text, i + 2) >= 0) {
					i += 7;
					continue;
				}
				if (ChatFormatting.getByCode(text.charAt(i + 1)) != null) {
					i++;
					continue;
				}
			}
			if (c == '<' && tag.region(i, text.length()).lookingAt()) {
				i = tag.end() - 1;
				continue;
			}
			out.add(i);
		}
		return out;
	}

	public static Component parseLine(String line) {
		return parseLine(line, 0f);
	}

	/** Parses & codes, &#RRGGBB hex colors and grad/wave tags; phase in [0,1) shifts wave gradients. */
	public static Component parseLine(String line, float phase) {
		return parse(line, phase, Integer.MAX_VALUE);
	}

	/**
	 * The line with only its first {@code visible} characters shown, as the
	 * typewriter reveals and erases it. Gradients still spread over the whole
	 * line, so every character keeps the color it will end up with. When
	 * {@code keepWidth} is set the hidden characters become spaces, which stops
	 * the sidebar from resizing around the animation.
	 */
	public static Component parseClipped(String line, float phase, int visible, int fullLength, boolean keepWidth) {
		int shown = Math.max(0, Math.min(visible, fullLength));
		MutableComponent out = parse(line, phase, shown);
		if (keepWidth && shown < fullLength) {
			out.append(Component.literal(" ".repeat(fullLength - shown)));
		}
		return out;
	}

	/** How many characters a line actually draws, ignoring & codes and gradient tags. */
	public static int visibleLength(String line) {
		return parse(line, 0f, Integer.MAX_VALUE).getString().length();
	}

	private static MutableComponent parse(String line, float phase, int limit) {
		line = capsSweep(line, phase);
		MutableComponent out = Component.empty();
		ParseState state = new ParseState(limit);
		Matcher matcher = GRADIENT_TAG.matcher(line);
		int last = 0;
		while (matcher.find()) {
			appendLegacy(out, line.substring(last, matcher.start()), state);
			appendGradient(out, matcher.group(3), parseStops(matcher.group(2)), matcher.group(1).equals("wave"), phase, state);
			last = matcher.end();
		}
		appendLegacy(out, line.substring(last), state);
		return out;
	}

	/** Carries active formatting across plain and gradient segments of one line. */
	private static final class ParseState {
		final List<ChatFormatting> active = new ArrayList<>(List.of(ChatFormatting.WHITE));
		TextColor hex;
		final StringBuilder buf = new StringBuilder();
		/** Characters still allowed through before the line is cut short. */
		int remaining;

		ParseState(int remaining) {
			this.remaining = remaining;
		}

		void flushInto(MutableComponent out) {
			if (buf.length() > 0) {
				MutableComponent piece = Component.literal(buf.toString()).withStyle(active.toArray(ChatFormatting[]::new));
				if (hex != null) {
					piece = piece.withColor(hex);
				}
				out.append(piece);
				buf.setLength(0);
			}
		}
	}

	private static void appendLegacy(MutableComponent out, String text, ParseState state) {
		if (state.remaining <= 0) {
			return;
		}
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '&' && i + 1 < text.length()) {
				char code = text.charAt(i + 1);
				if (code == '#' && i + 7 < text.length()) {
					int rgb = parseHex(text, i + 2);
					if (rgb >= 0) {
						state.flushInto(out);
						state.active.clear();
						state.hex = TextColor.fromRgb(rgb);
						i += 7;
						continue;
					}
				}
				ChatFormatting next = ChatFormatting.getByCode(code);
				if (next != null) {
					state.flushInto(out);
					// colors are the first 16 enum constants (BLACK..WHITE)
					if (next.ordinal() <= ChatFormatting.WHITE.ordinal() || next == ChatFormatting.RESET) {
						state.active.clear();
						state.active.add(next == ChatFormatting.RESET ? ChatFormatting.WHITE : next);
						state.hex = null;
					} else {
						state.active.add(next);
					}
					i++;
					continue;
				}
			}
			if (state.remaining <= 0) {
				break;
			}
			state.buf.append(text.charAt(i));
			state.remaining--;
		}
		state.flushInto(out);
	}

	/** Colors each visible character; & format codes inside the span apply, color codes are ignored. */
	private static void appendGradient(MutableComponent out, String content, int[] stops, boolean wave, float phase, ParseState state) {
		if (state.remaining <= 0) {
			return;
		}
		List<ChatFormatting> formats = new ArrayList<>(state.active);
		int visible = 0;
		for (int i = 0; i < content.length(); i++) {
			if (content.charAt(i) == '&' && i + 1 < content.length() && ChatFormatting.getByCode(content.charAt(i + 1)) != null) {
				i++;
				continue;
			}
			visible++;
		}
		int k = 0;
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			if (c == '&' && i + 1 < content.length()) {
				ChatFormatting next = ChatFormatting.getByCode(content.charAt(i + 1));
				if (next != null) {
					if (next.ordinal() > ChatFormatting.WHITE.ordinal() && next != ChatFormatting.RESET) {
						formats.add(next);
					}
					i++;
					continue;
				}
			}
			if (state.remaining <= 0) {
				break;
			}
			float t = wave
					? frac((float) k / Math.max(1, visible) + phase)
					: (visible <= 1 ? 0f : (float) k / (visible - 1));
			out.append(Component.literal(String.valueOf(c))
					.withStyle(formats.toArray(ChatFormatting[]::new))
					.withColor(sample(stops, t, wave)));
			k++;
			state.remaining--;
		}
	}

	private static int[] parseStops(String spec) {
		String[] parts = spec.split(":");
		int[] stops = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			stops[i] = Integer.parseInt(parts[i].substring(1), 16);
		}
		return stops;
	}

	/** Linear interpolation over the stops; wave gradients wrap around so the loop is seamless. */
	private static int sample(int[] stops, float t, boolean cyclic) {
		int count = stops.length;
		float scaled;
		int i0;
		int i1;
		if (cyclic) {
			scaled = t * count;
			i0 = ((int) scaled) % count;
			i1 = (i0 + 1) % count;
		} else {
			scaled = t * (count - 1);
			i0 = (int) scaled;
			i1 = Math.min(i0 + 1, count - 1);
		}
		float f = scaled - (int) scaled;
		int r = lerp(stops[i0] >> 16 & 0xFF, stops[i1] >> 16 & 0xFF, f);
		int g = lerp(stops[i0] >> 8 & 0xFF, stops[i1] >> 8 & 0xFF, f);
		int b = lerp(stops[i0] & 0xFF, stops[i1] & 0xFF, f);
		return r << 16 | g << 8 | b;
	}

	private static int lerp(int a, int b, float f) {
		return Math.round(a + (b - a) * f);
	}

	private static float frac(float value) {
		return value - (float) Math.floor(value);
	}

	private static int parseHex(String line, int start) {
		try {
			return Integer.parseInt(line.substring(start, start + 6), 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
