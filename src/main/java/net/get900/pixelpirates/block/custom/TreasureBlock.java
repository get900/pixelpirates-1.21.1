package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.entity.mob.BossHoards;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.homestead.hoard.Strongboxes;
import net.get900.pixelpirates.world.PlayerProgressionManager;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * TREASURE BLOCK (2026-10-01, user note): the treasure piled in the middle of a lair's loot room. Right-click it once and
 * the strongbox reel spins for you (Strongboxes.spin) - free, ONCE PER PLAYER per block. The tier follows the waters it
 * sits in (zones 1-2 common, 3-4 rare, 5 legendary). It is sealed while a boss hoard beside it is still sealed to you
 * (BossHoards), and the ones decorating Wavebreak Port are just decoration.
 */
public class TreasureBlock extends Block {
    private static final int SPAWN_RADIUS = 220;
    /** The opening show (2026-10-03): server -> clients near the heap: pos + tier -> TreasureFx (client) fireworks. */
    public static final Identifier FX = new Identifier("pixelpirates", "treasure_fx");
    /** The heap (tools/gen_treasure_assets.py): the mound + the chest at the back. */
    private static final VoxelShape SHAPE = VoxelShapes.union(Block.createCuboidShape(1, 0, 1, 15, 5.5, 15),
            Block.createCuboidShape(4, 5.5, 4, 12, 9, 11), Block.createCuboidShape(8, 5.5, 9.5, 15, 10, 14.5));

    public TreasureBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) return ActionResult.SUCCESS;
        boolean pp = sw.getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD);
        if (pp && Math.abs(pos.getX()) < SPAWN_RADIUS && Math.abs(pos.getZ()) < SPAWN_RADIUS) return ActionResult.PASS;   // port decoration
        if (BossHoards.sealedNear(sw, pos, sp, 10)) {
            sp.sendMessage(Text.literal("The treasure is bound to this lair's master - you are not ready for it.").formatted(Formatting.DARK_PURPLE), true);
            return ActionResult.CONSUME;
        }
        String flag = "treasure_spin:" + sw.getRegistryKey().getValue() + ":" + pos.asLong() + ":" + sp.getUuidAsString();
        HomesteadState st = HomesteadState.get(sw.getServer());
        if (st.flag(flag)) {
            sp.sendMessage(Text.literal("You have already taken your share of this treasure.").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        if (Strongboxes.busy(sp)) return ActionResult.CONSUME;
        st.setFlag(flag);
        int zone = pp ? PlayerProgressionManager.getZoneAt(pos.toCenterPos()) : 1;
        int tier = zone >= 5 ? 2 : zone >= 3 ? 1 : 0;
        Strongboxes.spin(sp, tier, pos);
        celebrate(sw, pos, tier);
        return ActionResult.CONSUME;
    }

    /** The show: coins burst out of the heap, chimes, and fireworks (client-side bursts - no rocket, so nobody is hurt
     *  in a low lair room). Tier 0 gold, 1 gold + aqua, 2 gold + purple + white and more of them. */
    private static void celebrate(ServerWorld w, BlockPos pos, int tier) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.7, z = pos.getZ() + 0.5;
        w.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(net.get900.pixelpirates.item.ModItems.COIN)),
                x, y, z, 30 + 20 * tier, 0.3, 0.2, 0.3, 0.35);
        w.spawnParticles(ParticleTypes.WAX_ON, x, y + 0.4, z, 25, 0.5, 0.4, 0.5, 0.6);
        w.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.2f, 1.2f);
        w.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.7f, 1.4f);
        for (ServerPlayerEntity p : PlayerLookup.around(w, pos.toCenterPos(), 64)) {
            PacketByteBuf buf = PacketByteBufs.create();                                 // one buffer per send
            buf.writeBlockPos(pos);
            buf.writeVarInt(tier);
            ServerPlayNetworking.send(p, FX, buf);
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) { return SHAPE; }

    /** Now and then a glint off the gold. */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) != 0) return;
        world.addParticle(ParticleTypes.WAX_ON, pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 0.3 + random.nextDouble() * 0.5,
                pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0, 0.02, 0);
    }
}
