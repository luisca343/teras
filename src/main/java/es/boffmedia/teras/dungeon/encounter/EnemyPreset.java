package es.boffmedia.teras.dungeon.encounter;

/**
 * One authored dungeon enemy, in engine-free terms so {@link DungeonEnemyPacks} stays plain data
 * and only {@link CnpcBridge} ever touches the CustomNPCs API.
 *
 * <p>Values map onto the CustomNPCs NPC editor: {@code strength} is melee damage, {@code delay}
 * ticks between swings, {@code range} the reach in blocks, {@code speed} the 0–10 walking speed
 * slider, {@code size} the 1–10 model scale (5 is player height).</p>
 *
 * <p>The trailing fields are the <b>traits</b>: everything the installer can bake into the clone
 * once, needing no runtime code. Behaviour that has to react during a fight is not here — that
 * lives in {@code dungeon/ability}, keyed by {@link #id()}. Every trait defaults to off, so
 * {@link #melee} builds the plain melee enemy the bestiary shipped with.</p>
 *
 * @param id            stable key, used as the clone name, the ability key and in {@code enemies.json}
 * @param displayName   what players see over its head
 * @param skinTexture   texture path — reskinning costs no new assets
 * @param health        max health
 * @param strength      melee damage per hit
 * @param delay         ticks between swings
 * @param range         melee reach in blocks
 * @param knockback     melee knockback strength
 * @param aggroRange    blocks at which it notices a player
 * @param speed         walking speed slider, 0–10
 * @param size          model scale, 1–10 (5 = player height)
 * @param mainHand      item id in its right hand, or "" for empty
 * @param helmet        item id on its head, or ""
 * @param dropItem      item id it drops, or ""
 * @param dropChance    drop chance, 0–100
 * @param expMin        minimum XP dropped
 * @param expMax        maximum XP dropped
 * @param geoModel      animated-model variant for the CNPC Gecko addon, or "" for the plain
 *                      humanoid — see {@link es.boffmedia.teras.dungeon.entity.GeoEnemyVariant}
 * @param rangedStrength projectile damage; 0 leaves the enemy melee-only and ignores every other
 *                      ranged field
 * @param rangedSpeed   projectile speed slider
 * @param rangedBurst   projectiles per burst
 * @param rangedDelay   ticks between bursts
 * @param rangedEffect  potion effect id its projectiles apply (CustomNPCs' numeric effect), 0 none
 * @param rangedEffectTime  seconds that effect lasts
 * @param meleeEffect   potion effect its melee applies (CustomNPCs' numeric effect), 0 for none
 * @param meleeEffectTime   seconds that effect lasts
 * @param arrowResist   0–1 share of projectile damage ignored
 * @param meleeResist   0–1 share of melee damage ignored
 * @param leaps         whether it leaps at its target
 * @param bossBar       0 none, 1–5 CustomNPCs' boss-bar styles; bosses earn one
 */
public record EnemyPreset(
        String id,
        String displayName,
        String skinTexture,
        int health,
        int strength,
        int delay,
        int range,
        int knockback,
        int aggroRange,
        int speed,
        int size,
        String mainHand,
        String helmet,
        String dropItem,
        int dropChance,
        int expMin,
        int expMax,
        String geoModel,
        int rangedStrength,
        int rangedSpeed,
        int rangedBurst,
        int rangedDelay,
        int rangedEffect,
        int rangedEffectTime,
        int meleeEffect,
        int meleeEffectTime,
        float arrowResist,
        float meleeResist,
        boolean leaps,
        int bossBar) {

    /** True when this enemy should be given a ranged attack at all. */
    public boolean isRanged() {
        return rangedStrength > 0;
    }

    /**
     * A plain melee enemy — every trait off. The bestiary's chaff is built this way, and it keeps
     * the seventeen-argument form the presets were originally written in readable.
     */
    public static EnemyPreset melee(String id, String displayName, String skinTexture,
                                    int health, int strength, int delay, int range, int knockback,
                                    int aggroRange, int speed, int size,
                                    String mainHand, String helmet, String dropItem, int dropChance,
                                    int expMin, int expMax, String geoModel) {
        return new EnemyPreset(id, displayName, skinTexture, health, strength, delay, range,
                knockback, aggroRange, speed, size, mainHand, helmet, dropItem, dropChance,
                expMin, expMax, geoModel,
                0, 0, 0, 0, 0, 0, 0, 0, 0f, 0f, false, 0);
    }

    /** Copy of this preset with its ranged attack configured. */
    public EnemyPreset ranged(int strengthValue, int speedValue, int burst, int rangedDelayValue,
                              int effect, int effectTime) {
        return new EnemyPreset(id, displayName, skinTexture, health, strength, delay, range,
                knockback, aggroRange, speed, size, mainHand, helmet, dropItem, dropChance,
                expMin, expMax, geoModel,
                strengthValue, speedValue, burst, rangedDelayValue, effect, effectTime,
                meleeEffect, meleeEffectTime, arrowResist, meleeResist, leaps, bossBar);
    }

    /** Copy of this preset with defensive and movement traits set. */
    public EnemyPreset traits(float arrowResistValue, float meleeResistValue, boolean leapsValue,
                              int bossBarValue) {
        return new EnemyPreset(id, displayName, skinTexture, health, strength, delay, range,
                knockback, aggroRange, speed, size, mainHand, helmet, dropItem, dropChance,
                expMin, expMax, geoModel,
                rangedStrength, rangedSpeed, rangedBurst, rangedDelay, rangedEffect,
                rangedEffectTime, meleeEffect, meleeEffectTime,
                arrowResistValue, meleeResistValue, leapsValue, bossBarValue);
    }

    /** Copy of this preset whose melee applies a potion effect. */
    public EnemyPreset meleeEffect(int effect, int effectTime) {
        return new EnemyPreset(id, displayName, skinTexture, health, strength, delay, range,
                knockback, aggroRange, speed, size, mainHand, helmet, dropItem, dropChance,
                expMin, expMax, geoModel,
                rangedStrength, rangedSpeed, rangedBurst, rangedDelay, rangedEffect,
                rangedEffectTime, effect, effectTime, arrowResist, meleeResist, leaps, bossBar);
    }
}
