package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.parrot.ParrotTypeHolder;
import net.get900.pixelpirates.homestead.parrot.ParrotTypes;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ParrotEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.ParrotEntity;

/** PARROT TYPES phase 2: the full-bright glow layer (textures/entity/parrot/<id>_glow.png) of the Ghost Parrot, Ember
 *  Macaw and Kraken's Pet, over the parrot in the world. On a shoulder: ShoulderParrotTypeMixin. */
@Environment(EnvType.CLIENT)
public class ParrotGlowFeature extends FeatureRenderer<ParrotEntity, ParrotEntityModel> {
    public ParrotGlowFeature(FeatureRendererContext<ParrotEntity, ParrotEntityModel> ctx) { super(ctx); }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider v, int light, ParrotEntity p, float limbAngle, float limbDistance,
                       float tickDelta, float age, float headYaw, float headPitch) {
        ParrotTypes.PType t = ParrotTypes.byId(((ParrotTypeHolder) p).pixelpirates$getParrotType());
        if (t == null || !t.glows() || p.isInvisible()) return;
        getContextModel().render(m, v.getBuffer(RenderLayer.getEyes(t.glowTexture())), 0xF00000, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
    }
}
