package net.get900.pixelpirates.entity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.item.custom.PixelArmorItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The 3D armor renderer for every PixelArmorItem: one model per armor material (see PixelArmorItem). */
@Environment(EnvType.CLIENT)
public class PixelArmorRenderer extends GeoArmorRenderer<PixelArmorItem> {
    public PixelArmorRenderer(String name) {
        super(new GeoModel<>() {
            final Identifier geo = PixelPirates.id("geo/armor/" + name + ".geo.json");
            final Identifier tex = PixelPirates.id("textures/armor/" + name + ".png");
            final Identifier anim = PixelPirates.id("animations/armor/" + name + ".animation.json");
            @Override public Identifier getModelResource(PixelArmorItem a) { return geo; }
            @Override public Identifier getTextureResource(PixelArmorItem a) { return tex; }
            @Override public Identifier getAnimationResource(PixelArmorItem a) { return anim; }
        });
        // glowing seams/eyes/veins - only for sets that have a glowmask (an empty one makes GeckoLib throw)
        if (MinecraftClient.getInstance().getResourceManager().getResource(PixelPirates.id("textures/armor/" + name + "_glowmask.png")).isPresent())
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** Installed as PixelArmorItem.CLIENT_PROVIDER by PixelPiratesClient. */
    public static Object provider(PixelArmorItem item) {
        return new RenderProvider() {
            private PixelArmorRenderer renderer;

            @Override
            public BipedEntityModel<LivingEntity> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, BipedEntityModel<LivingEntity> original) {
                if (renderer == null) renderer = new PixelArmorRenderer(item.modelName());
                renderer.prepForRender(entity, stack, slot, original);
                return renderer;
            }
        };
    }
}
