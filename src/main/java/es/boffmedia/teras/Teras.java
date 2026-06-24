package es.boffmedia.teras;


import com.google.gson.Gson;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.battles.attacks.EffectTypeAdapter;

import es.boffmedia.teras.blocks.LavenderFlower;
import es.boffmedia.teras.client.ClientProxy;
import es.boffmedia.teras.event.*;
import es.boffmedia.teras.init.*;
import es.boffmedia.teras.util.objects.quests.NpcCatalog;
import es.boffmedia.teras.integration.Integrations;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.util.PolygonCreator;
import es.boffmedia.teras.util.objects.karts.RaceManager;
import es.boffmedia.teras.model.config.TerasConfig;
import es.boffmedia.teras.pixelmon.attacks.DesenvaineSubito;
import es.boffmedia.teras.pixelmon.attacks.TestAttack;
import es.boffmedia.teras.pixelmon.battle.TerasBattleController;
import es.boffmedia.teras.tileentity.PantallaRenderer;
import es.boffmedia.teras.util.media.TerasSoundEvents;
import es.boffmedia.teras.client.renders.TVBlockRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.*;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;
import net.montoyo.mcef.api.*;
import net.montoyo.mcef.example.ModScheme;
import noppes.npcs.api.NpcAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Teras.MOD_ID)
public class Teras
{
    // Directly reference a log4j logger.
    public static TerasConfig config;
    public static List<PolygonCreator.Region> regions;

    // SmartRotom START
    public static final double PAD_RATIO = 59.0 / 30.0;
    public static final String MOD_ID = "teras";
    public static Gson GSON = new Gson();
    //public  static final String SMARTROTOM_HOME = "http://teras.es/smartrotom";
    // public static final String SMARTROTOM_HOME = "http://localhost:3000/smartrotom";
    public double padResX;
    public double padResY;

    public double avDist100 = 10.0;
    public double avDist0 = 30.0;
    public float ytVolume = 100.0f;

    // SmartRotom END
    public static final Logger LOGGER = LogManager.getLogger();
    //public static final String BLACKLIST_URL = "http://teras.es/smartrotom";

    public static Teras INSTANCE = null;
    public static TerasBattleController lbc;
    private API api;
    public static String HEADER_MENSAJE = TextFormatting.BLUE + "" + TextFormatting.BOLD+"[Teras]: ";

    public static SharedProxy PROXY = DistExecutor.<SharedProxy>safeRunForDist(() -> ClientProxy::new, () -> SharedProxy::new);

    public static RaceManager raceManager;


    public API getAPI() {
        return api;
    }

    public static Teras getInstance() {
        return INSTANCE;
    }

    public static Logger getLogger(){
        return LOGGER;
    }

    public static TerasBattleController getLBC() {
        return lbc;
    }


    public Teras() {
        INSTANCE = this;
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::end);

        padResY = 480;
        padResX = padResY * PAD_RATIO;

        NpcAPI npcAPI = NpcAPI.Instance();
        npcAPI.events().register(CustomNPCsEvents.class);

        // Register ourselves for server and other game events we are interested in
        //MinecraftForge.EVENT_BUS.register(this);
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();

        eventBus.addListener(Messages::registryNetworkPackets);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, es.boffmedia.teras.TerasConfig.SPEC, "teras/config.toml");


        ItemInit.register(eventBus);
        BlockInit.register(eventBus);
        FluidInit.register(eventBus);

        TileEntityInit.register(eventBus);
        ModBiomes.register(eventBus);
        TerasSoundEvents.register(eventBus);
    }
    public void setup(final FMLCommonSetupEvent event)
    {
        Pixelmon.EVENT_BUS.register(new MisionesCaza());
        Pixelmon.EVENT_BUS.register(new PixelmonEvents());
        Pixelmon.EVENT_BUS.register(new TerasBattleEvent());
        Pixelmon.EVENT_BUS.register(new TerasBattleLogEvent());
        Teras.PROXY.crearArchivo("config.json");


        ModBiomes.generateBiomes();
        //Grab the API and make sure it isn't null.
        api = MCEFApi.getAPI();

        if(api == null){
            Teras.LOGGER.error("MCEF API unavailable; SmartRotom browser features disabled");
            return;
        }

        api.registerScheme("teras", ModScheme.class, true, false, false, true, true, false, false);

        PROXY.preInit();
        lbc = new TerasBattleController();


        Integrations.registerBackpackIntegrations();


        EffectTypeAdapter.EFFECTS.put("TestAttack", TestAttack.class);
        EffectTypeAdapter.EFFECTS.put("DesenvaineSubito", DesenvaineSubito.class);



        event.enqueueWork(CommonHandler::setup);
    }

    public static InputStream getResource(String filename) throws IOException {
        URL url = Teras.class.getClassLoader().getResource("assets/" + MOD_ID + "/" + filename);

        if (url == null) {
            return null;
        }

        URLConnection connection = url.openConnection();
        connection.setUseCaches(false);
        return connection.getInputStream();
    }

    public void end (FMLLoadCompleteEvent event){
        PermissionAPI.registerNode("teras.frames.video", DefaultPermissionLevel.OP, "Permission to use video frames");
        PROXY.end(event);
    }


    private void doClientStuff(final FMLClientSetupEvent event) {
        // do something that can only be done on the es.boffmedia.teras.client
        // ClientRegistry.bindTileEntityRenderer(TileEntityInit.FUNKO_TE.get(), FunkoRenderer::new);

        //ClientRegistry.bindTileEntityRenderer(TileEntityInit.FUNKO_TE.get(), FunkoTERenderer::new);
        ClientRegistry.bindTileEntityRenderer(TileEntityInit.TEST_PANTALLA.get(), PantallaRenderer::new);


        RenderTypeLookup.setRenderLayer(FluidInit.AGUAS_TERMALES_SOURCE.get(), RenderType.translucent());
        RenderTypeLookup.setRenderLayer(FluidInit.AGUAS_TERMALES_FLOWING.get(), RenderType.translucent());
        RenderTypeLookup.setRenderLayer(FluidInit.AGUAS_TERMALES_BLOCK.get(), RenderType.translucent());


        RenderTypeLookup.setRenderLayer(BlockInit.TVBLOCK.get(), RenderType.cutout());
        ClientRegistry.bindTileEntityRenderer(TileEntityInit.FRAME_TE.get(), TVBlockRenderer::new);

        event.enqueueWork(() -> {
            LavenderFlower.registerRenderType();
        });

        LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().options);
    }

    private void enqueueIMC(final InterModEnqueueEvent event)
    {
        /*
        SlotTypePreset[] slots = {
                SlotTypePreset.HEAD, SlotTypePreset.NECKLACE, SlotTypePreset.BACK, SlotTypePreset.BODY,
                SlotTypePreset.HANDS, SlotTypePreset.RING, SlotTypePreset.CHARM
        };
        List<SlotTypeMessage.Builder> builders = new ArrayList<>();
        for (SlotTypePreset slot : slots) {
            SlotTypeMessage.Builder builder = slot.getMessageBuilder();
            if (slot == SlotTypePreset.RING) {
                builder.size(2);
            }
            builders.add(builder);
        }
        for (SlotTypeMessage.Builder builder : builders) {
            SlotTypeMessage message = builder.build();
            InterModComms.sendTo("curios", SlotTypeMessage.REGISTER_TYPE,
                    ()->message);
        }*/
    }

    private void processIMC(final InterModProcessEvent event)
    {
        // some example code to receive and process InterModComms from other mods
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m->m.getMessageSupplier().get()).
                collect(Collectors.toList()));
    }
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        // do something when the server starts
        LOGGER.info("HELLO from server starting");
        String carpetaMusica = "Teras";
        File directorio = new File(carpetaMusica);
        if(!directorio.exists()){
            try {
                Files.createDirectories(Paths.get("/Teras"));

            } catch (IOException e) {
                LOGGER.error("Failed to create Teras music directory", e);
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        NpcCatalog.saveIfDirty();
    }


    /* Pantallas */

    public static String applyBlacklist(String url) {
        if(url.contains("smartrotom"))
            return "Smart Rotom";
        return url;
    }

    /**
     * SmartRotom pads are whitelisted to the smartrotom site only: any other
     * URL is blocked and the pad is redirected back to its home page.
     */
    public static boolean isSiteAllowed(String url) {
        return url.contains("smartrotom");
    }


}

