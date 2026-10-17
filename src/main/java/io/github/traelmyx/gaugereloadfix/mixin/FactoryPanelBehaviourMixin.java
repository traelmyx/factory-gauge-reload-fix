package io.github.traelmyx.gaugereloadfix.mixin;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import io.github.traelmyx.gaugereloadfix.GaugeReloadFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourMixin {
	@Unique
	private static boolean gauge_reload_fix$warned;

	/** Server ticks since this gauge was loaded; a new behaviour object is made every time the block entity loads. */
	@Unique
	private int gauge_reload_fix$age;

	// require = 0: if a Create update renames this method, the fix stops working instead of crashing the game
	@Inject(method = "tickRequests", at = @At("HEAD"), cancellable = true, require = 0)
	private void gauge_reload_fix$waitAfterLoad(CallbackInfo ci) {
		if (!GaugeReloadFix.enabled || gauge_reload_fix$age >= GaugeReloadFix.graceTicks)
			return;
		gauge_reload_fix$age++;
		if (gauge_reload_fix$age == GaugeReloadFix.graceTicks) {
			// the summary cached while things were still loading may be short; make the next stock check read a fresh one
			try {
				FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
				if (self.network != null)
					LogisticsManager.SUMMARIES.invalidate(self.network);
			} catch (LinkageError e) {
				if (!gauge_reload_fix$warned) {
					gauge_reload_fix$warned = true;
					GaugeReloadFix.LOGGER.warn("Could not refresh the network stock count on this Create version ({})", e.toString());
				}
			}
		}
		ci.cancel();
	}
}
