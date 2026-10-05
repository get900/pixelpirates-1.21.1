package net.get900.pixelpirates.entity.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Draws a GeckoLib mob's held items at its "rightItem" / "leftItem" bones (2026-10-05; the user: "the pirates need to be
 * holding a weapon" - they always carried a cutlass, but no renderer drew it). The item is turned to point forward
 * out of the fist the way a player holds one.
 */
public class HeldItemGeoLayer<T extends LivingEntity & GeoAnimatable> extends BlockAndItemGeoLayer<T> {
    public HeldItemGeoLayer(GeoRenderer<T> renderer) { super(renderer); }

    @Nullable
    @Override
    protected ItemStack getStackForBone(GeoBone bone, T entity) {
        return switch (bone.getName()) {
            case "rightItem" -> entity.getMainHandStack();
            case "leftItem" -> entity.getOffHandStack();
            default -> null;
        };
    }

    @Override
    protected ModelTransformationMode getTransformTypeForStack(GeoBone bone, ItemStack stack, T entity) {
        return bone.getName().equals("leftItem") ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    protected void renderStackForBone(MatrixStack poseStack, GeoBone bone, ItemStack stack, T animatable,
                                      VertexConsumerProvider bufferSource, float partialTick, int packedLight, int packedOverlay) {
        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
        super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
    }
}
