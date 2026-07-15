package es.boffmedia.teras.pixelmon.battle;

public class PokemonPosition {
    private int participant;
    private int position;

    public PokemonPosition(int participant, int position) {
        this.participant = participant;
        this.position = position;
    }

    public int getParticipant() {
        return participant;
    }

    public int getPosition() {
        return position;
    }

    public void setParticipant(int participant) {
        this.participant = participant;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
