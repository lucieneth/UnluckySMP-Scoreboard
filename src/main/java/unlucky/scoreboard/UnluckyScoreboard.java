package unlucky.scoreboard;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnluckyScoreboard implements ModInitializer {
	public static final String MOD_ID = "unluckyscoreboard";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Live config; replaced wholesale by /sidebar reload. */
	public static volatile ScoreboardConfig CONFIG = new ScoreboardConfig();

	@Override
	public void onInitialize() {
		CONFIG = ScoreboardConfig.load();
		LOGGER.info("Unlucky Scoreboard loaded.");
	}
}
