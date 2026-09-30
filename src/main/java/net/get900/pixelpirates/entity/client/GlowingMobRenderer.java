package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.MimicEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer for the generated mobs (Siren, Coral Jelly, Magma Brute, Mimic, Abyssal Angler).
 * Adds GeckoLib's emissive layer, which reads textures/entity/NAME_glowmask.png - eyes, lures,
 * lava cracks and jelly fins stay full-bright in the dark.
 *
 * Scale here does NOT move the hitbox: each factory's scale is paired with the
 * EntityDimensions of the same mob in ModEntities (keep the two comments in sync).
 */
public class GlowingMobRenderer<T extends LivingEntity & GeoAnimatable> extends GeoEntityRenderer<T> {

    public GlowingMobRenderer(EntityRendererFactory.Context ctx, GeoModel<T> model, float scale, float shadow) {
        super(ctx, model);
        withScale(scale);
        this.shadowRadius = shadow;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(MatrixStack poseStack, T animatable, BakedGeoModel bakedModel, RenderLayer renderType,
                               VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick,
                               int packedLight, int packedOverlay) {
                // GeckoLib 4.4.9 THROWS on a glowmask with no lit pixels ("Invalid glow layer texture
                // provided") - crash of 2026-09-28. A dormant mimic has nothing that glows, and some
                // mobs (raft pirate, kraken tentacle...) have no emissive parts at all; the generators
                // never write an empty mask, so "mask file missing" == "nothing glows" -> skip the layer.
                if (animatable instanceof MimicEntity mimic && mimic.isDormant()) return;
                if (!hasGlowmask(getTextureLocation(animatable))) return;
                super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
            }
        });
    }

    /** Rooted mobs (kraken, its arms, tentacles) don't topple sideways when they die - they play their own death clip. */
    @Override
    protected float getDeathMaxRotation(T animatable) {
        if (animatable instanceof net.get900.pixelpirates.entity.mob.ModMob m
                && m.spec().kind() == net.get900.pixelpirates.entity.mob.MobSpec.Kind.STATIONARY) return 0f;
        return super.getDeathMaxRotation(animatable);
    }

    private static final java.util.Map<Identifier, Boolean> GLOWMASKS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Does textures/entity/NAME_glowmask.png exist for this texture? (cached; resource packs rarely change it) */
    private static boolean hasGlowmask(Identifier texture) {
        return GLOWMASKS.computeIfAbsent(texture, t -> {
            String path = t.getPath();
            Identifier mask = new Identifier(t.getNamespace(), path.substring(0, path.length() - 4) + "_glowmask.png");
            return net.minecraft.client.MinecraftClient.getInstance().getResourceManager().getResource(mask).isPresent();
        });
    }

    /**
     * Skin variants: a ModMob reporting {@link net.get900.pixelpirates.entity.mob.ModMob#skinVariant} (bosses: "enraged";
     * the Molten Warlord also "quenched") renders textures/entity/NAME_VARIANT.png when that file exists, with its own
     * NAME_VARIANT_glowmask.png. Same geo, so every skin must share the UV layout (gen_mob_roster.py SKINS checks it).
     */
    public static <T extends LivingEntity & GeoAnimatable> EntityRendererFactory<T> of(String name, float scale, float shadow) {
        return ctx -> new GlowingMobRenderer<>(ctx, model(name, null), scale, shadow);
    }

    /** Per-frame bone posing on top of the animation (runs after toggleBones). */
    // Deliberately NOT generic: a lambda typed with the intersection T (LivingEntity & GeoAnimatable) links to a mismatched
    // erasure at runtime (LambdaConversionException -> the whole resource reload fails; crash 2026-09-30).
    @FunctionalInterface
    public interface Pose { void pose(LivingEntity animatable, software.bernie.geckolib.core.animation.AnimationProcessor<?> bones); }

    /** The shared roster model: skin variants + ModMob bone toggles (+ an optional per-frame pose). */
    static <T extends LivingEntity & GeoAnimatable> NamedGeoModel<T> model(String name, Pose pose) {
        java.util.Map<String, Identifier> variants = new java.util.concurrent.ConcurrentHashMap<>();
        return new NamedGeoModel<T>(name) {
            @Override
            public Identifier getTextureResource(T animatable) {
                if (animatable instanceof net.get900.pixelpirates.entity.mob.ModMob mob) {
                    String v = mob.skinVariant();
                    if (v != null) {
                        Identifier tex = variants.computeIfAbsent(v, k -> new Identifier(PixelPirates.MOD_ID, "textures/entity/" + name + "_" + k + ".png"));
                        if (exists(tex)) return tex;
                    }
                }
                return super.getTextureResource(animatable);
            }

            @Override
            public void setCustomAnimations(T animatable, long instanceId, software.bernie.geckolib.core.animation.AnimationState<T> state) {
                super.setCustomAnimations(animatable, instanceId, state);
                if (!(animatable instanceof net.get900.pixelpirates.entity.mob.ModMob mob)) return;
                for (String bone : mob.toggleBones()) {
                    var b = getAnimationProcessor().getBone(bone);
                    if (b != null) b.setHidden(mob.isBoneHidden(bone));
                }
                if (pose != null) pose.pose(animatable, getAnimationProcessor());
            }
        };
    }

    /**
     * Ghosts (the Heart's phantasms, the Last Keeper): drawn translucent, and not at all for a player they are invisible to -
     * GeckoLib ignores entity invisibility, so a phantasm would otherwise show up for everyone.
     */
    public static <T extends LivingEntity & GeoAnimatable> EntityRendererFactory<T> ghost(String name, float scale) {
        return ctx -> new GlowingMobRenderer<T>(ctx, model(name, null), scale, 0f) {
            @Override
            public void render(T entity, float yaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider buffers, int light) {
                var me = net.minecraft.client.MinecraftClient.getInstance().player;
                if (me != null && entity.isInvisibleTo(me)) return;
                super.render(entity, yaw, partialTick, poseStack, buffers, light);
            }

            @Override
            public RenderLayer getRenderType(T animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
                return RenderLayer.getEntityTranslucent(texture);
            }
        };
    }

    /**
     * The Eye of Thalassar: its iris (bone "iris", pivot at the eyeball's centre) turns to follow THIS client's player -
     * everyone in the chest feels watched. Positive rotY swings the front toward the entity's left (world +X at yaw 0),
     * positive rotX lifts it (GeckoLib bakes the model facing -Z and the renderer turns it 180 - yaw).
     */
    public static <T extends LivingEntity & GeoAnimatable> EntityRendererFactory<T> rivalEye(float scale) {
        return ctx -> new GlowingMobRenderer<>(ctx, GlowingMobRenderer.<T>model("rival_eye", (LivingEntity eye, software.bernie.geckolib.core.animation.AnimationProcessor<?> bones) -> {
            var iris = bones.getBone("iris");
            var me = net.minecraft.client.MinecraftClient.getInstance().player;
            if (iris == null || me == null) return;
            if (eye instanceof net.get900.pixelpirates.entity.mob.RivalEyeEntity r && r.state() != net.get900.pixelpirates.entity.mob.RivalEyeEntity.OPEN
                    && r.state() != net.get900.pixelpirates.entity.mob.RivalEyeEntity.STIR) return;
            double yaw = Math.toRadians(eye.bodyYaw);
            double dx = me.getX() - eye.getX(), dy = me.getEyeY() - (eye.getY() + eye.getHeight() * 0.5), dz = me.getZ() - eye.getZ();
            double fwd = -Math.sin(yaw) * dx + Math.cos(yaw) * dz, left = Math.cos(yaw) * dx + Math.sin(yaw) * dz;
            float ry = (float) net.minecraft.util.math.MathHelper.clamp(Math.atan2(left, Math.max(1, fwd)), -0.32, 0.32);
            float rx = (float) net.minecraft.util.math.MathHelper.clamp(Math.atan2(dy, Math.max(1, Math.sqrt(fwd * fwd + left * left))), -0.26, 0.26);
            // absolute from the rest pose (never animated) - adding to getRotY() would accumulate frame after frame
            iris.setRotY(iris.getInitialSnapshot().getRotY() + ry);
            iris.setRotX(iris.getInitialSnapshot().getRotX() + rx);
        }), scale, 0f);
    }

    private static final java.util.Map<Identifier, Boolean> EXISTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean exists(Identifier tex) {
        return EXISTS.computeIfAbsent(tex, t -> net.minecraft.client.MinecraftClient.getInstance().getResourceManager().getResource(t).isPresent());
    }

    /** Mimic: plain chest texture while dormant (glow layer skipped - see constructor), eyes when awake. */
    public static EntityRendererFactory<MimicEntity> mimic() {
        Identifier dormant = new Identifier(PixelPirates.MOD_ID, "textures/entity/mimic_dormant.png");
        Identifier awake = new Identifier(PixelPirates.MOD_ID, "textures/entity/mimic_awake.png");
        return ctx -> new GlowingMobRenderer<>(ctx, new NamedGeoModel<MimicEntity>("mimic") {
            @Override
            public Identifier getTextureResource(MimicEntity mimic) {
                return mimic.isDormant() ? dormant : awake;
            }
        }, 1.0f, 0.55f);
    }
}
