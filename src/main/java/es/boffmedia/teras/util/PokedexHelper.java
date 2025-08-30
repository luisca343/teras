package es.boffmedia.teras.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pixelmonmod.pixelmon.api.pokedex.PlayerPokedex;
import com.pixelmonmod.pixelmon.api.pokedex.PokedexRegistrationStatus;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PokedexHelper {

    public static Map<PokedexRegistrationStatus, List<Integer>> getPokedexStatus(String uuid) {
        PlayerPartyStorage pps = StorageProxy.getParty(UUID.fromString(uuid));
        PlayerPokedex pokedex = pps.playerPokedex;

        Map<Integer, PokedexRegistrationStatus> data = pokedex.getSeenMap();

        // Group by status
        Map<PokedexRegistrationStatus, List<Integer>> groupedData = data.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        () -> new EnumMap<>(PokedexRegistrationStatus.class),
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));

        return groupedData;
    }

    public static void printPokedexStatusGroupedByStatus(String uuid) {
        Map<PokedexRegistrationStatus, List<Integer>> groupedData = getPokedexStatus(uuid);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(groupedData);
        System.out.println(json);
    }
}