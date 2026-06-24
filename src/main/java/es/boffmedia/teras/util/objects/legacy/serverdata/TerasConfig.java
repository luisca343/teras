package es.boffmedia.teras.util.objects.legacy.serverdata;

public class TerasConfig {
    private String id;
    private String home;
    private String API_URL;

    /**
     * Bearer token sent on outbound SmartRotom requests. SENSITIVE — must never be sent to clients
     * (see {@link #copyForClient()}). Loaded from config/teras/config.json on the server only.
     */
    private String apiToken;

    /**
     * When true, plaintext (non-HTTPS) SmartRotom requests are refused (fail-closed). Defaults to
     * false so existing localhost/dev setups keep working until an HTTPS endpoint is configured.
     */
    private boolean requireHttps;


    public String getId() {
        return id;
    }

    public String getHome() {
        return home;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getAPI_URL() {
        return API_URL;
    }

    public void setAPI_URL(String API_URL) {
        this.API_URL = API_URL;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public boolean isRequireHttps() {
        return requireHttps;
    }

    public void setRequireHttps(boolean requireHttps) {
        this.requireHttps = requireHttps;
    }

    /**
     * Returns a copy safe to serialise and send to clients: it deliberately omits {@link #apiToken}
     * so the server's backend credential never reaches a client.
     */
    public TerasConfig copyForClient() {
        TerasConfig c = new TerasConfig();
        c.id = this.id;
        c.home = this.home;
        c.API_URL = this.API_URL;
        c.requireHttps = this.requireHttps;
        // apiToken intentionally NOT copied.
        return c;
    }
}
