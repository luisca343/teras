package es.boffmedia.teras.util.objects._old.serverdata;

import es.boffmedia.teras.Teras;
import net.minecraft.entity.player.PlayerEntity;

public class UserData {
    private String uuid;
    private String nombre;
    private String server;

    public UserData(String uuid, String nombre) {
        this.uuid = uuid;
        this.nombre = nombre;
        server = Teras.PROXY.idServidor;
    }

    public UserData(String uuid, String nombre, String idServer) {
        this.uuid = uuid;
        this.nombre = nombre;
        this.server = idServer;
    }

    public UserData(PlayerEntity player) {
        this.uuid = player.getUUID().toString();
        this.nombre = player.getName().getString();
        this.server = Teras.config.getId();
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

}
