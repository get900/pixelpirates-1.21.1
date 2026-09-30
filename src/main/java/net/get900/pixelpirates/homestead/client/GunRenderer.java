package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.entity.client.NamedGeoModel;
import net.get900.pixelpirates.homestead.gun.GunItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** The flintlock pistol / blunderbuss in hand (geo from tools/mobs/guns.py; display transforms in models/item/NAME.json). */
@Environment(EnvType.CLIENT)
public class GunRenderer extends GeoItemRenderer<GunItem> {
    public GunRenderer(String name) { super(new NamedGeoModel<>(name)); }
}
