package es.boffmedia.teras.util.objects.post;

import es.boffmedia.teras.Teras;

public class SmartRotomPost {
    private String server;
    public String uuid;

    public SmartRotomPost() {
        this.server = Teras.config.getId();
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
