package es.boffmedia.teras.net.server;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.api.spawning.*;
import com.pixelmonmod.pixelmon.api.spawning.archetypes.entities.pokemon.SpawnInfoPokemon;
import com.pixelmonmod.pixelmon.api.world.BlockCollection;
import com.pixelmonmod.pixelmon.spawning.PixelmonSpawning;
import com.pixelmonmod.pixelmon.spawning.PlayerTrackingSpawner;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageMCEFResponse;
import es.boffmedia.teras.util.objects.post.PokedexSpawnChance;
import es.boffmedia.teras.util.objects._old.serverdata.TerasConfig;
import es.boffmedia.teras.util.file.FileHelper;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SMessageCheckSpawns implements Runnable{
    private String str;
    private ServerPlayerEntity player;

    public SMessageCheckSpawns(String str){
        this.str = str;
    }

    @Override
    public void run() {
        List<String> arguments = new ArrayList<>();
        TerasConfig terasConfig = FileHelper.getConfig();


        AbstractSpawner spawner  = PixelmonSpawning.coordinator.getSpawner(player.getName().getString());
        System.out.println("Nombre: "+player.getName().getString());
        System.out.println("Spawner Creado: "+spawner.name);
        PlayerTrackingSpawner pSpawner = (PlayerTrackingSpawner)spawner;

        SpawnerCoordinator spawnerCoordinator = PixelmonSpawning.coordinator;
        spawnerCoordinator.spawners.forEach((spawnerx) -> {
            Teras.getLogger().info("Spawner: "+spawnerx.name);
        });

        BlockCollection blocks = (BlockCollection)spawner.getTrackedBlockCollection(player, 0.0F, 0.0F, pSpawner.horizontalSliceRadius, pSpawner.verticalSliceRadius, 0, 0).join();
        ArrayList<SpawnLocation> spawnLocations = pSpawner.spawnLocationCalculator.calculateSpawnableLocations(blocks);
        Iterator var8 = arguments.iterator();

        System.out.println("SpawnLocations: "+spawnLocations.size());

        while(var8.hasNext()) {
            String argument = (String)var8.next();
            if (argument.equalsIgnoreCase("specific")) {
                spawnLocations.removeIf((spawnLocationx) -> {
                    return !spawnLocationx.location.pos.equals(player.blockPosition());
                });
                if (spawnLocations.isEmpty()) {
                    //sender.sendSuccess(TextHelper.translate(TextFormatting.RED, "spawning.error.midairorinvalid"), false);
                    return;
                }
                break;
            }
        }

        Map<SpawnLocation, List<SpawnInfoPokemon>> possibleSpawns = new HashMap();
        Iterator var13 = spawnLocations.iterator();


        while(var13.hasNext()) {
            SpawnLocation spawnLocation = (SpawnLocation)var13.next();
            List<SpawnInfo> spawns = spawner.getSuitableSpawns(spawnLocation);

            List<SpawnInfoPokemon> pokemonSpawns = spawns.stream()
                    .filter(spawnInfo -> spawnInfo instanceof SpawnInfoPokemon)
                    .map(spawnInfo -> (SpawnInfoPokemon) spawnInfo)
                    .collect(Collectors.toList());
            if (!pokemonSpawns.isEmpty()) {
                possibleSpawns.put(spawnLocation, pokemonSpawns);
            }
        }

        Map<String, SpawnInfoPokemon> pokemonSpawns = new HashMap<>();


        possibleSpawns.forEach((spawnLocation, spawns) -> {
            spawns.forEach((spawnInfo) -> {
                pokemonSpawns.put(spawnInfo.getPokemonSpec().toString(), spawnInfo);
            });
        });


        double totalWeight = pokemonSpawns.values().stream().mapToDouble(spawnInfo -> spawnInfo.rarity).sum();
        ArrayList<PokedexSpawnChance> pokedexSpawnChances = new ArrayList<>();

        pokemonSpawns.forEach((key, value) -> {
            SpawnInfoPokemon spawnInfo = value;
            String spec = spawnInfo.getPokemonSpec().toString();

            String species = null;
            String form = null;
            String palette = null;

            String[] parts = spec.split(" ");
            for (String part : parts) {
                if (part.startsWith("species:")) {
                    species = part.substring("species:".length());
                } else if (part.startsWith("form:")) {
                    form = part.substring("form:".length());
                } else if (part.startsWith("palette:")) {
                    palette = part.substring("palette:".length());
                }
            }

            // If form or palette does not exist, assign default values
            if (form == null) {
                form = "base";
            }
            if (palette == null) {
                palette = "none";
            }

            String name = spawnInfo.toString().replace("'", "");

            int dex = PixelmonSpecies.get(name).flatMap(RegistryValue::getValue).isPresent() ? PixelmonSpecies.get(species).flatMap(RegistryValue::getValue).get().getDex() : 0;
            PokedexSpawnChance pokedexSpawnChance = new PokedexSpawnChance(dex, species, form, palette, spawnInfo.rarity, (spawnInfo.rarity / totalWeight) * 100);
            pokedexSpawnChances.add(pokedexSpawnChance);
        });





        /*
        Map<String, Double> percentages = spawner.selectionAlgorithm.getPercentages(spawner, possibleSpawns);
        percentages.forEach((key, value) -> {
            int dex = PixelmonSpecies.get(key).flatMap(RegistryValue::getValue).isPresent() ? PixelmonSpecies.get(key).flatMap(RegistryValue::getValue).get().getDex() : 0;
            PokedexSpawnChance pokedexSpawnChance = new PokedexSpawnChance(key, value, dex);
            pokedexSpawnChances.add(pokedexSpawnChance);
        });*/

        Gson gson = new Gson();
        String json = gson.toJson(pokedexSpawnChances);


        System.out.println("Enviando datos de spawns: " + json);
        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageMCEFResponse(json));


    }


    public static SMessageCheckSpawns decode(PacketBuffer buf) {
        SMessageCheckSpawns message = new SMessageCheckSpawns(buf.toString(Charsets.UTF_8));
        return message;
    }

    public void encode(PacketBuffer buf) {
        buf.writeCharSequence(str, Charsets.UTF_8);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}
