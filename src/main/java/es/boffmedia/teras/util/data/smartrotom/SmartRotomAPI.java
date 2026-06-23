package es.boffmedia.teras.util.data.smartrotom;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.data.QueryHelper;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SmartRotomAPI {
    // private static String WINGULL_URL = "http://79.116.9.120:34301/";

    /** Connection/read timeout (ms) so a slow or dead endpoint can never hang the calling thread indefinitely. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    /** Shared, daemon-threaded pool for all outbound HTTP so we never spawn unbounded raw threads. */
    private static final ExecutorService HTTP_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Teras-SmartRotom-HTTP");
        t.setDaemon(true);
        return t;
    });

    /**
     * Performs a blocking GET on a background thread. The result is bounded by {@link #CONNECT_TIMEOUT_MS}/
     * {@link #READ_TIMEOUT_MS}, so the caller can never stall for longer than the timeout even if the API is down.
     * NOTE: callers should prefer {@link #wingullGETAsync(String)} and avoid calling this on the server thread.
     */
    public static String wingullGET(String str) {
        try {
            return wingullGETAsync(str).get(CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS + 1_000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Teras.LOGGER.error("WingullAPI GET interrupted: " + str);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            Teras.LOGGER.error("WingullAPI GET failed for " + str + ": " + e.getMessage());
            return null;
        }
    }

    /** Non-blocking GET. Result resolves on the shared HTTP executor; never touch MC state from the callback thread. */
    public static CompletableFuture<String> wingullGETAsync(String str) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection con = null;
            try {
                String apiUrl = Teras.config.getAPI_URL();
                if (apiUrl == null) {
                    Teras.LOGGER.error("API URL is null");
                    return null;
                }

                URL url = new URL(apiUrl + str);
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(CONNECT_TIMEOUT_MS);
                con.setReadTimeout(READ_TIMEOUT_MS);
                con.addRequestProperty("User-Agent", "Mozilla/4.0");
                con.setRequestProperty("Content-Type", "application/json");

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    Teras.LOGGER.info("WingullAPI GET " + str + " -> " + con.getResponseCode());
                    return response.toString();
                }
            } catch (IOException e) {
                Teras.LOGGER.error("WingullAPI GET error for " + str + ": " + e.getMessage());
                return null;
            } finally {
                if (con != null) con.disconnect();
            }
        }, HTTP_EXECUTOR);
    }

    public static void wingullPOST( String str, String json) {
        post(Teras.config.getAPI_URL() + str, json);
    }


    // Send post request
    public static void post(String str, String json) {
        Teras.LOGGER.info("WingullAPI: " + str + " " + json);
        HTTP_EXECUTOR.execute(() -> {
            HttpURLConnection con = null;
            try {
                URL url = new URL(str);
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(CONNECT_TIMEOUT_MS);
                con.setReadTimeout(READ_TIMEOUT_MS);
                con.setDoOutput(true);
                con.addRequestProperty("User-Agent", "Mozilla/4.0");
                con.setRequestProperty("Content-Type", "application/json");

                try (OutputStream os = con.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                QueryHelper.handlePOST(response, con);

            } catch (IOException e) {
                Teras.LOGGER.info("WingullAPI: " + e.getMessage());
            } finally {
                if (con != null) con.disconnect();
            }
        });
    }





}
