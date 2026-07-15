package es.boffmedia.teras.util.objects.post;

import es.boffmedia.teras.util.string.MessageHelper;

public class PokedexEventResponse {
    private String uuid;
    private String pokemonName;
    private String form;
    private String palette;
    private int status;

    public PokedexEventResponse(String uuid, String pokemonName, String form, String palette, int status) {
        this.uuid = uuid;
        this.pokemonName = pokemonName;
        this.form = form;
        this.palette = palette;
        this.status = status;
    }

    public void sendMessage() {
        String estado = this.status == 0 ? "visto" : "capturado";
        MessageHelper.enviarMensaje(uuid, "Se ha registrado como "
                + estado + " a " + pokemonName + form + palette);
    }
}
