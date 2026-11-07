package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Inicio automático para servidores sin organizador (redes): cuando hay suficientes jugadores
 * en el lobby empieza una cuenta atrás, reparte y comienza solo.
 */
public final class AutoStartModule implements UhcModule {
    private final TukiUHC plugin;
    private int countdown = -1;
    private boolean waitingScatter;

    public AutoStartModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("autostart.enabled", false);
    }

    private int readyPlayers() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("tukiuhc.spectator")) {
                count++;
            }
        }
        return count;
    }

    private void tick() {
        GameState state = plugin.game().state();
        if (waitingScatter) {
            if (state == GameState.PREPARED) {
                waitingScatter = false;
                plugin.game().start(Bukkit.getConsoleSender(), true);
            } else if (state != GameState.SCATTERING) {
                waitingScatter = false;
            }
            return;
        }
        if (!enabled() || state != GameState.LOBBY) {
            countdown = -1;
            return;
        }
        int min = Math.max(2, plugin.getConfig().getInt("autostart.min-players", 4));
        int ready = readyPlayers();
        if (ready < min) {
            if (countdown >= 0) {
                plugin.lang().broadcast("autostart.cancelled", Lang.p("ready", ready), Lang.p("min", min));
            }
            countdown = -1;
            return;
        }
        if (countdown < 0) {
            countdown = Math.max(5, plugin.getConfig().getInt("autostart.countdown", 60));
        }
        if (countdown == 60 || countdown == 30 || countdown == 15 || countdown == 10 || countdown <= 5 && countdown > 0) {
            plugin.lang().broadcast("autostart.countdown", Lang.p("seconds", countdown));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, countdown <= 5 ? 1.6f : 1f);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(plugin.lang().get("autostart.actionbar", Lang.p("seconds", countdown), Lang.p("ready", ready)));
        }
        if (countdown-- <= 0) {
            countdown = -1;
            if (plugin.game().prepare(Bukkit.getConsoleSender())) {
                waitingScatter = true;
            }
        }
    }

    @Override
    public void onReset() {
        countdown = -1;
        waitingScatter = false;
    }
}
