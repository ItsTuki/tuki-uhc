package cl.itstuki.uhc.command;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.GameManager;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.module.ItemsModule;
import cl.itstuki.uhc.module.TrollsModule;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Credits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Todos los comandos de TukiUHC (edición Legacy: declarados en plugin.yml). */
public final class UhcCommands implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "tukiuhc.admin";
    private static final List<String> COMMANDS = Arrays.asList("uhchelp", "uhcconfig", "uhcprepare", "uhcstart",
            "uhcforcestart", "uhcstop", "sos", "afk", "renameteam", "heal", "uhcrevive", "uhcrole", "uhcgive", "uhcteam",
            "uhcreload", "uhcdev", "uhchub", "uhcpregen");
    private static final List<String> GIVE_ITEMS = Arrays.asList("golden_head", "golden_skull", "super_apple", "hyper_apple",
            "revive_crystal");
    private final TukiUHC plugin;

    public UhcCommands(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (String name : COMMANDS) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                plugin.getLogger().warning("Falta el comando /" + name + " en plugin.yml");
                continue;
            }
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        if (plugin.getConfig().getBoolean("proxy.enabled", false) && plugin.getConfig().getBoolean("proxy.hub-aliases", true)) {
            registerHubAliases();
        }
    }

    /** /hub y /lobby solo si hay proxy, para no pisar a otros plugins de lobby. */
    private void registerHubAliases() {
        try {
            CommandMap map = (CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            for (String alias : Arrays.asList("hub", "lobby")) {
                map.register("tukiuhc", new Command(alias, "Vuelve al lobby de la network", "/" + alias, Collections.<String>emptyList()) {
                    @Override
                    public boolean execute(CommandSender sender, String label, String[] args) {
                        return onCommand(sender, this, "uhchub", args);
                    }
                });
            }
        } catch (ReflectiveOperationException | ClassCastException ex) {
            plugin.getLogger().warning("No se pudieron registrar /hub y /lobby: " + ex.getMessage());
        }
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.lang().send(sender, "commands.no-permission");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        plugin.lang().send(sender, "commands.player-only");
        return null;
    }

    private void usage(CommandSender sender, String usage) {
        plugin.lang().send(sender, "commands.usage", Lang.p("usage", usage));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "uhchelp":
                help(sender);
                break;
            case "uhcconfig": {
                if (!allowed(sender, "tukiuhc.command.config")) {
                    break;
                }
                Player p = player(sender);
                if (p != null) {
                    plugin.menus().openMain(p);
                }
                break;
            }
            case "uhcprepare":
                if (allowed(sender, "tukiuhc.command.prepare")) {
                    plugin.game().prepare(sender);
                }
                break;
            case "uhcstart":
                if (allowed(sender, "tukiuhc.command.start")) {
                    plugin.game().start(sender, false);
                }
                break;
            case "uhcforcestart":
                if (allowed(sender, "tukiuhc.command.start")) {
                    plugin.game().start(sender, true);
                }
                break;
            case "uhcstop":
                if (allowed(sender, "tukiuhc.command.stop")) {
                    plugin.game().end(Collections.<UUID>emptyList(), "game.stopped");
                    plugin.lang().send(sender, "commands.stopped");
                }
                break;
            case "sos":
                if (allowed(sender, "tukiuhc.command.reset")) {
                    plugin.game().reset();
                    plugin.reloadAll();
                    plugin.lang().broadcast("commands.sos");
                }
                break;
            case "afk": {
                if (!allowed(sender, "tukiuhc.afk")) {
                    break;
                }
                Player p = player(sender);
                if (p != null) {
                    plugin.afk().toggle(p);
                }
                break;
            }
            case "renameteam":
                if (allowed(sender, "tukiuhc.renameteam")) {
                    renameTeam(sender, args);
                }
                break;
            case "heal":
                if (allowed(sender, "tukiuhc.command.heal")) {
                    heal(sender, args);
                }
                break;
            case "uhcrevive":
                if (allowed(sender, "tukiuhc.command.revive")) {
                    revive(sender, args);
                }
                break;
            case "uhcrole":
                role(sender, args);
                break;
            case "uhcgive":
                if (allowed(sender, "tukiuhc.command.give")) {
                    give(sender, args);
                }
                break;
            case "uhcteam":
                if (allowed(sender, "tukiuhc.command.team")) {
                    team(sender, args);
                }
                break;
            case "uhcreload":
                if (allowed(sender, "tukiuhc.command.reload")) {
                    plugin.reloadAll();
                    plugin.lang().send(sender, "commands.reloaded");
                }
                break;
            case "uhcdev":
                if (allowed(sender, "tukiuhc.dev")) {
                    dev(sender, args);
                }
                break;
            case "uhcpregen":
                if (allowed(sender, "tukiuhc.command.pregen")) {
                    String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
                    if (sub.equals("start")) {
                        plugin.map().start(sender);
                    } else if (sub.equals("stop")) {
                        plugin.map().stop(sender);
                    } else {
                        plugin.map().status(sender);
                    }
                }
                break;
            case "uhchub": {
                if (!allowed(sender, "tukiuhc.hub")) {
                    break;
                }
                Player p = player(sender);
                if (p != null) {
                    plugin.proxy().sendToHub(p);
                }
                break;
            }
            default:
                return false;
        }
        return true;
    }

    // ── Jugadores ────────────────────────────────────────────

    /** Un nombre, "@a" (todos), "@p"/"@s" (uno mismo). */
    private List<Player> targets(CommandSender sender, String arg) {
        List<Player> list = new ArrayList<>();
        if (arg.equalsIgnoreCase("@a")) {
            list.addAll(Bukkit.getOnlinePlayers());
        } else if ((arg.equalsIgnoreCase("@s") || arg.equalsIgnoreCase("@p")) && sender instanceof Player) {
            list.add((Player) sender);
        } else {
            Player exact = Bukkit.getPlayerExact(arg);
            Player found = exact != null ? exact : Bukkit.getPlayer(arg);
            if (found != null) {
                list.add(found);
            }
        }
        if (list.isEmpty()) {
            plugin.lang().send(sender, "commands.player-not-found", Lang.p("player", arg));
        }
        return list;
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    private static String cut(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private void help(CommandSender s) {
        plugin.lang().sendList(s, "help.player", Lang.p("version", plugin.getDescription().getVersion()));
        if (s.hasPermission(ADMIN) || s.hasPermission("tukiuhc.command.start")) {
            plugin.lang().sendList(s, "help.admin");
        }
        plugin.lang().sendComponent(s, Credits.helpLine());
    }

    private void renameTeam(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (args.length == 0) {
            usage(p, "/renameteam <nombre>");
            return;
        }
        Party team = plugin.teams().team(p.getUniqueId());
        if (team == null) {
            plugin.lang().send(p, "teams.no-team");
            return;
        }
        plugin.teams().rename(team, cut(join(args, 0), 24));
        for (UUID member : team.members()) {
            Player mate = Bukkit.getPlayer(member);
            if (mate != null) {
                plugin.lang().send(mate, "teams.renamed", Lang.p("team", team.name()), Lang.p("player", p.getName()));
            }
        }
    }

    private void heal(CommandSender sender, String[] args) {
        if (args.length == 0) {
            usage(sender, "/heal <jugador|@a>");
            return;
        }
        List<Player> targets = targets(sender, args[0]);
        for (Player target : targets) {
            ItemsModule.heal(target, 1000);
            target.setFoodLevel(20);
            plugin.lang().send(target, "commands.healed");
        }
        if (!targets.isEmpty()) {
            plugin.lang().send(sender, "commands.heal-done");
        }
    }

    private void revive(CommandSender sender, String[] args) {
        if (args.length == 0) {
            usage(sender, "/uhcrevive <jugador>");
            return;
        }
        List<Player> targets = targets(sender, args[0]);
        if (targets.isEmpty()) {
            return;
        }
        Player target = targets.get(0);
        // Aparece en un punto al azar del mapa, como en el reparto.
        Location where = plugin.game().reviveSpot();
        if (plugin.resurrection().adminRevive(target, where)) {
            plugin.lang().broadcast("revive.done", Lang.p("player", target.getName()), Lang.p("by", sender.getName()));
        } else {
            plugin.lang().send(sender, "revive.cannot");
        }
    }

    private void role(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("revelar") || args[0].equalsIgnoreCase("reveal"))) {
            plugin.trolls().reveal(p);
            return;
        }
        TrollsModule.Role role = plugin.trolls().role(p.getUniqueId());
        if (plugin.settings().mode() != UhcMode.TROLLS_RATAS || role == null) {
            plugin.lang().send(p, "trolls.no-role");
        } else {
            plugin.trolls().tellRole(p, role);
        }
    }

    private void give(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (args.length == 0) {
            usage(p, "/uhcgive <" + String.join("|", GIVE_ITEMS) + ">");
            return;
        }
        ItemsModule items = plugin.items();
        ItemStack item;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "golden_head":
                item = items.goldenHead();
                break;
            case "golden_skull":
                item = items.goldenSkull();
                break;
            case "super_apple":
                item = items.superApple();
                break;
            case "hyper_apple":
                item = items.hyperApple();
                break;
            case "revive_crystal":
                item = items.reviveCrystal();
                break;
            default:
                item = null;
        }
        if (item != null) {
            p.getInventory().addItem(item);
        } else {
            usage(p, "/uhcgive <" + String.join("|", GIVE_ITEMS) + ">");
        }
    }

    // ── /uhcteam ─────────────────────────────────────────────

    /** Lee un nombre de equipo que puede ir entre comillas. Devuelve {nombre, índice siguiente}. */
    private static Object[] quoted(String[] args, int from) {
        if (from >= args.length) {
            return new Object[] {"", from};
        }
        if (!args[from].startsWith("\"")) {
            return new Object[] {args[from], from + 1};
        }
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            out.append(i == from ? "" : " ").append(args[i]);
            if (args[i].endsWith("\"") && (i > from || args[i].length() > 1)) {
                String text = out.toString();
                return new Object[] {text.substring(1, text.length() - 1), i + 1};
            }
        }
        return new Object[] {out.substring(1), args.length};
    }

    private void team(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            listTeams(sender);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Party.Kind kind = sub.startsWith("group") ? Party.Kind.GROUP : Party.Kind.TEAM;
        String action = sub.startsWith("group") ? sub.substring(5) : sub;
        switch (action) {
            case "create": {
                if (args.length < 2) {
                    usage(sender, "/uhcteam " + sub + " <nombre>");
                    return;
                }
                Party party = plugin.teams().create(kind, join(args, 1));
                plugin.lang().send(sender, "teams.created", Lang.p("team", party.name()));
                return;
            }
            case "delete": {
                Object[] q = quoted(args, 1);
                String name = (String) q[0];
                if (name.isEmpty()) {
                    usage(sender, "/uhcteam " + sub + " <nombre>");
                    return;
                }
                Optional<Party> party = plugin.teams().byName(kind, name);
                if (party.isPresent()) {
                    plugin.teams().delete(party.get());
                    plugin.lang().send(sender, "teams.deleted", Lang.p("team", party.get().name()));
                } else {
                    plugin.lang().send(sender, "teams.not-found", Lang.p("team", name));
                }
                return;
            }
            case "add": {
                Object[] q = quoted(args, 1);
                String name = (String) q[0];
                int next = (Integer) q[1];
                if (name.isEmpty() || next >= args.length) {
                    usage(sender, "/uhcteam " + sub + " <nombre> <jugador|@a>");
                    return;
                }
                Optional<Party> party = plugin.teams().byName(kind, name);
                if (!party.isPresent()) {
                    plugin.lang().send(sender, "teams.not-found", Lang.p("team", name));
                    return;
                }
                List<Player> added = new ArrayList<>();
                for (int i = next; i < args.length; i++) {
                    added.addAll(targets(sender, args[i]));
                }
                for (Player target : added) {
                    plugin.teams().add(party.get(), target.getUniqueId());
                    plugin.lang().send(target, "teams.joined", Lang.p("team", party.get().name()));
                }
                if (!added.isEmpty()) {
                    plugin.lang().send(sender, "teams.added", Lang.p("team", party.get().name()));
                }
                return;
            }
            case "remove": {
                if (args.length < 2) {
                    usage(sender, "/uhcteam " + sub + " <jugador|@a>");
                    return;
                }
                for (int i = 1; i < args.length; i++) {
                    for (Player target : targets(sender, args[i])) {
                        plugin.teams().remove(target.getUniqueId(), kind);
                    }
                }
                plugin.lang().send(sender, "teams.removed");
                return;
            }
            default:
                break;
        }
        switch (sub) {
            case "random": {
                int size;
                try {
                    size = Integer.parseInt(args.length > 1 ? args[1] : "");
                } catch (NumberFormatException ex) {
                    usage(sender, "/uhcteam random <tamaño>");
                    return;
                }
                size = Math.max(1, Math.min(50, size));
                plugin.teams().clearTeams();
                plugin.teams().randomize(onlineIds(), size, plugin.lang().raw("teams.default-name"));
                plugin.lang().send(sender, "teams.shuffled", Lang.p("count", plugin.teams().teams().size()));
                return;
            }
            case "force": {
                // Junta en equipos al azar a los vivos que siguen sin equipo (útil en Amor a primera vista).
                List<UUID> loose = plugin.game().alive().stream().filter(id -> !plugin.teams().hasTeam(id)).collect(Collectors.toList());
                plugin.teams().randomize(loose, plugin.settings().teamSize(), plugin.lang().raw("teams.default-name"));
                plugin.lang().broadcast("teams.forced", Lang.p("count", loose.size()));
                return;
            }
            case "clear":
                plugin.teams().clear();
                plugin.lang().send(sender, "teams.cleared");
                return;
            default:
                usage(sender, "/uhcteam <list|create|delete|add|remove|groupcreate|groupdelete|groupadd|groupremove|random|force|clear>");
        }
    }

    private static List<UUID> onlineIds() {
        List<UUID> ids = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ids.add(player.getUniqueId());
        }
        return ids;
    }

    private String members(Party party) {
        return party.members().stream().map(id -> {
            String n = Bukkit.getOfflinePlayer(id).getName();
            return n == null ? "?" : n;
        }).collect(Collectors.joining(", "));
    }

    private void listTeams(CommandSender s) {
        if (plugin.teams().teams().isEmpty() && plugin.teams().groups().isEmpty()) {
            plugin.lang().send(s, "teams.none");
            return;
        }
        for (Party party : plugin.teams().teams()) {
            plugin.lang().sendComponent(s, Component.text("■ " + party.name() + ": ", party.color()).append(Component.text(members(party))));
        }
        for (Party party : plugin.teams().groups()) {
            plugin.lang().sendComponent(s, Component.text("◆ " + party.name() + ": ", party.color()).append(Component.text(members(party))));
        }
    }

    // ── /uhcdev ──────────────────────────────────────────────

    /** /uhcdev: solo con tukiuhc.dev, que no viene con OP (lo da el modo dev de TukiCore). */
    private void dev(CommandSender sender, String[] args) {
        GameManager game = plugin.game();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info":
                plugin.lang().sendList(sender, "dev.info",
                        Lang.p("state", game.state().name()), Lang.p("mode", plugin.settings().mode().name()),
                        Lang.p("episode", game.episode()), Lang.p("second", game.episodeSecond()),
                        Lang.p("length", plugin.settings().episodeSeconds()), Lang.p("rest", game.restLeft()),
                        Lang.p("pvp", game.pvpAllowed()), Lang.p("participants", game.participants().size()),
                        Lang.p("alive", game.alive().size()), Lang.p("sides", game.aliveSides()),
                        Lang.p("total", game.totalSeconds()));
                break;
            case "skip":
                plugin.lang().send(sender, game.devSkip() ? "dev.skipped" : "dev.not-playing");
                break;
            case "time": {
                int second;
                try {
                    second = Math.max(0, Integer.parseInt(args.length > 1 ? args[1] : ""));
                } catch (NumberFormatException ex) {
                    usage(sender, "/uhcdev time <segundo>");
                    return;
                }
                plugin.lang().send(sender, game.devSetSecond(second) ? "dev.time-set" : "dev.not-playing",
                        Lang.p("second", game.episodeSecond()));
                break;
            }
            case "pvp":
                plugin.lang().send(sender, game.devTogglePvp() ? "dev.pvp-forced" : "dev.pvp-normal");
                break;
            default:
                plugin.lang().sendList(sender, "dev.help");
        }
    }

    // ── Autocompletado ───────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        int n = args.length;
        switch (name) {
            case "heal":
            case "uhcrevive":
                if (n == 1) {
                    options.addAll(playerNames());
                    if (name.equals("heal")) {
                        options.add("@a");
                    }
                }
                break;
            case "uhcrole":
                if (n == 1) {
                    options.add("revelar");
                }
                break;
            case "uhcgive":
                if (n == 1) {
                    options.addAll(GIVE_ITEMS);
                }
                break;
            case "uhcpregen":
                if (n == 1 && sender.hasPermission("tukiuhc.command.pregen")) {
                    options.addAll(Arrays.asList("start", "stop", "status"));
                }
                break;
            case "uhcdev":
                if (n == 1 && sender.hasPermission("tukiuhc.dev")) {
                    options.addAll(Arrays.asList("info", "skip", "time", "pvp"));
                }
                break;
            case "uhcteam":
                if (!sender.hasPermission("tukiuhc.command.team")) {
                    break;
                }
                if (n == 1) {
                    options.addAll(Arrays.asList("list", "create", "delete", "add", "remove", "groupcreate", "groupdelete",
                            "groupadd", "groupremove", "random", "force", "clear"));
                } else {
                    String sub = args[0].toLowerCase(Locale.ROOT);
                    Party.Kind kind = sub.startsWith("group") ? Party.Kind.GROUP : Party.Kind.TEAM;
                    if (n == 2 && (sub.endsWith("delete") || sub.endsWith("add"))) {
                        for (Party party : plugin.teams().all(kind)) {
                            options.add(party.name().contains(" ") ? "\"" + party.name() + "\"" : party.name());
                        }
                    } else if (sub.endsWith("add") && n >= 3 || sub.endsWith("remove") && n >= 2) {
                        options.addAll(playerNames());
                        options.add("@a");
                    } else if (sub.equals("random") && n == 2) {
                        options.addAll(Arrays.asList("2", "3", "4", "5"));
                    }
                }
                break;
            default:
                break;
        }
        String last = n == 0 ? "" : args[n - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }

    private static List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }
}
