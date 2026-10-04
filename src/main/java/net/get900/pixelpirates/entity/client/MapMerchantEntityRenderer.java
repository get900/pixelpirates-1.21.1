package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.MapMerchantEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Map Merchant (rebuilt 2026-10-01 in tools/mobs/traders.py - a normal forward-facing biped; the old Blockbench
 * export needed a -90 degree turn and had every bone pivoting at the feet, so it could not animate).
 */
public class MapMerchantEntityRenderer extends GeoEntityRenderer<MapMerchantEntity> {
    public MapMerchantEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new MapMerchantModel());
        this.shadowRadius = 0.5f;
    }
}
