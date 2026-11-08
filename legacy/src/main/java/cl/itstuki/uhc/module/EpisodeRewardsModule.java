package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.util.Show;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Recompensas y avisos por episodio: Shulker Box, aumento de vida, localizador y tumbas. */
public final class EpisodeRewardsModule implements UhcModule {
    private final TukiUHC plugin;
    private final java.util.Set<UUID> boosted = new java.util.HashSet<>();

    public EpisodeRewardsModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private boolean at(Feature feature, int episode) {
        return plugin.settings().enabled(feature) && plugin.settings().featureEpisode(feature) == episode;
    }

    @Override
    public void onSecond(int secondsToNextEpisode) {
        int next = plugin.game().episode() + 1;
        if (secondsToNextEpisode == 30 && at(Feature.SHULKER_BOX, next)) {
            plugin.lang().broadcast("rewards.shulker-warning");
        }
    }

    @Override
    public void onEpisodeStart(int episode) {
        // La Resistencia del aumento de vida termina al empezar la parte siguiente.
        if (plugin.settings().enabled(Feature.LIFE_BOOST) && episode == plugin.settings().featureEpisode(Feature.LIFE_BOOST) + 1) {
            for (UUID id : plugin.game().alive()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    Compat.removeEffect(player, XPotion.RESISTANCE);
                }
            }
        }
        if (at(Feature.SHULKER_BOX, episode)) {
            giveAll(Compat.item(XMaterial.SHULKER_BOX));
            plugin.lang().broadcast("rewards.shulker-given");
        }
        if (at(Feature.LIFE_BOOST, episode)) {
            lifeBoost();
        }
        if (at(Feature.GRAVES, episode)) {
            graves();
        }
    }

    private void giveAll(ItemStack item) {
        for (UUID id : plugin.game().alive()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.getInventory().addItem(item.clone()).values()
                        .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            }
        }
    }

    private void lifeBoost() {
        for (UUID id : plugin.game().alive()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (boosted.add(id)) {
                Compat.addMaxHealth(player, 20);
                ItemsModule.heal(player, 20);
            }
            // Resistencia II solo durante esta parte.
            Compat.effect(player, XPotion.RESISTANCE, plugin.settings().episodeSeconds() * 20, 1, true);
            Show.title(player, Show.title(plugin.lang().get("rewards.life-boost-title"), plugin.lang().get("rewards.life-boost-subtitle"),
                    200, 3000, 1000));
            Compat.sound(player, Compat.atLeast(11) ? XSound.ITEM_TOTEM_USE : XSound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        }
        plugin.lang().broadcast("rewards.life-boost");
    }

    private void graves() {
        Map<UUID, Location> deaths = plugin.game().deathLocations();
        if (deaths.isEmpty()) {
            plugin.lang().broadcast("rewards.graves-empty");
            return;
        }
        plugin.lang().broadcast("rewards.graves-header");
        for (Map.Entry<UUID, Location> entry : deaths.entrySet()) {
            if (plugin.game().isAlive(entry.getKey())) {
                continue;
            }
            Location loc = entry.getValue();
            String biome = loc.getBlock().getBiome().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            plugin.lang().broadcast("rewards.graves-entry", Lang.p("player", plugin.stats().name(entry.getKey())),
                    Lang.p("biome", biome), Lang.p("x", loc.getBlockX()), Lang.p("y", loc.getBlockY()), Lang.p("z", loc.getBlockZ()),
                    Lang.p("world", loc.getWorld().getName()));
        }
    }

    @Override
    public void onReset() {
        boosted.clear();
    }
}
