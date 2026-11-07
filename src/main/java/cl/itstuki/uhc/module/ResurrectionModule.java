package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Cristal de resurrección: colocado sobre bedrock revive a un compañero muerto (una vez por jugador). */
public final class ResurrectionModule implements UhcModule {
    private final TukiUHC plugin;
    private final Set<UUID> revivedOnce = new HashSet<>();

    public ResurrectionModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !"revive_crystal".equals(plugin.items().id(item))) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (block.getType() != Material.BEDROCK) {
            plugin.lang().send(player, "revive.need-bedrock");
            return;
        }
        if (!plugin.settings().enabled(Feature.RESURRECTION) || !plugin.game().isRunning()) {
            plugin.lang().send(player, "revive.disabled");
            return;
        }
        UUID target = null;
        for (UUID mate : plugin.teams().teammates(player.getUniqueId())) {
            if (plugin.game().isParticipant(mate) && !plugin.game().isAlive(mate)
                    && !revivedOnce.contains(mate) && Bukkit.getPlayer(mate) != null) {
                target = mate;
                break;
            }
        }
        if (target == null) {
            plugin.lang().send(player, "revive.no-target");
            return;
        }
        Player dead = Bukkit.getPlayer(target);
        Location where = block.getLocation().add(0.5, 1, 0.5);
        if (dead == null || !plugin.game().revive(dead, where)) {
            plugin.lang().send(player, "revive.no-target");
            return;
        }
        revivedOnce.add(target);
        item.subtract();
        where.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, where, 80, 0.5, 1, 0.5, 0.3);
        where.getWorld().playSound(where, Sound.ITEM_TOTEM_USE, 1f, 1f);
        plugin.lang().broadcast("revive.done", Lang.p("player", dead.getName()), Lang.p("by", player.getName()));
    }

    /** Revivir por comando de administrador (no consume el "una vez por jugador"). */
    public boolean adminRevive(Player dead, Location where) {
        return plugin.game().revive(dead, where);
    }

    @Override
    public void onPrepare() {
        revivedOnce.clear();
    }

    @Override
    public void onReset() {
        revivedOnce.clear();
    }
}
