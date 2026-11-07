package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Recompensas y avisos por episodio: Shulker Box, aumento de vida, localizador y tumbas. */
public final class EpisodeRewardsModule implements UhcModule {
    private final TukiUHC plugin;

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
        if (at(Feature.SHULKER_BOX, episode)) {
            giveAll(ItemStack.of(Material.SHULKER_BOX));
            plugin.lang().broadcast("rewards.shulker-given");
        }
        if (at(Feature.LIFE_BOOST, episode)) {
            lifeBoost();
        }
        if (at(Feature.PLAYER_LOCATOR, episode)) {
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRules.LOCATOR_BAR, true);
            }
            plugin.lang().broadcast("rewards.locator-on");
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
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && max.getModifier(plugin.key("life_boost")) == null) {
                max.addModifier(new AttributeModifier(plugin.key("life_boost"), 20, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                ItemsModule.heal(player, 20);
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, true));
            player.showTitle(Title.title(plugin.lang().get("rewards.life-boost-title"), plugin.lang().get("rewards.life-boost-subtitle"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.2f);
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
            String biome = loc.getBlock().getBiome().getKey().getKey().replace('_', ' ');
            plugin.lang().broadcast("rewards.graves-entry", Lang.p("player", plugin.stats().name(entry.getKey())),
                    Lang.p("biome", biome), Lang.p("x", loc.getBlockX()), Lang.p("y", loc.getBlockY()), Lang.p("z", loc.getBlockZ()),
                    Lang.p("world", loc.getWorld().getName()));
        }
    }
}
