package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.compat.Text;
import cl.itstuki.uhc.config.TeamMode;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.GameManager;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Format;
import cl.itstuki.uhc.util.Show;
import cl.itstuki.uhc.util.Vanish;
import com.cryptomorin.xseries.XMaterial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Scoreboard lateral, vida en el tabulador, colores de equipo y cabecera/pie del tab.
 * En 1.8–1.12 cada línea se arma con prefijo + sufijo de un equipo (16 + 16 caracteres).
 */
public final class ScoreboardModule implements UhcModule {
    private static final int MAX_LINES = 15;
    private static final boolean MODERN = Compat.atLeast(13);
    private final TukiUHC plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> tabManaged = new HashSet<>();

    public ScoreboardModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
        tabManaged.remove(event.getPlayer().getUniqueId());
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                plugin.lang().asViewer(player, () -> update(player));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Scoreboard: " + ex);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void update(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        Objective sidebar = board.getObjective("uhc");
        if (sidebar == null) {
            sidebar = board.registerNewObjective("uhc", "dummy");
            sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        String title = Text.fit(plugin.lang().get("scoreboard.title", Lang.p("name", plugin.settings().matchName())),
                MODERN ? 128 : 32);
        if (!title.equals(sidebar.getDisplayName())) {
            sidebar.setDisplayName(title);
        }
        if (board.getObjective("hp") == null) {
            Objective health = board.registerNewObjective("hp", "health");
            health.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        }

        List<String> lines = lines(player);
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = ChatColor.values()[i].toString() + ChatColor.RESET;
            Team lineTeam = board.getTeam("uhcl" + i);
            if (i < lines.size()) {
                if (lineTeam == null) {
                    lineTeam = board.registerNewTeam("uhcl" + i);
                    lineTeam.addEntry(entry);
                }
                setLine(lineTeam, lines.get(i));
                if (sidebar.getScore(entry).getScore() != MAX_LINES - i) {
                    sidebar.getScore(entry).setScore(MAX_LINES - i);
                }
            } else {
                board.resetScores(entry);
            }
        }
        syncTeams(board, player);
        tab(player);
    }

    /** Texto de una línea: hasta 64 caracteres en 1.13+, 16 + 16 antes. */
    private static void setLine(Team team, String text) {
        String prefix;
        String suffix = "";
        if (MODERN) {
            prefix = Text.cut(text, 64);
        } else if (text.length() <= 16) {
            prefix = text;
        } else {
            prefix = Text.cut(text, 16);
            String rest = text.substring(prefix.length());
            suffix = Text.cut(ChatColor.getLastColors(prefix) + rest, 16);
        }
        if (!prefix.equals(team.getPrefix())) {
            team.setPrefix(prefix);
        }
        if (!suffix.equals(team.getSuffix())) {
            team.setSuffix(suffix);
        }
    }

    // ── Líneas ───────────────────────────────────────────────

    private String s(Component component) {
        return Text.legacy(component);
    }

    private List<String> lines(Player player) {
        GameManager game = plugin.game();
        List<String> lines = new ArrayList<>();
        Lang lang = plugin.lang();
        GameState state = game.state();
        if (state == GameState.LOBBY) {
            lines.add(s(lang.get("scoreboard.mode", Lang.c("mode", lang.get("modes." + plugin.settings().mode().key() + ".name")))));
            lines.add(s(lang.get("scoreboard.teams", Lang.c("teams", lang.get("team-modes." + plugin.settings().teamMode().key() + ".name")),
                    Lang.p("size", plugin.settings().teamSize()))));
            long visible = Bukkit.getOnlinePlayers().stream().filter(p -> !Vanish.isVanished(p)).count();
            lines.add(s(lang.get("scoreboard.players", Lang.p("count", visible))));
            lines.add(blank(lines));
            Party lobbyTeam = plugin.teams().team(player.getUniqueId());
            lines.add(s(lobbyTeam != null
                    ? lang.get("scoreboard.your-team", Lang.c("team", Component.text(lobbyTeam.name(), lobbyTeam.color())))
                    : lang.get("scoreboard.no-team")));
            addTeamsSummary(lines, player);
            lines.add(blank(lines));
            lines.add(s(lang.get("scoreboard.waiting")));
            addFooter(lines, player);
            return lines;
        }
        if (state == GameState.SCATTERING || state == GameState.PREPARED || state == GameState.STARTING) {
            lines.add(s(lang.get("scoreboard.preparing")));
            lines.add(blank(lines));
            lines.add(s(lang.get("scoreboard.players", Lang.p("count", game.participants().size()))));
            return lines;
        }
        if (state == GameState.ENDED) {
            lines.add(s(lang.get("scoreboard.ended")));
            lines.add(s(lang.get("scoreboard.time", Lang.p("time", Format.time(game.totalSeconds())))));
            return lines;
        }

        UUID id = player.getUniqueId();
        if (game.timed()) {
            lines.add(s(lang.get(game.suddenDeath() ? "scoreboard.sudden-death" : "scoreboard.timed", Lang.c("phase", game.phaseLabel()))));
        } else {
            lines.add(s(lang.get("scoreboard.episode", Lang.p("episode", game.infiniteEpisodes() ? game.episode() + " ∞" : game.episode()),
                    Lang.c("phase", game.phaseLabel()))));
        }
        lines.add(blank(lines));

        Party team = plugin.teams().team(id);
        if (team != null && plugin.settings().mode() != UhcMode.SOLITARIOS) {
            lines.add(s(lang.get("scoreboard.your-team", Lang.c("team", Component.text(team.name(), team.color())))));
            int shown = 0;
            for (UUID mate : team.members()) {
                if (mate.equals(id) || shown >= 4) {
                    continue;
                }
                shown++;
                lines.add(mateLine(player, mate));
            }
            lines.add(blank(lines));
        } else if (plugin.settings().mode() != UhcMode.SOLITARIOS && plugin.game().isAlive(id)
                && plugin.settings().teamMode() != TeamMode.SOLO) {
            lines.add(s(lang.get(plugin.settings().teamMode() == TeamMode.RANDOM
                    || plugin.settings().teamMode() == TeamMode.PROXIMITY ? "scoreboard.team-pending" : "scoreboard.no-team")));
            lines.add(blank(lines));
        }

        lines.add(s(lang.get("scoreboard.time", Lang.p("time", Format.time(game.totalSeconds())))));
        if (game.timed()) {
            if (!game.suddenDeath()) {
                lines.add(s(lang.get("scoreboard.remaining", Lang.p("time", Format.time(game.timeLeft())))));
            }
        } else if (game.restLeft() > 0) {
            lines.add(s(lang.get("scoreboard.rest", Lang.p("seconds", game.restLeft()))));
        } else if (!game.infiniteEpisodes()) {
            lines.add(s(lang.get("scoreboard.remaining", Lang.p("time", Format.time(game.secondsLeftInEpisode())))));
        }
        lines.add(s(lang.get("scoreboard.alive", Lang.p("count", game.alive().size()), Lang.p("sides", game.aliveSides()))));
        lines.add(s(lang.get("scoreboard.kills", Lang.p("kills", plugin.stats().kills(id)))));
        lines.add(s(lang.get("scoreboard.border", Lang.p("radius", BorderModule.currentRadius(game.mainWorld())))));

        if (plugin.settings().mode() == UhcMode.RESOURCE_RUSH) {
            XMaterial target = plugin.resourceRush().target(id);
            lines.add(blank(lines));
            lines.add(s(target == null ? lang.get("scoreboard.rush-done")
                    : lang.get("scoreboard.rush-target", Lang.c("item", ResourceRushModule.itemName(target)),
                    Lang.p("done", plugin.resourceRush().progress(id)), Lang.p("total", plugin.resourceRush().total()))));
        }
        if (plugin.settings().mode() == UhcMode.TROLLS_RATAS) {
            TrollsModule.Role role = plugin.trolls().role(id);
            lines.add(s(lang.get("scoreboard.role", Lang.c("role", role == null ? lang.get("trolls.role-pending")
                    : lang.get("trolls.role." + role.name().toLowerCase(Locale.ROOT) + ".name")))));
        }
        List<String> footer = lang.rawList("scoreboard.footer");
        if (plugin.settings().mode() != UhcMode.SOLITARIOS && lines.size() < MAX_LINES - 2 - footer.size()) {
            lines.add(blank(lines));
            addTeamsSummary(lines, player);
        }
        addFooter(lines, player);
        return lines.size() > MAX_LINES ? new ArrayList<>(lines.subList(0, MAX_LINES)) : lines;
    }

    /** Línea vacía (cada una con códigos distintos para que no se junten). */
    private static String blank(List<String> lines) {
        return "";
    }

    private void addFooter(List<String> lines, Player player) {
        List<String> footer = plugin.lang().rawList("scoreboard.footer");
        if (footer.isEmpty() || lines.size() + footer.size() > MAX_LINES) {
            return;
        }
        for (String line : footer) {
            lines.add(s(plugin.lang().format(line, player)));
        }
    }

    private void addTeamsSummary(List<String> lines, Player viewer) {
        List<Party> teams = new ArrayList<>(plugin.teams().teams());
        if (teams.isEmpty()) {
            return;
        }
        boolean lobby = plugin.game().state() == GameState.LOBBY;
        teams.sort((a, b) -> Long.compare(aliveIn(b, lobby), aliveIn(a, lobby)));
        lines.add(s(plugin.lang().get("scoreboard.teams-header", Lang.p("count", teams.size()))));
        int room = Math.max(1, Math.min(4, MAX_LINES - lines.size() - (teams.size() > 1 ? 1 : 0)));
        int shown = 0;
        for (Party party : teams) {
            if (shown >= room) {
                break;
            }
            shown++;
            long alive = aliveIn(party, lobby);
            boolean mine = party.members().contains(viewer.getUniqueId());
            lines.add(s(plugin.lang().get(alive == 0 ? "scoreboard.team-out" : "scoreboard.team-line",
                    Lang.c("team", Component.text(party.name(), party.color())),
                    Lang.p("alive", alive), Lang.p("total", party.members().size()),
                    Lang.p("you", mine ? "★" : ""))));
        }
        if (teams.size() > shown) {
            lines.add(s(plugin.lang().get("scoreboard.teams-more", Lang.p("count", teams.size() - shown))));
        }
    }

    private long aliveIn(Party party, boolean lobby) {
        if (lobby) {
            return party.members().stream().filter(id -> Bukkit.getPlayer(id) != null).count();
        }
        return party.members().stream().filter(plugin.game()::isAlive).count();
    }

    private String mateLine(Player viewer, UUID mate) {
        Lang lang = plugin.lang();
        String name = plugin.stats().name(mate);
        if (!plugin.game().isAlive(mate)) {
            return s(lang.get("scoreboard.mate-dead", Lang.p("player", name)));
        }
        Player other = Bukkit.getPlayer(mate);
        if (other == null) {
            return s(lang.get("scoreboard.mate-offline", Lang.p("player", name)));
        }
        String where;
        if (!other.getWorld().equals(viewer.getWorld())) {
            where = other.getWorld().getEnvironment().name().toLowerCase(Locale.ROOT);
        } else {
            where = Format.arrow(viewer.getLocation(), other.getLocation()) + " " + (int) viewer.getLocation().distance(other.getLocation()) + "m";
        }
        return s(lang.get("scoreboard.mate", Lang.p("player", name), Lang.p("hearts", Format.hearts(other.getHealth())), Lang.p("where", where)));
    }

    // ── Equipos (colores, nombres y quién ve a quién) ───────

    private static final class Spec {
        final String id;
        final String prefix;
        final boolean showName;

        Spec(String id, String prefix, boolean showName) {
            this.id = id;
            this.prefix = prefix;
            this.showName = showName;
        }
    }

    /**
     * Cada jugador tiene su propio scoreboard: con nombres ocultos solo ve el nombre de su equipo y de
     * los enemigos que golpeó. Sin equipo, se muestra el prefijo de su rango (TukiCore o LuckPerms).
     */
    @SuppressWarnings("deprecation")
    private void syncTeams(Scoreboard board, Player viewer) {
        boolean hide = SkinRotationModule.hidingNames() && plugin.game().isRunning();
        Map<String, Spec> specs = new HashMap<>();
        Map<String, String> assignment = new HashMap<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            Party party = plugin.teams().team(target.getUniqueId());
            boolean known = !hide || plugin.skinRotation().knows(viewer.getUniqueId(), target.getUniqueId());
            Spec spec;
            if (party != null) {
                String color = Text.code(party.color());
                String prefix = Text.cut(color + "[" + abbreviate(party.name()) + "] " + color, 16);
                spec = new Spec(party.scoreboardId() + (known ? "" : "_h"), prefix, known);
            } else if (hide) {
                spec = known ? new Spec("uhct_known", "", true) : new Spec("uhct_hidden", "§7", false);
            } else {
                spec = rankSpec(target);
                if (spec == null) {
                    continue;
                }
            }
            specs.put(spec.id, spec);
            assignment.put(target.getName(), spec.id);
        }
        for (Spec spec : specs.values()) {
            Team team = board.getTeam(spec.id);
            if (team == null) {
                team = board.registerNewTeam(spec.id);
                team.setAllowFriendlyFire(true);
                team.setCanSeeFriendlyInvisibles(true);
            }
            if (!spec.prefix.equals(team.getPrefix())) {
                team.setPrefix(spec.prefix);
            }
            NameTagVisibility visibility = spec.showName ? NameTagVisibility.ALWAYS : NameTagVisibility.NEVER;
            if (team.getNameTagVisibility() != visibility) {
                team.setNameTagVisibility(visibility);
            }
        }
        for (Team team : new ArrayList<>(board.getTeams())) {
            String name = team.getName();
            if (name.startsWith("uhcl")) {
                continue;
            }
            if (!name.startsWith("uhct_") && !name.startsWith("uhcg_") && !name.startsWith("uhcr")) {
                continue;
            }
            if (!specs.containsKey(name)) {
                team.unregister();
                continue;
            }
            for (String entry : new ArrayList<>(team.getEntries())) {
                if (!name.equals(assignment.get(entry))) {
                    team.removeEntry(entry);
                }
            }
        }
        for (Map.Entry<String, String> entry : assignment.entrySet()) {
            Team team = board.getTeam(entry.getValue());
            if (team != null && !team.hasEntry(entry.getKey())) {
                team.addEntry(entry.getKey());
            }
        }
    }

    /** Sin equipo de UHC: el prefijo del rango sobre la cabeza. El peso ordena el tab. */
    private Spec rankSpec(Player target) {
        if (!plugin.hooks().hasRanks() || !plugin.getConfig().getBoolean("ranks.nametag-prefix", true)) {
            return null;
        }
        String prefix = plugin.hooks().ranks().prefix(target);
        if (prefix.isEmpty()) {
            return null;
        }
        int weight = Math.max(0, Math.min(9999, plugin.hooks().ranks().weight(target)));
        String hash = Integer.toHexString(prefix.hashCode());
        String id = "uhcr" + String.format("%04d", 9999 - weight) + hash.substring(0, Math.min(8, hash.length()));
        return new Spec(id, Text.cut(Text.legacy(Text.amp(prefix)), MODERN ? 64 : 16), true);
    }

    private static String abbreviate(String name) {
        int max = MODERN ? 10 : 7;
        return name.length() <= max ? name : name.substring(0, max);
    }

    @SuppressWarnings("deprecation")
    private void tab(Player player) {
        Lang lang = plugin.lang();
        Show.tab(player, lang.get("tab.header", Lang.p("name", plugin.settings().matchName())),
                lang.get("tab.footer", Lang.p("alive", plugin.game().alive().size()), Lang.p("episode", plugin.game().episode()),
                        Lang.p("time", Format.time(plugin.game().totalSeconds()))));
        String wanted = tabName(player);
        if (wanted != null) {
            tabManaged.add(player.getUniqueId());
            if (!wanted.equals(player.getPlayerListName())) {
                player.setPlayerListName(wanted);
            }
        } else if (tabManaged.remove(player.getUniqueId())) {
            // Solo se limpia lo que puso TukiUHC: así no se pisa el nombre que ponga otro plugin (TukiCore).
            player.setPlayerListName(null);
        }
    }

    /** Con los nombres ocultos, los no revelados salen como "???" en el tab de todos. */
    private String tabName(Player player) {
        boolean hide = SkinRotationModule.hidingNames() && plugin.game().isRunning();
        if (hide && !plugin.skinRotation().isRevealed(player.getUniqueId()) && plugin.game().isParticipant(player.getUniqueId())) {
            return Text.legacy(plugin.lang().get("skins.hidden-name"));
        }
        if (!plugin.hooks().tukiCore() && plugin.hooks().hasRanks() && !plugin.game().isRunning()
                && plugin.getConfig().getBoolean("luckperms.tab-prefix", true)) {
            return Text.legacy(Text.amp(plugin.hooks().ranks().prefix(player)).append(Component.text(player.getName()))
                    .append(Text.amp(plugin.hooks().ranks().suffix(player))));
        }
        return null;
    }

    @Override
    public void onReset() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (tabManaged.remove(player.getUniqueId())) {
                player.setPlayerListName(null);
            }
        }
    }
}
