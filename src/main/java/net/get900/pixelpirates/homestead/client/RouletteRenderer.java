package net.get900.pixelpirates.homestead.client;

import net.get900.pixelpirates.entity.client.NamedGeoModel;
import net.get900.pixelpirates.homestead.roulette.RouletteTableBlockEntity;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Spins the roulette wheel procedurally so it stops on the server's result. Measured in tools/mobs/roulette.py: bone
 * rotY = -i * 2pi/37 rests pocket i under the -Z marker, where the ball ends (ball rotY 0). The wheel turns 6 extra
 * revolutions and the ball 9 the other way, both easing out over the 5 s spin.
 */
public class RouletteRenderer extends GeoBlockRenderer<RouletteTableBlockEntity> {
    public RouletteRenderer() {
        super(new NamedGeoModel<>("roulette_table") {
            @Override
            public void setCustomAnimations(RouletteTableBlockEntity be, long instanceId, AnimationState<RouletteTableBlockEntity> state) {
                super.setCustomAnimations(be, instanceId, state);
                if (be.getWorld() == null) return;
                double now = be.getWorld().getTime() + state.getPartialTick();
                double p = Math.min(1, Math.max(0, (now - be.spinStart()) / RouletteTableBlockEntity.SPIN_TICKS));
                double e = 1 - Math.pow(1 - p, 3);
                int res = be.result() < 0 ? 0 : be.result();
                double rest = -RouletteTableBlockEntity.pocketOf(res) * (Math.PI * 2 / 37);
                float wheel = (float) (rest + (1 - e) * 6 * Math.PI * 2);
                float ball = (float) (-(1 - e) * 9 * Math.PI * 2);
                getAnimationProcessor().getBone("wheel").setRotY(wheel);
                getAnimationProcessor().getBone("ball").setRotY(ball);
            }
        });
    }

    @Override
    public boolean rendersOutsideBoundingBox(RouletteTableBlockEntity be) { return true; }
}
