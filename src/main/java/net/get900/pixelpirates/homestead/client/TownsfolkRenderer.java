package net.get900.pixelpirates.homestead.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.client.NamedGeoModel;
import net.get900.pixelpirates.homestead.town.TownsfolkEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Every townsperson has their own model, texture and clips: geo/folk_<id> (tools/mobs/townsfolk.py). The props in their
 * hands follow what they're doing: the work tools (kit_r / kit_l) only on duty, a tankard while drinking, the dice cup at
 * the tables, brush + palette at the easel.
 */
public class TownsfolkRenderer extends GeoEntityRenderer<TownsfolkEntity> {
    private static final Map<String, Identifier[]> RES = new HashMap<>();

    private static Identifier[] res(TownsfolkEntity e) {
        String id = e.folkId().isEmpty() ? "polly" : e.folkId();
        return RES.computeIfAbsent(id, k -> new Identifier[]{
                new Identifier(PixelPirates.MOD_ID, "geo/folk_" + k + ".geo.json"),
                new Identifier(PixelPirates.MOD_ID, "textures/entity/folk_" + k + ".png"),
                new Identifier(PixelPirates.MOD_ID, "animations/folk_" + k + ".animation.json")});
    }

    public TownsfolkRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new NamedGeoModel<>("folk_polly") {
            @Override
            public Identifier getModelResource(TownsfolkEntity e) { return res(e)[0]; }

            @Override
            public Identifier getTextureResource(TownsfolkEntity e) { return res(e)[1]; }

            @Override
            public Identifier getAnimationResource(TownsfolkEntity e) { return res(e)[2]; }
        });
        this.shadowRadius = 0.45f;
    }

    @Override
    public void preRender(MatrixStack poseStack, TownsfolkEntity e, BakedGeoModel model, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        TownsfolkEntity.Act a = e.act();
        boolean sleeping = e.isSleeping();
        boolean duty = !sleeping && switch (a) { case IDLE, WORK -> true; default -> false; } && !e.hasVehicle();
        hide(model, "kit_r", !duty);
        hide(model, "kit_l", !duty);
        hide(model, "tankard", a != TownsfolkEntity.Act.DRINK || sleeping);
        hide(model, "cup", a != TownsfolkEntity.Act.GAMBLE || sleeping);
        hide(model, "brush", a != TownsfolkEntity.Act.PAINT || sleeping);
        hide(model, "palette", a != TownsfolkEntity.Act.PAINT || sleeping);
        super.preRender(poseStack, e, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** The children are drawn at their own size (Townsfolk.scale; the hitbox matches - TownsfolkEntity.getDimensions). */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, MatrixStack poseStack, TownsfolkEntity e, BakedGeoModel model,
                                    boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float k = net.get900.pixelpirates.homestead.town.Townsfolk.scale(e.folkId());
        super.scaleModelForRender(widthScale * k, heightScale * k, poseStack, e, model, isReRender, partialTick, packedLight, packedOverlay);
    }

    private static void hide(BakedGeoModel model, String bone, boolean hidden) {
        model.getBone(bone).ifPresent(b -> b.setHidden(hidden));
    }
}
