package net.get900.pixelpirates.homestead.trophy;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** The mounted creature on a MobTrophyBlock: loops its mob's idle animation. */
public class MobTrophyBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MobTrophyBlockEntity(BlockPos pos, BlockState state) {
        super(HomesteadBlockEntities.MOB_TROPHY, pos, state);
    }

    public MobTrophyBlock.Mount mount() {
        return getCachedState().getBlock() instanceof MobTrophyBlock b ? b.mount() : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 5, st -> {
            MobTrophyBlock.Mount m = mount();
            return m == null ? software.bernie.geckolib.core.object.PlayState.STOP : st.setAndContinue(RawAnimation.begin().thenLoop(m.idle()));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
