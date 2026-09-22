package unlucky.scoreboard;

/**
 * Drives the leaderboard's type → hold → erase → next board loop. One instance
 * is shared by the whole server, so every player sees the same board at the
 * same point in the animation. It only tracks how many characters are on
 * screen; which characters those are is up to {@link SidebarUpdater}.
 */
final class LeaderboardCycle {
	private enum Phase {
		TYPE, HOLD, ERASE
	}

	private Phase phase = Phase.TYPE;
	private int index;
	private int ticks;
	private int revealed;
	private boolean boardChanged;
	private boolean revealChanged;

	/** Position of the board being shown in the configured rotation. */
	int index() {
		return index;
	}

	/** Characters of each row currently on screen; rows shorter than this are shown whole. */
	int revealed() {
		return revealed;
	}

	boolean boardChanged() {
		return boardChanged;
	}

	boolean revealChanged() {
		return revealChanged;
	}

	void reset() {
		phase = Phase.TYPE;
		index = 0;
		ticks = 0;
		revealed = 0;
		boardChanged = true;
		revealChanged = true;
	}

	/** Advances one server tick. {@code maxLength} is the longest row of the board on screen. */
	void advance(ScoreboardConfig config, int maxLength, int boardCount) {
		int wasRevealed = revealed;
		boardChanged = false;
		if (boardCount <= 0) {
			revealed = maxLength;
			revealChanged = revealed != wasRevealed;
			return;
		}
		if (index >= boardCount) {
			// The rotation shrank under us on /sidebar reload.
			index = 0;
			boardChanged = true;
		}
		int hold = Math.max(1, config.leaderboard_switch_seconds * 20);
		ticks++;
		if (!config.leaderboard_animate) {
			revealed = maxLength;
			if (ticks >= hold) {
				ticks = 0;
				next(boardCount);
			}
		} else {
			switch (phase) {
				case TYPE -> {
					revealed = Math.min(maxLength, ticks / Math.max(1, config.leaderboard_type_ticks));
					if (revealed >= maxLength) {
						phase = Phase.HOLD;
						ticks = 0;
					}
				}
				case HOLD -> {
					// Stay pinned to the full width: rows change as stats come in.
					revealed = maxLength;
					if (ticks >= hold) {
						phase = Phase.ERASE;
						ticks = 0;
					}
				}
				case ERASE -> {
					revealed = Math.max(0, maxLength - ticks / Math.max(1, config.leaderboard_erase_ticks));
					if (revealed <= 0) {
						phase = Phase.TYPE;
						ticks = 0;
						next(boardCount);
					}
				}
			}
		}
		revealChanged = revealed != wasRevealed;
	}

	private void next(int boardCount) {
		index = (index + 1) % boardCount;
		boardChanged = true;
	}
}
