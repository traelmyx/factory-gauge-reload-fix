package io.github.traelmyx.gaugereloadfix;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Create issue #8190. After a world or chunk loads, a packager finds the inventory it sits on only on its own first tick,
 * and the network summary a factory gauge reads is cached for 20 ticks. A gauge that ticks before that sees too little in
 * stock and orders things that are already there. A freshly loaded gauge now waits a moment before it may order, then reads
 * a fresh summary.
 */
@Mod(GaugeReloadFix.MOD_ID)
public class GaugeReloadFix {
	public static final String MOD_ID = "gauge_reload_fix";
	public static final Logger LOGGER = LoggerFactory.getLogger("Factory Gauge Reload Fix");
	/** The first Create version the fix may already be in; on it and newer the mod stands down unless told otherwise. */
	public static final String FIXED_UPSTREAM_IN = "6.0.11";

	public static boolean enabled = true;
	/** Ticks a freshly loaded gauge waits before it may place an order. */
	public static int graceTicks = 40;

	public GaugeReloadFix() {
		loadConfig(FMLPaths.CONFIGDIR.get());
	}

	static void loadConfig(Path configDir) {
		Path file = configDir.resolve(MOD_ID + ".properties");
		Properties props = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				props.load(reader);
			} catch (IOException e) {
				LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		boolean configured = Boolean.parseBoolean(props.getProperty("enabled", "true"));
		boolean keepOnNewerCreate = Boolean.parseBoolean(props.getProperty("keepOnNewerCreate", "false"));
		try {
			graceTicks = Math.max(0, Integer.parseInt(props.getProperty("graceTicks", "40").trim()));
		} catch (NumberFormatException e) {
			graceTicks = 40;
		}
		String create = ModList.get().getModContainerById("create").map(c -> c.getModInfo().getVersion().toString()).orElse("0");
		boolean newerCreate = new DefaultArtifactVersion(create).compareTo(new DefaultArtifactVersion(FIXED_UPSTREAM_IN)) >= 0;
		enabled = configured && (!newerCreate || keepOnNewerCreate);
		if (configured && !enabled)
			LOGGER.info("Create {} may already fix this; standing down (set keepOnNewerCreate=true to keep the fix)", create);
		props.setProperty("enabled", Boolean.toString(configured));
		props.setProperty("graceTicks", Integer.toString(graceTicks));
		props.setProperty("keepOnNewerCreate", Boolean.toString(keepOnNewerCreate));
		try {
			Files.createDirectories(configDir);
			try (Writer writer = Files.newBufferedWriter(file)) {
				props.store(writer, "Factory Gauge Reload Fix. graceTicks: how long a freshly loaded factory gauge waits before it may order. "
					+ "keepOnNewerCreate: keep the fix on Create " + FIXED_UPSTREAM_IN + " and newer.");
			}
		} catch (IOException e) {
			LOGGER.warn("Could not write {}", file, e);
		}
	}
}
