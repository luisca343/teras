package es.boffmedia.teras.dungeon.encounter;

import net.minecraft.network.chat.Component;

/**
 * What a dungeon enemy is called on screen.
 *
 * <p>Enemies are identified by id — {@code saqueador_cuevas}, {@code reina_madre} — and nothing has
 * ever turned one into a name, because until elites wore nametags and bosses had bars, nothing
 * needed to. There are no lang entries for them either, and there are more enemy ids than anyone
 * will keep translated.</p>
 *
 * <p>So the same shape gear uses: a translation key <b>with the id-derived name as its fallback</b>.
 * A new enemy is named the moment it exists, a lang entry still wins where one is written, and
 * nothing ever renders as {@code enemy.teras.saqueador_cuevas} in front of a player — which is
 * exactly what happened with gear before the fallback was added.</p>
 */
public final class EnemyNames {
    private EnemyNames() {}

    public static Component of(String id) {
        return Component.translatableWithFallback("enemy.teras." + id,
                es.boffmedia.teras.dungeon.model.Names.fromId(id));
    }

}
