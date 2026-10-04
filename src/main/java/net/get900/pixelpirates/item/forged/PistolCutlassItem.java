package net.get900.pixelpirates.item.forged;

import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.homestead.gun.MusketBallEntity;
import net.get900.pixelpirates.world.SkillEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * PISTOL CUTLASS (P3, Corsair Cutlass + a Flintlock Pistol + obsidian): a cutlass with a pistol in the guard. Right-click
 * fires one ball (11 damage, uses a Paper Cartridge); the reload is the cooldown (2.5 s, shortened by Quick Hands).
 */
public class PistolCutlassItem extends ForgedBlade {
    public PistolCutlassItem(ToolMaterial m, int dmg, float speed, Settings s) {
        super(m, dmg, speed, s, "Right-click: fire the guard pistol (11 damage)", "Uses a Paper Cartridge, reloads in 2.5 s");
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);
        boolean creative = user.getAbilities().creativeMode;
        ItemStack ammo = ItemStack.EMPTY;
        for (int i = 0; i < user.getInventory().size() && !creative; i++)
            if (user.getInventory().getStack(i).isOf(HomesteadItems.PAPER_CARTRIDGE)) { ammo = user.getInventory().getStack(i); break; }
        if (!creative && ammo.isEmpty()) {
            user.sendMessage(Text.literal("No Paper Cartridge for the guard pistol").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }
        if (!creative) ammo.decrement(1);
        ServerWorld sw = (ServerWorld) world;
        MusketBallEntity b = new MusketBallEntity(sw, user, 11f, 26, false);
        b.setVelocity(user, user.getPitch(), user.getYaw(), 0f, 4.2f, 0.8f * SkillEffects.spreadMult(user));
        sw.spawnEntity(b);
        Vec3d m = user.getEyePos().add(user.getRotationVec(1f)).add(0, -0.2, 0);
        sw.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 1.8f);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 6, 0.1, 0.1, 0.1, 0.02);
        sw.spawnParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 4, 0.05, 0.05, 0.05, 0.02);
        user.getItemCooldownManager().set(this, Math.max(16, (int) Math.round(50 * SkillEffects.reloadMult(user))));
        stack.damage(1, user, p -> p.sendToolBreakStatus(hand));
        return TypedActionResult.success(stack, false);
    }
}
