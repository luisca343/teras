package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.battles.api.rules.PropertyValue;
import java.util.Optional;

import com.pixelmonmod.pixelmon.battles.api.rules.property.type.AbstractBooleanProperty;
import com.pixelmonmod.pixelmon.battles.api.rules.value.BooleanValue;


public class TerasBattleProperty extends AbstractBooleanProperty {
    public TerasBattleProperty() {
    }

    public String getId() {
        return "TerasBattle";
    }

    public boolean requiredByClient() {
        // The client mod does not register this server-only property, so it must not be flagged
        // required-by-client — otherwise team-select/rules serialization would desync. It is used
        // purely server-side as a key object for rules.set/get.
        return false;
    }

    public Optional<PropertyValue<Boolean>> getDefault() {
        return Optional.of(new BooleanValue(false));
    }
}
