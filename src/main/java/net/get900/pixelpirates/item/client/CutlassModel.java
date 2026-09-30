package net.get900.pixelpirates.item.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.item.custom.CutlassItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class CutlassModel extends GeoModel<CutlassItem> {

    @Override
    public Identifier getModelResource(CutlassItem item) {
        return new Identifier(PixelPirates.MOD_ID, "geo/cutlass.geo.json");
    }

    @Override
    public Identifier getTextureResource(CutlassItem item) {
        return new Identifier(PixelPirates.MOD_ID, "textures/item/cutlassmodel.png");
    }

    @Override
    public Identifier getAnimationResource(CutlassItem item) {
        return null;
    }
}
