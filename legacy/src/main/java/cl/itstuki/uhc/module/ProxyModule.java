package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.compat.Text;
import org.bukkit.event.server.ServerListPingEvent;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Soporte para redes BungeeCord / Velocity:
 * enviar jugadores al lobby de la red (/hub y al terminar), qué hacer tras la partida,
 * bloquear entradas a mitad de partida y un MOTD que muestra el estado (útil para selectores de servidores).
 * Velocity es compatible porque acepta el canal "BungeeCord" (bungee-plugin-message-channel = true).
 */
public final class ProxyModule implements UhcModule {
    private static final String CHANNEL = "BungeeCord";
    private final TukiUHC plugin;

    public ProxyModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("proxy.enabled", false);
    }

    private String lobbyServer() {
        return plugin.getConfig().getString("proxy.lobby-server", "lobby");
    }

    public void registerChannel() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public void unregisterChannel() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    /** Envía al jugador a otro servidor de la red. Devuelve false si el proxy está desactivado. */
    public boolean connect(Player player, String server) {
        if (!enabled()) {
            return false;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().warning("No se pudo enviar a " + player.getName() + " a " + server + ": " + ex.getMessage());
            return false;
        }
    }

    /** /hub o /lobby: vuelve al lobby de la red. */
    public void sendToHub(Player player) {
        if (!enabled()) {
            plugin.lang().send(player, "proxy.disabled");
            return;
        }
        plugin.lang().send(player, "proxy.sending", Lang.p("server", lobbyServer()));
        connect(player, lobbyServer());
    }

    // ── Tras la partida ──────────────────────────────────────

    @Override
    public void onEnd() {
        if (!enabled() || !plugin.getConfig().getBoolean("proxy.send-to-lobby-on-end", true)) {
            return;
        }
        int delay = plugin.getConfig().getInt("lobby.results-delay", 8) + plugin.getConfig().getInt("proxy.end-delay", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.game().state() != GameState.ENDED) {
                return;
            }
            plugin.lang().broadcast("proxy.end-sending", Lang.p("server", lobbyServer()));
            for (Player player : Bukkit.getOnlinePlayers()) {
                connect(player, lobbyServer());
            }
            Bukkit.getScheduler().runTaskLater(plugin, this::afterMatch, 60L);
        }, 20L * Math.max(1, delay));
    }

    private void afterMatch() {
        String action = plugin.getConfig().getString("proxy.end-action", "RESET").toUpperCase(Locale.ROOT);
        if (action.equals("SHUTDOWN")) {
            plugin.getLogger().info("Partida terminada: apagando el servidor (proxy.end-action = SHUTDOWN).");
            Bukkit.shutdown();
        } else if (!action.equals("NONE")) {
            plugin.game().reset();
        }
    }

    // ── Entradas a mitad de partida ──────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.game().inGame() || plugin.game().isParticipant(player.getUniqueId())
                || !plugin.getConfig().getBoolean("proxy.block-late-joins", false)
                || player.hasPermission("tukiuhc.join.spectate")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (enabled()) {
                plugin.lang().send(player, "proxy.match-running");
                connect(player, lobbyServer());
            } else {
                player.kickPlayer(Text.legacy(plugin.lang().get("proxy.match-running-kick")));
            }
        }, 10L);
    }

    // ── MOTD con el estado ───────────────────────────────────

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("motd.enabled", true)) {
            return;
        }
        String state = plugin.game().state().name().toLowerCase(Locale.ROOT);
        event.setMotd(Text.legacy(plugin.lang().get("motd." + state,
                Lang.p("name", plugin.settings().matchName()),
                Lang.c("mode", plugin.lang().get("modes." + plugin.settings().mode().key() + ".name")),
                Lang.p("online", event.getNumPlayers()),
                Lang.p("alive", plugin.game().alive().size()),
                Lang.p("episode", plugin.game().episode()))));
    }
}
