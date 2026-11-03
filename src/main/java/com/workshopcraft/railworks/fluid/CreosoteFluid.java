package com.workshopcraft.railworks.fluid;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public abstract class CreosoteFluid extends FlowingFluid {
    @Override
    public Fluid getFlowing() {
        return ModContent.FLOWING_CREOSOTE;
    }

    @Override
    public Fluid getSource() {
        return ModContent.CREOSOTE;
    }

    @Override
    public Item getBucket() {
        return ModContent.CREOSOTE_BUCKET;
    }

    @Override
    public @Nullable ParticleOptions getDripParticle() {
        return null;
    }

    @Override
    protected boolean canConvertToSource(ServerLevel level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, be);
    }

    @Override
    public int getSlopeFindDistance(LevelReader level) {
        return 2;
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return ModContent.CREOSOTE_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public boolean isSame(Fluid other) {
        return other == ModContent.CREOSOTE || other == ModContent.FLOWING_CREOSOTE;
    }

    @Override
    public int getDropOff(LevelReader level) {
        return 2;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return 20;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
        return direction == Direction.DOWN && !isSame(other);
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    public static class Flowing extends CreosoteFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends CreosoteFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
