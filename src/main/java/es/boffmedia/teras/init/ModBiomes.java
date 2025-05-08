package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.biome.*;
import net.minecraft.world.gen.surfacebuilders.ConfiguredSurfaceBuilders;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

public class ModBiomes {

    public static final DeferredRegister<Biome> BIOMES = DeferredRegister.create(ForgeRegistries.BIOMES, Teras.MOD_ID);

    public static final RegistryObject<Biome> ARRECIFE_WINGULL = getPueblo("arrecife_wingull", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .build());

    public static final RegistryObject<Biome> PUERTO_WINGULL = getPueblo("puerto_wingull", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x44AFF5)
            .waterFogColor(0x050533)
            .build());

    public static final RegistryObject<Biome> PUEBLO_TULIPAN = getPueblo("pueblo_tulipan", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .grassColorOverride(0x91BD59)
            .foliageColorOverride(0x77AB2F)
            .build());

    public static final RegistryObject<Biome> PUEBLO_SHIROI = getPueblo("pueblo_shiroi", Biome.RainType.SNOW, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x7BA4FF)
            .waterColor(0x3938C9)
            .waterFogColor(0x050533)
            .grassColorOverride(0x80B497)
            .foliageColorOverride(0x60A17B)
            .build());

    public static final RegistryObject<Biome> PUEBLO_HAGANE = getPueblo("pueblo_hagane", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .grassColorOverride(0x7FCA98)
            .foliageColorOverride(0x5FAE7B)
            .build());

    public static final RegistryObject<Biome> PUEBLO_YUME = getPueblo("pueblo_yume", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x6FA4FF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .grassColorOverride(0x91BD59)
            .foliageColorOverride(0x77AB2F)
            .build());

    public static final RegistryObject<Biome> PUEBLO_DENTO = getPueblo("pueblo_dento", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x40A7FF)
            .waterFogColor(0x050533)
            .grassColorOverride(0x7FCA98)
            .foliageColorOverride(0x5FAE7B)
            .build());

    public static final RegistryObject<Biome> PUEBLO_IWA = getPueblo("pueblo_iwa", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .grassColorOverride(0x8AB689)
            .foliageColorOverride(0x71A74D)
            .build());

    public static final RegistryObject<Biome> PUEBLO_TSUCHI = getPueblo("pueblo_tsuchi", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .grassColorOverride(0x90814D)
            .foliageColorOverride(0x9E6F41)
            .build());

    public static final RegistryObject<Biome> PUEBLO_OASIS = getPueblo("pueblo_oasis", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x4AADFF)
            .waterFogColor(0x050533)
            .grassColorOverride(0x91BD59)
            .foliageColorOverride(0x77AB2F)
            .build());

    public static final RegistryObject<Biome> PUEBLO_SENSHI = crearPuebloBasico("pueblo_senshi", Biome.RainType.RAIN);

    public static final RegistryObject<Biome> PUEBLO_KINOKO = getPueblo("pueblo_kinoko", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .grassColorOverride(0x55C93F)
            .foliageColorOverride(0x2BBB0F)
            .build());

    public static final RegistryObject<Biome> PUEBLO_SAKURA = getPueblo("pueblo_sakura", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .grassColorOverride(0x91BD59)
            .foliageColorOverride(0x77AB2F)
            .build());

    public static final RegistryObject<Biome> PUEBLO_TAKAI = getPueblo("pueblo_takai", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x6FA4FF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .grassColorOverride(0x83BB6D)
            .foliageColorOverride(0x63A948)
            .build());

    public static final RegistryObject<Biome> PUEBLO_DOKU = getPueblo("pueblo_doku", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3A7BEC)
            .waterFogColor(0x050533)
            .grassColorOverride(0x7A942E)
            .foliageColorOverride(0x8BBB18)
            .build());

    public static final RegistryObject<Biome> PUEBLO_GAKU = getPueblo("pueblo_gaku", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3454A1)
            .waterFogColor(0x050533)
            .grassColorOverride(0x7FCA98)
            .foliageColorOverride(0x5FAE7B)
            .build());

    public static final RegistryObject<Biome> PUEBLO_LAVANDA = getPueblo("pueblo_lavanda", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x6FA4FF)
            .waterColor(0x3F76E4)
            .waterFogColor(0x050533)
            .grassColorOverride(0x91BD59)
            .foliageColorOverride(0x77AB2F)
            .build());

    public static final RegistryObject<Biome> PUEBLO_DENKI = getPueblo("pueblo_denki", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x4B6FA8)
            .waterFogColor(0x050533)
            .grassColorOverride(0x91BD59)
            .foliageColorOverride(0x77AB2F)
            .build());

    public static final RegistryObject<Biome> PUEBLO_MIZU = getPueblo("pueblo_mizu", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x3D57D6)
            .waterFogColor(0x050533)
            .grassColorOverride(0x2FC28C)
            .foliageColorOverride(0x218F66)
            .build());

    public static final RegistryObject<Biome> PUEBLO_OLIVO = crearPuebloBasico("pueblo_olivo", Biome.RainType.RAIN);

    public static final RegistryObject<Biome> NARUKAMI = crearPuebloBasico("narukami", Biome.RainType.RAIN);

    public static final RegistryObject<Biome> AKINA = crearPuebloBasico("akina", Biome.RainType.RAIN);

    public static final RegistryObject<Biome> FUKITSU = crearPuebloBasico("fukitsu", Biome.RainType.RAIN);

    public static final RegistryObject<Biome> GANSOLIA = crearPuebloBasico("gansolia", Biome.RainType.RAIN);

    public static final RegistryObject<Biome> QUESTION_MARK = getPueblo("acento_circunflejo", Biome.RainType.RAIN, new BiomeAmbience.Builder()
            .fogColor(0xC0D8FF)
            .skyColor(0x77ADFF)
            .waterColor(0x8A0303)
            .waterFogColor(0x8A0303)
            .grassColorOverride(0x7FCA98)
            .foliageColorOverride(0x5FAE7B)
            .build());

    public static void generateBiomes() {
        setupBiome(ARRECIFE_WINGULL.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUERTO_WINGULL.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_TULIPAN.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_SHIROI.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_HAGANE.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_YUME.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_DENTO.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_IWA.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_TSUCHI.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_OASIS.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_SENSHI.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_KINOKO.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_SAKURA.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_TAKAI.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_DOKU.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_GAKU.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_LAVANDA.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_DENKI.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_MIZU.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(PUEBLO_OLIVO.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(NARUKAMI.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(AKINA.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(FUKITSU.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(GANSOLIA.get(), BiomeManager.BiomeType.WARM, 0);
        setupBiome(QUESTION_MARK.get(), BiomeManager.BiomeType.WARM, 0);
    }

    public static RegistryObject<Biome> crearPuebloBasico(String nombre, Biome.RainType precipitaciones){
        return BIOMES.register(nombre, () ->
                new Biome.Builder()
                        .biomeCategory(Biome.Category.NONE)
                        .temperature(0.8f)
                        .downfall(.4f)
                        .precipitation(precipitaciones)
                        .specialEffects(new BiomeAmbience.Builder()
                                .fogColor(15658734)
                                .skyColor(7907327)
                                .waterColor(0x3F76E4)
                                .waterFogColor(0x050533)
                                .build()
                        )
                        .scale(0.01f)
                        .depth(0.01f)
                        .mobSpawnSettings(new MobSpawnInfo.Builder().build())
                        .generationSettings(new BiomeGenerationSettings.Builder().surfaceBuilder(ConfiguredSurfaceBuilders.GRASS).build())
                        .build()
        );
    }

    public static RegistryObject<Biome> getPueblo(String nombre, Biome.RainType precipitaciones, BiomeAmbience specialEffects){
        return BIOMES.register(nombre, () ->
                new Biome.Builder()
                        .biomeCategory(Biome.Category.NONE)
                        .temperature(0.95f)
                        .downfall(.4f)
                        .precipitation(precipitaciones)
                        .specialEffects(specialEffects)
                        .scale(0.01f)
                        .depth(0.01f)
                        .mobSpawnSettings(new MobSpawnInfo.Builder().build())
                        .generationSettings(new BiomeGenerationSettings.Builder().surfaceBuilder(ConfiguredSurfaceBuilders.GRASS).build())
                        .build()
        );
    }

    public static void register(IEventBus modEventBus) {
        BIOMES.register(modEventBus);
    }

    private static void setupBiome(Biome biome, BiomeManager.BiomeType biomeType, int weight, BiomeDictionary.Type... types){
        RegistryKey<Biome> key = RegistryKey.create(ForgeRegistries.Keys.BIOMES, Objects.requireNonNull(ForgeRegistries.BIOMES.getKey(biome)));
        BiomeDictionary.addTypes(key, types);
        BiomeManager.addBiome(biomeType, new BiomeManager.BiomeEntry(key, weight));
    }
}