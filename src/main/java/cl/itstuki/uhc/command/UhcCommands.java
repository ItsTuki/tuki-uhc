package cl.itstuki.uhc.command;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.module.ItemsModule;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Credits;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Todos los comandos de TukiUHC (registrados con Brigadier). */
public final class UhcCommands {
    private static final String ADMIN = "tukiuhc.admin";
    private final TukiUHC plugin;

    public UhcCommands(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private static Predicate<CommandSourceStack> perm(String permission) {
        return src -> src.getSender().hasPermission(permission);
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    private Player player(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player p) {
            return p;
        }
        return sender(ctx) instanceof Player p ? p : null;
    }

    private interface Run {
        void run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private static Command<CommandSourceStack> ok(Run run) {
        return ctx -> {
            run.run(ctx);
            return Command.SINGLE_SUCCESS;
        };
    }

    public void register(Commands registrar) {
        registrar.register(Commands.literal("uhchelp").executes(ok(this::help)).build(), "Ayuda de TukiUHC", List.of("uhc"));

        registrar.register(Commands.literal("uhcconfig").requires(perm("tukiuhc.command.config")).executes(ok(ctx -> {
            Player p = player(ctx);
            if (p == null) {
                plugin.lang().send(sender(ctx), "commands.player-only");
                return;
            }
            plugin.menus().openMain(p);
        })).build(), "Abre el menú de configuración", List.of());

        registrar.register(Commands.literal("uhcprepare").requires(perm("tukiuhc.command.prepare"))
                .executes(ok(ctx -> plugin.game().prepare(sender(ctx)))).build(), "Reparte a los jugadores", List.of());
        registrar.register(Commands.literal("uhcstart").requires(perm("tukiuhc.command.start"))
                .executes(ok(ctx -> plugin.game().start(sender(ctx), false))).build(), "Inicia la partida", List.of());
        registrar.register(Commands.literal("uhcforcestart").requires(perm("tukiuhc.command.start"))
                .executes(ok(ctx -> plugin.game().start(sender(ctx), true))).build(), "Inicia sin esperar a nadie", List.of());
        registrar.register(Commands.literal("uhcstop").requires(perm("tukiuhc.command.stop")).executes(ok(ctx -> {
            plugin.game().end(List.of(), "game.stopped");
            plugin.lang().send(sender(ctx), "commands.stopped");
        })).build(), "Termina la partida sin ganador", List.of());
        registrar.register(Commands.literal("sos").requires(perm("tukiuhc.command.reset")).executes(ok(ctx -> {
            plugin.game().reset();
            plugin.reloadAll();
            plugin.lang().broadcast("commands.sos");
        })).build(), "Reinicia la partida y recarga la configuración", List.of("uhcreset"));

        registrar.register(Commands.literal("afk").requires(perm("tukiuhc.afk")).executes(ok(ctx -> {
            Player p = player(ctx);
            if (p != null) {
                plugin.afk().toggle(p);
            }
        })).build(), "Modo AFK", List.of());

        registrar.register(Commands.literal("renameteam").requires(perm("tukiuhc.renameteam"))
                .then(Commands.argument("nombre", StringArgumentType.greedyString()).executes(ok(ctx -> {
                    Player p = player(ctx);
                    if (p == null) {
                        return;
                    }
                    Party team = plugin.teams().team(p.getUniqueId());
                    if (team == null) {
                        plugin.lang().send(p, "teams.no-team");
                        return;
                    }
                    String name = StringArgumentType.getString(ctx, "nombre");
                    plugin.teams().rename(team, name.length() > 24 ? name.substring(0, 24) : name);
                    for (var member : team.members()) {
                        Player mate = Bukkit.getPlayer(member);
                        if (mate != null) {
                            plugin.lang().send(mate, "teams.renamed", Lang.p("team", team.name()), Lang.p("player", p.getName()));
                        }
                    }
                }))).build(), "Cambia el nombre de tu equipo", List.of());

        registrar.register(Commands.literal("heal").requires(perm("tukiuhc.command.heal"))
                .then(Commands.argument("jugadores", ArgumentTypes.players()).executes(ok(ctx -> {
                    for (Player target : players(ctx, "jugadores")) {
                        ItemsModule.heal(target, 1000);
                        target.setFoodLevel(20);
                        plugin.lang().send(target, "commands.healed");
                    }
                    plugin.lang().send(sender(ctx), "commands.heal-done");
                }))).build(), "Cura a un jugador", List.of("uhcheal"));

        registrar.register(Commands.literal("uhcrevive").requires(perm("tukiuhc.command.revive"))
                .then(Commands.argument("jugador", ArgumentTypes.player()).executes(ok(ctx -> {
                    Player target = players(ctx, "jugador").getFirst();
                    Location where = sender(ctx) instanceof Player p ? p.getLocation() : plugin.game().mainWorld().getSpawnLocation();
                    if (plugin.resurrection().adminRevive(target, where)) {
                        plugin.lang().broadcast("revive.done", Lang.p("player", target.getName()), Lang.p("by", sender(ctx).getName()));
                    } else {
                        plugin.lang().send(sender(ctx), "revive.cannot");
                    }
                }))).build(), "Revive a un jugador eliminado", List.of());

        registrar.register(Commands.literal("uhcrole")
                .executes(ok(ctx -> {
                    Player p = player(ctx);
                    if (p == null) {
                        return;
                    }
                    var role = plugin.trolls().role(p.getUniqueId());
                    if (plugin.settings().mode() != UhcMode.TROLLS_RATAS || role == null) {
                        plugin.lang().send(p, "trolls.no-role");
                    } else {
                        plugin.trolls().tellRole(p, role);
                    }
                }))
                .then(Commands.literal("revelar").executes(ok(ctx -> {
                    Player p = player(ctx);
                    if (p != null) {
                        plugin.trolls().reveal(p);
                    }
                }))).build(), "Muestra o revela tu rol (Trolls y Ratas)", List.of("rol"));

        registrar.register(Commands.literal("uhcgive").requires(perm("tukiuhc.command.give"))
                .then(Commands.argument("item", StringArgumentType.word()).suggests((c, b) -> {
                    List.of("golden_head", "golden_skull", "super_apple", "hyper_apple", "revive_crystal").forEach(b::suggest);
                    return b.buildFuture();
                }).executes(ok(ctx -> {
                    Player p = player(ctx);
                    if (p == null) {
                        return;
                    }
                    ItemsModule items = plugin.items();
                    ItemStack item = switch (StringArgumentType.getString(ctx, "item").toLowerCase(Locale.ROOT)) {
                        case "golden_head" -> items.goldenHead();
                        case "golden_skull" -> items.goldenSkull();
                        case "super_apple" -> items.superApple();
                        case "hyper_apple" -> items.hyperApple();
                        case "revive_crystal" -> items.reviveCrystal();
                        default -> null;
                    };
                    if (item != null) {
                        p.getInventory().addItem(item);
                    }
                }))).build(), "Da un ítem especial de UHC", List.of());

        registrar.register(teamCommand().build(), "Gestiona equipos y grupos", List.of());

        registrar.register(Commands.literal("uhcreload").requires(perm("tukiuhc.command.reload")).executes(ok(ctx -> {
            plugin.reloadAll();
            plugin.lang().send(sender(ctx), "commands.reloaded");
        })).build(), "Recarga config.yml y los idiomas", List.of());

        registrar.register(devCommand().build(), "Herramientas de desarrollo (modo dev)", List.of());

        boolean proxyAliases = plugin.getConfig().getBoolean("proxy.enabled", false)
                && plugin.getConfig().getBoolean("proxy.hub-aliases", true);
        registrar.register(Commands.literal("uhchub").requires(perm("tukiuhc.hub")).executes(ok(ctx -> {
            Player p = player(ctx);
            if (p == null) {
                plugin.lang().send(sender(ctx), "commands.player-only");
                return;
            }
            plugin.proxy().sendToHub(p);
        })).build(), "Vuelve al lobby de la network", proxyAliases ? List.of("hub", "lobby") : List.of());
    }

    private List<Player> players(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        return ctx.getArgument(arg, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
    }

    private void help(CommandContext<CommandSourceStack> ctx) {
        CommandSender s = sender(ctx);
        plugin.lang().sendList(s, "help.player", Lang.p("version", plugin.getPluginMeta().getVersion()));
        if (s.hasPermission(ADMIN) || s.hasPermission("tukiuhc.command.start")) {
            plugin.lang().sendList(s, "help.admin");
        }
        s.sendMessage(Credits.helpLine());
    }

    // ── /uhcteam ─────────────────────────────────────────────

    private SuggestionProvider<CommandSourceStack> teamNames(Party.Kind kind) {
        return (ctx, builder) -> {
            for (Party party : plugin.teams().all(kind)) {
                String name = party.name().contains(" ") ? "\"" + party.name() + "\"" : party.name();
                builder.suggest(name);
            }
            return builder.buildFuture();
        };
    }

    private LiteralArgumentBuilder<CommandSourceStack> teamCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("uhcteam").requires(perm("tukiuhc.command.team")).executes(ok(this::listTeams));
        root.then(Commands.literal("list").executes(ok(this::listTeams)));
        for (Party.Kind kind : Party.Kind.values()) {
            String prefix = kind == Party.Kind.TEAM ? "" : "group";
            root.then(Commands.literal(prefix + "create").then(Commands.argument("nombre", StringArgumentType.greedyString()).executes(ok(ctx -> {
                Party party = plugin.teams().create(kind, StringArgumentType.getString(ctx, "nombre"));
                plugin.lang().send(sender(ctx), "teams.created", Lang.p("team", party.name()));
            }))));
            root.then(Commands.literal(prefix + "delete").then(Commands.argument("nombre", StringArgumentType.string())
                    .suggests(teamNames(kind)).executes(ok(ctx -> {
                        String name = StringArgumentType.getString(ctx, "nombre");
                        plugin.teams().byName(kind, name).ifPresentOrElse(party -> {
                            plugin.teams().delete(party);
                            plugin.lang().send(sender(ctx), "teams.deleted", Lang.p("team", party.name()));
                        }, () -> plugin.lang().send(sender(ctx), "teams.not-found", Lang.p("team", name)));
                    }))));
            root.then(Commands.literal(prefix + "add").then(Commands.argument("nombre", StringArgumentType.string()).suggests(teamNames(kind))
                    .then(Commands.argument("jugadores", ArgumentTypes.players()).executes(ok(ctx -> {
                        String name = StringArgumentType.getString(ctx, "nombre");
                        var party = plugin.teams().byName(kind, name);
                        if (party.isEmpty()) {
                            plugin.lang().send(sender(ctx), "teams.not-found", Lang.p("team", name));
                            return;
                        }
                        for (Player target : players(ctx, "jugadores")) {
                            plugin.teams().add(party.get(), target.getUniqueId());
                            plugin.lang().send(target, "teams.joined", Lang.p("team", party.get().name()));
                        }
                        plugin.lang().send(sender(ctx), "teams.added", Lang.p("team", party.get().name()));
                    })))));
            root.then(Commands.literal(prefix + "remove").then(Commands.argument("jugadores", ArgumentTypes.players()).executes(ok(ctx -> {
                for (Player target : players(ctx, "jugadores")) {
                    plugin.teams().remove(target.getUniqueId(), kind);
                }
                plugin.lang().send(sender(ctx), "teams.removed");
            }))));
        }
        root.then(Commands.literal("random").then(Commands.argument("tamaño", IntegerArgumentType.integer(1, 50)).executes(ok(ctx -> {
            plugin.teams().clearTeams();
            plugin.teams().randomize(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList(),
                    IntegerArgumentType.getInteger(ctx, "tamaño"), plugin.lang().raw("teams.default-name"));
            plugin.lang().send(sender(ctx), "teams.shuffled", Lang.p("count", plugin.teams().teams().size()));
        }))));
        root.then(Commands.literal("force").executes(ok(ctx -> {
            // Junta en equipos al azar a los vivos que siguen sin equipo (útil en Amor a primera vista).
            List<java.util.UUID> loose = plugin.game().alive().stream().filter(id -> !plugin.teams().hasTeam(id)).toList();
            plugin.teams().randomize(loose, plugin.settings().teamSize(), plugin.lang().raw("teams.default-name"));
            plugin.lang().broadcast("teams.forced", Lang.p("count", loose.size()));
        })));
        root.then(Commands.literal("clear").executes(ok(ctx -> {
            plugin.teams().clear();
            plugin.lang().send(sender(ctx), "teams.cleared");
        })));
        return root;
    }

    private void listTeams(CommandContext<CommandSourceStack> ctx) {
        CommandSender s = sender(ctx);
        if (plugin.teams().teams().isEmpty() && plugin.teams().groups().isEmpty()) {
            plugin.lang().send(s, "teams.none");
            return;
        }
        for (Party party : plugin.teams().teams()) {
            s.sendMessage(Component.text("■ " + party.name() + ": ", party.color()).append(Component.text(
                    String.join(", ", party.members().stream().map(id -> {
                        String n = Bukkit.getOfflinePlayer(id).getName();
                        return n == null ? "?" : n;
                    }).toList()))));
        }
        for (Party party : plugin.teams().groups()) {
            s.sendMessage(Component.text("◆ " + party.name() + ": ", party.color()).append(Component.text(
                    String.join(", ", party.members().stream().map(id -> {
                        String n = Bukkit.getOfflinePlayer(id).getName();
                        return n == null ? "?" : n;
                    }).toList()))));
        }
    }

    /** /uhcdev: solo con tukiuhc.dev, que no viene con OP (lo da el modo dev de TukiCore). */
    private LiteralArgumentBuilder<CommandSourceStack> devCommand() {
        return Commands.literal("uhcdev").requires(perm("tukiuhc.dev"))
                .executes(ok(ctx -> plugin.lang().sendList(sender(ctx), "dev.help")))
                .then(Commands.literal("info").executes(ok(ctx -> {
                    var game = plugin.game();
                    plugin.lang().sendList(sender(ctx), "dev.info",
                            Lang.p("state", game.state().name()), Lang.p("mode", plugin.settings().mode().name()),
                            Lang.p("episode", game.episode()), Lang.p("second", game.episodeSecond()),
                            Lang.p("length", plugin.settings().episodeSeconds()), Lang.p("rest", game.restLeft()),
                            Lang.p("pvp", game.pvpAllowed()), Lang.p("participants", game.participants().size()),
                            Lang.p("alive", game.alive().size()), Lang.p("sides", game.aliveSides()),
                            Lang.p("total", game.totalSeconds()));
                })))
                .then(Commands.literal("skip").executes(ok(ctx -> plugin.lang().send(sender(ctx),
                        plugin.game().devSkip() ? "dev.skipped" : "dev.not-playing"))))
                .then(Commands.literal("time").then(Commands.argument("segundo", IntegerArgumentType.integer(0))
                        .executes(ok(ctx -> {
                            int second = IntegerArgumentType.getInteger(ctx, "segundo");
                            plugin.lang().send(sender(ctx), plugin.game().devSetSecond(second) ? "dev.time-set" : "dev.not-playing",
                                    Lang.p("second", plugin.game().episodeSecond()));
                        }))))
                .then(Commands.literal("pvp").executes(ok(ctx -> plugin.lang().send(sender(ctx),
                        plugin.game().devTogglePvp() ? "dev.pvp-forced" : "dev.pvp-normal"))));
    }
}
