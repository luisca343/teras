package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/**
 * The blocks the run loop writes into a floor after it is built: the gate a sealed room drops, the
 * seal glyph's rune inlay, the wall a secret hides behind, the curse room's spikes — and the two
 * tolls that are paid in something other than coins.
 */
public record RoomsConfig(String sealBlock, String sealRuneBlock, String sealRuneLitBlock,
                          int sealDoorWidth, int sealDoorHeight, String crackBlock,
                          String spikeBlock, int curseDoorTollHearts) {

    static RoomsConfig defaults() {
        return new RoomsConfig(
                // Its own block rather than iron bars: panes connect to their neighbours and nine of
                // them read as a cage, where the reja tiles into one lattice — and you have to be
                // able to see and shoot through the room you are locked in.
                "teras:reja",
                // Dull while the boss lives, lit when the seal re-pins. The lit rune gets an
                // invisible light block stamped over it, so any block works here.
                "minecraft:polished_basalt",
                "minecraft:amethyst_block",
                // The grand ceremonial door the boss's death carves between the 2x2 arena and the
                // 2x2 sala del sello: one wide opening centered on the shared face.
                7, 5,
                "teras:muro_agrietado",
                "minecraft:pointed_dripstone",
                // One heart to cross, floored so it can never kill. Real under the health lockdown,
                // where the only healing left is a potion somebody paid for.
                1);
    }

    static RoomsConfig read(YamlConfig yaml, RoomsConfig previous) {
        return new RoomsConfig(
                yaml.string("bloqueSello", previous.sealBlock),
                yaml.string("bloqueRunaSello", previous.sealRuneBlock),
                yaml.string("bloqueRunaSelloEncendida", previous.sealRuneLitBlock),
                Math.max(1, yaml.integer("anchoPuertaSello", previous.sealDoorWidth)),
                Math.max(1, yaml.integer("altoPuertaSello", previous.sealDoorHeight)),
                yaml.string("bloqueGrieta", previous.crackBlock),
                yaml.string("bloquePinchos", previous.spikeBlock),
                yaml.integer("peajePuertaCorazones", previous.curseDoorTollHearts));
    }
}
