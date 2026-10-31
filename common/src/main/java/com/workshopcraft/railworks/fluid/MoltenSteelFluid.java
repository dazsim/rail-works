package com.workshopcraft.railworks.fluid;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Burns like lava, flows at half its speed and only 2 blocks out, never makes new sources. */
public abstract class MoltenSteelFluid extends FlowingFluid {
    @Override
    public Fluid getFlowing() {
        return ModContent.FLOWING_MOLTEN_STEEL;
    }

    @Override
    public Fluid getSource() {
        return ModContent.MOLTEN_STEEL;
    }

    @Override
    public Item getBucket() {
        return ModContent.MOLTEN_STEEL_BUCKET;
    }

    @Override
    public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        BlockPos above = pos.above();
        if (!level.getBlockState(above).isAir()) return;
        if (random.nextInt(80) == 0) {
            double x = pos.getX() + random.nextDouble(), y = pos.getY() + 1.0, z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
            level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.AMBIENT, 0.2F + random.nextFloat() * 0.2F, 0.8F, false);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.95, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }

    @Override
    protected void randomTick(ServerLevel level, BlockPos pos, FluidState state, RandomSource random) {
        if (!level.canSpreadFireAround(pos)) return;
        for (int i = 0; i < 3; i++) {
            BlockPos p = pos.offset(random.nextInt(3) - 1, random.nextInt(2), random.nextInt(3) - 1);
            if (!level.isLoaded(p)) return;
            if (level.isEmptyBlock(p.above()) && level.getBlockState(p).ignitedByLava()) {
                level.setBlockAndUpdate(p.above(), BaseFireBlock.getState(level, p.above()));
            }
        }
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects) {
        effects.apply(InsideBlockEffectType.CLEAR_FREEZE);
        effects.apply(InsideBlockEffectType.LAVA_IGNITE);
        effects.runAfter(InsideBlockEffectType.LAVA_IGNITE, Entity::lavaHurt);
    }

    @Override
    public @Nullable ParticleOptions getDripParticle() {
        return ParticleTypes.DRIPPING_LAVA;
    }

    @Override
    protected boolean canConvertToSource(ServerLevel level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        level.levelEvent(1501, pos, 0);
    }

    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            level.levelEvent(1501, pos, 0);
            return;
        }
        super.spreadTo(level, pos, state, direction, target);
    }

    @Override
    public int getSlopeFindDistance(LevelReader level) {
        return 2;
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return ModContent.MOLTEN_STEEL_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public boolean isSame(Fluid other) {
        return other == ModContent.MOLTEN_STEEL || other == ModContent.FLOWING_MOLTEN_STEEL;
    }

    @Override
    public int getDropOff(LevelReader level) {
        return 3;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return 60;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
        return false;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
    }

    public static class Flowing extends MoltenSteelFluid {
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

    public static class Source extends MoltenSteelFluid {
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
