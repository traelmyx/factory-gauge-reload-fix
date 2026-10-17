package io.github.traelmyx.gaugereloadfix.mixin;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import io.github.traelmyx.gaugereloadfix.GaugeReloadFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourMixin {
	@Shadow
	public UUID network;

	/** Server ticks since this gauge was loaded; a new behaviour object is made every time the block entity loads. */
	@Unique
	private int gauge_reload_fix$age;

	@Inject(method = "tickRequests", at = @At("HEAD"), cancellable = true)
	private void gauge_reload_fix$waitAfterLoad(CallbackInfo ci) {
		if (!GaugeReloadFix.enabled || gauge_reload_fix$age >= GaugeReloadFix.graceTicks)
			return;
		gauge_reload_fix$age++;
		// the summary cached while things were still loading may be short; make the next stock check read a fresh one
		if (gauge_reload_fix$age == GaugeReloadFix.graceTicks && network != null)
			LogisticsManager.SUMMARIES.invalidate(network);
		ci.cancel();
	}
}
