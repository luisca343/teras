package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/**
 * Every reward table the dungeon draws from, and the one reward that is not a table — the treasure
 * room's provisión stand, which pays coins and a wall charge into the shared purse.
 */
public record LootConfig(String treasure, String secret, String superSecret, String devil,
                         String orden, String treasureArma, String treasureVitalidad, String boss,
                         String chest, String chestProeza,
                         int provisionCoins, int provisionCharges) {

    static LootConfig defaults() {
        return new LootConfig(
                "teras:dungeon/treasure",
                // A secret costs a wall charge the party bought, so it pays the treasure table:
                // breaking in has to be worth at least what getting in cost. The super secret is
                // rarer and pays better.
                "teras:dungeon/treasure",
                "teras:dungeon/boss",
                "teras:dungeon/devil",
                "teras:dungeon/orden",
                "teras:dungeon/treasure_arma",
                "teras:dungeon/treasure_vitalidad",
                "teras:dungeon/boss",
                "teras:dungeon/treasure",
                // proeza draws the boss table on purpose: it is placed where only parkour reaches,
                // and the route is the whole price.
                "teras:dungeon/boss",
                40, 1);
    }

    static LootConfig read(YamlConfig yaml, LootConfig previous) {
        YamlConfig chest = yaml.section("cofres");
        return new LootConfig(
                yaml.string("lootTesoro", previous.treasure),
                yaml.string("lootSecreta", previous.secret),
                yaml.string("lootSupersecreta", previous.superSecret),
                yaml.string("lootTrato", previous.devil),
                yaml.string("lootOrden", previous.orden),
                yaml.string("lootTesoroArma", previous.treasureArma),
                yaml.string("lootTesoroVitalidad", previous.treasureVitalidad),
                yaml.string("lootJefe", previous.boss),
                chest.string("loot", previous.chest),
                chest.string("lootProeza", previous.chestProeza),
                yaml.integer("tesoroProvisionMonedas", previous.provisionCoins),
                yaml.integer("tesoroProvisionCargas", previous.provisionCharges));
    }

    /** Every table this config names, for validation — a blank one is a room that pays nothing. */
    java.util.Map<String, String> tables() {
        java.util.Map<String, String> named = new java.util.LinkedHashMap<>();
        named.put("lootTesoro", treasure);
        named.put("lootSecreta", secret);
        named.put("lootSupersecreta", superSecret);
        named.put("lootTrato", devil);
        named.put("lootOrden", orden);
        named.put("lootTesoroArma", treasureArma);
        named.put("lootTesoroVitalidad", treasureVitalidad);
        named.put("lootJefe", boss);
        named.put("cofres.loot", chest);
        named.put("cofres.lootProeza", chestProeza);
        return named;
    }
}
