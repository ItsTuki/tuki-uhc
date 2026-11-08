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
import cl.itstuki.uhc.compat.Compat;
import com.cryptomorin.xseries.XSound;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || Compat.isOffHand(event)) {
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
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        where.getWorld().playEffect(where, Effect.MOBSPAWNER_FLAMES, 0);
        Compat.sound(where, Compat.atLeast(11) ? XSound.ITEM_TOTEM_USE : XSound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
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
