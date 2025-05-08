package es.boffmedia.teras.pixelmon.frentebatalla;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import es.boffmedia.teras.api.PokePasteReader;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.clientOld.CMessageRunJS;
import es.boffmedia.teras.util.objects.pixelmon.BattleConfig;
import es.boffmedia.teras.pixelmon.battle.CombateFrenteBatalla;
import es.boffmedia.teras.pixelmon.battle.TeamManager;
import es.boffmedia.teras.util.string.MessageHelper;
import es.boffmedia.teras.util.data.PersistentDataFields;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class TorreBatallaController {



    public static void registrarEquipo(String tipo, ServerPlayerEntity player){
        String mensaje = String.format("frenteBatalla('%s', '%s')", tipo, player.getUUID());
        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageRunJS(mensaje));

        eleccionesCombate(player);
    }


    public static void iniciarCombate(ServerPlayerEntity player){

        List<Pokemon> tier1 = PokePasteReader.fromTeras("Frente Batalla/tier1").build();
        List<Pokemon> equipo = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int num = new Random().nextInt(tier1.size());
            MessageHelper.enviarMensaje(player, "§aCargando pokemon " + tier1.get(num).getSpecies().getName());
            Pokemon pokemon = tier1.get(num);

            boolean match = equipo.stream().anyMatch(p -> p.getSpecies().getName().equals(pokemon.getSpecies().getName()));
            if(match){
                i--;
                continue;
            }

            tier1.remove(num);
            equipo.add(pokemon);
        }

        BattleConfig configCombate = new BattleConfig();
        configCombate.setEquipo(equipo);
        configCombate.setCarpeta("torre_batalla");
        configCombate.setNombreArchivo("tier1");
        configCombate.setNombre("Tier 1");
        configCombate.setDinero(0);
        configCombate.setCurar(true);
        configCombate.setExp(false);
        configCombate.setNombre("Entrenador de la Torre Batalla");


        CombateFrenteBatalla combate = new CombateFrenteBatalla(player, configCombate);
        combate.start();
    }


    public static void iniciarCombatev2(ServerPlayerEntity player, String modalidad) {
        List<Pokemon> tier1 = PokePasteReader.fromTeras("Frente Batalla/tier1").build();
        List<Pokemon> equipo = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int num = new Random().nextInt(tier1.size());
            MessageHelper.enviarMensaje(player, "§aCargando pokemon " + tier1.get(num).getSpecies().getName());
            Pokemon pokemon = tier1.get(num);

            boolean match = equipo.stream().anyMatch(p -> p.getSpecies().getName().equals(pokemon.getSpecies().getName()));
            if(match){
                i--;
                continue;
            }

            tier1.remove(num);
            equipo.add(pokemon);
        }

        BattleConfig configCombate = new BattleConfig();
        configCombate.setEquipo(equipo);
        configCombate.setCarpeta("torre_batalla");
        configCombate.setNombreArchivo("tier1");
        configCombate.setNombre("Tier 1");
        configCombate.setDinero(0);
        configCombate.setCurar(true);
        configCombate.setExp(false);
        configCombate.setNombre("Entrenador de la Torre Batalla");


        CombateFrenteBatalla combate = new CombateFrenteBatalla(player, configCombate);
        combate.setModalidad(modalidad);
        combate.start();
    }

    public static void pausar(ServerPlayerEntity player, String modalidad) {
        TeamManager.saveTeam(player, modalidad);
        TeamManager.loadTeam(player, "equipo");
        TeamManager.deleteTeam(player, "equipo");
        player.getPersistentData().putBoolean(PersistentDataFields.FB_ACTIVO.label, false);
        player.getPersistentData().putString(PersistentDataFields.EQUIPO_ACTIVO.label, "");
        int racha = player.getPersistentData().getInt(modalidad);
        MessageHelper.enviarMensaje(player, "§aHas pausado el Frente Batalla con una racha de " + racha + " victorias");
    }


    public static void eleccionesCombate(ServerPlayerEntity p){
        String modalidad = p.getPersistentData().getString(PersistentDataFields.EQUIPO_ACTIVO.label);

        MessageHelper.enviarMensaje(p, "§aVictorias en la modalidad " + modalidad + ": " + p.getPersistentData().getInt(modalidad));

        int victorias = p.getPersistentData().getInt(modalidad);
        if(victorias >= 7){
            MessageHelper.enviarMensaje(p, "§a¡Has conseguido 7 victorias en la modalidad " + modalidad + "!");
        }else{
            MessageHelper.enviarMensaje(p, "§a¡Te faltan " + (7 - victorias) + " victorias para conseguir el premio!");
        }

        IFormattableTextComponent strContinuar = new StringTextComponent(TextFormatting.GREEN + " [Siguiente Combate] ")
                .setStyle(StringTextComponent.EMPTY.getStyle()
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/frenteBatalla continuar"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Continuar combate"))));

        IFormattableTextComponent strSalir = new StringTextComponent(TextFormatting.RED + " [Salir] ")
                .setStyle(StringTextComponent.EMPTY.getStyle()
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/frenteBatalla pausar"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Salir del frente de batalla"))));

        TextComponent mensaje = new StringTextComponent("§a¿Qué deseas hacer? ");
        mensaje.append(strContinuar);
        mensaje.append(strSalir);

        p.sendMessage(mensaje, UUID.randomUUID());
    }

    public static void reanudar(ServerPlayerEntity player, String modalidad) {
        TeamManager.saveTeam(player, "equipo");
        TeamManager.loadTeam(player, modalidad);
        TeamManager.deleteTeam(player, modalidad);
        player.getPersistentData().putBoolean(PersistentDataFields.FB_ACTIVO.label, true);
        player.getPersistentData().putString(PersistentDataFields.EQUIPO_ACTIVO.label, modalidad);
        eleccionesCombate(player);
    }

    public static boolean puedeIniciar(ServerPlayerEntity player){
        if(player.getPersistentData().getBoolean(PersistentDataFields.FB_ACTIVO.label)){
            MessageHelper.enviarMensaje(player, "Ya estás participando en el Frente Batalla");
            return false;
        }
        MessageHelper.enviarMensaje(player, "No está participando en el Frente Batalla");
        return true;
    }
}


