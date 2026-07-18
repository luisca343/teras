package es.boffmedia.teras.battle.team.pixelmon;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory;
import com.pixelmonmod.pixelmon.api.storage.PCBox;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PartyStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.team.BattleTeam;
import es.boffmedia.teras.battle.team.api.TeamProvider;
import es.boffmedia.teras.integration.PokemonEngines;
import es.boffmedia.teras.storage.pixelmon.PixelmonMons;
import es.boffmedia.teras.storage.model.StoredMon;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * File-backed battle teams for Pixelmon, one {@code .dat} per team under the world save.
 *
 * <p>1.16.5 wrote these to {@code teras/data/<uuid>/} beside the server jar; here they live in the
 * world folder, where per-player save data belongs — a world swap should not carry another world's
 * teams. Nothing is lost by not reading the old path: the files hold 1.16.5-era Pokémon NBT, which
 * 9.3.16 cannot deserialise anyway.</p>
 */
public final class PixelmonTeamProvider implements TeamProvider {

    private static final String TEAMS_DIR = "teras/teams";
    private static final String TEAM_SUFFIX = ".dat";
    private static final long LOAD_TIMEOUT_SECONDS = 4;
    private static final long READ_TIMEOUT_SECONDS = 5;

    @Override
    public String engineId() {
        return PokemonEngines.PIXELMON;
    }

    @Override
    public List<BattleTeam> readAll(MinecraftServer server, UUID player) throws Exception {
        // Disk first, off this thread; the NBT is inert until Pixelmon parses it.
        Map<String, CompoundTag> raw = readTeamFiles(teamsDir(server, player));
        if (raw.isEmpty()) {
            return List.of();
        }
        // Parsing touches the species registry, so it happens on the server thread.
        return server.submit(() -> toTeams(raw, server.registryAccess()))
                .get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public boolean updateSlot(MinecraftServer server, UUID player, String teamName, int teamSlot,
                              int sourceBox, int sourceSlot) throws Exception {
        if (teamSlot < 0 || teamSlot >= BattleTeam.SIZE) {
            return false;
        }
        // Loading storage off-thread, for the reason in PixelmonStorageProvider: the engine may
        // schedule that load onto the server thread, and joining there would deadlock.
        PlayerPartyStorage party = StorageProxy.getParty(player)
                .get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PCStorage pc = StorageProxy.getPCForPlayer(player)
                .get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CompoundTag pokemonNbt = server.submit(
                        () -> writePokemon(party, pc, sourceBox, sourceSlot, server.registryAccess()))
                .get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (pokemonNbt == null) {
            Teras.LOGGER.warn("updatebattleteam: no Pokémon at box={} slot={} for {}",
                    sourceBox, sourceSlot, player);
            return false;
        }

        Path file = teamsDir(server, player).resolve(safeName(teamName) + TEAM_SUFFIX);
        CompoundTag team = Files.exists(file) ? readTag(file) : new CompoundTag();
        if (team == null) {
            return false;
        }
        team.put(String.valueOf(teamSlot), pokemonNbt);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(team, file);
        return true;
    }

    /** Server thread: reads the live storage the Pokémon is sitting in. */
    private static CompoundTag writePokemon(PlayerPartyStorage party, PCStorage pc,
                                            int sourceBox, int sourceSlot,
                                            HolderLookup.Provider registries) {
        Pokemon pokemon = null;
        if (sourceBox == PARTY_BOX) {
            if (party != null && sourceSlot >= 0 && sourceSlot < PartyStorage.MAX_PARTY) {
                pokemon = party.get(sourceSlot);
            }
        } else if (pc != null && sourceBox >= 0 && sourceBox < pc.getBoxCount()
                && sourceSlot >= 0 && sourceSlot < PCBox.POKEMON_PER_BOX) {
            pokemon = pc.get(sourceBox, sourceSlot);
        }
        if (pokemon == null || pokemon.getSpecies() == null) {
            return null;
        }
        return pokemon.writeToNBT(new CompoundTag(), registries);
    }

    /** Box {@code -1} is the party, matching the PC routes. */
    private static final int PARTY_BOX = -1;

    /** Server thread: {@code PokemonFactory.create} resolves species against the registries. */
    private static List<BattleTeam> toTeams(Map<String, CompoundTag> raw,
                                            HolderLookup.Provider registries) {
        List<BattleTeam> teams = new ArrayList<>(raw.size());
        for (Map.Entry<String, CompoundTag> entry : raw.entrySet()) {
            List<StoredMon> slots = new ArrayList<>(BattleTeam.SIZE);
            boolean any = false;
            for (int slot = 0; slot < BattleTeam.SIZE; slot++) {
                StoredMon mon = readSlot(entry.getValue(), slot, registries);
                slots.add(mon);
                any |= mon != null;
            }
            // A team file that parses to nothing is a corrupt or emptied one; showing an all-blank
            // team would read as data loss rather than as the absence of a team.
            if (any) {
                teams.add(new BattleTeam(entry.getKey(), entry.getKey(), slots));
            }
        }
        return teams;
    }

    private static StoredMon readSlot(CompoundTag team, int slot, HolderLookup.Provider registries) {
        String key = String.valueOf(slot);
        if (!team.contains(key)) {
            return null;
        }
        try {
            Pokemon pokemon = PokemonFactory.create(team.getCompound(key), registries);
            return pokemon == null || pokemon.getSpecies() == null ? null : PixelmonMons.read(pokemon);
        } catch (Exception e) {
            // One unreadable slot must not cost the whole team.
            Teras.LOGGER.warn("Battle team slot {} could not be read: {}", slot, e.toString());
            return null;
        }
    }

    /** Every {@code .dat} in the player's folder, name-ordered so the panel is stable between reads. */
    private static Map<String, CompoundTag> readTeamFiles(Path dir) {
        Map<String, CompoundTag> teams = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            return teams;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + TEAM_SUFFIX)) {
            for (Path file : new TreeSet<>(collect(files))) {
                CompoundTag tag = readTag(file);
                if (tag != null) {
                    String name = file.getFileName().toString();
                    teams.put(name.substring(0, name.length() - TEAM_SUFFIX.length()), tag);
                }
            }
        } catch (IOException e) {
            Teras.LOGGER.warn("Could not list battle teams in {}: {}", dir, e.toString());
        }
        return teams;
    }

    private static List<Path> collect(DirectoryStream<Path> files) {
        List<Path> out = new ArrayList<>();
        files.forEach(out::add);
        return out;
    }

    private static CompoundTag readTag(Path file) {
        try {
            return NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            Teras.LOGGER.warn("Could not read battle team {}: {}", file, e.toString());
            return null;
        }
    }

    private static Path teamsDir(MinecraftServer server, UUID player) {
        return server.getWorldPath(LevelResource.ROOT).resolve(TEAMS_DIR).resolve(player.toString());
    }

    /**
     * A team name becomes a file name, and it arrives from the network — so anything that is not a
     * plain name is rejected rather than sanitised, since a silently renamed team would read as a
     * lost one. {@code ..} and separators are the reason this exists.
     */
    private static String safeName(String teamName) {
        if (teamName == null || teamName.isBlank() || teamName.length() > 64
                || !teamName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid team name: " + teamName);
        }
        return teamName;
    }
}
