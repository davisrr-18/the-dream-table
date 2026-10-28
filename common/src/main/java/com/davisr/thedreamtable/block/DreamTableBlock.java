package com.davisr.thedreamtable.block;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
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

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
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
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof DreamTableBlockEntity table) {
			table.dropMaterials(world);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(pos) instanceof DreamTableBlockEntity table) {
			Platform.menus().open(serverPlayer, table);
		}
		return ActionResult.SUCCESS;
	}
}
