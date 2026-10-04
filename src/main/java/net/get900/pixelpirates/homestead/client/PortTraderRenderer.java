package net.get900.pixelpirates.homestead.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.client.NamedGeoModel;
import net.get900.pixelpirates.homestead.trade.PortTraderEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Every trader kind has his own model, texture and clips: geo/trader_<kind> (tools/mobs/traders.py). */
public class PortTraderRenderer extends GeoEntityRenderer<PortTraderEntity> {
    private static final Identifier[][] RES = new Identifier[PortTraderEntity.Kind.values().length][3];

    static {
        for (PortTraderEntity.Kind k : PortTraderEntity.Kind.values()) {
            String n = "trader_" + k.name().toLowerCase();
            RES[k.ordinal()][0] = new Identifier(PixelPirates.MOD_ID, "geo/" + n + ".geo.json");
            RES[k.ordinal()][1] = new Identifier(PixelPirates.MOD_ID, "textures/entity/" + n + ".png");
            RES[k.ordinal()][2] = new Identifier(PixelPirates.MOD_ID, "animations/" + n + ".animation.json");
        }
    }

    public PortTraderRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new NamedGeoModel<>("trader_quartermaster") {
            @Override
            public Identifier getModelResource(PortTraderEntity e) { return RES[e.kind().ordinal()][0]; }

            @Override
            public Identifier getTextureResource(PortTraderEntity e) { return RES[e.kind().ordinal()][1]; }

            @Override
            public Identifier getAnimationResource(PortTraderEntity e) { return RES[e.kind().ordinal()][2]; }
        });
        this.shadowRadius = 0.5f;
    }
}
