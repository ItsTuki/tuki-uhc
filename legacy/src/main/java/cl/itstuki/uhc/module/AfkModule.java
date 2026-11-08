package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.compat.Text;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import com.cryptomorin.xseries.XPotion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** /afk: invulnerable y ciego con un cartel "AFK" sobre la cabeza hasta que te muevas. */
public final class AfkModule implements UhcModule {
    private final TukiUHC plugin;
    private final Map<UUID, ArmorStand> afk = new HashMap<>();

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
        // Jugando solo se puede en el descanso entre partes (se quita al empezar la siguiente).
        if (plugin.game().inGame() && plugin.game().isAlive(player.getUniqueId()) && plugin.game().restLeft() <= 0) {
            plugin.lang().send(player, "commands.afk-running");
            return;
        }
        Compat.effect(player, XPotion.BLINDNESS, Integer.MAX_VALUE, 0, false);
        ArmorStand label = player.getWorld().spawn(player.getLocation().add(0, 2.3, 0), ArmorStand.class);
        label.setVisible(false);
        label.setGravity(false);
        label.setSmall(true);
        label.setMarker(true);
        label.setCustomName(Text.legacy(plugin.lang().get("afk.label")));
        label.setCustomNameVisible(true);
        player.setPassenger(label);
        afk.put(player.getUniqueId(), label);
        plugin.lang().send(player, "afk.enabled");
        plugin.lang().broadcast("afk.broadcast", Lang.p("player", player.getName()));
    }

    private void clear(Player player, boolean message) {
        ArmorStand label = afk.remove(player.getUniqueId());
        if (label != null) {
            label.remove();
        }
        Compat.removeEffect(player, XPotion.BLINDNESS);
        if (message) {
            plugin.lang().send(player, "afk.disabled");
        }
    }

    /** En 1.8 no existe setInvulnerable: el daño se cancela aquí. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && afk.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!afk.containsKey(event.getPlayer().getUniqueId()) || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
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
    public void onEpisodeStart(int episode) {
        for (UUID id : new ArrayList<>(afk.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && plugin.game().isAlive(id)) {
                clear(player, true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
            attacker = (Player) ((Projectile) event.getDamager()).getShooter();
        }
        if (attacker != null && afk.containsKey(attacker.getUniqueId())) {
            clear(attacker, true);
        }
    }

    @Override
    public void onReset() {
        for (UUID id : new ArrayList<>(afk.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clear(player, false);
            }
        }
        afk.clear();
    }
}
