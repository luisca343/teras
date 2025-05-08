package es.boffmedia.teras.util.objects.dex;

import es.boffmedia.teras.util.objects.post.SmartRotomPost;

public class ActualizarDex extends SmartRotomPost {
    private int pokemonId;
    String form;
    String palette;
    private int status;

    public ActualizarDex(String uuid, int idPokemon, int estado, String form, String palette) {
        super();
        this.uuid = uuid;
        this.pokemonId = idPokemon;
        this.status = estado;
        this.form = form;
        this.palette = palette;
    }

    public int getIdPokemon() {
        return pokemonId;
    }

    public void setIdPokemon(int idPokemon) {
        this.pokemonId = idPokemon;
    }

    public int getEstado() {
        return status;
    }

    public void setEstado(int estado) {
        this.status = estado;
    }

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        this.form = form;
    }

    public String getPalette() {
        return palette;
    }

    public void setPalette(String palette) {
        this.palette = palette;
    }
}
