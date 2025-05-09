package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.WeatherChangeAction;
import com.pixelmonmod.pixelmon.battles.status.GlobalStatusBase;
import com.pixelmonmod.pixelmon.battles.status.Weather;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;
import es.boffmedia.teras.pixelmon.battle.TerasBattleLog.WeatherType;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class WeatherChangeActionHandler implements BattleActionHandler<WeatherChangeAction> {
    @Override
    public void handle(WeatherChangeAction action, TerasBattle terasBattle) {
        GlobalStatusBase newGlobalStatus = (GlobalStatusBase) getProtectedProperty("newWeather", action);
        if (newGlobalStatus instanceof Weather) {
            Weather newWeather = (Weather) newGlobalStatus;
            WeatherType weatherType = WeatherType.fromPixelmonWeather(newWeather);
            appendLine(terasBattle, "|-weather|" + weatherType.getShowdownName() + "|");
        }
    }
}