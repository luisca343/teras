package es.boffmedia.teras.client.auth;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.mcef.JsQueryCallback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Step 2 of the Mojang identity handshake, run inside the game because only the game holds the
 * player's Minecraft access token.
 *
 * <pre>
 *   page  -> API  POST /auth/minecraft/challenge      -> serverId (60s TTL)
 *   page  -> mod  mcefQuery("MC_JOIN_SERVER",{serverId})   <-- this class
 *   mod   -> Mojang  session/minecraft/join(uuid, token, serverId)
 *   page  -> API  POST /auth/minecraft/session {username, serverId} -> in-game session
 * </pre>
 *
 * <p>This replaces {@code /auth/loginmc}, whose only credential was the {@code MC_WORLD} string —
 * which ships inside the browser bundle, so knowing a player's (public) UUID was enough to mint a
 * session as them. After this, the API learns the identity from Mojang rather than from the page.</p>
 *
 * <p>The token never leaves the game: the page receives only the username, and the API confirms the
 * join with Mojang itself. That is the whole point of the design — a page that could read the access
 * token would be a worse hole than the one being closed.</p>
 */
public final class MinecraftJoinQueries {
    private MinecraftJoinQueries() {}

    private static final Gson GSON = new Gson();

    /**
     * The API mints the serverId as 16 random bytes in hex. Validating the shape here means a
     * malformed or hostile value is refused before it is handed to authlib, and keeps the query from
     * being usable as a generic "join any server" primitive for a page that got past the origin
     * check.
     */
    private static final Pattern SERVER_ID = Pattern.compile("[A-Za-z0-9]{8,64}");

    /**
     * Completes the join for {@code {serverId:"..."}} and replies {@code {ok,username,uuid}}.
     *
     * <p>Two hops: the {@link User} is read on the client thread, then the blocking Mojang call runs
     * on the IO pool. Doing the HTTP round trip on the render thread would freeze the game for as
     * long as sessionserver takes to answer.</p>
     */
    public static void handleJoinServer(JsonObject json, JsQueryCallback callback) {
        final String serverId = readServerId(json);
        if (serverId == null) {
            callback.failure(400, "mcJoinServer requires a valid serverId");
            return;
        }

        Minecraft.getInstance().execute(() -> {
            final User user = Minecraft.getInstance().getUser();
            if (user == null) {
                callback.failure(401, "No Minecraft session in this client");
                return;
            }
            final UUID profileId = user.getProfileId();
            final String accessToken = user.getAccessToken();
            final String name = user.getName();
            if (profileId == null || accessToken == null || accessToken.isBlank()) {
                callback.failure(401, "This client has no online-mode session");
                return;
            }

            Util.ioPool().execute(() -> join(profileId, accessToken, name, serverId, callback));
        });
    }

    /** The query's {@code serverId} when it is present and well-shaped, {@code null} otherwise. */
    static String readServerId(JsonObject json) {
        JsonElement raw = json == null ? null : json.get("serverId");
        if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
            return null;
        }
        String serverId = raw.getAsString();
        return SERVER_ID.matcher(serverId).matches() ? serverId : null;
    }

    private static void join(UUID profileId, String accessToken, String name,
                             String serverId, JsQueryCallback callback) {
        try {
            Minecraft.getInstance().getMinecraftSessionService()
                    .joinServer(profileId, accessToken, serverId);
        } catch (Exception e) {
            // An offline/cracked client and a Mojang outage both land here. They are reported the
            // same way on purpose: the page's only correct response to either is "could not prove
            // your identity", and telling it which would only help someone probing.
            Teras.LOGGER.warn("Mojang join failed for {}: {}", name, e.toString());
            callback.failure(401, "Mojang refused the session: " + e.getMessage());
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("username", name);
        // Undashed, exactly as Mojang stores it; the API re-dashes on arrival because
        // `rotom_users.uuid` is a dashed char(36).
        response.addProperty("uuid", profileId.toString().replace("-", "").toLowerCase(Locale.ROOT));
        callback.success(GSON.toJson(response));
    }
}
