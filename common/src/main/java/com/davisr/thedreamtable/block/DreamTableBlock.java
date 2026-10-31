package com.davisr.thedreamtable.block;

import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableBreakGuard;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class DreamTableBlock extends BlockWithEntity {
	public static final MapCodec<DreamTableBlock> CODEC = createCodec(DreamTableBlock::new);

	private static final VoxelShape BASE_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
			Block.createCuboidShape(4.0, 2.0, 4.0, 12.0, 14.0, 12.0)
	);
	private static final VoxelShape COLLISION_SHAPE = VoxelShapes.union(
			BASE_SHAPE,
			Block.createCuboidShape(0.0, 15.0, 0.0, 16.0, 15.0, 16.0)
	);
	private static final VoxelShape NORTH_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0.0, 10.0, 1.0, 16.0, 14.0, 5.333333),
			Block.createCuboidShape(0.0, 12.0, 5.333333, 16.0, 16.0, 9.666667),
			Block.createCuboidShape(0.0, 14.0, 9.666667, 16.0, 18.0, 14.0),
			BASE_SHAPE
	);
	private static final VoxelShape SOUTH_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0.0, 10.0, 10.666667, 16.0, 14.0, 15.0),
			Block.createCuboidShape(0.0, 12.0, 6.333333, 16.0, 16.0, 10.666667),
			Block.createCuboidShape(0.0, 14.0, 2.0, 16.0, 18.0, 6.333333),
			BASE_SHAPE
	);
	private static final VoxelShape EAST_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(10.666667, 10.0, 0.0, 15.0, 14.0, 16.0),
			Block.createCuboidShape(6.333333, 12.0, 0.0, 10.666667, 16.0, 16.0),
			Block.createCuboidShape(2.0, 14.0, 0.0, 6.333333, 18.0, 16.0),
			BASE_SHAPE
	);
	private static final VoxelShape WEST_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(1.0, 10.0, 0.0, 5.333333, 14.0, 16.0),
			Block.createCuboidShape(5.333333, 12.0, 0.0, 9.666667, 16.0, 16.0),
			Block.createCuboidShape(9.666667, 14.0, 0.0, 14.0, 18.0, 16.0),
			BASE_SHAPE
	);

	public DreamTableBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	/**
	 * A null state refuses the placement: BlockItem places nothing. On the server a table can't go inside a farm area, since
	 * builds, take-downs and finished farms keep their blocks as they are. The refused player is told, and their inventory is
	 * sent again, since the client predicted the placement from the same state.
	 */
	@Override
	public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
		if (ctx.getWorld() instanceof ServerWorld world && FarmAreas.of(world).reserved(world, ctx.getBlockPos())) {
			if (ctx.getPlayer() instanceof ServerPlayerEntity player) {
				TableNoticePayload.problem(player, Text.translatable("gui.the_dream_table.table_in_farm_area"));
				player.playerScreenHandler.syncState();
			}
			return null;
		}
		return getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.HORIZONTAL_FACING);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(Properties.HORIZONTAL_FACING)) {
			case SOUTH -> SOUTH_SHAPE;
			case EAST -> EAST_SHAPE;
			case WEST -> WEST_SHAPE;
			default -> NORTH_SHAPE;
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return COLLISION_SHAPE;
	}

	@Override
	protected VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
		return BASE_SHAPE;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DreamTableBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.DREAM_TABLE.get(), DreamTableBlockEntity::tick);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		// Only the server owns the sessions: the client's copy gets this call too when the server's block change arrives.
		if (!world.isClient() && !state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof DreamTableBlockEntity table) {
			table.onBroken(world);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	/**
	 * Whoever starts to break the table is told why they can't when another player has materials or work here
	 * ({@link TableBreakGuard}, which also enforces it when the break completes), or else what it costs when a build is running.
	 */
	@Override
	protected void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
		if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer
				&& world.getBlockEntity(pos) instanceof DreamTableBlockEntity table) {
			Optional<TableBreakGuard.Claim> blocker = TableBreakGuard.blockerAt(serverWorld, serverPlayer, pos);
			if (blocker.isPresent()) {
				TableBreakGuard.tell(serverWorld, serverPlayer, blocker.get());
			} else if (table.hasBuildInProgress()) {
				TableNoticePayload.problem(serverPlayer, Text.translatable("gui.the_dream_table.break_warning"));
			}
		}
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(pos) instanceof DreamTableBlockEntity table) {
			Platform.menus().open(serverPlayer, table);
		}
		return ActionResult.SUCCESS;
	}
}
