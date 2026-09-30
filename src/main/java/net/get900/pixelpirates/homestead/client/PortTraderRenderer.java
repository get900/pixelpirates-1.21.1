package net.get900.pixelpirates.homestead.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.client.NamedGeoModel;
import net.get900.pixelpirates.homestead.trade.PortTraderEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** One geometry, one texture per trader kind: port_trader (quartermaster), port_trader_fishmonger / _barkeep / _curio_dealer. */
public class PortTraderRenderer extends GeoEntityRenderer<PortTraderEntity> {
    private static final Identifier[] TEX = new Identifier[PortTraderEntity.Kind.values().length];

    static {
        for (PortTraderEntity.Kind k : PortTraderEntity.Kind.values())
            TEX[k.ordinal()] = new Identifier(PixelPirates.MOD_ID, "textures/entity/port_trader"
                    + (k == PortTraderEntity.Kind.QUARTERMASTER ? "" : "_" + k.name().toLowerCase()) + ".png");
    }

    public PortTraderRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new NamedGeoModel<>("port_trader") {
            @Override
            public Identifier getTextureResource(PortTraderEntity e) { return TEX[e.kind().ordinal()]; }
        });
        this.shadowRadius = 0.5f;
    }
}
