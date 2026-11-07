package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.GameManager;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Format;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Scoreboard lateral, vida en el tabulador, colores de equipo y cabecera/pie del tab. Sin dependencias. */
public final class ScoreboardModule implements UhcModule {
    private static final int MAX_LINES = 15;
    private final TukiUHC plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                update(player);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Scoreboard: " + ex.getMessage());
            }
        }
    }

    private void update(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        Objective sidebar = board.getObjective("uhc");
        if (sidebar == null) {
            sidebar = board.registerNewObjective("uhc", Criteria.DUMMY, Component.empty());
            sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
            sidebar.numberFormat(NumberFormat.blank());
        }
        sidebar.displayName(plugin.lang().get("scoreboard.title", Lang.p("name", plugin.settings().matchName())));
        Objective health = board.getObjective("hp");
        if (health == null) {
            health = board.registerNewObjective("hp", Criteria.HEALTH, Component.text("❤", NamedTextColor.RED), RenderType.HEARTS);
            health.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        }

        List<Component> lines = lines(player);
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = "l" + (i < 10 ? "0" : "") + i;
            if (i < lines.size()) {
                var score = sidebar.getScore(entry);
                score.setScore(MAX_LINES - i);
                score.customName(lines.get(i));
            } else {
                board.resetScores(entry);
            }
        }
        syncTeams(board);
        tab(player);
    }

    // ── Líneas ───────────────────────────────────────────────

    private List<Component> lines(Player player) {
        GameManager game = plugin.game();
        List<Component> lines = new ArrayList<>();
        Lang lang = plugin.lang();
        GameState state = game.state();
        if (state == GameState.LOBBY) {
            lines.add(lang.get("scoreboard.mode", Lang.c("mode", lang.get("modes." + plugin.settings().mode().key() + ".name"))));
            lines.add(lang.get("scoreboard.teams", Lang.c("teams", lang.get("team-modes." + plugin.settings().teamMode().key() + ".name")),
                    Lang.p("size", plugin.settings().teamSize())));
            lines.add(lang.get("scoreboard.players", Lang.p("count", Bukkit.getOnlinePlayers().size())));
            lines.add(Component.empty());
            lines.add(lang.get("scoreboard.waiting"));
            return lines;
        }
        if (state == GameState.SCATTERING || state == GameState.PREPARED || state == GameState.STARTING) {
            lines.add(lang.get("scoreboard.preparing"));
            lines.add(Component.empty());
            lines.add(lang.get("scoreboard.players", Lang.p("count", game.participants().size())));
            return lines;
        }
        if (state == GameState.ENDED) {
            lines.add(lang.get("scoreboard.ended"));
            lines.add(lang.get("scoreboard.time", Lang.p("time", Format.time(game.totalSeconds()))));
            return lines;
        }

        UUID id = player.getUniqueId();
        lines.add(lang.get("scoreboard.episode", Lang.p("episode", game.infiniteEpisodes() ? game.episode() + " ∞" : game.episode()),
                Lang.c("phase", game.phaseLabel())));
        lines.add(Component.empty());

        Party team = plugin.teams().team(id);
        if (team != null && plugin.settings().mode() != UhcMode.SOLITARIOS) {
            lines.add(lang.get("scoreboard.your-team", Lang.c("team", Component.text(team.name(), team.color()))));
            int shown = 0;
            for (UUID mate : team.members()) {
                if (mate.equals(id) || shown >= 4) {
                    continue;
                }
                shown++;
                lines.add(mateLine(player, mate));
            }
            lines.add(Component.empty());
        }

        lines.add(lang.get("scoreboard.time", Lang.p("time", Format.time(game.totalSeconds()))));
        if (game.restLeft() > 0) {
            lines.add(lang.get("scoreboard.rest", Lang.p("seconds", game.restLeft())));
        } else if (!game.infiniteEpisodes()) {
            lines.add(lang.get("scoreboard.remaining", Lang.p("time", Format.time(game.secondsLeftInEpisode()))));
        }
        lines.add(lang.get("scoreboard.alive", Lang.p("count", game.alive().size()), Lang.p("sides", game.aliveSides())));
        lines.add(lang.get("scoreboard.kills", Lang.p("kills", plugin.stats().kills(id))));
        lines.add(lang.get("scoreboard.border", Lang.p("radius", BorderModule.currentRadius(game.mainWorld()))));

        if (plugin.settings().mode() == UhcMode.RESOURCE_RUSH) {
            Material target = plugin.resourceRush().target(id);
            lines.add(Component.empty());
            lines.add(target == null ? lang.get("scoreboard.rush-done")
                    : lang.get("scoreboard.rush-target", Lang.c("item", ResourceRushModule.itemName(target)),
                    Lang.p("done", plugin.resourceRush().progress(id)), Lang.p("total", plugin.resourceRush().total())));
        }
        if (plugin.settings().mode() == UhcMode.TROLLS_RATAS) {
            TrollsModule.Role role = plugin.trolls().role(id);
            lines.add(lang.get("scoreboard.role", Lang.c("role", role == null ? lang.get("trolls.role-pending")
                    : lang.get("trolls.role." + role.name().toLowerCase(java.util.Locale.ROOT) + ".name"))));
        }
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    private Component mateLine(Player viewer, UUID mate) {
        Lang lang = plugin.lang();
        String name = plugin.stats().name(mate);
        if (!plugin.game().isAlive(mate)) {
            return lang.get("scoreboard.mate-dead", Lang.p("player", name));
        }
        Player other = Bukkit.getPlayer(mate);
        if (other == null) {
            return lang.get("scoreboard.mate-offline", Lang.p("player", name));
        }
        String where;
        if (!other.getWorld().equals(viewer.getWorld())) {
            where = other.getWorld().getEnvironment().name().toLowerCase(java.util.Locale.ROOT);
        } else {
            where = Format.arrow(viewer.getLocation(), other.getLocation()) + " " + (int) viewer.getLocation().distance(other.getLocation()) + "m";
        }
        return lang.get("scoreboard.mate", Lang.p("player", name), Lang.p("hearts", Format.hearts(other.getHealth())), Lang.p("where", where));
    }

    // ── Equipos (colores y nombres) ──────────────────────────

    private void syncTeams(Scoreboard board) {
        boolean hide = SkinRotationModule.hidingNames();
        Set<String> wanted = new HashSet<>();
        for (Party party : plugin.teams().teams()) {
            String id = party.scoreboardId();
            wanted.add(id);
            Team team = board.getTeam(id);
            if (team == null) {
                team = board.registerNewTeam(id);
            }
            team.color(party.color());
            team.prefix(Component.text("[" + abbreviate(party.name()) + "] ", party.color()));
            team.setAllowFriendlyFire(plugin.settings().mode() == UhcMode.TROLLS_RATAS);
            team.setCanSeeFriendlyInvisibles(true);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, hide ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS);
            Set<String> names = new HashSet<>();
            for (UUID member : party.members()) {
                names.add(plugin.stats().name(member).equals("?") ? nameOf(member) : plugin.stats().name(member));
            }
            for (String entry : Set.copyOf(team.getEntries())) {
                if (!names.contains(entry)) {
                    team.removeEntry(entry);
                }
            }
            for (String name : names) {
                if (!team.hasEntry(name)) {
                    team.addEntry(name);
                }
            }
        }
        Team hidden = board.getTeam("uhc_hidden");
        if (hide) {
            wanted.add("uhc_hidden");
            if (hidden == null) {
                hidden = board.registerNewTeam("uhc_hidden");
            }
            hidden.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!plugin.teams().hasTeam(online.getUniqueId()) && !hidden.hasEntry(online.getName())) {
                    hidden.addEntry(online.getName());
                }
            }
        }
        for (Team team : Set.copyOf(board.getTeams())) {
            if ((team.getName().startsWith("uhct_") || team.getName().equals("uhc_hidden")) && !wanted.contains(team.getName())) {
                team.unregister();
            }
        }
    }

    private static String nameOf(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            return player.getName();
        }
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString().substring(0, 8);
    }

    private static String abbreviate(String name) {
        return name.length() <= 10 ? name : name.substring(0, 10);
    }

    private void tab(Player player) {
        Lang lang = plugin.lang();
        player.sendPlayerListHeaderAndFooter(
                lang.get("tab.header", Lang.p("name", plugin.settings().matchName())),
                lang.get("tab.footer", Lang.p("alive", plugin.game().alive().size()), Lang.p("episode", plugin.game().episode()),
                        Lang.p("time", Format.time(plugin.game().totalSeconds()))));
        if (SkinRotationModule.hidingNames() && plugin.game().isRunning()) {
            player.playerListName(Component.text("???", NamedTextColor.GRAY));
        } else {
            player.playerListName(null);
        }
    }

    @Override
    public void onReset() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(null);
        }
    }
}
