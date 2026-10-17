package io.github.traelmyx.gaugereloadfix.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
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
 * A restocking factory gauge on a packager whose chest already holds 64 cobblestone, asking for 32, on a network whose
 * other chest also holds 64. Nothing should ever be ordered. To reproduce issue #8190 the gauge and its packager are
 * reloaded from their saved data the way a chunk load does it, gauge first, with the gauge's request timer run out.
 */
@GameTestHolder(GaugeTests.MOD_ID)
@PrefixGameTestTemplate(false)
public class GaugeTests {
	public static final String MOD_ID = "gauge_reload_fix_gametest";

	private static final BlockPos SOURCE_CHEST = new BlockPos(0, 1, 0), SOURCE_PACKAGER = new BlockPos(1, 1, 0), LINK = new BlockPos(1, 2, 0);
	private static final BlockPos TARGET_CHEST = new BlockPos(0, 1, 2), TARGET_PACKAGER = new BlockPos(1, 1, 2), GAUGE = new BlockPos(1, 2, 2);

	private static void build(GameTestHelper helper) {
		UUID network = UUID.randomUUID();
		helper.setBlock(SOURCE_CHEST, Blocks.CHEST);
		helper.setBlock(TARGET_CHEST, Blocks.CHEST);
		BlockState packager = AllBlocks.PACKAGER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST);
		helper.setBlock(SOURCE_PACKAGER, packager);
		helper.setBlock(TARGET_PACKAGER, packager);
		helper.setBlock(LINK, AllBlocks.STOCK_LINK.getDefaultState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		helper.setBlock(GAUGE, AllBlocks.FACTORY_GAUGE.getDefaultState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		fill(helper, SOURCE_CHEST);
		fill(helper, TARGET_CHEST);

		SmartBlockEntity link = (SmartBlockEntity) helper.getBlockEntity(LINK);
		link.getBehaviour(LogisticallyLinkedBehaviour.TYPE).freqId = network;
		FactoryPanelBlockEntity gauge = (FactoryPanelBlockEntity) helper.getBlockEntity(GAUGE);
		gauge.addPanel(PanelSlot.BOTTOM_LEFT, network);
		gauge.restocker = true;
		FactoryPanelBehaviour panel = gauge.panels.get(PanelSlot.BOTTOM_LEFT);
		panel.setFilter(new ItemStack(Items.COBBLESTONE));
		panel.count = 32;
		panel.upTo = true;
		panel.recipeAddress = "restock";
		gauge.setChanged();
	}

	private static void fill(GameTestHelper helper, BlockPos pos) {
		Container chest = (Container) helper.getBlockEntity(pos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
	}

	private static int count(GameTestHelper helper, BlockPos pos) {
		Container chest = (Container) helper.getBlockEntity(pos);
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) n += chest.getItem(i).getCount();
		return n;
	}

	private static FactoryPanelBehaviour panel(GameTestHelper helper) {
		return ((FactoryPanelBlockEntity) helper.getBlockEntity(GAUGE)).panels.get(PanelSlot.BOTTOM_LEFT);
	}

	/** Re-creates the gauge, then its packager, from their saved data, like a chunk load; the gauge ticks first. */
	private static void reload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		BlockPos gaugePos = helper.absolutePos(GAUGE), packagerPos = helper.absolutePos(TARGET_PACKAGER);
		CompoundTag gaugeTag = level.getBlockEntity(gaugePos).saveWithFullMetadata(registries);
		CompoundTag packagerTag = level.getBlockEntity(packagerPos).saveWithFullMetadata(registries);
		gaugeTag.getCompound("bottom_left").putInt("Timer", 0);
		level.removeBlockEntity(gaugePos);
		level.removeBlockEntity(packagerPos);
		level.setBlockEntity(BlockEntity.loadStatic(gaugePos, level.getBlockState(gaugePos), gaugeTag, registries));
		level.setBlockEntity(BlockEntity.loadStatic(packagerPos, level.getBlockState(packagerPos), packagerTag, registries));
	}

	private static void run(GameTestHelper helper, boolean fixed) {
		GaugeReloadFix.enabled = fixed;
		build(helper);
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(panel(helper).getPromised() == 0 && count(helper, SOURCE_CHEST) == 64, "ordered before any reload");
			reload(helper);
		});
		helper.runAfterDelay(200, () -> {
			int promised = panel(helper).getPromised(), left = count(helper, SOURCE_CHEST);
			System.out.printf("[gauge_reload_fix] fix %s: promised %d, source chest %d%n", fixed ? "on" : "off", promised, left);
			GaugeReloadFix.enabled = true;
			if (fixed) {
				helper.assertTrue(promised == 0 && left == 64, "gauge ordered after reload: promised " + promised + ", source chest " + left);
			} else {
				// without the fix the reloaded gauge must over-order, or this test can't tell the fix works
				helper.assertTrue(promised > 0 || left < 64, "the reload did not reproduce the bug");
			}
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
