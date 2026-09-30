package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.CastawayEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class CastawayModel extends GeoModel<CastawayEntity> {
    // Asset files keep the Blockbench source name (castaway_man) so re-exports drop in unrenamed
    @Override
    public Identifier getModelResource(CastawayEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "geo/castaway_man.geo.json");
    }

    @Override
    public Identifier getTextureResource(CastawayEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "textures/entity/castaway_man.png");
    }

    @Override
    public Identifier getAnimationResource(CastawayEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "animations/castaway_man.animation.json");
    }
}
