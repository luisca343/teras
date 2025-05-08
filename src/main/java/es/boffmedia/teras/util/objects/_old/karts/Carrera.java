package es.boffmedia.teras.util.objects._old.karts;

import com.google.gson.Gson;
import com.mrcrayfish.vehicle.entity.EngineTier;
import com.mrcrayfish.vehicle.entity.PoweredVehicleEntity;
import com.mrcrayfish.vehicle.entity.vehicle.ATVEntity;
import com.mrcrayfish.vehicle.init.ModEntities;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.clientOld.CMessageCambioPosicion;
import es.boffmedia.teras.util.data.smartrotom.SmartRotomService;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.*;

public class Carrera {
    private Circuito circuito;
    private int vueltas;
    private ArrayList<Participante> participantes;
    private EstadoCarrera estado;
    private ArrayList<UUID> votos;

    private ArrayList<UUID> participantesTerminados;

    private ArrayList<Participante> posiciones;

    private long tiempoInicio;

    public Carrera(Circuito circuito, int numeroVueltas) {
        this.circuito = circuito;
        this.vueltas = numeroVueltas;
        this.participantes = new ArrayList<>();
        this.estado = EstadoCarrera.BUSCANDO;
        this.votos = new ArrayList<>();
        this.participantesTerminados = new ArrayList<>();
        this.posiciones = new ArrayList<>();

    }

    public void cerrarCarrera(){
        this.participantes = new ArrayList<>();
        this.estado = EstadoCarrera.FINALIZADA;
        this.votos = new ArrayList<>();
        this.participantesTerminados = new ArrayList<>();
        this.posiciones = new ArrayList<>();
    }

    public Circuito getCircuito() {
        return circuito;
    }

    public void setCircuito(Circuito circuito) {
        this.circuito = circuito;
    }

    public int getVueltas() {
        return vueltas;
    }

    public void setVueltas(int vueltas) {
        this.vueltas = vueltas;
    }

    public ArrayList<Participante> getParticipantes() {
        return participantes;
    }

    public void setParticipantes(ArrayList<Participante> participantes) {
        this.participantes = participantes;
    }

    public EstadoCarrera getEstado() {
        return estado;
    }

    public void setEstado(EstadoCarrera estado) {
        this.estado = estado;
    }

    public ArrayList<UUID> getVotos() {
        return votos;
    }

    public void setVotos(ArrayList<UUID> votos) {
        this.votos = votos;
    }

    public void playerTick(Participante participante) {

    }

    public ArrayList<Checkpoint> getCheckpoints() {
        return circuito.getCheckpoints();
    }

    public Checkpoint getCheckpoint(int index) {
        return circuito.getCheckpoint(index);
    }

    public void sonido(ServerPlayerEntity player, float pitch) {

        player.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_HARP, SoundCategory.PLAYERS, 1.0f, pitch);
    }

    public void iniciarCuentaAtras() {
        this.estado = EstadoCarrera.INICIANDO;
        int i = 0;
        for (Participante participante : participantes) {
            // Teleport each participant to a position
            Punto punto = circuito.getInicios().get(i);
            String orientacion = circuito.getOrientacion() == null ? "NORTE" : circuito.getOrientacion();
            spawnearVehiculo(participante.getJugador(), punto, orientacion);
            i++;
        }

        new Thread(() -> {
            try {
                Thread.sleep(1000);
                for (int j = 3; j > 0; j--) {
                    for (Participante participante : participantes) {
                        sonido(participante.getJugador(), 0.3f);
                        // Send title to player
                        MessageHelper.enviarTitulo(participante.getJugador(), j + "");
                    }
                    Thread.sleep(1000);
                }
                for (Participante participante : participantes) {
                    MessageHelper.enviarTitulo(participante.getJugador(), "¡YA!");
                    sonido(participante.getJugador(), 0.8f);
                    participante.getJugador().sendMessage(new StringTextComponent("¡YA!"), UUID.randomUUID());
                    moverVehiculo(participante.getJugador().getUUID(), true);
                }
                iniciar();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

    }


    public void spawnearVehiculo(ServerPlayerEntity player, Punto punto, String orientacion) {
        World world = player.level;
        ATVEntity vehicleEntity = new ATVEntity(ModEntities.ATV.get(), world);
        vehicleEntity.setRequiresFuel(false);
        vehicleEntity.setColorRGB(1, 2, 3);
        vehicleEntity.setEngineTier(EngineTier.WOOD);
        vehicleEntity.setPos(punto.getX() + 0.5f, punto.getY() + 1, punto.getZ() + 0.5f);
        vehicleEntity.disableFallDamage = true;

        Random random = new Random();
        int r = random.nextInt(255);
        int g = random.nextInt(255);
        int b = random.nextInt(255);
        vehicleEntity.setColorRGB(r,g,b);

        //vehicleEntity.setOwner(uuid);

        switch (orientacion){
            case "ESTE":
                vehicleEntity.yRot = -90;
                break;
            case "OESTE":
                vehicleEntity.yRot = 90;
                break;
            case "NORTE":
                vehicleEntity.yRot = 0;
                break;
            case "SUR":
                vehicleEntity.yRot = 180;
                break;
        }

        world.addFreshEntity(vehicleEntity);
        vehicleEntity.getSeatTracker().setSeatIndex(0, player.getUUID());
        player.startRiding(vehicleEntity);
    }

    public static void moverVehiculo(UUID uuid, boolean mover) {
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof PoweredVehicleEntity) {
            PoweredVehicleEntity powered = (PoweredVehicleEntity) vehicle;
            powered.setEngine(mover);
        }
    }

    public void iniciar() {
        this.estado = EstadoCarrera.EN_CURSO;
        this.tiempoInicio = System.currentTimeMillis();
        posiciones = (ArrayList<Participante>) getParticipantes().clone();
        calcularPosiciones();
    }

    private void calcularPosiciones() {
        new Thread(() -> {
            try {
                while (estado == EstadoCarrera.EN_CURSO){
                    Collections.sort(posiciones,
                            Comparator.comparing(Participante::getVuelta)
                                    .thenComparing(Participante::getSiguienteCheck).reversed()
                                    .thenComparing(Participante::getDistanciaSiguienteCheck)

                    );
                    for (Participante participante : participantes) {
                        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> participante.getJugador()), new CMessageCambioPosicion(posiciones.indexOf(participante) + 1+""));
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void añadirTerminado(UUID uuid) {
        participantesTerminados.add(uuid);
        if(participantesTerminados.size() == participantes.size()) {
            finalizar();
        }
    }

    private void finalizar() {
        estado = EstadoCarrera.FINALIZADA;
        long tiempoFinal = System.currentTimeMillis();
        Gson gson = new Gson();
        ArrayList<ParticipanteCarrera> participantesCarrera = new ArrayList<>();

        ResultadoCarrera resultado = new ResultadoCarrera();

        resultado.setFecha(tiempoInicio);
        resultado.setCircuito(circuito.getNombre());

        for (Participante participante : participantes) {
            ParticipanteCarrera participanteCarrera = new ParticipanteCarrera();

            participanteCarrera.setUuid(participante.getJugador().getStringUUID());
            participanteCarrera.setNombre(participante.getJugador().getName().getString());
            participanteCarrera.setPosicion(posiciones.indexOf(participante) + 1);

            if(participante.getTiempoFin() == 0) {
                participanteCarrera.setTiempo(tiempoFinal - tiempoInicio);
            } else{
                participanteCarrera.setTiempo(participante.getTiempoFin() - tiempoInicio);
            }

            participantesCarrera.add(participanteCarrera);

            MessageHelper.enviarTitulo(participante.getJugador(), "¡FIN!");
            sonido(participante.getJugador(), 0.8f);

            Teras.carreraManager.participantes.remove(participante.getJugador().getUUID());
            printResultados(participante.getJugador());
            Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> participante.getJugador()), new CMessageCambioPosicion(0+""));
        }

        resultado.setParticipantes(participantesCarrera);
        SmartRotomService.postCarrera(resultado);

        cerrarCarrera();
        Teras.carreraManager.carreras.remove(circuito.nombre);
        Teras.carreraManager.listarCarreras();
    }

    public void printResultados(ServerPlayerEntity jugador){
        String resultados = "";
        for (Participante participante : participantes) {
            resultados += participante.getJugador().getScoreboardName() + ": " + MessageHelper.formatearTiempo(participante.getTiempoFin() - getTiempoInicio());
        }

        MessageHelper.enviarMensaje(jugador, resultados);

    }

    public long getTiempoInicio() {
        return tiempoInicio;
    }
}
