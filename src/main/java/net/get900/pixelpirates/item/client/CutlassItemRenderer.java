package net.get900.pixelpirates.item.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.item.custom.CutlassItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@Environment(EnvType.CLIENT)
public class CutlassItemRenderer extends GeoItemRenderer<CutlassItem> {

    public CutlassItemRenderer() {
        super(new CutlassModel());
    }
}
