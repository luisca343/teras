package es.boffmedia.teras.event;

import com.google.gson.Gson;
import com.pixelmonmod.pixelmon.blocks.tileentity.PCTileEntity;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.commands.*;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageConfigServer;
import es.boffmedia.teras.net.video.ScreenManager;
import es.boffmedia.teras.util.data.PersistentDataFields;
import es.boffmedia.teras.util.file.FileHelper;
import es.boffmedia.teras.util.game.ShinyTracker;
import es.boffmedia.teras.util.objects.karts.RaceManager;
import es.boffmedia.teras.model.config.TerasConfig;
import es.boffmedia.teras.particle.FakeParticle;
import es.boffmedia.teras.util.cache.TextureCache;
import es.boffmedia.teras.util.displayers.VideoDisplayer;
import es.boffmedia.teras.util.performance.ClientScheduler;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.server.command.ConfigCommand;


@Mod.EventBusSubscriber
public class TerasEvents {
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onPokemonSpawn(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof PixelmonEntity && !event.isCanceled() && event.getResult() != Event.Result.DENY) {
            ClientScheduler.schedule(1, () -> { //Wait a tick so the entity is fully loaded, so it isn't a bulbasaur
                PixelmonEntity entity = (PixelmonEntity) event.getEntity();
                if ((entity.getPokemon().isShiny() || entity.getPokemon().isPalette("shiny2")) && !entity.isBossPokemon()) {
                    ShinyTracker tracker = ShinyTracker.INSTANCE;
                    if (tracker.shouldTrackShiny(entity)) {
                        tracker.track(entity);
                    }
                }
            });
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onPlayerLeaveWorld(PlayerEvent.PlayerLoggedOutEvent event) {
        ShinyTracker.INSTANCE.untrackAll();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderWorldLastEvent(RenderWorldLastEvent event) {
        ShinyTracker.INSTANCE.camera = new ClippingHelper(event.getMatrixStack().last().pose(), event.getProjectionMatrix());


    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.getMap().location().equals(AtlasTexture.LOCATION_PARTICLES)) {
            event.addSprite(new ResourceLocation(Teras.MOD_ID, "particle/stars_0"));
            event.addSprite(new ResourceLocation(Teras.MOD_ID, "particle/stars_1"));
            FakeParticle.atlasTexture = event.getMap();
        }
    }

    @SubscribeEvent
    public static void onRenderTickEvent(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TextureCache.renderTick();
        }
    }

    @SubscribeEvent
    public static void onClientTickEvent(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TextureCache.clientTick();
            VideoDisplayer.tick();
        }

        if (!Minecraft.getInstance().isPaused()
                && (Minecraft.getInstance().screen == null || Minecraft.getInstance().screen instanceof ChatScreen)) {
            ShinyTracker.INSTANCE.tick();
            ClientScheduler.tick();
        }
    }

    @SubscribeEvent
    public static void onUnloadingLevel(WorldEvent.Unload unload) {
        if (unload.getWorld() != null && unload.getWorld().isClientSide()) {
            TextureCache.unload();
            VideoDisplayer.unload();
        }
    }


    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientTickEvent(TickEvent.PlayerTickEvent event) {
        /*
        if (event.phase == TickEvent.Phase.START) {
            BlockState bloque = event.player.level.getBlockState(event.player.blockPosition());
            if(bloque.getBlock() == FluidInit.AGUAS_TERMALES_BLOCK.get() && !event.player.hasEffect(Effects.REGENERATION)){
                event.player.addEffect(new net.minecraft.potion.EffectInstance(Effects.REGENERATION, 100, 1));
                }
            }*/
    }

    @SubscribeEvent
    public static void onServerStarted(FMLServerStartedEvent event) {
        ScreenManager.loadScreens();

        try {
            Teras.raceManager = new RaceManager();
        } catch (NullPointerException e) {
            Teras.LOGGER.error("ERROR AL CREAR EL CARRERA MANAGER");
        }
    }


    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        new TestCommand(event.getDispatcher());
        new GpsCommand(event.getDispatcher());
        new DiscosCommand(event.getDispatcher());
        new CombateCommand(event.getDispatcher());
        new WECommand(event.getDispatcher());
        new DimTPCommand(event.getDispatcher());

        new ObjectiveCommand(event.getDispatcher());

        new KartsCommand(event.getDispatcher());
        new FrenteBatallaCommand(event.getDispatcher());

        new DungeonCommand(event.getDispatcher());
        new FunkoCommand(event.getDispatcher());

        // Client only commands


        /*
        if(event.getEnvironment().compareTo(Commands.EnvironmentType.DEDICATED) == 0){
            new KartsCommand(event.getDispatcher());
        }*/

        ConfigCommand.register(event.getDispatcher());
    }


    @SubscribeEvent
    public static void onTeleport(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getPlayer().getPersistentData().getBoolean(PersistentDataFields.FB_ACTIVO.label)) {
            event.setCanceled(true);
            MessageHelper.enviarMensaje(event.getPlayer(), "NO PUEDES TELETRANSPORTARTE EN ESTE MOMENTO");
        }
    }

    @SubscribeEvent
    public static void interactuarBloque(PlayerInteractEvent.RightClickBlock event) {
        TileEntity block = event.getWorld().getBlockEntity(event.getPos());
        TileEntity blockDebajo = event.getWorld().getBlockEntity(event.getPos().below());
        if (!event.getPlayer().getPersistentData().getBoolean(PersistentDataFields.FB_ACTIVO.label)) return;


        if (block instanceof PCTileEntity || blockDebajo instanceof PCTileEntity) {
            MessageHelper.enviarMensaje(event.getPlayer(), "NO PUEDES USAR EL PC EN ESTE MOMENTO");
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void enterWorld(EntityJoinWorldEvent event) {
        if (!(event.getEntity() instanceof PlayerEntity)) return;
        // API URL DOES NOT EXIST YET
        //PolygonCreator.createPolygon();
    }


    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent ev) {
        Gson gson = new Gson();
        TerasConfig terasConfig = FileHelper.getConfig();
        // Send the client-safe copy: it omits apiToken so the backend credential never leaves the server.
        String data = gson.toJson(terasConfig.copyForClient());

        ev.getPlayer().getPersistentData().putBoolean("frentebatalla", false);

        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) ev.getPlayer()), new CMessageConfigServer(data));
    }
}

