package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.world.PirateXp;
import net.get900.pixelpirates.world.SkillEffects;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first time a player opens a loot chest/barrel (its loot table is still unrolled):
 *  - Pirate XP (PirateXp.lootChest);
 *  - TREASURE HUNTER / PLUNDERER: the table may be rolled a second time into the same container (world/SkillEffects).
 */
@Mixin(LootableContainerBlockEntity.class)
public abstract class LootChestXpMixin {
    @Shadow @Nullable protected Identifier lootTableId;
    @Shadow protected long lootTableSeed;

    @Unique @Nullable private Identifier pp_pendingTable;
    @Unique private long pp_pendingSeed;

    @Inject(method = "checkLootInteraction", at = @At("HEAD"))
    private void pp_lootXp(@Nullable PlayerEntity player, CallbackInfo ci) {
        pp_pendingTable = null;
        if (net.get900.pixelpirates.entity.mob.BossHoards.sealedFor((LootableContainerBlockEntity) (Object) this, player)) return;
        if (this.lootTableId != null && player instanceof ServerPlayerEntity sp && !sp.isSpectator()) {
            PirateXp.lootChest(sp, this.lootTableId);
            pp_pendingTable = this.lootTableId;
            pp_pendingSeed = this.lootTableSeed;
        }
    }

    @Inject(method = "checkLootInteraction", at = @At("RETURN"))
    private void pp_doubleLoot(@Nullable PlayerEntity player, CallbackInfo ci) {
        Identifier table = pp_pendingTable;
        pp_pendingTable = null;
        if (table == null || this.lootTableId != null || !(player instanceof ServerPlayerEntity sp)) return;
        net.get900.pixelpirates.homestead.parrot.ParrotCompanion.gildedFind(sp, (Inventory) (Object) this);   // parrot types phase 2
        boolean hoard = table.getNamespace().equals("pixelpirates") && table.getPath().contains("hoard");
        if (!SkillEffects.doubleLoot(sp, hoard)) return;
        var loot = sp.getServer().getLootManager().getLootTable(table);
        var params = new LootContextParameterSet.Builder(sp.getServerWorld())
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(((BlockEntity) (Object) this).getPos()))
                .luck(sp.getLuck())
                .add(LootContextParameters.THIS_ENTITY, sp)
                .build(LootContextTypes.CHEST);
        loot.supplyInventory((Inventory) (Object) this, params, pp_pendingSeed ^ 0x5DEECE66DL);
        sp.sendMessage(Text.literal(hoard && SkillEffects.lvl(sp, "plunderer") > 0 ? "Plunderer: the hoard is twice as deep!"
                : "Treasure Hunter: double loot!").formatted(Formatting.GOLD), true);
    }
}
