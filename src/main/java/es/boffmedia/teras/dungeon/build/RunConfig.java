package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/**
 * The run itself rather than the floor: party size, how close to an entrance NPC you must stand,
 * the two teardown safety nets, whether results are posted, how long an unclaimed way home is kept,
 * and what a death costs.
 *
 * @param containersLostPerDeath heart containers a death takes, and therefore how many deaths a
 *                               member has in them. This is the dungeon's only loss condition —
 *                               a player out of containers is out of the expedition, and a party
 *                               with nobody left has wiped — so it is the single number that
 *                               decides how punishing a run is. Zero disables it, which restores
 *                               the old behaviour of a run that cannot be lost
 */
public record RunConfig(int desertionGraceSeconds, int buildTimeoutSeconds, int maxParty,
                        int entranceRadius, boolean backendPostEnabled, int returnExpiryDays,
                        int containersLostPerDeath) {

    static RunConfig defaults() {
        return new RunConfig(
                // Long enough to survive a router blip or a client restart, short enough that a
                // party that is not coming back stops holding a slot and a floor.
                180,
                // Generous on purpose. A build that actually fails is reported the moment it does,
                // so this only ever catches a completion that never arrives at all — and the job
                // queue is one shared line, so a floor can legitimately sit behind a dozen other
                // builds and discards.
                300,
                4, 16, true,
                // A pending return outlives the run that filed it and is only ever consumed by its
                // owner logging back in; without an expiry, returns.json only grows.
                30,
                // Two of a player's ten, so a member has five deaths in them over a whole run.
                // Ships non-zero deliberately: a loss condition that is off by default is a loss
                // condition nobody plays against, which is how the run got here.
                2);
    }

    static RunConfig read(YamlConfig yaml, RunConfig previous) {
        return new RunConfig(
                yaml.integer("graciaAbandonoSegundos", previous.desertionGraceSeconds),
                yaml.integer("timeoutConstruccionSegundos", previous.buildTimeoutSeconds),
                Math.max(1, yaml.integer("maxGrupo", previous.maxParty)),
                Math.max(1, yaml.integer("radioEntrada", previous.entranceRadius)),
                yaml.bool("enviarResultados", previous.backendPostEnabled),
                yaml.integer("diasParaExpirarRetornos", previous.returnExpiryDays),
                Math.max(0, yaml.integer("contenedoresPorMuerte",
                        previous.containersLostPerDeath)));
    }
}
