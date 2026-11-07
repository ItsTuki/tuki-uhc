package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Edad de Cobre: en el último episodio cada equipo recibe una estatua de Golem de Cobre.
 * Al colocarla crea un Área de Bendición (regeneración para el equipo durante 5 minutos).
 * Si un enemigo rompe la estatua, cada miembro del equipo pierde 3 corazones.
 */
public final class CopperAgeModule implements UhcModule {
    private static final int RADIUS = 8;
    private static final int DURATION = 300;

    private final TukiUHC plugin;
    private final NamespacedKey statueKey;
    private final List<Zone> zones = new ArrayList<>();

    private static final class Zone {
        final Location center;
        final String side;
        int secondsLeft = DURATION;
        final Map<UUID, Long> alerted = new HashMap<>();

        Zone(Location center, String side) {
            this.center = center;
            this.side = side;
        }
    }

    public CopperAgeModule(TukiUHC plugin) {
        this.plugin = plugin;
        this.statueKey = plugin.key("blessing_side");
    }

    private String side(UUID player) {
        Party team = plugin.teams().team(player);
        return team != null ? "t" + team.id() : "p" + player;
    }

    private List<UUID> members(String side) {
        if (side.startsWith("t")) {
            return plugin.teams().byId(Integer.parseInt(side.substring(1)))
                    .map(p -> List.copyOf(p.members())).orElse(List.of());
        }
        return List.of(UUID.fromString(side.substring(1)));
    }

    @Override
    public void onEpisodeStart(int episode) {
        if (!plugin.settings().enabled(Feature.COPPER_AGE) || episode != plugin.settings().finalEpisode()) {
            return;
        }
        Set<String> given = new HashSet<>();
        for (UUID id : plugin.game().alive()) {
            Player player = Bukkit.getPlayer(id);
            String side = side(id);
            if (player == null || !given.add(side)) {
                continue;
            }
            player.getInventory().addItem(Items.of(Material.COPPER_GOLEM_STATUE)
                    .name(plugin.lang().item("copper.item-name")).lore(plugin.lang().itemList("copper.item-lore"))
                    .glow(true).tag(statueKey, side).build()).values()
                    .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            for (UUID member : members(side)) {
                Player mate = Bukkit.getPlayer(member);
                if (mate != null) {
                    plugin.lang().send(mate, "copper.blessed", Lang.p("player", player.getName()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String side = Items.tag(event.getItemInHand(), statueKey);
        if (side == null) {
            return;
        }
        Zone zone = new Zone(event.getBlockPlaced().getLocation(), side);
        zones.add(zone);
        for (UUID member : members(side)) {
            Player mate = Bukkit.getPlayer(member);
            if (mate != null) {
                plugin.lang().send(mate, "copper.activated");
            }
        }
        zone.center.getWorld().playSound(zone.center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.3f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Iterator<Zone> it = zones.iterator();
        while (it.hasNext()) {
            Zone zone = it.next();
            if (!zone.center.getBlock().equals(block)) {
                continue;
            }
            it.remove();
            Player breaker = event.getPlayer();
            if (side(breaker.getUniqueId()).equals(zone.side)) {
                return;
            }
            for (UUID member : members(zone.side)) {
                Player mate = Bukkit.getPlayer(member);
                if (mate != null && plugin.game().isAlive(member)) {
                    mate.damage(6.0);
                    plugin.lang().send(mate, "copper.broken", Lang.p("player", breaker.getName()));
                }
            }
            plugin.lang().send(breaker, "copper.you-broke");
            return;
        }
    }

    @Override
    public void onSecond(int secondsToNextEpisode) {
        Iterator<Zone> it = zones.iterator();
        long now = System.currentTimeMillis();
        while (it.hasNext()) {
            Zone zone = it.next();
            if (zone.center.getBlock().getType() != Material.COPPER_GOLEM_STATUE
                    && !zone.center.getBlock().getType().name().contains("COPPER_GOLEM_STATUE")) {
                it.remove();
                continue;
            }
            zone.secondsLeft--;
            if (zone.secondsLeft <= 0) {
                it.remove();
                continue;
            }
            zone.center.getWorld().spawnParticle(Particle.WAX_ON, zone.center.clone().add(0.5, 1, 0.5), 6, RADIUS / 2.0, 1, RADIUS / 2.0, 0);
            for (Player player : zone.center.getWorld().getPlayers()) {
                if (!plugin.game().isAlive(player.getUniqueId())
                        || player.getLocation().distanceSquared(zone.center) > RADIUS * RADIUS) {
                    continue;
                }
                if (side(player.getUniqueId()).equals(zone.side)) {
                    if (zone.secondsLeft > 0) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, true));
                    }
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 1, true, true));
                    Long last = zone.alerted.get(player.getUniqueId());
                    if (last == null || now - last > 20_000) {
                        zone.alerted.put(player.getUniqueId(), now);
                        for (UUID member : members(zone.side)) {
                            Player mate = Bukkit.getPlayer(member);
                            if (mate != null) {
                                plugin.lang().send(mate, "copper.intruder", Lang.p("player", player.getName()));
                            }
                        }
                    }
                }
            }
            if (zone.secondsLeft == 0) {
                for (UUID member : members(zone.side)) {
                    Player mate = Bukkit.getPlayer(member);
                    if (mate != null) {
                        plugin.lang().send(mate, "copper.expired");
                    }
                }
            }
        }
    }

    @Override
    public void onReset() {
        zones.clear();
    }
}
