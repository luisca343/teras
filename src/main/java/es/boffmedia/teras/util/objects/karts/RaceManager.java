package es.boffmedia.teras.util.objects.karts;

import com.google.gson.internal.LinkedTreeMap;
import com.mrcrayfish.vehicle.entity.PoweredVehicleEntity;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.file.FileHelper;
import es.boffmedia.teras.util.string.MessageHelper;
import io.leangen.geantyref.TypeToken;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;

public class RaceManager {
    public Map<String, RaceTrack> tracks;
    public Map<String, Race> activeRaces;
    public Map<UUID, RaceParticipant> participants;


    public RaceManager(){
        tracks = new LinkedTreeMap<>();
        activeRaces = new LinkedTreeMap<>();
        participants = new LinkedTreeMap<>();

        loadTracks();
    }

    public void loadTracks(){
        Type token = new TypeToken<Map<String, RaceTrack>>() {}.getType();

        tracks = (LinkedTreeMap<String, RaceTrack>) FileHelper.readFile("config/teras/circuitos.json", token);

        for (Map.Entry<String, RaceTrack> entry : tracks.entrySet()) {
            Teras.LOGGER.info(entry.getKey() + " : " + entry.getValue());
        }
    }

    public void joinRace(String trackName, int laps, ServerPlayerEntity player) {
        if (!tracks.containsKey(trackName)) {
            return;
        }

        if (!activeRaces.containsKey(trackName)) {
            RaceTrack track = tracks.get(trackName);
            // Crear una nueva carrera
            Race carrera = new Race(track, laps);
            activeRaces.put(trackName, carrera);
        }

        Race race = activeRaces.get(trackName);
        if (participants.containsKey(player.getUUID())) {
            leaveRace(player);
        }

        MessageHelper.enviarMensaje(player, "Entrando en la carrera " + trackName + " con " + laps + " vueltas");

        RaceParticipant participant = new RaceParticipant(player, race);
        participants.put(player.getUUID(), participant);
        race.getParticipants().add(participant);

        for (RaceParticipant part : race.getParticipants()) {
            TextComponent mensaje = new StringTextComponent("Participantes: " + race.getParticipants().size());
            StringTextComponent strVotarInicio = new StringTextComponent(TextFormatting.GREEN + " [Votar inicio] ");
            Style estilo = strVotarInicio.getStyle()
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karts votar"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Entrar en carrera")));

            StringTextComponent strCancelarVoto = new StringTextComponent(TextFormatting.RED + " [Cancelar voto] ");
            Style estiloCancelar = strCancelarVoto.getStyle()
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karts cancelarvoto"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Cancelar voto")));


            StringTextComponent strSalir = new StringTextComponent(TextFormatting.RED + " [Salir] ");
            Style estiloSalir = strSalir.getStyle()
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karts salir"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Salir de la carrera")));


            strVotarInicio.setStyle(estilo);
            strCancelarVoto.setStyle(estiloCancelar);
            strSalir.setStyle(estiloSalir);

            mensaje.append(strVotarInicio);
            mensaje.append(strCancelarVoto);
            mensaje.append(strSalir);

            part.getPlayer().sendMessage(mensaje, UUID.randomUUID());

        }
    }



    public void leaveRace(ServerPlayerEntity jugador) {
        if (!participants.containsKey(jugador.getUUID())) {
            MessageHelper.enviarMensaje(jugador, "No estás en ninguna carrera");
            return;
        }

        Race race = participants.get(jugador.getUUID()).getRaceIn();
        race.getParticipants().removeIf(participante -> participante.getPlayer().getUUID().equals(jugador.getUUID()));
        participants.remove(jugador.getUUID());
        Teras.LOGGER.info(jugador.getName() + " has salido de la carrera " + race.getTrack().getName());

        if (race.getParticipants().isEmpty()) {
            activeRaces.remove(race.getTrack().getName());
            Teras.LOGGER.info("La carrera ha sido eliminada al no tener participantes");
        }
    }

    public void startCountdown(String nombre) {
        if (!activeRaces.containsKey(nombre)) {
            return;
        }

        Race race = activeRaces.get(nombre);
        race.startCountdown();
    }

    public void startRace(String nombre) {
        if (!activeRaces.containsKey(nombre)) {
            return;
        }

        Race race = activeRaces.get(nombre);
        race.start();
    }


    public void voteStart(ServerPlayerEntity jugador) {
        System.out.println("Votando inicio");
        System.out.println(jugador.getUUID());
        System.out.println(participants);
        if (!participants.containsKey(jugador.getUUID())) {
            MessageHelper.enviarMensaje(jugador, "No estás en ninguna carrera");
            return;
        }
        Race race = participants.get(jugador.getUUID()).getRaceIn();

        Teras.getLogger().info(race.getTrack().getName());
        Teras.getLogger().info(race.getStartVotes());

        if(race.getStartVotes().contains(jugador.getUUID())){
            MessageHelper.enviarMensaje(jugador, "Ya has votado por este circuito");
            return;
        }
        race.getStartVotes().add(jugador.getUUID());

        if(race.getStartVotes().size() > race.getParticipants().size() / 2){
            race.startCountdown();
            raceBroadcast("Han votado suficientes participantes, iniciando carrera, la carrera comenzará en 3 segundos");
        }
    }


    public void unvoteStart(ServerPlayerEntity player) {
        if (!participants.containsKey(player.getUUID())) {
            MessageHelper.enviarMensaje(player, "No estás en ninguna carrera");
            return;
        }
        Race race = participants.get(player.getUUID()).getRaceIn();

        if(!race.getStartVotes().contains(player.getUUID())){
            MessageHelper.enviarMensaje(player, "No has votado por este circuito");
            return;
        }

        MessageHelper.enviarMensaje(player, "Has cancelado tu voto");
        race.getStartVotes().remove(player.getUUID());
    }

    public void raceBroadcast(String mensaje){
        for (Map.Entry<UUID, RaceParticipant> entry : participants.entrySet()) {
            RaceParticipant participant = entry.getValue();
            participant.getPlayer().sendMessage(new StringTextComponent(mensaje), UUID.randomUUID());
        }
    }

    public void listTracks(ServerPlayerEntity player) {
        for (Map.Entry<String, RaceTrack> entry : tracks.entrySet()) {
            player.sendMessage(new StringTextComponent(entry.getKey()), UUID.randomUUID());
        }
    }

    public void playerTick(ServerPlayerEntity jugador) {
        if (!participants.containsKey(jugador.getUUID())) {
            return;
        }
        RaceParticipant participant = participants.get(jugador.getUUID());
        participant.tick();
    }

    private static Method setRawPosition;

    static {
        try {
            setRawPosition = ObfuscationReflectionHelper.findMethod(PoweredVehicleEntity.class, "func_70080_a", double.class, double.class, double.class, float.class, float.class);
            setRawPosition.setAccessible(true);
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to get setRawPosition method", e);
        }
    }

    public void hitCar(ServerPlayerEntity player) {
        Teras.LOGGER.info("GOLPEANDO COCHE");
        if (player.getVehicle() instanceof PoweredVehicleEntity) {
            PoweredVehicleEntity vehicleEntity = (PoweredVehicleEntity) player.getVehicle();

            // Disable the engine temporarily
            vehicleEntity.setEngine(false);

            // Start a new thread to handle the spinning effect
            new Thread(() -> {
                try {
                    int spinDuration = 40; // Number of ticks to spin (2 seconds at 20 ticks per second)
                    float totalRotation = 360f; // Total degrees to rotate
                    float instabilityStrength = 0.05f; // Adjust this value to control instability

                    double initialX = vehicleEntity.getX();
                    double initialY = vehicleEntity.getY();
                    double initialZ = vehicleEntity.getZ();
                    float initialYaw = vehicleEntity.yRot;

                    for (int i = 0; i < spinDuration; i++) {
                        // Calculate new rotation
                        float progress = (float) i / spinDuration;
                        float newYaw = initialYaw + progress * totalRotation;

                        // Apply rotation
                        vehicleEntity.setYBodyRot(newYaw);
                        vehicleEntity.yRotO = newYaw;

                        // Sync player rotation with vehicle
                        player.setYBodyRot(newYaw);
                        player.yRotO = newYaw;

                        // Add instability
                        double instabilityX = (Math.random() - 0.5) * instabilityStrength;
                        double instabilityZ = (Math.random() - 0.5) * instabilityStrength;

                        // Apply a small upward force to prevent sinking
                        double upwardForce = 0.05;

                        Vector3d instabilityMotion = new Vector3d(instabilityX, upwardForce, instabilityZ);
                        vehicleEntity.setDeltaMovement(instabilityMotion);

                        // Force position update to keep the vehicle in place
                        vehicleEntity.setPos(initialX, initialY, initialZ);

                        // Update the vehicle's prevPosX, prevPosY, prevPosZ
                        vehicleEntity.xo = initialX;
                        vehicleEntity.yo = initialY;
                        vehicleEntity.zo = initialZ;

                        Thread.sleep(50); // 50ms sleep for 20 ticks per second
                    }

                    // Re-enable the engine after spinning
                    vehicleEntity.setEngine(true);

                    // Apply speed loss
                    Vector3d currentMotion = vehicleEntity.getDeltaMovement();
                    vehicleEntity.setDeltaMovement(currentMotion.multiply(0.5, 1, 0.5));

                } catch (InterruptedException e) {
                    Teras.LOGGER.error("Error during car hit effect", e);
                }
            }).start();
        }
    }
    private boolean isPlayerInRace(UUID uuid) {
        return participants.containsKey(uuid);
    }

    public void displayRaceStatus(ServerPlayerEntity player) {
        if (!isPlayerInRace(player.getUUID())) {
            MessageHelper.enviarMensaje(player, "No estás en ninguna carrera.");
            return;
        }

        RaceParticipant participant = participants.get(player.getUUID());
        Race race = participant.getRaceIn();

        String status = String.format("Carrera: %s\n" +
                        "Vuelta: %d/%d\n" +
                        "Checkpoint: %d/%d\n" +
                        "Posición: %d/%d\n" +
                        "Tiempo restante: %s",
                race.getTrack().getName(),
                participant.getCurrentLap(),
                race.laps,
                participant.getCurrentCheckpointIndex() + 1,
                race.getCheckpoints().size(),
                race.getParticipants().indexOf(participant) + 1,
                race.getParticipants().size(),
                MessageHelper.formatearTiempo(race.getRemainingTime()));

        MessageHelper.enviarMensaje(player, status);
    }
}

