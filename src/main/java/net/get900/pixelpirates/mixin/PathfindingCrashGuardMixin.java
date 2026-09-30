package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

/**
 * Guards against a Valkyrien Skies 2.4.0 crash that has killed this server repeatedly since
 * 2026-05-23 (11 crash reports, always {@code VillagerEntity.mobTick}).
 *
 * <p>VS2's {@code MixinPathFinder.onCollectPath} wraps the {@code collect(...)} inside
 * {@link PathNodeNavigator#findPathToAny} and substitutes its own two-argument
 * {@code Collectors.toMap(keyFn, Function.identity())} — a form with <b>no merge function</b>.
 * Its key function converts each candidate {@link BlockPos} into a path {@code Node}, translating
 * ship-space blocks into world space on the way. When a genuine world block and a shipyard block
 * translate onto the same node, {@code toMap} throws
 * {@code IllegalStateException: Duplicate key Node{...}} and the whole server tick dies.
 *
 * <p>The bug is upstream and unreachable from our code — it lives inside a mixin handler injected
 * into a vanilla class, so it cannot be overridden or patched directly. What we <i>can</i> do is
 * intercept the one vanilla call site that reaches it and degrade gracefully.
 *
 * <p>Returning {@code null} is safe and non-invasive: vanilla's
 * {@code EntityNavigation.findPathToAny} already null-checks this exact call and treats null as
 * "no path found". The affected mob simply skips pathing for one tick and retries on the next.
 */
@Mixin(EntityNavigation.class)
public class PathfindingCrashGuardMixin {

    /** Wall-clock of the last logged suppression, so a hot path cannot flood the log. */
    @Unique
    private static long pixelpirates$lastPathWarning = 0L;

    @Unique
    private static final long PIXELPIRATES$WARN_INTERVAL_MS = 60_000L;

    @Redirect(
            method = "findPathToAny(Ljava/util/Set;IZIF)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/PathNodeNavigator;findPathToAny("
                            + "Lnet/minecraft/world/chunk/ChunkCache;"
                            + "Lnet/minecraft/entity/mob/MobEntity;"
                            + "Ljava/util/Set;FIF)"
                            + "Lnet/minecraft/entity/ai/pathing/Path;"
            )
    )
    private Path pixelpirates$guardAgainstVs2NodeCollision(
            PathNodeNavigator navigator, ChunkCache chunkCache, MobEntity mob,
            Set<BlockPos> positions, float followRange, int distance, float rangeMultiplier) {
        try {
            return navigator.findPathToAny(chunkCache, mob, positions, followRange, distance, rangeMultiplier);
        } catch (IllegalStateException e) {
            String message = e.getMessage();
            // Only swallow the known VS2 collision — anything else is a real bug and must surface
            if (message == null || !message.startsWith("Duplicate key Node")) {
                throw e;
            }

            long now = System.currentTimeMillis();
            if (now - pixelpirates$lastPathWarning > PIXELPIRATES$WARN_INTERVAL_MS) {
                pixelpirates$lastPathWarning = now;
                PixelPirates.LOGGER.warn(
                        "Suppressed a Valkyrien Skies pathfinding node collision near {} at {}. "
                                + "The mob skipped pathing this tick instead of crashing the server. "
                                + "Cause: {}",
                        mob.getType().getTranslationKey(), mob.getBlockPos(), message);
            }
            return null;
        }
    }
}
