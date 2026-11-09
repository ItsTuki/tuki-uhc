package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.gui.Menu;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Format;
import cl.itstuki.uhc.util.Items;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.jspecify.annotations.Nullable;

/**
 * Espectadores "fantasma": los eliminados (y quien entra a mirar) vuelan, son invisibles para los vivos, no
 * pueden tocar nada y tienen una brújula con la lista de jugadores vivos: clic izquierdo para ir con uno,
 * clic derecho para ver su inventario sin poder cambiarlo. Con spectators.ghost: false se usa el modo
 * espectador normal del juego.
 */
public final class SpectatorModule implements UhcModule {
    private final TukiUHC plugin;
    private final Set<UUID> ghosts = new HashSet<>();
    private final NamespacedKey key;

    public SpectatorModule(TukiUHC plugin) {
        this.plugin = plugin;
        this.key = plugin.key("spectator_item");
    }

    private boolean ghostMode() {
        return plugin.getConfig().getBoolean("spectators.ghost", true);
    }

    public boolean isGhost(Player player) {
        return ghosts.contains(player.getUniqueId());
    }

    /** Pasa a un jugador a espectador (fantasma o el modo normal, según config). */
    public void spectate(Player player) {
        if (!ghostMode()) {
            player.setGameMode(GameMode.SPECTATOR);
            return;
        }
        ghosts.add(player.getUniqueId());
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max != null ? max.getValue() : 20);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setInvulnerable(true);
        player.setSilent(true);
        player.setCanPickupItems(false);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player) && !isGhost(other)) {
                other.hidePlayer(plugin, player);
            }
        }
        giveItems(player);
        plugin.lang().send(player, "spectator.info");
    }

    private void giveItems(Player player) {
        player.getInventory().setItem(4, Items.of(Material.COMPASS).name(plugin.lang().get("spectator.compass.name"))
                .lore(plugin.lang().itemList("spectator.compass.lore")).tag(key, "players").build());
    }

    /** Vuelve a la normalidad (al revivir, al terminar o al reiniciar). */
    public void clear(Player player) {
        if (!ghosts.remove(player.getUniqueId())) {
            return;
        }
        player.getInventory().clear();
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        player.setCollidable(true);
        player.setInvulnerable(false);
        player.setSilent(false);
        player.setCanPickupItems(true);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    private void clearAll() {
        for (UUID id : new ArrayList<>(ghosts)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clear(player);
            }
        }
        ghosts.clear();
    }

    @Override
    public void onEnd() {
        clearAll();
    }

    @Override
    public void onReset() {
        clearAll();
    }

    // ── Menú de jugadores vivos ──────────────────────────────

    public void openPlayers(Player viewer) {
        new Menu(6, plugin.lang().get("spectator.menu.title")) {
            @Override
            protected void render() {
                int slot = 0;
                for (UUID id : plugin.game().alive()) {
                    Player target = Bukkit.getPlayer(id);
                    if (target == null || slot >= 45) {
                        continue;
                    }
                    Party team = plugin.teams().team(id);
                    String distance = target.getWorld().equals(viewer.getWorld())
                            ? String.valueOf((int) target.getLocation().distance(viewer.getLocation())) : "?";
                    set(slot++, Items.of(Material.PLAYER_HEAD).owner(target)
                            .name(Component.text(target.getName(), team != null ? team.color() : net.kyori.adventure.text.format.NamedTextColor.WHITE))
                            .lore(plugin.lang().itemList("spectator.menu.player-lore",
                                    Lang.p("team", team != null ? team.name() : "-"),
                                    Lang.p("hearts", Format.hearts(target.getHealth())),
                                    Lang.p("distance", distance),
                                    Lang.p("kills", plugin.stats().kills(id))))
                            .build(), (p, click) -> {
                        Player now = Bukkit.getPlayer(id);
                        if (now == null || !plugin.game().isAlive(id)) {
                            refresh();
                            return;
                        }
                        if (click.isRightClick()) {
                            openInventory(p, now);
                        } else {
                            p.closeInventory();
                            p.teleport(now.getLocation().add(0, 2, 0));
                            plugin.lang().send(p, "spectator.teleported", Lang.p("player", now.getName()));
                        }
                    });
                }
                if (slot == 0) {
                    set(22, Items.of(Material.BARRIER).name(plugin.lang().get("spectator.menu.empty")).build(), null);
                }
                set(49, Items.of(Material.CLOCK).name(plugin.lang().get("spectator.menu.refresh")).build(), (p, c) -> refresh());
            }
        }.open(viewer);
    }

    /** Inventario de un jugador, solo para mirar (es una copia; los clics no hacen nada). */
    public void openInventory(Player viewer, Player target) {
        new Menu(6, plugin.lang().get("spectator.inventory.title", Lang.p("player", target.getName()))) {
            @Override
            protected void render() {
                PlayerInventory inv = target.getInventory();
                for (int i = 9; i < 36; i++) {
                    copy(i - 9, inv.getItem(i));
                }
                for (int i = 0; i < 9; i++) {
                    copy(27 + i, inv.getItem(i));
                }
                copy(36, inv.getHelmet());
                copy(37, inv.getChestplate());
                copy(38, inv.getLeggings());
                copy(39, inv.getBoots());
                copy(40, inv.getItemInOffHand());
                AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
                set(45, Items.of(Material.RED_DYE).name(plugin.lang().get("spectator.inventory.health",
                        Lang.p("hearts", Format.hearts(target.getHealth())),
                        Lang.p("max", Format.hearts(max != null ? max.getValue() : 20)))).build(), null);
                set(46, Items.of(Material.COOKED_BEEF).name(plugin.lang().get("spectator.inventory.food",
                        Lang.p("food", target.getFoodLevel()))).build(), null);
                set(47, Items.of(Material.EXPERIENCE_BOTTLE).name(plugin.lang().get("spectator.inventory.level",
                        Lang.p("level", target.getLevel()))).build(), null);
                List<Component> effects = new ArrayList<>();
                for (PotionEffect effect : target.getActivePotionEffects()) {
                    effects.add(Component.text("• " + pretty(effect.getType().getKey().getKey()) + " " + (effect.getAmplifier() + 1)
                            + " (" + Format.time(effect.getDuration() / 20) + ")", net.kyori.adventure.text.format.NamedTextColor.GRAY));
                }
                set(48, Items.of(Material.POTION).name(plugin.lang().get("spectator.inventory.effects", Lang.p("count", effects.size())))
                        .lore(effects).hideAll().build(), null);
                set(49, Items.of(Material.CLOCK).name(plugin.lang().get("spectator.menu.refresh")).build(), (p, c) -> refresh());
                set(53, Items.of(Material.ARROW).name(plugin.lang().get("spectator.inventory.back")).build(), (p, c) -> openPlayers(p));
            }

            private void copy(int slot, @Nullable ItemStack item) {
                if (item != null && !item.getType().isAir()) {
                    set(slot, item.clone(), null);
                }
            }
        }.open(viewer);
    }

    private static String pretty(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : key.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    // ── Brújula ──────────────────────────────────────────────

    private boolean isCompass(@Nullable ItemStack item) {
        return "players".equals(Items.tag(item, key));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isGhost(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction().isRightClick() && isCompass(event.getItem())) {
            openPlayers(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!isGhost(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        // Clic derecho sobre un jugador vivo: ver su inventario.
        if (event.getRightClicked() instanceof Player target && plugin.game().isAlive(target.getUniqueId())) {
            openInventory(event.getPlayer(), target);
        }
    }

    // ── Que no toquen nada ───────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOwnInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isGhost(player)
                && !(event.getInventory().getHolder() instanceof Menu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isGhost(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isGhost(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (isGhost(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (isGhost(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isGhost(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player player && isGhost(player)) {
            event.setCancelled(true);
        }
    }

    /** Las flechas atraviesan a los fantasmas en vez de chocar con ellos. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectile(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player player && isGhost(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && isGhost(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isGhost(player)) {
            event.setCancelled(true);
        }
    }

    /** Si cae al vacío, vuelve arriba. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (isGhost(event.getPlayer()) && to.getY() < to.getWorld().getMinHeight() - 5) {
            Location up = to.clone();
            up.setY(to.getWorld().getHighestBlockYAt(to) + 10);
            event.setTo(up);
        }
    }

    /** Los fantasmas siguen ocultos para quien entra; quien entra fantasma se oculta para los vivos. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID id : ghosts) {
            Player ghost = Bukkit.getPlayer(id);
            if (ghost != null && !ghost.equals(joined) && !isGhost(joined)) {
                joined.hidePlayer(plugin, ghost);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Se mantiene como fantasma: al volver, RulesModule lo vuelve a poner con spectate().
        ghosts.remove(event.getPlayer().getUniqueId());
        Player player = event.getPlayer();
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setSilent(false);
        player.setCanPickupItems(true);
    }
}
