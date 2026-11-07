package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.config.TeamMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Modo escolta: los compañeros no pueden separarse más de X bloques.
 * Si siguen separados, cada 10 segundos todo el equipo pierde 1 corazón.
 */
public final class EscortModule implements UhcModule {
    private static final int PENALTY_EVERY = 10;
    private final TukiUHC plugin;
    private static final int MIN_DISTANCE = 40;
    private final Map<Integer, Integer> apartSeconds = new HashMap<>();
    private int maxDistance = 100;

    public EscortModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private boolean active() {
        if (!plugin.settings().enabled(Feature.ESCORT) || !plugin.game().isRunning()) {
            return false;
        }
        // Con equipos que se revelan más tarde, el episodio de revelación es de gracia.
        if (plugin.settings().teamMode() == TeamMode.RANDOM
                && plugin.game().episode() <= plugin.settings().randomRevealEpisode()) {
            return false;
        }
        return true;
    }

    @Override
    public void onStart() {
        maxDistance = plugin.settings().value(Feature.ESCORT);
    }

    /** Cada parte la distancia permitida baja 10 bloques, hasta un mínimo de 40. */
    @Override
    public void onEpisodeStart(int episode) {
        if (!plugin.settings().enabled(Feature.ESCORT) || episode <= 1 || maxDistance <= MIN_DISTANCE) {
            return;
        }
        maxDistance = Math.max(MIN_DISTANCE, maxDistance - 10);
        plugin.lang().broadcast("escort.reduced", Lang.p("max", maxDistance));
    }

    public int maxDistance() {
        return maxDistance;
    }

    @Override
    public void onSecond(int secondsToNextEpisode) {
        if (!active()) {
            return;
        }
        int max = maxDistance;
        for (Party team : plugin.teams().teams()) {
            boolean apart = false;
            for (UUID id : team.members()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || !plugin.game().isAlive(id)) {
                    continue;
                }
                for (UUID mateId : team.members()) {
                    Player mate = Bukkit.getPlayer(mateId);
                    if (mate == null || mate.equals(player) || !plugin.game().isAlive(mateId)) {
                        continue;
                    }
                    if (!mate.getWorld().equals(player.getWorld()) || mate.getLocation().distance(player.getLocation()) > max) {
                        apart = true;
                    }
                }
            }
            if (!apart) {
                apartSeconds.remove(team.id());
                continue;
            }
            int seconds = apartSeconds.merge(team.id(), 1, Integer::sum);
            for (UUID id : team.members()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || !plugin.game().isAlive(id)) {
                    continue;
                }
                if (seconds == 1) {
                    player.showTitle(Title.title(plugin.lang().get("escort.warning-title"),
                            plugin.lang().get("escort.warning-subtitle", Lang.p("max", max)),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
                }
                if (seconds % PENALTY_EVERY == 0) {
                    player.damage(2.0);
                    player.showTitle(Title.title(plugin.lang().get("escort.danger-title"),
                            plugin.lang().get("escort.danger-subtitle"),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
                }
            }
        }
    }

    @Override
    public void onReset() {
        apartSeconds.clear();
    }
}
