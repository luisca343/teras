package es.boffmedia.teras.util.file;

import com.google.gson.Gson;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import es.boffmedia.teras.api.PokePasteReader;
import es.boffmedia.teras.util.objects.pixelmon.BattleConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class Reader {
    public static InputStream getConnectionStream(URL url) {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.addRequestProperty("User-Agent", "Mozilla/4.0");

            return con.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static BattleConfig getDatosEncuentro(String npc) {
        return getDatosCombate(npc, "eventos");
    }


    public static BattleConfig getDatosNPC(String npc) {
        return getDatosCombate(npc, "entrenadores");
    }

    public static BattleConfig getDatosCombate(String npc, String tipo) {
        URL url = null;
        try {
            url = new URL("http://api.boffmedia.es/smartrotom/combates/"+ tipo +"/"+npc+"/config.json");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        InputStream inputStream = getConnectionStream(url);

        if(inputStream == null) {
            return null;
        }

        String str = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining());

        BattleConfig configCombate = new Gson().fromJson(str, BattleConfig.class);

        int[] equipos = configCombate.getEquipos();
        int equipoElegido = equipos[(int) (Math.random() * equipos.length)];

        List<Pokemon> team = PokePasteReader.fromTeras(tipo +"/" + npc + "/" + equipoElegido).build();

        configCombate.setEquipo(team);
        configCombate.setNombreArchivo(npc);
        configCombate.setCarpeta(tipo);


        return configCombate;
    }
}
