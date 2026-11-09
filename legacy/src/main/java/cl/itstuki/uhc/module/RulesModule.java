package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.compat.Text;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.util.Show;
import com.cryptomorin.xseries.XMaterial;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/** Reglas fijas: PvP por episodios, muerte con cabeza en pica, lobby protegido y mecánicas UHC. */
public final class RulesModule implements UhcModule {
    private final TukiUHC plugin;
    private final Map<UUID, Long> pvpWarned = new HashMap<>();

    public RulesModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    /** Jugador que causó el daño (directo, proyectil o TNT), o null. */
    public static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) event.getDamager()).getShooter();
            return shooter instanceof Player ? (Player) shooter : null;
        }
        if (event.getDamager() instanceof TNTPrimed && ((TNTPrimed) event.getDamager()).getSource() instanceof Player) {
            return (Player) ((TNTPrimed) event.getDamager()).getSource();
        }
        return null;
    }

    // ── Lobby y espera ───────────────────────────────────────

    private boolean protectedState() {
        return plugin.game().state() != GameState.PLAYING;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && protectedState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (protectedState() && event.getEntity() instanceof Player
                && event.getFoodLevel() < ((Player) event.getEntity()).getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        GameState state = plugin.game().state();
        if ((state == GameState.PREPARED || state == GameState.STARTING)
                && plugin.game().isParticipant(event.getPlayer().getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            // Solo se bloquea el movimiento horizontal: así nadie queda atrapado bajo el agua.
            if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
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
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
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
                Show.actionBar(attacker, plugin.lang().get("game.pvp-disabled-hit"));
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
        if (event.getEntity() instanceof Player && plugin.game().isAlive(event.getEntity().getUniqueId())) {
            Player player = (Player) event.getEntity();
            // /kill y el vacío hacen daño enorme: se cuenta como máximo la vida que le quedaba.
            double real = Math.min(event.getFinalDamage(), player.getHealth());
            plugin.stats().addDamage(player.getUniqueId(), Math.max(0, real));
        }
    }

    // ── Muerte ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.game().isRunning() || !plugin.game().isAlive(player.getUniqueId())) {
            return;
        }
        Location where = player.getLocation();
        Player killer = player.getKiller();
        player.getWorld().strikeLightningEffect(where);
        if (event.getDeathMessage() != null) {
            event.setDeathMessage(Text.legacy(plugin.lang().get("game.death-prefix")) + event.getDeathMessage());
        }
        boolean headStolen = plugin.items().headStolen(player.getUniqueId());
        Block base = placePike(where, headStolen ? null : player.getName());
        if (plugin.getConfig().getBoolean("death.chest", true) && !event.getDrops().isEmpty()) {
            // Las cosas quedan en un cofre bajo la estaca (el bloque útil va dentro).
            java.util.List<ItemStack> items = new java.util.ArrayList<>(event.getDrops());
            items.add(new ItemStack(base.getType()));
            event.getDrops().clear();
            fillChests(base, items);
        }
        plugin.game().eliminate(player, killer, where);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (plugin.game().inGame() && plugin.game().isParticipant(id) && !plugin.game().isAlive(id)) {
            Location death = plugin.game().deathLocations().get(id);
            if (death != null) {
                event.setRespawnLocation(death.clone().add(0, 2, 0));
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.spectators().spectate(player);
                plugin.lang().send(player, "game.you-died");
            });
        }
    }

    /** En 1.8–1.14 no existe la reaparición inmediata: se pulsa "Reaparecer" sola. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathRespawn(PlayerDeathEvent event) {
        if (Compat.atLeast(15) || !plugin.game().inGame()) {
            return;
        }
        final Player player = event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                try {
                    player.spigot().respawn();
                } catch (RuntimeException | NoSuchMethodError ignored) {
                    // Algunos forks antiguos no lo tienen: el jugador pulsa el botón.
                }
            }
        }, 2L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!plugin.game().inGame()) {
            return;
        }
        if (!plugin.game().isParticipant(id)) {
            plugin.spectators().spectate(player);
            plugin.lang().send(player, "game.late-join");
            player.teleport(watchPoint(player));
        } else if (!plugin.game().isAlive(id)) {
            if (plugin.game().consumeOfflineElimination(id)) {
                player.getInventory().clear(); // Sus cosas ya se soltaron donde se desconectó.
                plugin.lang().send(player, "game.you-were-offline-eliminated");
            }
            plugin.spectators().spectate(player);
            Location death = plugin.game().deathLocations().get(id);
            if (death != null) {
                player.teleport(death.clone().add(0, 2, 0));
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
    private Location watchPoint(Player viewer) {
        for (UUID alive : plugin.game().alive()) {
            Player target = Bukkit.getPlayer(alive);
            if (target != null && !target.equals(viewer)) {
                return target.getLocation().add(0, 3, 0);
            }
        }
        org.bukkit.World world = plugin.game().mainWorld();
        return new Location(world, 0.5, plugin.game().groundY(world, 0, 0) + 10, 0.5);
    }

    @SuppressWarnings("deprecation")
    /** Cofre en la base de la estaca y, si no alcanza, otro al lado. Lo que no cabe se suelta encima. */
    private void fillChests(Block base, java.util.List<ItemStack> items) {
        java.util.List<Block> chests = new java.util.ArrayList<>();
        base.setType(Material.CHEST);
        chests.add(base);
        if (items.size() > 27) {
            for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[] {org.bukkit.block.BlockFace.EAST,
                    org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH}) {
                Block side = base.getRelative(face);
                if (Compat.isAir(side.getType())) {
                    side.setType(Material.CHEST);
                    chests.add(side);
                    break;
                }
            }
        }
        int index = 0;
        for (Block block : chests) {
            BlockState state = block.getState();
            if (!(state instanceof org.bukkit.block.Chest)) {
                continue;
            }
            org.bukkit.inventory.Inventory inventory = ((org.bukkit.block.Chest) state).getBlockInventory();
            while (index < items.size() && inventory.firstEmpty() >= 0) {
                ItemStack item = items.get(index++);
                if (item != null && !Compat.isAir(item.getType())) {
                    inventory.addItem(item);
                }
            }
        }
        for (; index < items.size(); index++) {
            ItemStack item = items.get(index);
            if (item != null && !Compat.isAir(item.getType())) {
                base.getWorld().dropItemNaturally(base.getLocation().add(0.5, 1.2, 0.5), item);
            }
        }
    }

    private Block placePike(Location where, String headOwner) {
        Block block = where.getBlock();
        int guard = 0;
        while (!Compat.isAir(block.getType()) && !block.isLiquid() && guard++ < 10) {
            block = block.getRelative(0, 1, 0);
        }
        Block base = block;
        Block fence = base.getRelative(0, 1, 0);
        Block head = base.getRelative(0, 2, 0);
        base.setType(randomPikeBlock());
        fence.setType(Compat.first(XMaterial.OAK_FENCE));
        if (headOwner != null) {
            if (Compat.atLeast(13)) {
                head.setType(Compat.mat(XMaterial.PLAYER_HEAD));
            } else {
                head.setType(Material.valueOf("SKULL"));
                head.setData((byte) 1);
            }
            BlockState state = head.getState();
            if (state instanceof Skull) {
                Skull skull = (Skull) state;
                if (!Compat.atLeast(13)) {
                    skull.setSkullType(SkullType.PLAYER);
                }
                skull.setOwner(headOwner);
                skull.update(true);
            }
        }
        return base;
    }

    private Material randomPikeBlock() {
        Map<Material, Integer> weights = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("death.pike-blocks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = XMaterial.matchXMaterial(key).map(XMaterial::parseMaterial).orElse(null);
                if (material != null && material.isBlock()) {
                    weights.put(material, Math.max(0, section.getInt(key)));
                }
            }
        }
        Material table = Compat.mat(XMaterial.CRAFTING_TABLE);
        if (weights.isEmpty()) {
            weights.put(table, 1);
        }
        int total = 0;
        for (int w : weights.values()) {
            total += w;
        }
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        for (Map.Entry<Material, Integer> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return table;
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
            event.getDrops().add(new ItemStack(Material.GOLD_INGOT));
        }
    }
}
