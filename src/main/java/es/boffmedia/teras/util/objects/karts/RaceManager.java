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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RaceManager {
    public Map<String, RaceTrack> tracks;
    public Map<String, Race> activeRaces;
    public Map<UUID, RaceParticipant> participants;
    private Map<UUID, VehicleHitHandler> hitHandlers = new HashMap<>();

    private Map<UUID, VehicleDriftHandler> driftHandlers = new HashMap<>();


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

    public void playerTick(ServerPlayerEntity player) {
        if (!participants.containsKey(player.getUUID())) {
            return;
        }
        RaceParticipant participant = participants.get(player.getUUID());
        participant.tick();

        // Add vehicle tick
        tickVehicles();
    }

    public void handleDriftInput(ServerPlayerEntity player, boolean startDrift, boolean driftRight, float vehicleYaw) {
        if (!participants.containsKey(player.getUUID())) return;

        if (player.getVehicle() instanceof PoweredVehicleEntity) {
            PoweredVehicleEntity vehicle = (PoweredVehicleEntity) player.getVehicle();

            // Get or create drift handler
            VehicleDriftHandler driftHandler = driftHandlers.computeIfAbsent(
                    vehicle.getUUID(),
                    k -> new VehicleDriftHandler(vehicle)
            );

            if (startDrift) {
                driftHandler.startDrift(driftRight, vehicleYaw);
            } else {
                driftHandler.endDrift();
            }
        }
    }

    public void tickVehicles() {
        // Remove handlers for vehicles that no longer exist
        hitHandlers.entrySet().removeIf(entry -> !entry.getValue().isStunned());

        // Update all active hit handlers
        hitHandlers.values().forEach(VehicleHitHandler::tick);

        // Remove drift handlers for vehicles that no longer exist or aren't drifting
        driftHandlers.entrySet().removeIf(entry ->
                !entry.getValue().getVehicle().isAlive() ||
                        (!entry.getValue().isDrifting() && !entry.getValue().isBoosting())
        );

        // Update all active drift handlers
        driftHandlers.values().forEach(VehicleDriftHandler::tick);
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
        if (player.getVehicle() instanceof PoweredVehicleEntity) {
            PoweredVehicleEntity vehicleEntity = (PoweredVehicleEntity) player.getVehicle();

            // Get or create hit handler
            VehicleHitHandler hitHandler = hitHandlers.computeIfAbsent(
                    vehicleEntity.getUUID(),
                    k -> new VehicleHitHandler(vehicleEntity)
            );

            // Calculate hit direction (you can modify this based on where the hit comes from)
            Vector3d hitDirection = new Vector3d(
                    player.level.random.nextDouble() - 0.5,
                    0,
                    player.level.random.nextDouble() - 0.5
            );

            // Apply the hit
            hitHandler.applyHit(hitDirection);
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

    public boolean hasParticipant(UUID uuid) {
        return participants.containsKey(uuid);
    }
}

