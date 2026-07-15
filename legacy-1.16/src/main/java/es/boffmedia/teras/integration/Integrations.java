package es.boffmedia.teras.integration;

import net.minecraftforge.fml.ModList;

public class Integrations {

    public static boolean usefulBackpacks() {
        return isLoaded("usefulbackpacks");
    }

    public static boolean simplyBackpacks() {
        return isLoaded("simplybackpacks");
    }

    public static boolean sophisticatedBackpacks() {
        return isLoaded("sophisticatedbackpacks");
    }

    public static boolean curiosApi() {
        return isLoaded("curios");
    }

    public static boolean travelersBackpack() {
        return isLoaded("travelersbackpack") ;
    }

    public static boolean jei() {
        return isLoaded("jei");
    }

    public static boolean ftbQuests() {
        return isLoaded("ftbquests");
    }

    static boolean isLoaded(String modid) {
        return ModList.get().isLoaded(modid);
    }

    public static void registerBackpackIntegrations() {
        new TravelersBackpackIntegration();
    }

}