# Unlucky Scoreboard

Server-side Fabric mod for the Unlucky SMP: a DonutSMP-style stats sidebar showing
each player's kills, deaths, blocks mined and playtime, plus a rotating
leaderboard and server info.

The mod tracks nothing itself — vanilla already records every statistic. Online
players are read from their live stat counters; everyone else is read from
`world/players/stats/<uuid>.json` (with a fallback to the pre-26.x `world/stats/`
location), so the numbers cover the whole life of the world, not just since the
mod was installed. The sidebar is sent per player with scoreboard packets and
never touches the real server scoreboard, so it won't fight datapacks or
`/scoreboard` commands.

Vanilla clients see it — nothing to install client-side.

## Config

`config/unlucky-scoreboard.json` (created on first run). The generated file
starts with a `//` comment block listing every placeholder and formatting code,
so the reference is always at hand — comments are preserved across reloads.

- `title` — sidebar title.
- `lines` — the sidebar, one string per line. `%leaderboard%` on its own line
  expands into the rotating leaderboard. Placeholders: `%player%`, `%ping%`,
  `%kills%`, `%deaths%`, `%mined%`, `%placed%`, `%playtime%`, `%online%`,
  `%max%`, `%board%`. (Vanilla has no blocks-placed statistic, so `%placed%`
  sums how often the player used a block item — which is what placing a block
  records.)
- `top_playtime_entry` / `top_playtime_count` — the older fixed playtime
  leaderboard, still available as `%top_playtime%` on its own line.
- `update_interval_ticks` — refresh rate (20 = once per second).

## Leaderboard

`%leaderboard%` cycles through the boards listed in `leaderboard_boards`. Each
one holds for `leaderboard_switch_seconds` (default 15), then types itself out
backwards a character at a time and the next one types back in.

- `leaderboard_boards` — the rotation. Each entry is a board id, optionally
  followed by `:` and your own heading, e.g. `"ancient_debris:ɴᴇᴛʜᴇʀɪᴛᴇ"`.
  Available ids (also listed by `/sidebar boards`):

  `playtime` `kills` `pvp_kills` `deaths` `mined` `placed` `diamonds`
  `ancient_debris` `emeralds` `gold` `iron` `crafted` `distance`
  `damage_dealt` `damage_taken` `fish_caught` `animals_bred` `trades`
  `raids_won` `jumps`

  `diamonds` counts diamond ore plus deepslate diamond ore, `gold` counts the
  nether variant too, and `ancient_debris` is what netherite actually comes
  from. `distance` covers every way of moving, not just walking.
- `leaderboard_title` — the heading row, where `%board%` is the current board's
  name. Set it to `""` for no heading.
- `leaderboard_entry` — one row per player: `%rank%`, `%name%`, `%value%`,
  `%board%`.
- `leaderboard_count` — rows per board, 1–10. Players sitting at zero are left
  out, so a board nobody has touched yet shows just its heading.
- `leaderboard_switch_seconds` — how long each board stays up. Default 15.
- `leaderboard_animate` — `false` swaps boards instantly with no typewriter.
- `leaderboard_type_ticks` / `leaderboard_erase_ticks` — ticks per character
  (1 = 20 characters a second). Raise them to slow the effect down.
- `leaderboard_keep_width` — pads the hidden part of each row with spaces so the
  sidebar keeps its width while typing instead of breathing in and out. Widths
  still shift a little because a space is narrower than an average character —
  for a sidebar that never moves, keep one static line wider than the widest
  leaderboard row.

Formatting: classic `&` codes (`&d`, `&l`, `&m`, ...) plus hex colors like
`&#ff5ee6`. The defaults use Minecraft's small-caps unicode glyphs
(ᴀʙᴄᴅᴇꜰɢ...) for labels — paste your own from any "minecraft small caps"
generator.

Gradients: `<grad:#a855f7:#5ee6ff>text</grad>` colors the text across any
number of `:`-separated stops. `<wave:...>text</wave>` is the animated
version — the colors flow through the text, like the big-server lobby boards.
`animation_frame_ticks` is the frame rate (3 = ~7 fps), `animation_period_ticks`
the loop length. `&` format codes like `&l` still work inside a gradient.

Caps wave: `<caps>ᴛᴏᴘ %board%</caps>` raises one letter at a time to its
full-height capital and walks it along the text, then leaves the text alone for
the rest of the loop. Small-caps glyphs sit at x-height, so the capital reads as
a bump travelling through the word:

```
Tᴏᴘ ᴘʟᴀʏᴛɪᴍᴇ → ᴛOᴘ ᴘʟᴀʏᴛɪᴍᴇ → ᴛᴏP ᴘʟᴀʏᴛɪᴍᴇ → ᴛᴏᴘ Pʟᴀʏᴛɪᴍᴇ → …
```

It runs before colors are parsed, so it nests with the others —
`<caps><grad:#a855f7:#5ee6ff>ᴛᴏᴘ %board%</grad></caps>`. One pass takes half of
`animation_period_ticks`, the text rests for the other half; spaces and
punctuation are stepped over. Works on plain lowercase text too.

The vanilla sidebar renders at most 15 lines; extra lines are dropped.

## Commands

- `/sidebar` — mod info.
- `/sidebar preview` — print the rendered board into chat to check config edits.
- `/sidebar boards` — list every leaderboard id.
- `/sidebar top [board]` — top-10 for a board in chat, playtime by default.
- `/sidebar reload` — reload the config and rescan stats files (op level 2).

## Building

`gradlew build` — the jar lands in `build/libs/`. Requires Java 25.
