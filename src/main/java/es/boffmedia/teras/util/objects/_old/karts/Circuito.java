package es.boffmedia.teras.util.objects._old.karts;

import es.boffmedia.teras.Teras;
import net.minecraft.command.CommandSource;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.LinkedList;

public class Circuito {
    String nombre;
    ArrayList<Checkpoint> checkpoints;
    LinkedList<Punto> inicios;
    public String orientacion;

    public Circuito(String nombre) {
        this.nombre = nombre;
        this.checkpoints = new ArrayList<>();
        this.inicios = new LinkedList<>();
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public ArrayList<Checkpoint> getCheckpoints() {
        return checkpoints;
    }

    public Checkpoint getCheckpoint(int i) {
        if(i < checkpoints.size()){
            return checkpoints.get(i);
        }
        return null;
    }


    public void nuevoCheckpoint(Checkpoint checkpoint) {
        checkpoints.add(checkpoint);
    }

    public void nuevoCheckpoint(Punto puntoInicio, Punto puntoFin) {
        checkpoints.add(new Checkpoint(puntoInicio, puntoFin));
    }

    public void setCheckpoints(ArrayList<Checkpoint> checkpoints) {
        this.checkpoints = checkpoints;
    }

    public LinkedList<Punto> getInicios() {
        return inicios;
    }

    public void nuevoInicio(Punto inicio) {
        inicios.addLast(inicio);
    }

    public void printCheckpoints(CommandSource source) {
        int i = 0;
        for (Checkpoint checkpoint : checkpoints) {
            source.sendSuccess(new StringTextComponent("#"+ ++i + " " + checkpoint.toString()), false);
        }
    }

    public void printInicios(CommandSource source) {
        int i = 0;
        for (Punto inicio : inicios) {
            source.sendSuccess(new StringTextComponent("#"+ ++i + " " + inicio.toString()), false);
        }
    }

    public void guardar() {
        Teras.carreraManager.circuitos.put(nombre, this);
        // Transform into JSON with Gson
        Teras.carreraManager.guardarCircuito(this);



    }

    public String getOrientacion() {
        return orientacion;
    }
}
