package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.util.PlayerProgressionComponent;
import net.get900.pixelpirates.world.faction.Faction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerProgressionMixin implements PlayerProgressionComponent {

    @Unique private int pp_unlockedZone = 0;
    @Unique private final int[] pp_zoneKills = new int[6];
    @Unique private boolean pp_hasRadar = false;
    @Unique private int pp_bossStep = 0;
    @Unique private boolean pp_hasStarterCoins = false;

    // ── Pirate leveling ───────────────────────────────────────────────────────
    @Unique private int pp_pirateLevel = 1;
    @Unique private int pp_pirateXp = 0;
    @Unique private int pp_skillPoints = 0;
    @Unique private final int[] pp_skillLevels = new int[25]; // one per ALL_SKILLS entry
    @Unique private int pp_secondWindCooldown = 0;
    @Unique private int pp_davysLuckCooldown  = 0;
    @Unique private final int[] pp_factionRep = new int[Faction.values().length];

    // ── Zone / radar / coins ──────────────────────────────────────────────────
    @Override public int pp_getUnlockedZone() { return pp_unlockedZone; }
    @Override public void pp_setUnlockedZone(int zone) { this.pp_unlockedZone = zone; }
    @Override public int pp_getBossStep() { return pp_bossStep; }
    @Override public void pp_setBossStep(int step) { this.pp_bossStep = step; }
    @Override public boolean pp_hasRadar() { return pp_hasRadar; }
    @Override public void pp_setHasRadar(boolean value) { this.pp_hasRadar = value; }
    @Override public boolean pp_hasStarterCoins() { return pp_hasStarterCoins; }
    @Override public void pp_setStarterCoins(boolean value) { this.pp_hasStarterCoins = value; }

    @Override
    public int pp_getZoneKills(int zone) {
        return (zone >= 0 && zone < pp_zoneKills.length) ? pp_zoneKills[zone] : 0;
    }

    @Override
    public void pp_addZoneKill(int zone) {
        if (zone >= 0 && zone < pp_zoneKills.length) pp_zoneKills[zone]++;
    }

    // ── Leveling ──────────────────────────────────────────────────────────────
    @Override public int  pp_getPirateLevel()             { return pp_pirateLevel; }
    @Override public void pp_setPirateLevel(int level)    { this.pp_pirateLevel = level; }
    @Override public int  pp_getPirateXp()                { return pp_pirateXp; }
    @Override public void pp_setPirateXp(int xp)          { this.pp_pirateXp = xp; }
    @Override public int  pp_getSkillPoints()             { return pp_skillPoints; }
    @Override public void pp_setSkillPoints(int points)   { this.pp_skillPoints = points; }
    @Override public int  pp_getSecondWindCooldown()      { return pp_secondWindCooldown; }
    @Override public void pp_setSecondWindCooldown(int t) { this.pp_secondWindCooldown = t; }
    @Override public int  pp_getDavysLuckCooldown()       { return pp_davysLuckCooldown; }
    @Override public void pp_setDavysLuckCooldown(int t)  { this.pp_davysLuckCooldown = t; }

    @Override
    public int pp_getSkillLevel(int idx) {
        return (idx >= 0 && idx < pp_skillLevels.length) ? pp_skillLevels[idx] : 0;
    }

    @Override
    public void pp_setSkillLevel(int idx, int level) {
        if (idx >= 0 && idx < pp_skillLevels.length) pp_skillLevels[idx] = level;
    }

    // ── Faction reputation ────────────────────────────────────────────────────
    @Override public int  pp_getFactionRep(int idx) {
        return (idx >= 0 && idx < pp_factionRep.length) ? pp_factionRep[idx] : 0;
    }
    @Override public void pp_setFactionRep(int idx, int rep) {
        if (idx >= 0 && idx < pp_factionRep.length) pp_factionRep[idx] = rep;
    }

    // ── NBT serialization ─────────────────────────────────────────────────────

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void readProgressionData(NbtCompound nbt, CallbackInfo ci) { pp_readProgression(nbt); }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void writeProgressionData(NbtCompound nbt, CallbackInfo ci) { pp_writeProgression(nbt); }

    @Override
    public void pp_readProgression(NbtCompound nbt) {
        this.pp_unlockedZone = nbt.contains("PPUnlockedZone") ? nbt.getInt("PPUnlockedZone") : 0;
        this.pp_hasRadar = nbt.getBoolean("PPHasRadar");
        this.pp_bossStep = nbt.contains("PPBossStep") ? nbt.getInt("PPBossStep")
                : net.get900.pixelpirates.entity.mob.BossProgression.migrateFromZone(this.pp_unlockedZone);
        this.pp_hasStarterCoins = nbt.getBoolean("PPHasStarterCoins");
        if (nbt.contains("PPZoneKills")) {
            int[] saved = nbt.getIntArray("PPZoneKills");
            for (int i = 0; i < Math.min(saved.length, pp_zoneKills.length); i++) {
                pp_zoneKills[i] = saved[i];
            }
        }
        // Leveling
        this.pp_pirateLevel  = nbt.contains("PPLevel")  ? nbt.getInt("PPLevel")  : 1;
        this.pp_pirateXp     = nbt.contains("PPXp")     ? nbt.getInt("PPXp")     : 0;
        this.pp_skillPoints  = nbt.contains("PPSkillPts")? nbt.getInt("PPSkillPts"): 0;
        if (nbt.contains("PPSkillLevels")) {
            int[] saved = nbt.getIntArray("PPSkillLevels");
            for (int i = 0; i < Math.min(saved.length, pp_skillLevels.length); i++) {
                pp_skillLevels[i] = saved[i];
            }
        }
        if (nbt.contains("PPFactionRep")) {
            int[] saved = nbt.getIntArray("PPFactionRep");
            for (int i = 0; i < Math.min(saved.length, pp_factionRep.length); i++) {
                pp_factionRep[i] = saved[i];
            }
        }
    }

    @Override
    public void pp_writeProgression(NbtCompound nbt) {
        nbt.putInt("PPUnlockedZone", this.pp_unlockedZone);
        nbt.putBoolean("PPHasRadar", this.pp_hasRadar);
        nbt.putInt("PPBossStep", this.pp_bossStep);
        nbt.putBoolean("PPHasStarterCoins", this.pp_hasStarterCoins);
        nbt.putIntArray("PPZoneKills", pp_zoneKills);
        // Leveling
        nbt.putInt("PPLevel",    this.pp_pirateLevel);
        nbt.putInt("PPXp",       this.pp_pirateXp);
        nbt.putInt("PPSkillPts", this.pp_skillPoints);
        nbt.putIntArray("PPSkillLevels", pp_skillLevels);
        nbt.putIntArray("PPFactionRep",  pp_factionRep);
    }
}
