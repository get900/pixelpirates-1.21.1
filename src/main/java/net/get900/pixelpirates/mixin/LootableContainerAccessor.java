package net.get900.pixelpirates.mixin;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The loot table a container still has to roll (null once opened) - BossHoards seals boss hoards by it. */
@Mixin(LootableContainerBlockEntity.class)
public interface LootableContainerAccessor {
    @Accessor("lootTableId")
    Identifier pp_getLootTableId();
}
