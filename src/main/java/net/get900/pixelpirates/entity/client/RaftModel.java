package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.RaftEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class RaftModel extends GeoModel<RaftEntity> {

    @Override
    public Identifier getModelResource(RaftEntity animatable) {
        return new Identifier(PixelPirates.MOD_ID, "geo/raft.geo.json");
    }

    @Override
    public Identifier getTextureResource(RaftEntity animatable) {
        return new Identifier(PixelPirates.MOD_ID, "textures/entity/raft.png");
    }

    @Override
    public Identifier getAnimationResource(RaftEntity animatable) {
        return new Identifier(PixelPirates.MOD_ID, "animations/raft.animation.json");
    }

    @Override
    public void setCustomAnimations(RaftEntity entity, long instanceId, AnimationState<RaftEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        float yawRotation = (float) Math.toRadians(-entity.getYaw());

        getBone("raft_base").ifPresent(base -> {
            base.setRotY(yawRotation);
            base.setPivotY(base.getPivotY() + entity.getSinkingAmount());
        });
        getBone("sail").ifPresent(sail -> sail.setRotY(yawRotation));
        getBone("mast").ifPresent(mast -> mast.setRotY(yawRotation));
    }
}
