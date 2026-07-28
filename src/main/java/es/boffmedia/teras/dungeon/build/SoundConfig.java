package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Cue name to vanilla sound id, and one master volume. Overridable under {@code sonidos:} — the
 * migration path to first-party audio: point an entry at a {@code teras:} id once the assets exist
 * and nothing here has to change.
 */
public record SoundConfig(float volume, Map<String, String> cues) {

    static final Map<String, String> DEFAULT_CUES = Map.ofEntries(
            Map.entry("ROOM_SEALED", "minecraft:block.iron_door.close"),
            Map.entry("ROOM_SEALED_BODY", "minecraft:block.anvil.land"),
            Map.entry("BOSS_SEALED", "minecraft:block.iron_door.close"),
            Map.entry("BOSS_SEALED_BODY", "minecraft:entity.wither.spawn"),
            Map.entry("ROOM_OPENED", "minecraft:block.iron_door.open"),
            Map.entry("ROOM_OPENED_BODY", "minecraft:block.note_block.bell"),
            Map.entry("BOSS_DEFEATED", "minecraft:ui.toast.challenge_complete"),
            Map.entry("TRAPDOOR_OPEN", "minecraft:block.end_portal.spawn"),
            Map.entry("SECRET_OPENED", "minecraft:block.vault.open_shutter"),
            Map.entry("ENEMY_ENRAGED", "minecraft:entity.ravager.roar"),
            Map.entry("COIN_PICKUP", "minecraft:entity.experience_orb.pickup"),
            Map.entry("PURCHASE", "minecraft:entity.player.levelup"),
            Map.entry("PURCHASE_DENIED", "minecraft:block.note_block.bass"),
            Map.entry("CHALLENGE_STARTED", "minecraft:event.raid.horn"),
            Map.entry("WAVE_CLEARED", "minecraft:block.note_block.chime"),
            Map.entry("SACRIFICE", "minecraft:entity.player.hurt"),
            Map.entry("SACRIFICE_REWARD", "minecraft:block.amethyst_block.chime"),
            Map.entry("ARCADE_PLAY", "minecraft:block.lever.click"),
            Map.entry("ARCADE_WIN", "minecraft:entity.player.levelup"),
            Map.entry("ARCADE_BREAK", "minecraft:entity.item.break"),
            Map.entry("DEVIL_OPENED", "minecraft:entity.wither.ambient"),
            Map.entry("DEVIL_DEAL", "minecraft:entity.evoker.cast_spell"),
            Map.entry("PHOENIX", "minecraft:item.totem.use"),
            Map.entry("SEAL_RESTORED", "minecraft:block.beacon.activate"),
            Map.entry("DESCENT_TICK", "minecraft:block.note_block.hat"));

    static SoundConfig defaults() {
        return new SoundConfig(0.8f, new LinkedHashMap<>(DEFAULT_CUES));
    }

    static SoundConfig read(YamlConfig yaml, SoundConfig previous) {
        YamlConfig block = yaml.section("sonidos");
        Map<String, String> cues = new LinkedHashMap<>(previous.cues);
        for (String cue : DEFAULT_CUES.keySet()) {
            cues.put(cue, block.string(cue.toLowerCase(Locale.ROOT), cues.get(cue)));
        }
        return new SoundConfig(
                (float) yaml.integer("volumenSonidos", Math.round(previous.volume * 100)) / 100f,
                cues);
    }

    /** Never null — unknown cues fall back silently. */
    String cue(String name) {
        return cues.getOrDefault(name, DEFAULT_CUES.get(name));
    }
}
