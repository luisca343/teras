package es.boffmedia.teras.pixelmon;

import com.google.gson.Gson;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.pokemon.species.Stats;
import com.pixelmonmod.pixelmon.api.spawning.AbstractSpawner;
import com.pixelmonmod.pixelmon.api.spawning.SpawnInfo;
import com.pixelmonmod.pixelmon.api.spawning.SpawnLocation;
import com.pixelmonmod.pixelmon.api.spawning.archetypes.entities.pokemon.SpawnInfoPokemon;
import com.pixelmonmod.pixelmon.api.spawning.calculators.CalculateSpawnLocations;
import com.pixelmonmod.pixelmon.api.world.BlockCollection;
import com.pixelmonmod.pixelmon.spawning.PixelmonSpawning;
import com.pixelmonmod.pixelmon.spawning.PlayerTrackingSpawner;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects.PokedexSpawnChance;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pixelmon 1.21.1 spawn scan — the "checkspawns" data path, ported from the 1.16.5
 * {@code SMessageCheckSpawns.run()}. Given a player, it works out which Pokémon can currently spawn
 * around them and with what chance, and returns that as the JSON array the SmartRotom web expects
 * ({@link PokedexSpawnChance}: {@code dex, species, form, palette, rarity, percentage}).
 *
 * <p><b>This is the only Pixelmon-coupled class in the mod.</b> It compiles against Pixelmon
 * ({@code com.pixelmonmod.*}, a {@code compileOnly} dependency) and is only ever invoked server-side
 * from {@code TerasNet.handleSpawnsRequest}, so it is never classloaded on a Pixelmon-less client.</p>
 *
 * <p>1.16→1.21 API changes handled: the per-spawner {@code spawnLocationCalculator} is gone — the
 * spawnable-location step now goes through {@link CalculateSpawnLocations#getDefault()}. Species/form
 * are read from {@link SpawnInfoPokemon#getSpecies()}/{@link SpawnInfoPokemon#getForm()} directly
 * instead of parsing the spec string (only palette still needs the spec, which has no accessor).
 * {@code getTrackedBlockCollection(...).join()} briefly blocks the server thread, as in 1.16.5 —
 * acceptable for a user-triggered query.</p>
 */
public final class SpawnScanner {
    private SpawnScanner() {}

    private static final Gson GSON = new Gson();

    /** Returns the current spawns around {@code player} as a JSON array, or {@code "[]"} on failure. */
    public static String scanAsJson(ServerPlayer player) {
        String who = player.getName().getString();
        try {
            PlayerTrackingSpawner tracking = findSpawner(player);
            if (tracking == null) {
                Teras.LOGGER.warn("getSpawns[{}]: no player-tracking spawner found (coordinator has {} "
                                + "spawners); returning []", who,
                        PixelmonSpawning.coordinator.spawners.size());
                return "[]";
            }

            BlockCollection blocks = tracking.getTrackedBlockCollection(
                            player, 0.0F, 0.0F, tracking.horizontalSliceRadius, tracking.verticalSliceRadius, 0, 0)
                    .get(5, TimeUnit.SECONDS);
            ArrayList<SpawnLocation> spawnLocations =
                    CalculateSpawnLocations.getDefault().calculateSpawnableLocations(blocks);
            Teras.LOGGER.info("getSpawns[{}]: {} spawnable locations", who, spawnLocations.size());

            // Dedup distinct Pokémon (by full spec) across every nearby spawnable location.
            Map<String, SpawnInfoPokemon> unique = new LinkedHashMap<>();
            for (SpawnLocation location : spawnLocations) {
                for (SpawnInfo info : suitableSpawns(tracking, location)) {
                    if (info instanceof SpawnInfoPokemon pokemon) {
                        unique.putIfAbsent(pokemon.getPokemonSpec().toString(), pokemon);
                    }
                }
            }

            double totalWeight = unique.values().stream().mapToDouble(info -> info.rarity).sum();

            List<PokedexSpawnChance> chances = new ArrayList<>(unique.size());
            for (SpawnInfoPokemon pokemon : unique.values()) {
                Species species = pokemon.getSpecies();
                Stats form = pokemon.getForm();

                int dex = species != null ? species.getDex() : 0;
                String speciesName = species != null ? species.getName() : "";
                String formName = (form != null && form.getName() != null && !form.getName().isEmpty())
                        ? form.getName() : "base";
                String palette = parsePalette(pokemon.getPokemonSpec().toString());
                double percentage = totalWeight > 0.0 ? (pokemon.rarity / totalWeight) * 100.0 : 0.0;

                chances.add(new PokedexSpawnChance(dex, speciesName, formName, palette, pokemon.rarity, percentage));
            }

            String json = GSON.toJson(chances);
            Teras.LOGGER.info("getSpawns[{}]: {} distinct Pokémon spawns -> {}", who, chances.size(), json);
            return json;
        } catch (Throwable t) {
            // Never let a Pixelmon API hiccup crash the network handler — reply [] instead.
            Teras.LOGGER.error("getSpawns[{}]: scan failed; returning []", who, t);
            return "[]";
        }
    }

    /**
     * Finds the player's {@link PlayerTrackingSpawner}. Primary lookup is by name (what Pixelmon's
     * own {@code /checkspawns} uses: {@code coordinator.getSpawner(source.getTextName())}); if that
     * misses we fall back to scanning the coordinator for a spawner whose {@code playerUUID} matches,
     * which is robust regardless of how the spawner was keyed.
     */
    private static PlayerTrackingSpawner findSpawner(ServerPlayer player) {
        AbstractSpawner byName = PixelmonSpawning.coordinator.getSpawner(player.getName().getString());
        if (byName instanceof PlayerTrackingSpawner pts) {
            return pts;
        }
        for (AbstractSpawner spawner : PixelmonSpawning.coordinator.spawners) {
            if (spawner instanceof PlayerTrackingSpawner pts && player.getUUID().equals(pts.playerUUID)) {
                Teras.LOGGER.info("getSpawns[{}]: spawner matched by UUID fallback (not by name)",
                        player.getName().getString());
                return pts;
            }
        }
        return null;
    }

    /**
     * Calls {@code AbstractSpawner.getSuitableSpawns(SpawnLocation)} reflectively. Its return type
     * drifts between Pixelmon patch versions ({@code ArrayList<SpawnInfo>} vs {@code List<SpawnInfo>}),
     * and a direct call bakes the compile-time return type into the {@code invokevirtual} descriptor —
     * which throws {@link NoSuchMethodError} when the server runs a different patch. Reflection
     * resolves by name + parameter types only, so it works across those versions.
     */
    private static volatile java.lang.reflect.Method suitableSpawnsMethod;

    @SuppressWarnings("unchecked")
    private static List<SpawnInfo> suitableSpawns(AbstractSpawner spawner, SpawnLocation location) throws Exception {
        java.lang.reflect.Method method = suitableSpawnsMethod;
        if (method == null) {
            method = AbstractSpawner.class.getMethod("getSuitableSpawns", SpawnLocation.class);
            suitableSpawnsMethod = method;
        }
        return (List<SpawnInfo>) method.invoke(spawner, location);
    }

    /** Palette has no direct {@link SpawnInfoPokemon} accessor, so parse it from the spec; default "none". */
    private static String parsePalette(String spec) {
        if (spec != null) {
            for (String part : spec.split(" ")) {
                if (part.startsWith("palette:")) {
                    String value = part.substring("palette:".length());
                    if (!value.isEmpty()) return value;
                }
            }
        }
        return "none";
    }
}
