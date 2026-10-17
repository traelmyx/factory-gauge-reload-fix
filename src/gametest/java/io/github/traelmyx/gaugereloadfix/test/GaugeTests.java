package io.github.traelmyx.gaugereloadfix.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import io.github.traelmyx.gaugereloadfix.GaugeReloadFix;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * A production gauge asks for 32 stone, made from cobblestone; the network's chest already holds 64 of each, so nothing
 * should ever be ordered. To reproduce issue #8190 the gauge and the packager on that chest are reloaded from their saved
 * data the way a chunk load does it, gauge first, with the gauge's request timer about to run out.
 */
@GameTestHolder(GaugeTests.MOD_ID)
@PrefixGameTestTemplate(false)
public class GaugeTests {
	public static final String MOD_ID = "gauge_reload_fix_gametest";

	private static final BlockPos CHEST = new BlockPos(0, 1, 0), PACKAGER = new BlockPos(1, 1, 0), LINK = new BlockPos(1, 2, 0);
	private static final BlockPos INPUT_GAUGE = new BlockPos(3, 2, 0), GAUGE = new BlockPos(3, 2, 2);

	private static void build(GameTestHelper helper, int stone) {
		UUID network = UUID.randomUUID();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		if (stone > 0) chest.setItem(0, new ItemStack(Items.STONE, stone));
		chest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
		helper.setBlock(PACKAGER, AllBlocks.PACKAGER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
		helper.setBlock(LINK, AllBlocks.STOCK_LINK.getDefaultState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		SmartBlockEntity link = (SmartBlockEntity) helper.getBlockEntity(LINK);
		link.getBehaviour(LogisticallyLinkedBehaviour.TYPE).freqId = network;

		BlockState gaugeState = AllBlocks.FACTORY_GAUGE.getDefaultState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
		helper.setBlock(INPUT_GAUGE.below(), Blocks.STONE);
		helper.setBlock(GAUGE.below(), Blocks.STONE);
		helper.setBlock(INPUT_GAUGE, gaugeState);
		helper.setBlock(GAUGE, gaugeState);
		FactoryPanelBlockEntity input = (FactoryPanelBlockEntity) helper.getBlockEntity(INPUT_GAUGE);
		input.addPanel(PanelSlot.BOTTOM_LEFT, network);
		input.panels.get(PanelSlot.BOTTOM_LEFT).setFilter(new ItemStack(Items.COBBLESTONE));
		FactoryPanelBlockEntity gauge = (FactoryPanelBlockEntity) helper.getBlockEntity(GAUGE);
		gauge.addPanel(PanelSlot.BOTTOM_LEFT, network);
		FactoryPanelBehaviour panel = gauge.panels.get(PanelSlot.BOTTOM_LEFT);
		panel.setFilter(new ItemStack(Items.STONE));
		panel.count = 32;
		panel.upTo = true;
		panel.recipeAddress = "furnace";
		panel.addConnection(new FactoryPanelPosition(helper.absolutePos(INPUT_GAUGE), PanelSlot.BOTTOM_LEFT));
		input.setChanged();
		gauge.setChanged();
	}

	private static int cobblestone(GameTestHelper helper) {
		Container chest = (Container) helper.getBlockEntity(CHEST);
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.COBBLESTONE)) n += chest.getItem(i).getCount();
		return n;
	}

	private static FactoryPanelBehaviour panel(GameTestHelper helper) {
		return ((FactoryPanelBlockEntity) helper.getBlockEntity(GAUGE)).panels.get(PanelSlot.BOTTOM_LEFT);
	}

	/** Re-creates the gauge, then the packager on the network's chest, from their saved data, like a chunk load; the gauge ticks first. */
	private static void reload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		BlockPos gaugePos = helper.absolutePos(GAUGE), packagerPos = helper.absolutePos(PACKAGER);
		CompoundTag gaugeTag = level.getBlockEntity(gaugePos).saveWithFullMetadata(registries);
		CompoundTag packagerTag = level.getBlockEntity(packagerPos).saveWithFullMetadata(registries);
		// the request timer runs out on the third tick: after the packager has found its chest, before the cached count expires
		gaugeTag.getCompound("bottom_left").putInt("Timer", 2);
		// coming back after a while: the network's cached stock counts have long expired
		UUID network = ((FactoryPanelBlockEntity) level.getBlockEntity(gaugePos)).panels.get(PanelSlot.BOTTOM_LEFT).network;
		LogisticsManager.SUMMARIES.invalidate(network);
		LogisticsManager.ACCURATE_SUMMARIES.invalidate(network);
		level.removeBlockEntity(gaugePos);
		level.removeBlockEntity(packagerPos);
		level.setBlockEntity(BlockEntity.loadStatic(gaugePos, level.getBlockState(gaugePos), gaugeTag, registries));
		level.setBlockEntity(BlockEntity.loadStatic(packagerPos, level.getBlockState(packagerPos), packagerTag, registries));
	}

	private static void run(GameTestHelper helper, boolean fixed) {
		GaugeReloadFix.enabled = fixed;
		build(helper, 64);
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(panel(helper).getPromised() == 0 && cobblestone(helper) == 64, "ordered before any reload");
			reload(helper);
		});
		helper.runAfterDelay(200, () -> {
			int promised = panel(helper).getPromised(), left = cobblestone(helper);
			System.out.printf("[gauge_reload_fix] fix %s: promised %d, cobblestone left %d%n", fixed ? "on" : "off", promised, left);
			GaugeReloadFix.enabled = true;
			if (fixed) {
				helper.assertTrue(promised == 0 && left == 64, "gauge ordered after reload: promised " + promised + ", cobblestone left " + left);
			} else {
				// without the fix the reloaded gauge must over-order, or this test can't tell the fix works
				helper.assertTrue(promised > 0 || left < 64, "the reload did not reproduce the bug");
			}
			helper.succeed();
		});
	}

	/** The wiring works: with no stone in the chest, the gauge does order cobblestone (with the fix on, after its wait). */
	@GameTest(template = "empty", timeoutTicks = 400, batch = "short")
	public static void ordersWhenStoneIsShort(GameTestHelper helper) {
		GaugeReloadFix.enabled = true;
		build(helper, 0);
		helper.runAfterDelay(200, () -> {
			int promised = panel(helper).getPromised(), left = cobblestone(helper);
			System.out.printf("[gauge_reload_fix] short on stone: promised %d, cobblestone left %d%n", promised, left);
			helper.assertTrue(promised > 0 || left < 64, "gauge never ordered although the chest has no stone");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "fixed")
	public static void noOrderAfterReload(GameTestHelper helper) {
		run(helper, true);
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "control")
	public static void controlReproducesBug(GameTestHelper helper) {
		run(helper, false);
	}
}
