package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.TeamMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Equipos aleatorios: todos empiezan separados y los equipos se revelan en un episodio. */
public final class RandomTeamsModule implements UhcModule {
    private final TukiUHC plugin;
    private boolean revealed;

    public RandomTeamsModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private boolean active() {
        return plugin.settings().teamMode() == TeamMode.RANDOM;
    }

    @Override
    public void onPrepare() {
        revealed = false;
    }

    @Override
    public void onStart() {
        if (active() && plugin.settings().randomRevealEpisode() <= 1) {
            reveal();
        } else if (active()) {
            plugin.lang().broadcast("teams.random-pending", Lang.p("episode", plugin.settings().randomRevealEpisode()));
        }
    }

    @Override
    public void onSecond(int secondsToNextEpisode) {
        if (!active() || revealed) {
            return;
        }
        int next = plugin.game().episode() + 1;
        if (next == plugin.settings().randomRevealEpisode() && secondsToNextEpisode >= 1 && secondsToNextEpisode <= 5) {
            plugin.lang().broadcast("teams.random-countdown", Lang.p("seconds", secondsToNextEpisode));
        }
    }

    @Override
    public void onEpisodeStart(int episode) {
        if (active() && !revealed && episode >= plugin.settings().randomRevealEpisode()) {
            reveal();
        }
    }

    private void reveal() {
        revealed = true;
        List<UUID> pool = new ArrayList<>();
        for (UUID id : plugin.game().alive()) {
            if (!plugin.teams().hasTeam(id)) {
                pool.add(id);
            }
        }
        plugin.teams().randomize(pool, plugin.settings().teamSize(), plugin.lang().raw("teams.default-name"));
        plugin.lang().broadcast("teams.random-revealed");
        for (Party party : plugin.teams().teams()) {
            List<String> names = party.members().stream().map(plugin.stats()::name).toList();
            for (UUID id : party.members()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    continue;
                }
                List<String> mates = names.stream().filter(n -> !n.equals(player.getName())).toList();
                Component list = Component.text(mates.isEmpty() ? "-" : String.join(", ", mates), party.color());
                player.showTitle(Title.title(plugin.lang().get("teams.your-team-title"), list,
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofSeconds(1))));
                plugin.lang().send(player, "teams.your-team", Lang.p("team", party.name()), Lang.c("mates", list));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }
    }

    @Override
    public void onReset() {
        revealed = false;
    }
}
