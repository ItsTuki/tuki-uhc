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
    private final java.util.Set<java.util.UUID> tabManaged = new java.util.HashSet<>();
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
                plugin.lang().asViewer(player, () -> update(player));
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
        Component title = plugin.lang().get("scoreboard.title", Lang.p("name", plugin.settings().matchName()));
        if (!title.equals(sidebar.displayName())) {
            sidebar.displayName(title);
        }
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
                if (!score.isScoreSet() || score.getScore() != MAX_LINES - i) {
                    score.setScore(MAX_LINES - i);
                }
                if (!lines.get(i).equals(score.customName())) {
                    score.customName(lines.get(i));
                }
            } else {
                board.resetScores(entry);
            }
        }
        syncTeams(board, player);
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
            lines.add(lang.get("scoreboard.players", Lang.p("count", Bukkit.getOnlinePlayers().stream().filter(p -> !cl.itstuki.uhc.util.Vanish.isVanished(p)).count())));
            lines.add(Component.empty());
            Party lobbyTeam = plugin.teams().team(player.getUniqueId());
            lines.add(lobbyTeam != null
                    ? lang.get("scoreboard.your-team", Lang.c("team", Component.text(lobbyTeam.name(), lobbyTeam.color())))
                    : lang.get("scoreboard.no-team"));
            addTeamsSummary(lines, player);
            lines.add(Component.empty());
            lines.add(lang.get("scoreboard.waiting"));
            addFooter(lines, player);
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
        if (game.timed()) {
            lines.add(lang.get(game.suddenDeath() ? "scoreboard.sudden-death" : "scoreboard.timed", Lang.c("phase", game.phaseLabel())));
        } else {
            lines.add(lang.get("scoreboard.episode", Lang.p("episode", game.infiniteEpisodes() ? game.episode() + " ∞" : game.episode()),
                    Lang.c("phase", game.phaseLabel())));
        }
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
        } else if (plugin.settings().mode() != UhcMode.SOLITARIOS && plugin.game().isAlive(id)
                && plugin.settings().teamMode() != cl.itstuki.uhc.config.TeamMode.SOLO) {
            lines.add(lang.get(plugin.settings().teamMode() == cl.itstuki.uhc.config.TeamMode.RANDOM
                    || plugin.settings().teamMode() == cl.itstuki.uhc.config.TeamMode.PROXIMITY
                    ? "scoreboard.team-pending" : "scoreboard.no-team"));
            lines.add(Component.empty());
        }

        lines.add(lang.get("scoreboard.time", Lang.p("time", Format.time(game.totalSeconds()))));
        if (game.timed()) {
            if (!game.suddenDeath()) {
                lines.add(lang.get("scoreboard.remaining", Lang.p("time", Format.time(game.timeLeft()))));
            }
        } else if (game.restLeft() > 0) {
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
        List<String> footer = lang.rawList("scoreboard.footer");
        if (plugin.settings().mode() != UhcMode.SOLITARIOS && lines.size() < MAX_LINES - 2 - footer.size()) {
            lines.add(Component.empty());
            addTeamsSummary(lines, player);
        }
        addFooter(lines, player);
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    /** Líneas fijas al final del scoreboard (scoreboard.footer en lang/*.yml), con <rank> y <papi:...>. */
    private void addFooter(List<Component> lines, Player player) {
        List<String> footer = plugin.lang().rawList("scoreboard.footer");
        if (footer.isEmpty() || lines.size() + footer.size() > MAX_LINES) {
            return;
        }
        for (String line : footer) {
            lines.add(plugin.lang().format(line, player));
        }
    }

    /** Hasta 4 equipos con sus jugadores vivos (en el lobby: los inscritos). */
    private void addTeamsSummary(List<Component> lines, Player viewer) {
        List<Party> teams = new ArrayList<>(plugin.teams().teams());
        if (teams.isEmpty()) {
            return;
        }
        boolean lobby = plugin.game().state() == GameState.LOBBY;
        teams.sort((a, b) -> Long.compare(aliveIn(b, lobby), aliveIn(a, lobby)));
        lines.add(plugin.lang().get("scoreboard.teams-header", Lang.p("count", teams.size())));
        int room = Math.max(1, Math.min(4, MAX_LINES - lines.size() - (teams.size() > 1 ? 1 : 0)));
        int shown = 0;
        for (Party party : teams) {
            if (shown >= room) {
                break;
            }
            shown++;
            long alive = aliveIn(party, lobby);
            boolean mine = party.members().contains(viewer.getUniqueId());
            lines.add(plugin.lang().get(alive == 0 ? "scoreboard.team-out" : "scoreboard.team-line",
                    Lang.c("team", Component.text(party.name(), party.color())),
                    Lang.p("alive", alive), Lang.p("total", party.members().size()),
                    Lang.p("you", mine ? "★" : "")));
        }
        if (teams.size() > shown) {
            lines.add(plugin.lang().get("scoreboard.teams-more", Lang.p("count", teams.size() - shown)));
        }
    }

    private long aliveIn(Party party, boolean lobby) {
        if (lobby) {
            return party.members().stream().filter(id -> Bukkit.getPlayer(id) != null).count();
        }
        return party.members().stream().filter(plugin.game()::isAlive).count();
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

    // ── Equipos (colores, nombres y quién ve a quién) ───────

    private record Spec(String id, NamedTextColor color, Component prefix, Team.OptionStatus nametag) {
    }

    /**
     * Cada jugador tiene su propio scoreboard, así que los equipos se arman según quién mira:
     * con la rotación de skins solo ve el nombre de su equipo y de los enemigos que golpeó.
     */
    private void syncTeams(Scoreboard board, Player viewer) {
        boolean hide = SkinRotationModule.hidingNames() && plugin.game().isRunning();
        boolean friendlyFire = plugin.settings().mode() == UhcMode.TROLLS_RATAS;
        Map<String, Spec> specs = new HashMap<>();
        Map<String, String> assignment = new HashMap<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            Party party = plugin.teams().team(target.getUniqueId());
            boolean known = !hide || plugin.skinRotation().knows(viewer.getUniqueId(), target.getUniqueId());
            Spec spec;
            if (party != null) {
                Team.OptionStatus tag = !hide ? Team.OptionStatus.ALWAYS
                        : known ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER;
                String id = party.scoreboardId() + (tag == Team.OptionStatus.NEVER ? "_h" : "");
                spec = new Spec(id, party.color(), Component.text("[" + abbreviate(party.name()) + "] ", party.color()), tag);
            } else if (hide) {
                spec = known ? new Spec("uhct_known", NamedTextColor.WHITE, Component.empty(), Team.OptionStatus.ALWAYS)
                        : new Spec("uhct_hidden", NamedTextColor.GRAY, Component.empty(), Team.OptionStatus.NEVER);
            } else if ((spec = rankSpec(target)) == null) {
                continue;
            }
            specs.putIfAbsent(spec.id(), spec);
            assignment.put(target.getName(), spec.id());
        }
        for (Spec spec : specs.values()) {
            Team team = board.getTeam(spec.id());
            if (team == null) {
                team = board.registerNewTeam(spec.id());
            }
            // uhct_known/uhct_hidden agrupan a jugadores sin equipo solo para el nombre: entre ellos sí hay PvP.
            boolean ff = friendlyFire || spec.id().startsWith("uhct_") || spec.id().startsWith("uhcr_");
            // Cada setter manda un paquete: solo se cambia lo que cambió.
            if (!team.hasColor() || team.color() != spec.color()) {
                team.color(spec.color());
            }
            if (!spec.prefix().equals(team.prefix())) {
                team.prefix(spec.prefix());
            }
            if (team.allowFriendlyFire() != ff) {
                team.setAllowFriendlyFire(ff);
            }
            if (!team.canSeeFriendlyInvisibles()) {
                team.setCanSeeFriendlyInvisibles(true);
            }
            if (team.getOption(Team.Option.NAME_TAG_VISIBILITY) != spec.nametag()) {
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, spec.nametag());
            }
        }
        for (Team team : Set.copyOf(board.getTeams())) {
            if (!team.getName().startsWith("uhct_") && !team.getName().startsWith("uhc_") && !team.getName().startsWith("uhcr_")) {
                continue;
            }
            if (!specs.containsKey(team.getName())) {
                team.unregister();
                continue;
            }
            for (String entry : Set.copyOf(team.getEntries())) {
                if (!team.getName().equals(assignment.get(entry))) {
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

    /**
     * Sin equipo de UHC: el prefijo del rango (TukiCore o LuckPerms) sobre la cabeza y en el tab.
     * El nombre del equipo lleva el peso, así el tab queda ordenado de mayor a menor rango.
     */
    private @org.jspecify.annotations.Nullable Spec rankSpec(Player target) {
        if (!plugin.hooks().hasRanks() || !plugin.getConfig().getBoolean("ranks.nametag-prefix", true)) {
            return null;
        }
        String prefix = plugin.hooks().ranks().prefix(target);
        if (prefix.isEmpty()) {
            return null;
        }
        int weight = Math.max(0, Math.min(9999, plugin.hooks().ranks().weight(target)));
        String id = "uhcr_" + String.format("%04d", 9999 - weight) + "_" + Integer.toHexString(prefix.hashCode());
        return new Spec(id, NamedTextColor.WHITE, cl.itstuki.uhc.hook.Hooks.legacy(prefix), Team.OptionStatus.ALWAYS);
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
        Component wanted = tabName(player);
        if (wanted != null) {
            tabManaged.add(player.getUniqueId());
            if (!wanted.equals(player.playerListName())) {
                player.playerListName(wanted);
            }
        } else if (tabManaged.remove(player.getUniqueId())) {
            // Solo se limpia lo que puso TukiUHC: así no se pisa el nombre que ponga otro plugin (TukiCore).
            player.playerListName(null);
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!player.isListed(other)) {
                player.listPlayer(other);
            }
        }
    }
    /** Con la rotación de skins, los no revelados salen como "???" en el tab de todos. */
    private @org.jspecify.annotations.Nullable Component tabName(Player player) {
        boolean hide = SkinRotationModule.hidingNames() && plugin.game().isRunning();
        boolean revealed = plugin.skinRotation().isRevealed(player.getUniqueId());
        if (hide && !revealed && plugin.game().isParticipant(player.getUniqueId())) {
            return plugin.lang().get("skins.hidden-name");
        }
        // Fuera de la partida, el tab muestra el rango de LuckPerms (en partida mandan los colores de equipo).
        // Con TukiCore el tab del lobby es suyo.
        if (!plugin.hooks().tukiCore() && plugin.hooks().hasRanks() && !plugin.game().isRunning()
                && plugin.getConfig().getBoolean("luckperms.tab-prefix", true)) {
            return cl.itstuki.uhc.hook.Hooks.legacy(plugin.hooks().ranks().prefix(player))
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(cl.itstuki.uhc.hook.Hooks.legacy(plugin.hooks().ranks().suffix(player)));
        }
        return null;
    }

    /**
     * Cambiar la skin hace que Paper vuelva a enviar al jugador a todos los clientes, y eso borra
     * en el cliente el nombre del tab y quién está listado. Aquí se reenvía todo a la fuerza.
     */
    public void forceTabRefresh() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Component wanted = tabName(player);
            Component previous = player.playerListName();
            player.playerListName(null);
            // Se vuelve a mandar lo que había (el de TukiUHC o el de otro plugin, como el rango de TukiCore).
            Component restore = wanted != null ? wanted : previous;
            if (!restore.equals(Component.text(player.getName()))) {
                player.playerListName(restore);
            }
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(other) && viewer.canSee(other)) {
                    viewer.unlistPlayer(other);
                    viewer.listPlayer(other);
                }
            }
        }
    }

    @Override
    public void onReset() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(null);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!player.isListed(other)) {
                    player.listPlayer(other);
                }
            }
        }
    }
}
