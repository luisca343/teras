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
import java.util.concurrent.atomic.AtomicInteger;

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

    // --- Circuit breaker: after a burst of failures, fail fast for a cooldown instead of
    //     hammering a dead/unreachable backend (which would otherwise re-stall login/race flows). ---
    private static final int FAILURE_THRESHOLD = 5;
    private static final long OPEN_DURATION_MS = 30_000L;
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static volatile long circuitOpenUntil = 0L;

    private static boolean isCircuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil;
    }

    private static void recordSuccess() {
        consecutiveFailures.set(0);
        circuitOpenUntil = 0L;
    }

    private static void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD && !isCircuitOpen()) {
            circuitOpenUntil = System.currentTimeMillis() + OPEN_DURATION_MS;
            Teras.LOGGER.warn("SmartRotom circuit OPEN for {}ms after {} consecutive failures",
                    OPEN_DURATION_MS, FAILURE_THRESHOLD);
        }
    }

    /**
     * Enforces the HTTPS policy. When {@code requireHttps} is set in config, a non-HTTPS URL
     * is refused (fail-closed). Returns false if the request must not proceed.
     */
    private static boolean isTransportAllowed(URL url) {
        if (Teras.config != null && Teras.config.isRequireHttps()
                && !"https".equalsIgnoreCase(url.getProtocol())) {
            Teras.LOGGER.error("SmartRotom request refused: requireHttps is enabled but URL is not HTTPS ({})",
                    url);
            return false;
        }
        return true;
    }

    /** Applies the shared request headers, including the bearer token when one is configured. */
    private static void applyHeaders(HttpURLConnection con) {
        con.setConnectTimeout(CONNECT_TIMEOUT_MS);
        con.setReadTimeout(READ_TIMEOUT_MS);
        con.setRequestProperty("User-Agent", "Teras-SmartRotom");
        con.setRequestProperty("Content-Type", "application/json");
        if (Teras.config != null) {
            String token = Teras.config.getApiToken();
            if (token != null && !token.isEmpty()) {
                con.setRequestProperty("Authorization", "Bearer " + token);
            }
        }
    }

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
            if (isCircuitOpen()) {
                Teras.LOGGER.warn("WingullAPI GET skipped (circuit open): " + str);
                return null;
            }
            HttpURLConnection con = null;
            try {
                String apiUrl = Teras.config != null ? Teras.config.getAPI_URL() : null;
                if (apiUrl == null) {
                    Teras.LOGGER.error("API URL is null");
                    return null;
                }

                URL url = new URL(apiUrl + str);
                if (!isTransportAllowed(url)) {
                    return null;
                }
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                applyHeaders(con);

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    Teras.LOGGER.info("WingullAPI GET " + str + " -> " + con.getResponseCode());
                    recordSuccess();
                    return response.toString();
                }
            } catch (IOException e) {
                Teras.LOGGER.error("WingullAPI GET error for " + str + ": " + e.getMessage());
                recordFailure();
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
            if (isCircuitOpen()) {
                Teras.LOGGER.warn("WingullAPI POST skipped (circuit open): " + str);
                return;
            }
            HttpURLConnection con = null;
            try {
                URL url = new URL(str);
                if (!isTransportAllowed(url)) {
                    return;
                }
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                applyHeaders(con);

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
                recordSuccess();
                QueryHelper.handlePOST(response, con);

            } catch (IOException e) {
                Teras.LOGGER.info("WingullAPI: " + e.getMessage());
                recordFailure();
            } finally {
                if (con != null) con.disconnect();
            }
        });
    }





}
