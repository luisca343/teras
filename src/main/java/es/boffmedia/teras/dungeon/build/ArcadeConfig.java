package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/** The arcade cabinet: what a play costs, and how often the machine breaks. */
public record ArcadeConfig(int price, int breakChancePct) {

    static ArcadeConfig defaults() {
        return new ArcadeConfig(5, 8);
    }

    static ArcadeConfig read(YamlConfig yaml, ArcadeConfig previous) {
        YamlConfig arcade = yaml.section("arcada");
        return new ArcadeConfig(
                arcade.integer("precio", previous.price),
                arcade.integer("probRoturaPct", previous.breakChancePct));
    }
}
