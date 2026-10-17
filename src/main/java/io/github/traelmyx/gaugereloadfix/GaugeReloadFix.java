package io.github.traelmyx.gaugereloadfix;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
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

	public static boolean enabled = true;
	/** Ticks a freshly loaded gauge waits before it may place an order. */
	public static int graceTicks = 40;

	public GaugeReloadFix() {
		loadConfig(FMLPaths.CONFIGDIR.get());
		checkTarget();
	}

	/** The mixin is optional so a Create update can't crash a pack; say so when it no longer finds its target. */
	private static void checkTarget() {
		try {
			Class<?> panel = Class.forName("com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour", false, GaugeReloadFix.class.getClassLoader());
			panel.getDeclaredMethod("tickRequests");
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("This Create version has no FactoryPanelBehaviour.tickRequests; the fix is inactive ({})", e.toString());
		}
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
		enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
		try {
			graceTicks = Math.max(0, Integer.parseInt(props.getProperty("graceTicks", "40").trim()));
		} catch (NumberFormatException e) {
			graceTicks = 40;
		}
		props.setProperty("enabled", Boolean.toString(enabled));
		props.setProperty("graceTicks", Integer.toString(graceTicks));
		try {
			Files.createDirectories(configDir);
			try (Writer writer = Files.newBufferedWriter(file)) {
				props.store(writer, "Factory Gauge Reload Fix. graceTicks: how long a freshly loaded factory gauge waits before it may order.");
			}
		} catch (IOException e) {
			LOGGER.warn("Could not write {}", file, e);
		}
	}
}
