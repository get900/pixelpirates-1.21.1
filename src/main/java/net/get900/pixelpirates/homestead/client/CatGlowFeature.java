package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.cat.CatCoatHolder;
import net.get900.pixelpirates.homestead.cat.CatCoats;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.CatEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.CatEntity;

/** SHIP'S CATS: the full-bright glow layer (textures/entity/cat/<id>_glow.png) of the Ghost Cat and the Sea Witch's Cat. */
@Environment(EnvType.CLIENT)
public class CatGlowFeature extends FeatureRenderer<CatEntity, CatEntityModel<CatEntity>> {
    public CatGlowFeature(FeatureRendererContext<CatEntity, CatEntityModel<CatEntity>> ctx) { super(ctx); }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider v, int light, CatEntity cat, float limbAngle, float limbDistance,
                       float tickDelta, float age, float headYaw, float headPitch) {
        CatCoats.Coat c = CatCoats.byId(((CatCoatHolder) cat).pixelpirates$getCoat());
        if (c == null || !c.glows() || cat.isInvisible()) return;
        getContextModel().render(m, v.getBuffer(RenderLayer.getEyes(c.glowTexture())), 0xF00000, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
    }
}
