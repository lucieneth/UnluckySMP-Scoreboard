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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScoreboardConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("unlucky-scoreboard.json");

	// & codes: colors (&7 gray, &d pink, ...), formats (&l bold, &m strikethrough, &o italic, &r reset)
	// and hex colors (&#ff5ee6). Gradients: <grad:#a855f7:#5ee6ff>text</grad> (2+ stops);
	// animated gradients: <wave:...>text</wave> — the colors flow through the text.
	// The vanilla sidebar shows at most 15 lines after %top_playtime% expands.
	// Placeholders: %player%, %kills%, %deaths%, %mined%, %playtime%, %online%, %max%
	public String title = "&l<wave:#ff5ee6:#a855f7:#5ee6ff:#a855f7>ᴜɴʟᴜᴄᴋʏꜱᴍᴘ</wave>";
	public List<String> lines = List.of(
			"&8&m---------------------",
			"&f&l%player%",
			"&8| &#d97dffᴋɪʟʟꜱ: &f%kills%",
			"&8| &#d97dffᴅᴇᴀᴛʜꜱ: &f%deaths%",
			"&8| &#d97dffᴍɪɴᴇᴅ: &f%mined%",
			"&8| &#d97dffᴘʟᴀʏᴛɪᴍᴇ: &f%playtime%",
			"<grad:#a855f7:#5ee6ff>ᴛᴏᴘ ᴘʟᴀʏᴛɪᴍᴇ</grad> &8&m--------",
			"%top_playtime%",
			"&fꜱᴇʀᴠᴇʀ &8[&a%online%&7/&f%max%&8] &8&m---",
			"&8| &#5ee6ffᴍᴄ.ᴜɴʟᴜᴄᴋʏ.ʟɪꜰᴇ",
			"&8&m---------------------");
	// One line per top player; placeholders: %rank%, %name%, %playtime%
	public String top_playtime_entry = "&8| &e#%rank% &f%name% &7%playtime%";
	public int top_playtime_count = 3;
	public int update_interval_ticks = 20;
	// How often animated gradients advance (in ticks) and how long one full loop takes.
	public int animation_frame_ticks = 3;
	public int animation_period_ticks = 60;

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
			Files.writeString(PATH, GSON.toJson(this));
		} catch (IOException e) {
			UnluckyScoreboard.LOGGER.error("Failed to write {}", PATH, e);
		}
	}

	private static final Pattern GRADIENT_TAG = Pattern.compile("<(grad|wave):(#[0-9a-fA-F]{6}(?::#[0-9a-fA-F]{6})+)>(.*?)</\\1>");

	/** True if the line contains a &lt;wave&gt; gradient and needs periodic resending. */
	public static boolean isAnimated(String line) {
		Matcher matcher = GRADIENT_TAG.matcher(line);
		while (matcher.find()) {
			if (matcher.group(1).equals("wave")) {
				return true;
			}
		}
		return false;
	}

	public static Component parseLine(String line) {
		return parseLine(line, 0f);
	}

	/** Parses & codes, &#RRGGBB hex colors and grad/wave tags; phase in [0,1) shifts wave gradients. */
	public static Component parseLine(String line, float phase) {
		MutableComponent out = Component.empty();
		ParseState state = new ParseState();
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
			state.buf.append(text.charAt(i));
		}
		state.flushInto(out);
	}

	/** Colors each visible character; & format codes inside the span apply, color codes are ignored. */
	private static void appendGradient(MutableComponent out, String content, int[] stops, boolean wave, float phase, ParseState state) {
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
			float t = wave
					? frac((float) k / Math.max(1, visible) + phase)
					: (visible <= 1 ? 0f : (float) k / (visible - 1));
			out.append(Component.literal(String.valueOf(c))
					.withStyle(formats.toArray(ChatFormatting[]::new))
					.withColor(sample(stops, t, wave)));
			k++;
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
