package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.util.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;

/** Caña de pescar cabezas: enganchar a un enemigo le roba la cabeza (no la dejará al morir). */
public final class HeadStealModule implements UhcModule {
    private final TukiUHC plugin;
    private final NamespacedKey stolenKey;

    public HeadStealModule(TukiUHC plugin) {
        this.plugin = plugin;
        this.stolenKey = plugin.key("head_stolen");
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || !(event.getCaught() instanceof Player victim)) {
            return;
        }
        Player thief = event.getPlayer();
        if (!plugin.settings().enabled(Feature.HEAD_STEAL_ROD) || !plugin.game().pvpAllowed()
                || !plugin.game().isAlive(victim.getUniqueId())
                || plugin.teams().sameTeam(thief.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        if (Boolean.TRUE.equals(victim.getPersistentDataContainer().get(stolenKey, PersistentDataType.BOOLEAN))) {
            plugin.lang().send(thief, "head-steal.already", Lang.p("player", victim.getName()));
            return;
        }
        victim.getPersistentDataContainer().set(stolenKey, PersistentDataType.BOOLEAN, true);
        thief.getInventory().addItem(Items.of(Material.PLAYER_HEAD).owner(victim).build()).values()
                .forEach(rest -> thief.getWorld().dropItem(thief.getLocation(), rest));
        thief.playSound(thief.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
        plugin.lang().send(thief, "head-steal.thief", Lang.p("player", victim.getName()));
        plugin.lang().send(victim, "head-steal.victim", Lang.p("player", thief.getName()));
    }
}
