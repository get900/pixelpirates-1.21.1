package net.get900.pixelpirates.util;

public interface PlayerProgressionComponent {
    int pp_getUnlockedZone();
    void pp_setUnlockedZone(int zone);
    int pp_getZoneKills(int zone);
    void pp_addZoneKill(int zone);
    boolean pp_hasRadar();
    void pp_setHasRadar(boolean value);
    /** Number of bosses beaten along BossProgression.CHAIN (0-10). */
    int pp_getBossStep();
    void pp_setBossStep(int step);
    boolean pp_hasStarterCoins();
    void pp_setStarterCoins(boolean value);

    // ── Pirate leveling ───────────────────────────────────────────────────────
    int  pp_getPirateLevel();
    void pp_setPirateLevel(int level);
    int  pp_getPirateXp();
    void pp_setPirateXp(int xp);
    int  pp_getSkillPoints();
    void pp_setSkillPoints(int points);
    int  pp_getSkillLevel(int skillIndex);
    void pp_setSkillLevel(int skillIndex, int level);
    int  pp_getSecondWindCooldown();
    void pp_setSecondWindCooldown(int ticks);
    int  pp_getDavysLuckCooldown();
    void pp_setDavysLuckCooldown(int ticks);

    // ── Faction reputation ────────────────────────────────────────────────────
    int  pp_getFactionRep(int factionOrdinal);
    void pp_setFactionRep(int factionOrdinal, int rep);

    // ── Persistence (also used to carry progress across death, see PixelPirates COPY_FROM) ──
    void pp_writeProgression(net.minecraft.nbt.NbtCompound nbt);
    void pp_readProgression(net.minecraft.nbt.NbtCompound nbt);
}
