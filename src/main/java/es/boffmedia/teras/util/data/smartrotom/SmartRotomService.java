package es.boffmedia.teras.util.data.smartrotom;

import com.google.gson.Gson;
import es.boffmedia.teras.util.objects.ShopTransaction;
import es.boffmedia.teras.util.objects.TrainerDefeatMoney;
import es.boffmedia.teras.util.objects.dex.ActualizarDex;
import es.boffmedia.teras.util.objects._old.karts.ResultadoCarrera;
import es.boffmedia.teras.util.objects.logros.LogroCombate;
import es.boffmedia.teras.util.objects.quests.UpdateNPCs;

public class SmartRotomService {
    static Gson gson = new Gson();

    public static void postRegistry(ActualizarDex updateDex) {
        SmartRotomAPI.wingullPOST("/pokemon/registry", gson.toJson(updateDex));
    }

    public static void postCarrera(ResultadoCarrera carrera) {
        SmartRotomAPI.wingullPOST("/karts/carrera", gson.toJson(carrera));
    }

    public static String getRegions() {
        return SmartRotomAPI.wingullGET("/regions");
    }

    public static void updateNPCs(UpdateNPCs npcs) {
        SmartRotomAPI.wingullPOST("/misiones/npcs", gson.toJson(npcs));
    }

    public static void saveBattle(LogroCombate battle) {
        SmartRotomAPI.wingullPOST("/smartrotom/achievements/battle", gson.toJson(battle));
    }

    public static void saveShopTransaction(ShopTransaction shopTransaction) {
        SmartRotomAPI.wingullPOST("/starbank/shop", gson.toJson(shopTransaction));
    }

    public static void defeatTrainer(TrainerDefeatMoney defeatMoney) {
        SmartRotomAPI.wingullPOST("/starbank/trainerdefeat", gson.toJson(defeatMoney));
    }
}
