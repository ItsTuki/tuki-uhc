package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.team.Party;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Recompensas configurables: comandos de consola al ganar, matar, ser eliminado o participar.
 * Sirve con cualquier plugin (economía, LuckPerms, cajas…). Ej: "economy give {player} gold-coin 5".
 */
public final class RewardsModule implements UhcModule {
    private final TukiUHC plugin;

    public RewardsModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private void run(String trigger, String player, String other, int kills, String team) {
        if (!plugin.getConfig().getBoolean("rewards.enabled", true)) {
            return;
        }
        List<String> commands = plugin.getConfig().getStringList("rewards." + trigger);
        for (String raw : commands) {
            String command = raw.replace("{player}", player)
                    .replace("{killer}", trigger.equals("kill") ? player : other == null ? "" : other)
                    .replace("{victim}", trigger.equals("kill") && other != null ? other : player)
                    .replace("{kills}", String.valueOf(kills))
                    .replace("{team}", team)
                    .replace("{match}", plugin.settings().matchName());
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (!command.trim().isEmpty()) {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Recompensa fallida (" + command + "): " + ex.getMessage());
                }
            }
        }
    }

    private String team(UUID id) {
        Party party = plugin.teams().team(id);
        return party == null ? "" : party.name();
    }

    @Override
    public void onEliminate(UUID player, Player killer) {
        String victim = plugin.stats().name(player);
        if (killer != null && !killer.getUniqueId().equals(player)) {
            run("kill", killer.getName(), victim, plugin.stats().kills(killer.getUniqueId()), team(killer.getUniqueId()));
        }
        run("eliminated", victim, killer == null ? null : killer.getName(), plugin.stats().kills(player), team(player));
    }

    @Override
    public void onEnd() {
        if (plugin.game().totalSeconds() < plugin.getConfig().getInt("rewards.min-match-seconds", 60)) {
            return; // Una partida detenida al instante no da premios.
        }
        List<UUID> winners = plugin.game().lastWinners();
        for (UUID id : plugin.game().participants()) {
            String name = plugin.stats().name(id);
            run("participation", name, null, plugin.stats().kills(id), team(id));
            if (winners.contains(id)) {
                run("win", name, null, plugin.stats().kills(id), team(id));
            }
        }
    }
}
