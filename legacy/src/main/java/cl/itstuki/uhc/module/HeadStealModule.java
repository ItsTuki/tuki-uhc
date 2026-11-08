package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.util.Items;
import cl.itstuki.uhc.compat.Compat;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;

/** Caña de pescar cabezas: enganchar a un enemigo le roba la cabeza (no la dejará al morir). */
public final class HeadStealModule implements UhcModule {
    private final TukiUHC plugin;

    public HeadStealModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || !(event.getCaught() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getCaught();
        Player thief = event.getPlayer();
        if (!plugin.settings().enabled(Feature.HEAD_STEAL_ROD) || !plugin.game().pvpAllowed()
                || !plugin.game().isAlive(victim.getUniqueId())
                || plugin.teams().sameTeam(thief.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        if (!plugin.items().stealHead(victim.getUniqueId())) {
            plugin.lang().send(thief, "head-steal.already", Lang.p("player", victim.getName()));
            return;
        }
        thief.getInventory().addItem(Items.of(XMaterial.PLAYER_HEAD).owner(victim.getName()).build()).values()
                .forEach(rest -> thief.getWorld().dropItem(thief.getLocation(), rest));
        Compat.sound(thief, XSound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
        plugin.lang().send(thief, "head-steal.thief", Lang.p("player", victim.getName()));
        plugin.lang().send(victim, "head-steal.victim", Lang.p("player", thief.getName()));
    }
}
