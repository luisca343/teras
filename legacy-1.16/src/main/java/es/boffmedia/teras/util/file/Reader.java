package es.boffmedia.teras.util.file;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import es.boffmedia.teras.Teras;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Reader {

    /** Connect/read timeouts (ms) so a slow or dead endpoint can never hang the calling thread indefinitely. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    /** How long a fetched body stays fresh. Repeated gym battles reuse it instead of re-downloading. */
    private static final long CACHE_TTL_MS = 60_000L;

    /**
     * Allowed shape for a combat/event identifier interpolated into the request URL. Restricting it to
     * a flat token closes the path-injection vector (no {@code ../}, encoded slashes, query strings).
     */
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");

    /** Response-body cache keyed by full URL. Stores immutable text only — callers rebuild fresh objects. */
    private static final ConcurrentHashMap<String, CacheEntry> TEXT_CACHE = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        final String body;
        final long expiresAt;
        CacheEntry(String body, long expiresAt) {
            this.body = body;
            this.expiresAt = expiresAt;
        }
    }

    /** True when {@code id} is safe to interpolate into the config/team URL. */
    public static boolean isValidIdentifier(String id) {
        return id != null && VALID_IDENTIFIER.matcher(id).matches();
    }

    /**
     * Enforces the HTTPS policy shared with SmartRotom. When {@code requireHttps} is set, a non-HTTPS
     * URL is refused (fail-closed) so config/team payloads can't be MITM-injected over cleartext.
     */
    private static boolean isTransportAllowed(URL url) {
        if (Teras.config != null && Teras.config.isRequireHttps()
                && !"https".equalsIgnoreCase(url.getProtocol())) {
            Teras.getLogger().error("Combat config request refused: requireHttps is enabled but URL is not HTTPS ({})", url);
            return false;
        }
        return true;
    }

    public static InputStream getConnectionStream(URL url) {
        if (url == null || !isTransportAllowed(url)) {
            return null;
        }
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(CONNECT_TIMEOUT_MS);
            con.setReadTimeout(READ_TIMEOUT_MS);
            con.addRequestProperty("User-Agent", "Mozilla/4.0");

            return con.getInputStream();
        } catch (IOException e) {
            Teras.getLogger().error("Error opening connection stream", e);
        }

        return null;
    }

    /**
     * Fetches {@code url}'s body as text, served from a short-lived cache when still fresh. Returns
     * {@code null} on any transport/IO failure. The cached value is an immutable string, so callers
     * parse/rebuild their own (mutable) objects each time — nothing live is shared between battles.
     */
    public static String fetchTextCached(URL url) {
        if (url == null) {
            return null;
        }
        String key = url.toString();
        long now = System.currentTimeMillis();

        CacheEntry cached = TEXT_CACHE.get(key);
        if (cached != null && cached.expiresAt > now) {
            return cached.body;
        }

        InputStream inputStream = getConnectionStream(url);
        if (inputStream == null) {
            return null;
        }
        String body;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            body = br.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            Teras.getLogger().error("Error reading body from " + key, e);
            return null;
        }

        TEXT_CACHE.put(key, new CacheEntry(body, now + CACHE_TTL_MS));
        return body;
    }

    public static BattleConfig getDatosEncuentro(String npc) {
        return getDatosCombate(npc, "eventos");
    }


    public static BattleConfig getDatosNPC(String npc) {
        return getDatosCombate(npc, "entrenadores");
    }

    public static BattleConfig getDatosCombate(String npc, String tipo) {
        if (!isValidIdentifier(npc)) {
            Teras.getLogger().error("Refused combat config fetch: invalid identifier '{}'", npc);
            return null;
        }

        URL url;
        try {
            url = new URL("http://api.boffmedia.es/smartrotom/combates/"+ tipo +"/"+npc+"/config.json");
        } catch (MalformedURLException e) {
            Teras.getLogger().error("Malformed combat config URL for npc " + npc, e);
            return null;
        }

        String str = fetchTextCached(url);
        if (str == null) {
            return null;
        }

        BattleConfig configCombate;
        try {
            configCombate = new Gson().fromJson(str, BattleConfig.class);
        } catch (JsonSyntaxException e) {
            Teras.getLogger().error("Malformed combat config JSON for npc " + npc, e);
            return null;
        }

        if (configCombate == null) {
            Teras.getLogger().error("Empty combat config for npc " + npc);
            return null;
        }

        int[] equipos = configCombate.getEquipos();
        if (equipos == null || equipos.length == 0) {
            Teras.getLogger().error("Combat config for npc " + npc + " declares no teams");
            return null;
        }
        int equipoElegido = equipos[(int) (Math.random() * equipos.length)];

        // The team is rebuilt fresh every battle (the paste text is cached, the Pokémon objects are not)
        // so two battles never share the same mutable PixelmonWrapper/Pokemon instances.
        List<Pokemon> team = PokePasteReader.fromTeras(tipo +"/" + npc + "/" + equipoElegido).build();

        if (team == null || team.isEmpty()) {
            Teras.getLogger().error("Combat config for npc " + npc + " resolved an empty team");
            return null;
        }

        configCombate.setEquipo(team);
        configCombate.setNombreArchivo(npc);
        configCombate.setCarpeta(tipo);


        return configCombate;
    }
}
