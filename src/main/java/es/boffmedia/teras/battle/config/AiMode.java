package es.boffmedia.teras.battle.config;

import java.util.Locale;

/**
 * Engine-neutral trainer AI difficulty. Providers map this to their own AI type where supported
 * (Pixelmon {@code BattleAIMode}); engines without a matching concept ignore it. Accepts both the
 * English and Spanish labels used in the config JSON's {@code IA} field.
 */
public enum AiMode {
    DEFAULT,
    AGGRESSIVE,
    ADVANCED,
    TACTICAL;

    public static AiMode fromLabel(String ia) {
        switch (ia == null ? "" : ia.toUpperCase(Locale.ROOT)) {
            case "AGGRESSIVE":
            case "AGRESIVA":
                return AGGRESSIVE;
            case "ADVANCED":
            case "AVANZADA":
                return ADVANCED;
            case "TACTICAL":
            case "TACTICA":
            case "TÁCTICA":
                return TACTICAL;
            default:
                return DEFAULT;
        }
    }
}
