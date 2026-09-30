package net.get900.pixelpirates.entity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.DebrisEntity;
import net.get900.pixelpirates.entity.custom.PowderBargeEntity;
import net.get900.pixelpirates.entity.mob.LeviathanEntity;
import net.get900.pixelpirates.entity.mob.LeviathanSegmentEntity;
import net.get900.pixelpirates.entity.mob.LeviathanWakeEntity;
import net.get900.pixelpirates.entity.mob.ModMob;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Leviathan's renderers (boss 10/10). Its head and body pieces swap to their phase's model with the FORM (models
 * leviathan_head_p1..3, leviathan_body_p1..3, leviathan_tail_p1..3 from tools/mobs/leviathan.py): crusted and chained,
 * the raw hungry form of the reference render, the split and crowned form. The pieces pitch with the path (the serpent
 * dives and climbs), segments taper (SIZE). Bones toggled here: `crust` (phase 1, until blasted off), `shaft` (phase 3's
 * crown, until knocked loose). Plus the wake's fin, the powder barge and flung debris (drawn from real blocks).
 */
@Environment(EnvType.CLIENT)
public final class LeviathanRenderers {
    private LeviathanRenderers() {}

    private static final Map<Identifier, Boolean> EXISTS = new ConcurrentHashMap<>();

    static boolean exists(Identifier id) {
        return EXISTS.computeIfAbsent(id, t -> MinecraftClient.getInstance().getResourceManager().getResource(t).isPresent());
    }

    /** A model that follows the FORM: leviathan_PART_pN (+ _VARIANT skin when present). */
    static final class FormModel<T extends GeoAnimatable> extends GeoModel<T> {
        private final String part;
        FormModel(String part) { this.part = part; }

        int form(T a) {
            if (a instanceof LeviathanEntity l) return l.form();
            if (a instanceof LeviathanSegmentEntity s) return s.form();
            return 2;
        }

        String part(T a) { return a instanceof LeviathanSegmentEntity s && s.isTail() ? "tail" : part; }

        String base(T a) { return "leviathan_" + part(a) + "_p" + form(a); }

        @Override
        public Identifier getModelResource(T a) { return new Identifier(PixelPirates.MOD_ID, "geo/" + base(a) + ".geo.json"); }

        @Override
        public Identifier getTextureResource(T a) {
            if (a instanceof LeviathanEntity l && l.skinVariant() != null) {
                Identifier v = new Identifier(PixelPirates.MOD_ID, "textures/entity/" + base(a) + "_" + l.skinVariant() + ".png");
                if (exists(v)) return v;
            }
            return new Identifier(PixelPirates.MOD_ID, "textures/entity/" + base(a) + ".png");
        }

        @Override
        public Identifier getAnimationResource(T a) { return new Identifier(PixelPirates.MOD_ID, "animations/" + base(a) + ".animation.json"); }

        @Override
        public void setCustomAnimations(T a, long id, AnimationState<T> state) {
            super.setCustomAnimations(a, id, state);
            var crust = getAnimationProcessor().getBone("crust");
            if (crust != null) crust.setHidden(a instanceof LeviathanEntity l ? !l.headCrusted() : a instanceof LeviathanSegmentEntity s && !s.crusted());
            var shaft = getAnimationProcessor().getBone("shaft");
            if (shaft != null) shaft.setHidden(!(a instanceof LeviathanEntity l && l.crownShaft()));
        }
    }

    /** Pitch the model with its path (GeckoLib only yaws), then taper segments. */
    static <T extends net.minecraft.entity.LivingEntity & GeoAnimatable> GlowingMobRenderer<T> pitched(EntityRendererFactory.Context ctx, String part, float scale) {
        return new GlowingMobRenderer<>(ctx, new FormModel<>(part), scale, 0f) {
            @Override
            protected void applyRotations(T e, MatrixStack m, float age, float yaw, float tick) {
                super.applyRotations(e, m, age, yaw, tick);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-MathHelper.lerp(tick, e.prevPitch, e.getPitch())));
            }

            @Override
            public void scaleModelForRender(float w, float h, MatrixStack m, T e, software.bernie.geckolib.cache.object.BakedGeoModel model, boolean re,
                                            float tick, int light, int overlay) {
                float s = e instanceof LeviathanSegmentEntity seg ? seg.size() : 1f;
                super.scaleModelForRender(w * s, h * s, m, e, model, re, tick, light, overlay);
            }
        };
    }

    public static EntityRendererFactory<ModMob> head(float scale) { return ctx -> pitched(ctx, "head", scale); }

    public static EntityRendererFactory<LeviathanSegmentEntity> segment(float scale) { return ctx -> pitched(ctx, "body", scale); }

    /** The fin and back ridge breaking the surface while it flees. */
    public static EntityRendererFactory<LeviathanWakeEntity> wake(float scale) {
        return ctx -> {
            GeoEntityRenderer<LeviathanWakeEntity> r = new GeoEntityRenderer<>(ctx, new NamedGeoModel<>("leviathan_fin"));
            r.withScale(scale);
            r.addRenderLayer(new AutoGlowingGeoLayer<>(r));
            return r;
        };
    }

    /** Draws a block state as an entity (the powder barge's raft of barrels; flung wreckage). */
    static abstract class BlockEntityishRenderer<E extends Entity> extends EntityRenderer<E> {
        BlockEntityishRenderer(EntityRendererFactory.Context ctx) { super(ctx); this.shadowRadius = 0.6f; }

        void block(BlockState s, MatrixStack m, VertexConsumerProvider buffers, int light, double x, double y, double z, float sc) {
            m.push();
            m.translate(x, y, z);
            m.scale(sc, sc, sc);
            m.translate(-0.5, 0, -0.5);
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(s, m, buffers, light, OverlayTexture.DEFAULT_UV);
            m.pop();
        }

        @Override
        public Identifier getTexture(E entity) { return new Identifier("textures/atlas/blocks.png"); }
    }

    public static EntityRendererFactory<PowderBargeEntity> barge() {
        return ctx -> new BlockEntityishRenderer<>(ctx) {
            @Override
            public void render(PowderBargeEntity e, float yaw, float tick, MatrixStack m, VertexConsumerProvider buffers, int light) {
                m.push();
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerp(tick, e.prevYaw, e.getYaw())));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin((e.age + tick) * 0.1) * 3f));
                BlockState raft = Blocks.SPRUCE_SLAB.getDefaultState(), keg = Blocks.BARREL.getDefaultState(), tnt = Blocks.TNT.getDefaultState();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) block(raft, m, buffers, light, x * 0.85, 0, z * 0.85, 0.85f);
                block(keg, m, buffers, light, -0.45, 0.42, -0.45, 0.8f);
                block(keg, m, buffers, light, 0.45, 0.42, -0.45, 0.8f);
                block(tnt, m, buffers, light, -0.45, 0.42, 0.45, 0.8f);
                block(keg, m, buffers, light, 0.45, 0.42, 0.45, 0.8f);
                block(keg, m, buffers, light, 0, 1.2, 0, 0.7f);
                block(Blocks.RED_BANNER.getDefaultState(), m, buffers, light, 0, 1.9, 0, 0.6f);
                m.pop();
                super.render(e, yaw, tick, m, buffers, light);
            }
        };
    }

    public static EntityRendererFactory<DebrisEntity> debris() {
        return ctx -> new BlockEntityishRenderer<>(ctx) {
            @Override
            public void render(DebrisEntity e, float yaw, float tick, MatrixStack m, VertexConsumerProvider buffers, int light) {
                m.push();
                float spin = (e.age + tick) * 18f;
                m.translate(0, 0.8, 0);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin * 0.7f));
                block(e.look(), m, buffers, light, 0, -0.8, 0, 1.6f);
                m.pop();
                super.render(e, yaw, tick, m, buffers, light);
            }
        };
    }
}
