package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Animates the Gallowbrand in the Stone: `idle` (the blade hums, the chains sway) / `empty`, plus the `pull` one-shot. */
public class GallowbrandStoneBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation EMPTY = RawAnimation.begin().thenLoop("empty");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GallowbrandStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.GALLOWBRAND_STONE_ENTITY, pos, state);
    }

    public boolean hasSword() {
        BlockState s = getCachedState();
        return !s.contains(GallowbrandStoneBlock.HAS_SWORD) || s.get(GallowbrandStoneBlock.HAS_SWORD);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "state", 0, st -> st.setAndContinue(hasSword() ? IDLE : EMPTY)));
        // added AFTER "state" so the pull wins on the bones both animate while it plays
        controllers.add(new AnimationController<>(this, "pull", 0, st -> PlayState.STOP)
                .triggerableAnim("pull", RawAnimation.begin().thenPlay("pull")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
