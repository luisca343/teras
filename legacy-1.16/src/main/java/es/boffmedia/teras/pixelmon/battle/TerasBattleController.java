package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.battles.BattleRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TerasBattleController {
    public Map<Integer, TerasBattle> terasBattles = new ConcurrentHashMap<>();



    public static enum TipoCombate{
        INDIVIDUAL("INDIVIDUAL"),
        DOBLE("DOBLE"),
        TRIPLE("TRIPLE"),
        MULTIPLE("MULTIPLE"),
        TB_INDIVIDUAL("TB_INDIVIDUAL"),
        TB_DOBLE("TB_DOBLE"),
        TB_TRIPLE("TB_TRIPLE"),
        TB_MULTIPLE("TB_MULTIPLE");

        public final String label;

        private TipoCombate(String label) {
            this.label = label;
        }

    }


    public boolean existsTerasBattle(int id){
        return terasBattles.containsKey(id);
    }

    public TerasBattle getTerasBattle(int id){
        return terasBattles.get(id);
    }

    public void addTerasBattle(int id, TerasBattle combate){
        reapStaleBattles();
        terasBattles.put(id, combate);
        TerasBattleLog.appendStartBattle(combate);
    }

    public void removeTerasBattle(int id){
        terasBattles.remove(id);
    }

    /**
     * Drops entries whose underlying battle is no longer active in Pixelmon's registry. Normal endings
     * are cleared by {@link #removeTerasBattle} from {@code BattleEndEvent}; this reaps the ones that
     * leak on abnormal termination (crash/disconnect with no end event). Runs opportunistically each
     * time a new battle is registered, so the map can't grow unbounded across a long uptime.
     */
    private void reapStaleBattles(){
        terasBattles.keySet().removeIf(battleId -> BattleRegistry.getBattle(battleId) == null);
    }
}
