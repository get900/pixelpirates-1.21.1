package net.get900.pixelpirates.item.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.entity.client.NamedGeoModel;
import net.get900.pixelpirates.item.custom.GallowbrandItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Gallowbrand in hand / in the GUI (geo gallowbrand, tools/mobs/gallows.py); display transforms in models/item/gallowbrand.json. */
@Environment(EnvType.CLIENT)
public class GallowbrandItemRenderer extends GeoItemRenderer<GallowbrandItem> {
    public GallowbrandItemRenderer() {
        super(new NamedGeoModel<>("gallowbrand"));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
