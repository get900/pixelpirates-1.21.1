package net.get900.pixelpirates.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.world.ZoneEffectsClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeParticleConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/**
 * /pptest clearsight: suppress biome ambient particles (the ash / warped-spore haze of phases 4-5)
 * without touching block display ticks (torch flames etc. in the same method keep working).
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public class ClientWorldParticleMixin {

    @Redirect(method = "randomBlockDisplayTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/Biome;getParticleConfig()Ljava/util/Optional;"))
    private Optional<BiomeParticleConfig> pixelpirates_clearSightParticles(Biome biome) {
        return ZoneEffectsClient.clearSight ? Optional.empty() : biome.getParticleConfig();
    }
}
