package es.boffmedia.teras.util.data.smartrotom;

import com.google.gson.Gson;
import es.boffmedia.teras.util.objects.ShopTransaction;
import es.boffmedia.teras.util.objects.TrainerDefeatMoney;
import es.boffmedia.teras.util.objects.dex.ActualizarDex;
import es.boffmedia.teras.model.race.ResultadoCarrera;
import es.boffmedia.teras.util.objects.logros.LogroCombate;
import es.boffmedia.teras.util.objects.quests.UpdateNPCs;

public class SmartRotomService {
    static Gson gson = new Gson();

    public static void postRegistry(ActualizarDex updateDex) {
        SmartRotomAPI.wingullPOST("/smartrotom/pokemon/register", gson.toJson(updateDex));
    }

    // TODO: ADAPT TO NEW ENDPOINT
    public static void postCarrera(ResultadoCarrera carrera) {
        SmartRotomAPI.wingullPOST("/smartrotom/karts/carrera", gson.toJson(carrera));
    }

    // TODO: ADAPT TO NEW ENDPOINT
    public static String getRegions() {
        return SmartRotomAPI.wingullGET("/wingull/regions");
    }

    // TODO: ADAPT TO NEW ENDPOINT
    public static void updateNPCs(UpdateNPCs npcs) {
        SmartRotomAPI.wingullPOST("/smartrotom/misiones/npcs", gson.toJson(npcs));
    }

    // TODO: ADAPT TO NEW ENDPOINT
    public static void saveBattle(LogroCombate battle) {
        SmartRotomAPI.wingullPOST("/smartrotom/achievements/battle", gson.toJson(battle));
    }

    // TODO: ADAPT TO NEW ENDPOINT
    public static void saveShopTransaction(ShopTransaction shopTransaction) {
        SmartRotomAPI.wingullPOST("/smartrotom/starbank/shop", gson.toJson(shopTransaction));
    }

    // TODO: ADAPT TO NEW ENDPOINT
    public static void defeatTrainer(TrainerDefeatMoney defeatMoney) {
        SmartRotomAPI.wingullPOST("/smartrotom/starbank/trainerdefeat", gson.toJson(defeatMoney));
    }
}

