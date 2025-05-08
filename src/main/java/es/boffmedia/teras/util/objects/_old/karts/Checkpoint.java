package es.boffmedia.teras.util.objects._old.karts;

public class Checkpoint {
    private Punto pos1, pos2;

    public Checkpoint(Punto inicio, Punto fin) {
        this.pos1 = inicio;
        this.pos2 = fin;
    }

    public Punto getPos1() {
        return pos1;
    }

    public void setPos1(Punto pos1) {
        this.pos1 = pos1;
    }

    public Punto getPos2() {
        return pos2;
    }

    public void setPos2(Punto pos2) {
        this.pos2 = pos2;
    }

    @Override
    public String toString() {
        return "["+ pos1.toString()+" "+ pos2.toString()+"]";
    }
}
