# Unlucky Scoreboard

Server-side Fabric mod for the Unlucky SMP: a DonutSMP-style stats sidebar showing
each player's kills, deaths, blocks mined and playtime, plus a top-playtime
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

`config/unlucky-scoreboard.json` (created on first run):

- `title` — sidebar title.
- `lines` — the sidebar, one string per line. `%top_playtime%` on its own line
  expands into the leaderboard. Placeholders: `%player%`, `%kills%`, `%deaths%`,
  `%mined%`, `%playtime%`, `%online%`, `%max%`.
- `top_playtime_entry` — template for each leaderboard row (`%rank%`, `%name%`,
  `%playtime%`).
- `top_playtime_count` — leaderboard size, 1–10.
- `update_interval_ticks` — refresh rate (20 = once per second).

Formatting: classic `&` codes (`&d`, `&l`, `&m`, ...) plus hex colors like
`&#ff5ee6`. The defaults use Minecraft's small-caps unicode glyphs
(ᴀʙᴄᴅᴇꜰɢ...) for labels — paste your own from any "minecraft small caps"
generator.

Gradients: `<grad:#a855f7:#5ee6ff>text</grad>` colors the text across any
number of `:`-separated stops. `<wave:...>text</wave>` is the animated
version — the colors flow through the text, like the big-server lobby boards.
`animation_frame_ticks` is the frame rate (3 = ~7 fps), `animation_period_ticks`
the loop length. `&` format codes like `&l` still work inside a gradient.

The vanilla sidebar renders at most 15 lines; extra lines are dropped.

## Commands

- `/sidebar` — mod info.
- `/sidebar preview` — print the rendered board into chat to check config edits.
- `/sidebar top` — top-10 playtime leaderboard in chat.
- `/sidebar reload` — reload the config and rescan stats files (op level 2).

## Building

`gradlew build` — the jar lands in `build/libs/`. Requires Java 25.
