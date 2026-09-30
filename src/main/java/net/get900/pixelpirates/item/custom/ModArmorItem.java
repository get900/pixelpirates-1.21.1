package net.get900.pixelpirates.item.custom;

import com.google.common.collect.ImmutableMap;
import net.get900.pixelpirates.item.ModArmorMaterials;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class ModArmorItem extends PixelArmorItem {
    private static final Map<ArmorMaterial, List<StatusEffectInstance>> MATERIAL_TO_EFFECT_MAP =
            (new ImmutableMap.Builder<ArmorMaterial, List<StatusEffectInstance>>())
                    .put(ModArmorMaterials.PIRATE_ARMOR,
                            List.of(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 400, 3, false, false),
                                    new StatusEffectInstance(StatusEffects.SPEED, 400, 0, false, false)))
                    // Ring 2 — Navy Officer: merchants respect the uniform (better trades)
                    .put(ModArmorMaterials.NAVY_OFFICER,
                            List.of(new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 400, 0, false, false)))
                    // Ring 3 — Corsair: boarding agility
                    .put(ModArmorMaterials.CORSAIR,
                            List.of(new StatusEffectInstance(StatusEffects.SPEED, 400, 0, false, false),
                                    new StatusEffectInstance(StatusEffects.JUMP_BOOST, 400, 0, false, false)))
                    // Volcanic — Ashen: walk through fire
                    .put(ModArmorMaterials.ASHEN,
                            List.of(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 400, 0, false, false)))
                    // Ring 4 — Cursed Bone: see through the ghost fog, shrug off blows
                    .put(ModArmorMaterials.CURSED_BONE,
                            List.of(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false),
                                    new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 0, false, false)))
                    // Ring 5 — Kraken-Scale: the abyss claims its own
                    .put(ModArmorMaterials.KRAKEN_SCALE,
                            List.of(new StatusEffectInstance(StatusEffects.CONDUIT_POWER, 400, 0, false, false),
                                    new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 400, 0, false, false)))
                    .build();

    public ModArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient()) {
            if (entity instanceof PlayerEntity player) {
                if (hasFullSuitOfArmorOn(player)) {
                    evaluateArmorEffects(player);
                }
            }
        }
        super.inventoryTick(stack, world, entity, slot, selected);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (MATERIAL_TO_EFFECT_MAP.containsKey(getMaterial())) {
            tooltip.add(Text.translatable("tooltip.pixelpirates.set_bonus." + getMaterial().getName())
                    .formatted(Formatting.GOLD));
        }
        super.appendTooltip(stack, world, tooltip, context);
    }

    private void evaluateArmorEffects(PlayerEntity player) {
        for (Map.Entry<ArmorMaterial, List<StatusEffectInstance>> entry : MATERIAL_TO_EFFECT_MAP.entrySet()) {
            ArmorMaterial mapArmorMaterial = entry.getKey();
            List<StatusEffectInstance> mapStatusEffects = entry.getValue();
            if (hasCorrectArmorOn(mapArmorMaterial, player)) {
                addStatusEffectForMaterial(player, mapStatusEffects);
            }
        }
    }

    private void addStatusEffectForMaterial(PlayerEntity player, List<StatusEffectInstance> mapStatusEffect) {
        // Refresh before effects run low — stops Night Vision's end-of-effect screen flashing.
        boolean needsRefresh = mapStatusEffect.stream().anyMatch(e -> {
            StatusEffectInstance current = player.getStatusEffect(e.getEffectType());
            return current == null || current.getDuration() < 210;
        });
        if (needsRefresh) {
            for (StatusEffectInstance instance : mapStatusEffect) {
                player.addStatusEffect(new StatusEffectInstance(instance.getEffectType(),
                        instance.getDuration(), instance.getAmplifier(), instance.isAmbient(), instance.shouldShowParticles()));
            }
        }
    }

    private boolean hasFullSuitOfArmorOn(PlayerEntity player) {
        ItemStack boots = player.getInventory().getArmorStack(0);
        ItemStack leggings = player.getInventory().getArmorStack(1);
        ItemStack breastplate = player.getInventory().getArmorStack(2);
        ItemStack helmet = player.getInventory().getArmorStack(3);
        return !helmet.isEmpty() && !breastplate.isEmpty() && !leggings.isEmpty() && !boots.isEmpty();
    }

    private boolean hasCorrectArmorOn(ArmorMaterial material, PlayerEntity player) {
        for (ItemStack armorStack : player.getInventory().armor) {
            if (!(armorStack.getItem() instanceof ArmorItem)) return false;
        }
        ArmorItem boots = (ArmorItem) player.getInventory().getArmorStack(0).getItem();
        ArmorItem leggings = (ArmorItem) player.getInventory().getArmorStack(1).getItem();
        ArmorItem breastplate = (ArmorItem) player.getInventory().getArmorStack(2).getItem();
        ArmorItem helmet = (ArmorItem) player.getInventory().getArmorStack(3).getItem();
        return helmet.getMaterial() == material && breastplate.getMaterial() == material
                && leggings.getMaterial() == material && boots.getMaterial() == material;
    }
}
