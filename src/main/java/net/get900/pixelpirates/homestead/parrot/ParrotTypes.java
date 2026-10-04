package net.get900.pixelpirates.homestead.parrot;

import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * PARROT TYPES (2026-10-03, docs/parrot_ideas.md): the five vanilla colours plus our own, each with a rarity and a weight
 * (tickets in the aviary's daily draw). A custom type is saved on the parrot as "PPType" (ParrotTypeMixin) and drawn with
 * textures/entity/parrot/<id>.png (tools/gen_parrot_textures.py) on top of a vanilla base variant; a vanilla type is just
 * the vanilla Variant with no PPType. Abilities per type come in phase 2/3.
 */
public final class ParrotTypes {
    private ParrotTypes() {}

    public enum Tier {
        COMMON("Common", Formatting.WHITE), UNCOMMON("Uncommon", Formatting.GREEN), RARE("Rare", Formatting.AQUA),
        VERY_RARE("Very Rare", Formatting.LIGHT_PURPLE), LEGENDARY("Legendary", Formatting.GOLD);
        public final String label;
        public final Formatting colour;
        Tier(String label, Formatting colour) { this.label = label; this.colour = colour; }
    }

    /** custom = has its own texture (saved as PPType); base = the vanilla variant underneath (and the vanilla look). */
    public record PType(String id, String name, Tier tier, int weight, ParrotEntity.Variant base, boolean custom) {
        public Identifier texture() { return new Identifier("pixelpirates", "textures/entity/parrot/" + id + ".png"); }

        /** Has a full-bright glow layer (<id>_glow.png, phase 2). */
        public boolean glows() { return id.equals("ghost_parrot") || id.equals("ember_macaw") || id.equals("krakens_pet"); }

        public Identifier glowTexture() { return new Identifier("pixelpirates", "textures/entity/parrot/" + id + "_glow.png"); }
    }

    private static PType v(String id, String name, Tier t, int w, ParrotEntity.Variant base) { return new PType(id, name, t, w, base, false); }
    private static PType c(String id, String name, Tier t, int w) { return new PType(id, name, t, w, ParrotEntity.Variant.GRAY, true); }

    public static final List<PType> ALL = List.of(
            v("green_parakeet", "Green Parakeet", Tier.COMMON, 30, ParrotEntity.Variant.GREEN),
            v("grey_parrot", "Grey Parrot", Tier.COMMON, 25, ParrotEntity.Variant.GRAY),
            c("sunset_conure", "Sunset Conure", Tier.COMMON, 20),
            v("scarlet_macaw", "Scarlet Macaw", Tier.UNCOMMON, 15, ParrotEntity.Variant.RED_BLUE),
            c("cockatoo", "Cockatoo", Tier.UNCOMMON, 12),
            c("rainbow_lorikeet", "Rainbow Lorikeet", Tier.UNCOMMON, 10),
            c("cockatiel", "Cockatiel", Tier.UNCOMMON, 10),
            v("blue_and_gold_macaw", "Blue-and-Gold Macaw", Tier.RARE, 8, ParrotEntity.Variant.YELLOW_BLUE),
            c("black_palm_cockatoo", "Black Palm Cockatoo", Tier.RARE, 6),
            c("kakapo", "Kakapo", Tier.RARE, 5),
            v("hyacinth_macaw", "Hyacinth Macaw", Tier.VERY_RARE, 3, ParrotEntity.Variant.BLUE),
            c("ghost_parrot", "Ghost Parrot", Tier.VERY_RARE, 2),
            c("gilded_parrot", "Gilded Parrot", Tier.VERY_RARE, 2),
            c("ember_macaw", "Ember Macaw", Tier.LEGENDARY, 1),
            c("krakens_pet", "Kraken's Pet", Tier.LEGENDARY, 1));

    public static PType byId(String id) {
        for (PType t : ALL) if (t.id().equals(id)) return t;
        return null;
    }

    /** The type a parrot is: its PPType if it has one, else its vanilla variant's. */
    public static PType of(ParrotEntity p) {
        String id = ((ParrotTypeHolder) p).pixelpirates$getParrotType();
        PType t = id.isEmpty() ? null : byId(id);
        if (t != null) return t;
        for (PType v : ALL) if (!v.custom() && v.base() == p.getVariant()) return v;
        return ALL.get(0);
    }

    public static void apply(ParrotEntity p, PType t) {
        p.setVariant(t.base());
        ((ParrotTypeHolder) p).pixelpirates$setParrotType(t.custom() ? t.id() : "");
    }

    /** Draw one type by weight, from those at or above {@code min}. nextInt = a bound -> [0, bound) source. */
    public static PType roll(Tier min, IntUnaryOperator nextInt) { return roll(min, nextInt, java.util.Set.of()); }

    /** ...leaving out the ids in {@code skip} (types the player already owns); null when nothing is left. */
    public static PType roll(Tier min, IntUnaryOperator nextInt, java.util.Set<String> skip) {
        int total = 0;
        for (PType t : ALL) if (t.tier().ordinal() >= min.ordinal() && !skip.contains(t.id())) total += t.weight();
        if (total == 0) return null;
        int n = nextInt.applyAsInt(total);
        for (PType t : ALL) if (t.tier().ordinal() >= min.ordinal() && !skip.contains(t.id()) && (n -= t.weight()) < 0) return t;
        return null;
    }
}
