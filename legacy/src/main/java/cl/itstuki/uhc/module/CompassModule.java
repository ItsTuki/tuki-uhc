package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.util.Items;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Brújula de compañero: desde un episodio, apunta siempre al compañero vivo más cercano. */
public final class CompassModule implements UhcModule {
    private final TukiUHC plugin;
    private boolean active;

    public CompassModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPrepare() {
        active = false;
    }

    @Override
    public void onEpisodeStart(int episode) {
        if (active || !plugin.settings().enabled(Feature.TEAMMATE_COMPASS)
                || episode < plugin.settings().featureEpisode(Feature.TEAMMATE_COMPASS)) {
            return;
        }
        active = true;
        for (UUID id : plugin.game().alive()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && !plugin.teams().teammates(id).isEmpty()) {
                ItemStack compass = Items.of(XMaterial.COMPASS).name(plugin.lang().item("items.team-compass.name"))
                        .lore(plugin.lang().itemList("items.team-compass.lore")).build();
                player.getInventory().addItem(compass).values()
                        .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
                plugin.lang().send(player, "items.team-compass.received");
            }
        }
    }

    @Override
    public void onSecond(int secondsToNextEpisode) {
        if (!active) {
            return;
        }
        for (UUID id : plugin.game().alive()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            Location target = nearestTeammate(player);
            player.setCompassTarget(target != null ? target : player.getWorld().getSpawnLocation());
        }
    }

    private Location nearestTeammate(Player player) {
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (UUID mate : plugin.teams().teammates(player.getUniqueId())) {
            Player other = Bukkit.getPlayer(mate);
            if (other == null || !plugin.game().isAlive(mate) || !other.getWorld().equals(player.getWorld())) {
                continue;
            }
            double d = other.getLocation().distanceSquared(player.getLocation());
            if (d < bestDistance) {
                bestDistance = d;
                best = other.getLocation();
            }
        }
        return best;
    }

    @Override
    public void onReset() {
        active = false;
    }
}
