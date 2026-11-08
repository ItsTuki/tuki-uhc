package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.TeamMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.util.Show;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;

/** "Amor a primera vista": los equipos se forman al encontrarse durante la partida. */
public final class ProximityTeamsModule implements UhcModule {
    private static final double JOIN_DISTANCE = 2.5;
    private final TukiUHC plugin;

    public ProximityTeamsModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onStart() {
        if (plugin.settings().teamMode() == TeamMode.PROXIMITY) {
            plugin.lang().broadcast("teams.proximity-start", Lang.p("size", plugin.settings().teamSize()),
                    Lang.p("radius", plugin.settings().proximityRadius()));
        }
    }

    private boolean hasRoom(Player player) {
        Party team = plugin.teams().team(player.getUniqueId());
        return team == null || team.members().size() < plugin.settings().teamSize();
    }

    @Override
    public void onSecond(int secondsToNextEpisode) {
        if (plugin.settings().teamMode() != TeamMode.PROXIMITY || plugin.settings().teamSize() < 2) {
            return;
        }
        List<Player> candidates = new ArrayList<>();
        for (UUID id : plugin.game().alive()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && hasRoom(player)) {
                candidates.add(player);
            }
        }
        double radius = plugin.settings().proximityRadius();
        for (Player player : candidates) {
            Player nearest = nearest(player, candidates, radius);
            if (nearest == null) {
                continue;
            }
            double distance = player.getLocation().distance(nearest.getLocation());
            if (distance <= JOIN_DISTANCE) {
                join(player, nearest);
                return; // La lista cambió; se recalcula el próximo segundo.
            }
            Show.actionBar(player, plugin.lang().get("teams.proximity-near", Lang.p("player", nearest.getName()),
                    Lang.p("distance", (int) distance)));
            Compat.effect(nearest, XPotion.GLOWING, 30, 0, false);
        }
    }

    private Player nearest(Player player, List<Player> candidates, double radius) {
        Player best = null;
        double bestDistance = radius * radius;
        for (Player other : candidates) {
            if (other.equals(player) || !other.getWorld().equals(player.getWorld())
                    || plugin.teams().sameTeam(player.getUniqueId(), other.getUniqueId())) {
                continue;
            }
            Party a = plugin.teams().team(player.getUniqueId());
            Party b = plugin.teams().team(other.getUniqueId());
            int combined = (a == null ? 1 : a.members().size()) + (b == null ? 1 : b.members().size());
            if (combined > plugin.settings().teamSize()) {
                continue;
            }
            double d = other.getLocation().distanceSquared(player.getLocation());
            if (d <= bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        return best;
    }

    private void join(Player a, Player b) {
        Party teamA = plugin.teams().team(a.getUniqueId());
        Party teamB = plugin.teams().team(b.getUniqueId());
        Party target;
        if (teamA != null) {
            target = teamA;
            if (teamB != null) {
                for (UUID member : new ArrayList<>(teamB.members())) {
                    plugin.teams().add(target, member);
                }
                plugin.teams().delete(teamB);
            } else {
                plugin.teams().add(target, b.getUniqueId());
            }
        } else if (teamB != null) {
            target = teamB;
            plugin.teams().add(target, a.getUniqueId());
        } else {
            target = plugin.teams().create(cl.itstuki.uhc.team.Party.Kind.TEAM,
                    plugin.lang().raw("teams.default-name") + " " + (plugin.teams().teams().size() + 1));
            plugin.teams().add(target, a.getUniqueId());
            plugin.teams().add(target, b.getUniqueId());
        }
        for (UUID id : target.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) {
                Compat.removeEffect(member, XPotion.GLOWING);
                Compat.sound(member, XSound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                plugin.lang().send(member, "teams.proximity-formed", Lang.p("team", target.name()),
                        Lang.p("players", target.members().stream().map(plugin.stats()::name).collect(Collectors.joining(", "))));
            }
        }
    }
}
