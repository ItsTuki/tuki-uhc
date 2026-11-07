package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.Nullable;

/** Reglas fijas: PvP por episodios, muerte con cabeza en pica, lobby protegido y mecánicas UHC. */
public final class RulesModule implements UhcModule {
    private final TukiUHC plugin;
    private final java.util.Map<UUID, Long> pvpWarned = new java.util.HashMap<>();

    public RulesModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    /** Jugador que causó el daño (directo, proyectil o TNT), o null. */
    public static @Nullable Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    // ── Lobby y espera ───────────────────────────────────────

    private boolean protectedState() {
        GameState state = plugin.game().state();
        return state != GameState.PLAYING;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && protectedState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (protectedState() && event.getEntity() instanceof Player player
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        GameState state = plugin.game().state();
        if ((state == GameState.PREPARED || state == GameState.STARTING)
                && plugin.game().isParticipant(event.getPlayer().getUniqueId())
                && event.hasChangedPosition()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            // Solo se bloquea el movimiento horizontal: así nadie queda atrapado bajo el agua.
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                Location stay = from.clone();
                stay.setY(to.getY());
                stay.setYaw(to.getYaw());
                stay.setPitch(to.getPitch());
                event.setTo(stay);
            }
        }
    }

    // ── PvP ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (!plugin.game().pvpAllowed()) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = pvpWarned.get(attacker.getUniqueId());
            if (last == null || now - last > 3000) {
                pvpWarned.put(attacker.getUniqueId(), now);
                attacker.sendActionBar(plugin.lang().get("game.pvp-disabled-hit"));
            }
            return;
        }
        boolean teammates = plugin.teams().sameTeam(attacker.getUniqueId(), victim.getUniqueId());
        if (teammates && plugin.settings().mode() != UhcMode.TROLLS_RATAS) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && plugin.game().isAlive(player.getUniqueId())) {
            // /kill y el vacío hacen daño enorme: se cuenta como máximo la vida que le quedaba.
            double real = Math.min(event.getFinalDamage(), player.getHealth() + player.getAbsorptionAmount());
            plugin.stats().addDamage(player.getUniqueId(), Math.max(0, real));
        }
    }

    // ── Muerte ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!plugin.game().isRunning() || !plugin.game().isAlive(player.getUniqueId())) {
            return;
        }
        Location where = player.getLocation();
        Player killer = player.getKiller();
        player.getWorld().strikeLightningEffect(where);
        if (event.deathMessage() != null) {
            event.deathMessage(plugin.lang().get("game.death-prefix").append(event.deathMessage()));
        }
        boolean headStolen = Boolean.TRUE.equals(player.getPersistentDataContainer()
                .get(plugin.key("head_stolen"), PersistentDataType.BOOLEAN));
        placePike(where, headStolen ? null : player);
        plugin.game().eliminate(player, killer, where);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (plugin.game().inGame() && plugin.game().isParticipant(id) && !plugin.game().isAlive(id)) {
            Location death = plugin.game().deathLocations().get(id);
            if (death != null) {
                event.setRespawnLocation(death.clone().add(0, 2, 0));
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                plugin.lang().send(player, "game.you-died");
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!plugin.game().inGame()) {
            return;
        }
        if (!plugin.game().isParticipant(id)) {
            player.setGameMode(GameMode.SPECTATOR);
            plugin.lang().send(player, "game.late-join");
            watchPoint(player).ifPresent(player::teleportAsync);
        } else if (!plugin.game().isAlive(id)) {
            if (plugin.game().consumeOfflineElimination(id)) {
                player.getInventory().clear(); // Sus cosas ya se soltaron donde se desconectó.
                plugin.lang().send(player, "game.you-were-offline-eliminated");
            }
            player.setGameMode(GameMode.SPECTATOR);
            Location death = plugin.game().deathLocations().get(id);
            if (death != null) {
                player.teleportAsync(death.clone().add(0, 2, 0));
            }
        } else {
            plugin.game().handleRejoin(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.game().handleQuit(event.getPlayer());
    }

    /** Un jugador vivo al azar para observar, o el centro del mapa. */
    private java.util.Optional<Location> watchPoint(Player viewer) {
        for (UUID alive : plugin.game().alive()) {
            Player target = Bukkit.getPlayer(alive);
            if (target != null && !target.equals(viewer)) {
                return java.util.Optional.of(target.getLocation().add(0, 3, 0));
            }
        }
        org.bukkit.World world = plugin.game().mainWorld();
        return java.util.Optional.of(new Location(world, 0.5, plugin.game().groundY(world, 0, 0) + 10, 0.5));
    }

    private void placePike(Location where, @Nullable Player headOwner) {
        Block block = where.getBlock();
        int guard = 0;
        while (!block.getType().isAir() && !block.isLiquid() && guard++ < 10) {
            block = block.getRelative(0, 1, 0);
        }
        Block base = block;
        Block fence = base.getRelative(0, 1, 0);
        Block head = base.getRelative(0, 2, 0);
        base.setType(randomPikeBlock());
        fence.setType(Material.OAK_FENCE);
        if (headOwner != null) {
            head.setType(Material.PLAYER_HEAD);
            if (head.getState() instanceof Skull skull) {
                skull.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(headOwner.getPlayerProfile()));
                skull.update();
            }
        }
    }

    private Material randomPikeBlock() {
        Map<Material, Integer> weights = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("death.pike-blocks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null && material.isBlock()) {
                    weights.put(material, Math.max(0, section.getInt(key)));
                }
            }
        }
        if (weights.isEmpty()) {
            weights.put(Material.CRAFTING_TABLE, 1);
        }
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        for (Map.Entry<Material, Integer> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return Material.CRAFTING_TABLE;
    }

    // ── Mecánicas fijas ──────────────────────────────────────

    /** Los ghasts sueltan un lingote de oro en vez de lágrimas. */
    @EventHandler
    public void onGhastDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.GHAST) {
            return;
        }
        boolean hadTears = event.getDrops().removeIf(item -> item.getType() == Material.GHAST_TEAR);
        if (hadTears || plugin.game().isRunning()) {
            event.getDrops().add(ItemStack.of(Material.GOLD_INGOT));
        }
    }

    /** La sopa sospechosa solo da brillo. */
    @EventHandler(ignoreCancelled = true)
    public void onStew(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.SUSPICIOUS_STEW || !plugin.game().inGame()) {
            return;
        }
        ItemStack stew = event.getItem().clone();
        stew.editMeta(SuspiciousStewMeta.class, meta -> {
            meta.clearCustomEffects();
            meta.addCustomEffect(io.papermc.paper.potion.SuspiciousEffectEntry.create(PotionEffectType.GLOWING, 20 * 8), true);
        });
        event.setItem(stew);
        plugin.lang().send(event.getPlayer(), "rules.stew", Lang.p("seconds", 8));
    }
}
