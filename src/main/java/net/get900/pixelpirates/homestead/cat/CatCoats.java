package net.get900.pixelpirates.homestead.cat;

import net.get900.pixelpirates.homestead.parrot.ParrotTypes.Tier;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * SHIP'S CATS (2026-10-04, townhouse #36 the ship's-cat keeper): the eleven vanilla coats plus eight of our own, each with
 * a rarity and a weight (tickets in the cattery's daily draw) - the parrots' scheme (ParrotTypes), the same tiers. A custom
 * coat is saved on the cat as "PPCoat" (CatCoatMixin) and drawn with textures/entity/cat/<id>.png
 * (tools/gen_cat_textures.py) over a vanilla base variant; a vanilla coat is just the vanilla variant with no PPCoat.
 */
public final class CatCoats {
    private CatCoats() {}

    public record Coat(String id, String name, Tier tier, int weight, RegistryKey<CatVariant> base, boolean custom) {
        public Identifier texture() { return new Identifier("pixelpirates", "textures/entity/cat/" + id + ".png"); }

        /** Has a full-bright glow layer (<id>_glow.png). */
        public boolean glows() { return id.equals("ghost_cat") || id.equals("sea_witch"); }

        public Identifier glowTexture() { return new Identifier("pixelpirates", "textures/entity/cat/" + id + "_glow.png"); }

        /** The Luck a ship's cat of this coat gives (effect amplifier): rare and better = Luck II. */
        public int luck() { return tier.ordinal() >= Tier.RARE.ordinal() ? 1 : 0; }
    }

    private static Coat v(String id, String name, Tier t, int w, RegistryKey<CatVariant> base) { return new Coat(id, name, t, w, base, false); }
    private static Coat c(String id, String name, Tier t, int w, RegistryKey<CatVariant> base) { return new Coat(id, name, t, w, base, true); }

    public static final List<Coat> ALL = List.of(
            v("tabby", "Tabby", Tier.COMMON, 22, CatVariant.TABBY),
            v("ginger", "Ginger", Tier.COMMON, 20, CatVariant.RED),
            v("tuxedo", "Tuxedo", Tier.COMMON, 18, CatVariant.BLACK),
            c("marmalade", "Marmalade", Tier.COMMON, 16, CatVariant.RED),
            v("white", "White", Tier.COMMON, 12, CatVariant.WHITE),
            v("british_shorthair", "British Shorthair", Tier.UNCOMMON, 10, CatVariant.BRITISH_SHORTHAIR),
            v("calico", "Calico", Tier.UNCOMMON, 10, CatVariant.CALICO),
            v("siamese", "Siamese", Tier.UNCOMMON, 8, CatVariant.SIAMESE),
            c("tortoiseshell", "Tortoiseshell", Tier.UNCOMMON, 8, CatVariant.CALICO),
            c("smoke", "Smoke", Tier.UNCOMMON, 8, CatVariant.BRITISH_SHORTHAIR),
            c("snowshoe", "Snowshoe", Tier.UNCOMMON, 7, CatVariant.SIAMESE),
            v("persian", "Persian", Tier.RARE, 6, CatVariant.PERSIAN),
            v("ragdoll", "Ragdoll", Tier.RARE, 6, CatVariant.RAGDOLL),
            c("bengal", "Bengal", Tier.RARE, 5, CatVariant.TABBY),
            v("jellie", "Jellie", Tier.RARE, 4, CatVariant.JELLIE),
            v("midnight", "Midnight", Tier.VERY_RARE, 3, CatVariant.ALL_BLACK),
            c("ghost_cat", "Ghost Cat", Tier.VERY_RARE, 2, CatVariant.WHITE),
            c("gilded_cat", "Gilded Cat", Tier.VERY_RARE, 2, CatVariant.BRITISH_SHORTHAIR),
            c("sea_witch", "Sea Witch's Cat", Tier.LEGENDARY, 1, CatVariant.ALL_BLACK));

    public static Coat byId(String id) {
        for (Coat t : ALL) if (t.id().equals(id)) return t;
        return null;
    }

    /** The coat a cat wears: its PPCoat if it has one, else its vanilla variant's. */
    public static Coat of(CatEntity cat) {
        Coat t = byId(((CatCoatHolder) cat).pixelpirates$getCoat());
        if (t != null && t.custom()) return t;
        for (Coat v : ALL) if (!v.custom() && Registries.CAT_VARIANT.get(v.base()) == cat.getVariant()) return v;
        return ALL.get(0);
    }

    public static void apply(CatEntity cat, Coat t) {
        CatVariant base = Registries.CAT_VARIANT.get(t.base());
        if (base != null) cat.setVariant(base);
        ((CatCoatHolder) cat).pixelpirates$setCoat(t.custom() ? t.id() : "");
    }

    /** Draw one coat by weight, from those at or above {@code min}. nextInt = a bound -> [0, bound) source. */
    public static Coat roll(Tier min, IntUnaryOperator nextInt) {
        int total = 0;
        for (Coat t : ALL) if (t.tier().ordinal() >= min.ordinal()) total += t.weight();
        int n = nextInt.applyAsInt(total);
        for (Coat t : ALL) if (t.tier().ordinal() >= min.ordinal() && (n -= t.weight()) < 0) return t;
        return ALL.get(0);
    }
}
