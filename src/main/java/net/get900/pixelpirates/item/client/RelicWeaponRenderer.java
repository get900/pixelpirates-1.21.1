package net.get900.pixelpirates.item.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.client.NamedGeoModel;
import net.get900.pixelpirates.item.relic.RelicMeleeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Every relic weapon in hand / in the GUI (geo + texture + glowmask from tools/gen_weapon_models.py). Display transforms
 * live in models/item/ID.json (tools/gen_weapon_item_models.py). The glow layer is only added when the weapon has a
 * glowmask (crash #8). Keyframed clips play through the item's controllers (item/relic/WeaponAnims); two things are
 * posed per frame in {@link Model#setCustomAnimations} (after the clips) instead: the SERPENTSPINE LONGBOW's draw (from
 * the real pull of whoever is drawing THIS stack) and the HEARTSEEKER's heart (on the real beat - world time, the same
 * clock as RelicMeleeItem.onBeat).
 */
@Environment(EnvType.CLIENT)
public class RelicWeaponRenderer<T extends net.minecraft.item.Item & GeoAnimatable> extends GeoItemRenderer<T> {

    public RelicWeaponRenderer(String id) {
        super(new Model<>(id));
        ((Model<T>) getGeoModel()).renderer = this;
        Identifier mask = new Identifier(PixelPirates.MOD_ID, "textures/entity/" + id + "_glowmask.png");
        if (MinecraftClient.getInstance().getResourceManager().getResource(mask).isPresent())
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    static final class Model<T extends net.minecraft.item.Item & GeoAnimatable> extends NamedGeoModel<T> {
        private final String id;
        RelicWeaponRenderer<T> renderer;

        Model(String id) { super(id); this.id = id; }

        @Override
        public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
            super.setCustomAnimations(animatable, instanceId, state);
            float pt = state.getPartialTick();
            if (id.equals("serpentspine_longbow")) bowPose(this, pull(renderer == null ? null : renderer.getCurrentItemStack(), pt));
            else if (id.equals("heartseeker")) heartbeat(this, pt);
        }
    }

    // ------------------------------------------------------------------ the bow's draw
    /** 0..1 draw of this stack (anyone drawing it), with vanilla's bow curve. */
    static float pull(ItemStack stack, float partialTick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || stack == null || stack.isEmpty()) return 0;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!p.isUsingItem() || !p.getActiveItem().isOf(stack.getItem())) continue;
            // the local player's first-person stack may be a copy; anyone else must hold this very stack
            if (p.getActiveItem() != stack && p != mc.player) continue;
            float f = (p.getItemUseTime() + partialTick) / 20f;
            f = (f * f + f * 2) / 3f;
            return Math.min(1f, f);
        }
        return 0;
    }

    /** Signs/amounts match the "draw_full" preview clip in tools/gen_weapon_models.py (keyframe X a = setRotX(-rad a)). */
    private static void bowPose(Model<?> m, float p) {
        rotX(m, "upper_limb", -14 * p);
        rotX(m, "lower_limb", 14 * p);
        rotX(m, "string_top", 36 * p);
        rotX(m, "string_bottom", -36 * p);
        var b = m.getAnimationProcessor().getBone("arrow");
        if (b != null) {
            b.setPosZ(10 * p);
            float s = p > 0.02f ? 1 : 0;
            b.setScaleX(s); b.setScaleY(s); b.setScaleZ(s);
            b.markPositionAsChanged(); b.markScaleAsChanged();
        }
    }

    // ------------------------------------------------------------------ the heart
    /** A double thump centred on each beat (world time % BEAT == 0), i.e. exactly in the on-beat window. */
    private static void heartbeat(Model<?> m, float partialTick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        float t = (mc.world.getTime() % RelicMeleeItem.BEAT) + partialTick;
        float d1 = Math.min(t, RelicMeleeItem.BEAT - t), d2 = Math.abs(t - 6);
        float s = 1 + 0.22f * (float) Math.exp(-d1 * d1 / 3.0) + 0.12f * (float) Math.exp(-d2 * d2 / 3.0);
        var b = m.getAnimationProcessor().getBone("heart_beat");
        if (b != null) {
            b.setScaleX(s); b.setScaleY(s); b.setScaleZ(s);
            b.markScaleAsChanged();
        }
    }

    private static void rotX(Model<?> m, String bone, float deg) {
        var b = m.getAnimationProcessor().getBone(bone);
        if (b != null) {
            b.setRotX((float) Math.toRadians(-deg));
            b.markRotationAsChanged();
        }
    }

    /** The RenderProvider body shared by every relic weapon's createRenderer. */
    public static Object provider(String id) {
        return new software.bernie.geckolib.animatable.client.RenderProvider() {
            private RelicWeaponRenderer<?> renderer;

            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new RelicWeaponRenderer<>(id);
                return this.renderer;
            }
        };
    }
}
