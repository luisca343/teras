package es.boffmedia.teras.util.objects.pixelmon;

import com.pixelmonmod.pixelmon.api.battles.BattleAIMode;
import com.pixelmonmod.pixelmon.api.battles.BattleType;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTiers;
import com.pixelmonmod.pixelmon.battles.api.rules.clauses.BattleClause;
import com.pixelmonmod.pixelmon.battles.api.rules.clauses.BattleClauseRegistry;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects._old.pixelmon.Recompensa;
import noppes.npcs.api.entity.ICustomNpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BattleConfig {
    private String nombre; // Nombre del entrenador / evento
    private String nivel; // Nivel de los Pokémon del entrenador
    private int dinero; // Dinero que se obtiene al vencer al entrenador
    private String modalidad; // Modalidad de combate (1vs1, 2vs2, 3vs3, etc)
    private String tamanoEquipos; // Cantidad de Pokémon de cada equipo (3vs3, 4vs4, 3vs6, etc)
    private String frecuencia; // Frecuencia de combate (DIA, DIA_MC, SEMANA, MES)
    private ArrayList<Recompensa> recompensas; // Recompensas que se obtienen al vencer al entrenador
    private ArrayList<String> normas; // Normas de combate (No usar objetos, sleep clause, etc)
    private ArrayList<String> gimmick; // Gimmick generacional permitida (Mega, Z, etc)
    private String IA; // Modo de la IA (AGGRESSIVE, ADVANCED, TACTICAL)
    private boolean curar; // Curar a los Pokémon antes de la batalla
    private boolean preview; // Mostrar el equipo del entrenador antes de la batalla
    private String logro; // Logro que se obtiene al vencer al entrenador
    private boolean exp; // Exp que se obtiene al vencer al entrenador
    private int[] equipos; // Equipos disponibles para el entrenador / evento

    // Atributos no serializables
    private transient String nombreArchivo; // Nombre del archivo de configuración (no hay que escribirlo)
    private transient String carpeta; // Carpeta donde se encuentra el archivo de configuración (no hay que escribirlo)
    private transient ICustomNpc npc; // NPC asociado al entrenador / evento
    private transient List<Pokemon> equipo; // Equipo del entrenador / evento

    public BattleConfig(){
        this.dinero = 0;
        this.nivel = "0";
        this.modalidad = "doble";
        this.IA = "TÁCTICA";
        this.exp = false;
        this.curar = false;
        this.preview = false;
        this.frecuencia = "DIA";
        this.recompensas = new ArrayList<>();
        this.normas = new ArrayList<>();
        this.gimmick = new ArrayList<>();
        this.equipos = new int[]{1};
    }

    public int getPlayerPkmCount(){
        return Integer.parseInt(getTamanoEquipos().split("vs")[0]);
    }

    public int getRivalPkmCount(){
        return Integer.parseInt(getTamanoEquipos().split("vs")[1]);
    }

    public int getNumPkmJugador(){
        return getPlayerPkmCount();
    }

    public int getNumPkmRival(){
        return getRivalPkmCount();
    }

    public int getDinero() {
        return dinero;
    }

    public void setDinero(int dinero) {
        this.dinero = dinero;
    }

    public ArrayList<Recompensa> getRecompensas() {
        return recompensas;
    }

    public void setRecompensas(ArrayList<Recompensa> recompensas) {
        this.recompensas = recompensas;
    }

    public void recibirRecompensas(UUID uuid){
        Teras.PROXY.darObjetos(recompensas, uuid);
    }

    public boolean healBeforeStart() {
        return curar;
    }

    public void setCurar(boolean curar) {
        this.curar = curar;
    }

    public boolean hasPreview() {
        return preview;
    }

    public void setPreview(boolean preview) {
        this.preview = preview;
    }

    public BattleType getBattleType(){
        switch (modalidad.toLowerCase()){
            case "doble":
                return BattleType.DOUBLE;
            case "triple":
                return BattleType.TRIPLE;
            case "rotatorio":
                return BattleType.ROTATION;
            case "horda":
                return BattleType.HORDE;
            case "raid":
                return BattleType.RAID;
            default:
                return BattleType.SINGLE;
        }
    }

    public int[] getModalidad() {
        switch (getBattleType()){
            case SINGLE:
                return new int[]{1, 1};
            case DOUBLE:
                return new int[]{2, 2};
            case TRIPLE:
                return new int[]{3, 3};
            case ROTATION:
                return new int[]{3, 3};
            case HORDE:
                return new int[]{1, 5};
            case RAID:
                return new int[]{4, 1};
            default:
                return new int[]{1, 1};
        }

        /*
        if(modalidad == null) return new int[]{1, 1};
        modalidad.replace("v", "vs");
        String[] partes = modalidad.split("vs");
        int[] numeros = new int[partes.length];
        for(int i = 0; i < partes.length; i++){
            numeros[i] = Integer.parseInt(partes[i]);
        }

        return numeros;*/
    }

    public void setModalidad(String modalidad) {
        this.modalidad = modalidad;
    }

    public String getFrecuencia() {
        return frecuencia == null ? "DIARIA" : frecuencia;
    }

    public void setFrecuencia(String frecuencia) {
        this.frecuencia = frecuencia;
    }

    public BattleAIMode getIA() {
        switch (IA.toUpperCase(Locale.ROOT)){
            case "AGGRESSIVE":
            case "AGRESIVA":
                return BattleAIMode.AGGRESSIVE;
            case "ADVANCED":
            case "AVANZADA":
                return BattleAIMode.ADVANCED;
            case "TACTICAL":
            case "TACTICA":
                return BattleAIMode.TACTICAL;
            default:
                return BattleAIMode.DEFAULT;
        }
    }

    public void setIA(String IA) {
        this.IA = IA;
    }

    public String getNivel() {
        if(nivel == null) return BossTiers.NOT_BOSS;
        if(nivel.contains("+")) return nivel.split("\\+")[1];
        if(nivel.contains("-")) return nivel;

        switch (nivel){
            case "0":
            case "=":
            case "IGUALADO":
                return BossTiers.EQUAL;
            default:
                return BossTiers.NOT_BOSS;
        }

    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public List<BattleClause> getNormas() {
        List<BattleClause> clauses = new ArrayList<>();
        for (String norma : normas) {
            clauses.add(BattleClauseRegistry.getClause(norma.toLowerCase(Locale.ROOT)));
        }
        return clauses;
    }

    public void setNormas(ArrayList<String> normas) {
        this.normas = normas;
    }

    public void setNpc(ICustomNpc npc){
        this.npc = npc;
    }

    public ICustomNpc getNpc(){
        return npc;
    }

    public List<Pokemon> getEquipo() {
        return equipo;
    }

    public void setEquipo(List<Pokemon> equipo) {
        this.equipo = equipo;
    }

    public int calculateTeamLevel(int nivelJugador){
        int nivel;

        if(this.nivel.contains("+")) nivel = nivelJugador + Integer.parseInt(this.nivel.split("\\+")[1]);
        else if(this.nivel.contains("-")) nivel = nivelJugador - Integer.parseInt(this.nivel.split("-")[1]);
        else if(this.nivel.equals("0") || this.nivel.equals("=") || this.nivel.equals("IGUALADO") || this.nivel.equals("EQUAL")) nivel = nivelJugador;
        else nivel = Integer.parseInt(this.nivel);
        return nivel;
    }

    public void setNivelEquipo(int nivelJugador){
        int nivel = calculateTeamLevel(nivelJugador);

        for (Pokemon pokemon : equipo) {
            pokemon.setLevel(nivel);
        }
    }

    public int getNivelesExtra(){
        if(this.nivel.contains("+")) return Integer.parseInt(this.nivel.split("\\+")[1]);
        else return 0;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public String getNombreObjetivo() {
        return nombreArchivo.replace("/","");
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getCarpeta() {
        return carpeta;
    }

    public void setCarpeta(String carpeta) {
        this.carpeta = carpeta;
    }

    public boolean esEntrenador(){
        return !carpeta.equals("eventos");
    }

    public Pokemon getFirstPokemon(){
        return getEquipo().get(0);
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setExp(boolean b) {
        this.exp = b;
    }

    public ArrayList<String> getGimmick() {
        return gimmick;
    }

    public void setGimmick(ArrayList<String> gimmick) {
        this.gimmick = gimmick;
    }

    public boolean isCurar() {
        return curar;
    }

    public boolean isPreview() {
        return preview;
    }

    public String getLogro() {
        return logro;
    }

    public void setLogro(String logro) {
        this.logro = logro;
    }

    public boolean isExp() {
        return exp;
    }

    public int[] getEquipos() {
        return equipos;
    }

    public void setEquipos(int[] equipos) {
        this.equipos = equipos;
    }

    public void setTamanoEquipos(String tamanoEquipos) {
        this.tamanoEquipos = tamanoEquipos;
    }

    public String getTamanoEquipos() {
        return tamanoEquipos;
    }
}
