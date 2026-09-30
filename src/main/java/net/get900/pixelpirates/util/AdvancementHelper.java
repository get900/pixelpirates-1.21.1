package net.get900.pixelpirates.util;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;

/** One-line helper so grant calls don't clutter gameplay code. */
public class AdvancementHelper {
    public static void grant(ServerPlayerEntity player, String id) {
        Advancement adv = player.getServer().getAdvancementLoader().get(PixelPirates.id(id));
        if (adv == null) return;
        PlayerAdvancementTracker tracker = player.getAdvancementTracker();
        if (!tracker.getProgress(adv).isDone()) {
            tracker.grantCriterion(adv, "got_here");
        }
    }
}
