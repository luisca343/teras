package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.YamlConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a floor and where it is built: cell pitch, room height, door opening, the void
 * lattice the instances stand on, and the per-floor cell budget.
 *
 * <p>{@code roomSize} is the <b>only</b> size convention: grid pitch, template footprint per cell
 * and door math all derive from it.</p>
 */
public record GenerationConfig(int roomSize, int roomHeight, int doorWidth, int doorHeight,
                               String dimension, int slotY, int slotSpacing, int maxSlots,
                               List<Integer> celdas, int jitter, int descentSeconds) {

    static GenerationConfig defaults() {
        return new GenerationConfig(21, 12, 3, 3, "teras:vacio", 64, 4096, 64,
                es.boffmedia.teras.dungeon.gen.GenConfig.defaults().celdas(),
                es.boffmedia.teras.dungeon.gen.GenConfig.defaults().jitter(),
                // The straggler bell: the first member down the pit starts this countdown, and when
                // it ends the rest of the party descends with them.
                15);
    }

    static GenerationConfig read(YamlConfig yaml, GenerationConfig previous) {
        YamlConfig gen = yaml.section("generacion");
        return new GenerationConfig(
                yaml.integer("tamanoSala", previous.roomSize),
                yaml.integer("alturaSala", previous.roomHeight),
                yaml.integer("anchoPuerta", previous.doorWidth),
                yaml.integer("altoPuerta", previous.doorHeight),
                yaml.string("dimension", previous.dimension),
                yaml.integer("slotY", previous.slotY),
                yaml.integer("separacionSlots", previous.slotSpacing),
                yaml.integer("maxSlots", previous.maxSlots),
                curve(gen, previous.celdas),
                Math.max(0, gen.integer("jitter", previous.jitter)),
                Math.max(3, yaml.integer("segundosDescenso", previous.descentSeconds)));
    }

    /**
     * The cell curve, all or nothing: half a parsed curve would silently reshape whichever floors
     * survived parsing, and a floor quietly changing size is the bug this table was built to end.
     */
    private static List<Integer> curve(YamlConfig gen, List<Integer> fallback) {
        List<Object> raw = gen.list("celdas");
        if (raw.isEmpty()) {
            return fallback;
        }
        List<Integer> parsed = new ArrayList<>();
        for (Object cell : raw) {
            try {
                parsed.add(Integer.parseInt(String.valueOf(cell).trim()));
            } catch (NumberFormatException e) {
                parsed.clear();
                break;
            }
        }
        if (parsed.isEmpty() || parsed.stream().anyMatch(cells -> cells < 1)) {
            Teras.LOGGER.error("Dungeons: 'generacion.celdas' is not a list of positive integers; "
                    + "keeping the built-in curve {}", fallback);
            return fallback;
        }
        return List.copyOf(parsed);
    }
}
