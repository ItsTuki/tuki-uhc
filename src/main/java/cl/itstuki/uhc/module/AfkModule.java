package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.game.UhcModule;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** /afk: invulnerable y ciego con un cartel "AFK" sobre la cabeza hasta que te muevas. */
public final class AfkModule implements UhcModule {
    private final TukiUHC plugin;
    private final Map<UUID, TextDisplay> afk = new HashMap<>();

    public AfkModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public boolean isAfk(UUID id) {
        return afk.containsKey(id);
    }

    public void toggle(Player player) {
        if (afk.containsKey(player.getUniqueId())) {
            clear(player, true);
            return;
        }
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false));
        TextDisplay label = player.getWorld().spawn(player.getLocation().add(0, 2.3, 0), TextDisplay.class, display -> {
            display.text(plugin.lang().get("afk.label"));
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            display.setSeeThrough(true);
        });
        player.addPassenger(label);
        afk.put(player.getUniqueId(), label);
        plugin.lang().send(player, "afk.enabled");
        plugin.lang().broadcast("afk.broadcast", cl.itstuki.uhc.lang.Lang.p("player", player.getName()));
    }

    private void clear(Player player, boolean message) {
        TextDisplay label = afk.remove(player.getUniqueId());
        if (label != null) {
            label.remove();
        }
        player.setInvulnerable(false);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (message) {
            plugin.lang().send(player, "afk.disabled");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (afk.containsKey(event.getPlayer().getUniqueId()) && event.hasChangedBlock()) {
            clear(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (afk.containsKey(event.getPlayer().getUniqueId())) {
            clear(event.getPlayer(), false);
        }
    }

    @Override
    public void onReset() {
        for (UUID id : Map.copyOf(afk).keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clear(player, false);
            }
        }
        afk.clear();
    }
}
