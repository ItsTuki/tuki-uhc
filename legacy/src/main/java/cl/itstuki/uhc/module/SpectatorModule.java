package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.compat.ItemTag;
import cl.itstuki.uhc.compat.Text;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.gui.Menu;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Format;
import cl.itstuki.uhc.util.Items;
import com.cryptomorin.xseries.XMaterial;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.potion.PotionEffect;

/**
 * Espectadores "fantasma": los eliminados (y quien entra a mirar) vuelan, son invisibles para los vivos, no
 * pueden tocar nada y tienen una brújula con la lista de jugadores vivos: clic izquierdo para ir con uno,
 * clic derecho para ver su inventario sin poder cambiarlo. Con spectators.ghost: false se usa el modo
 * espectador normal del juego (1.8+).
 */
public final class SpectatorModule implements UhcModule {
    private static final String COMPASS = "spec_players";
    private final TukiUHC plugin;
    private final Set<UUID> ghosts = new HashSet<>();

    public SpectatorModule(TukiUHC plugin) {
        this.plugin = plugin;
        registerPickup();
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
        player.getInventory().setArmorContents(null);
        Compat.clearEffects(player);
        player.setHealth(Math.min(player.getMaxHealth(), Compat.maxHealth(player)));
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCanPickupItems(false);
        Compat.ghostFlags(player, true);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player) && !isGhost(other)) {
                Compat.hide(other, plugin, player);
            }
        }
        player.getInventory().setItem(4, Items.of(XMaterial.COMPASS).name(plugin.lang().item("spectator.compass.name"))
                .lore(plugin.lang().itemList("spectator.compass.lore")).tag(COMPASS).build());
        plugin.lang().send(player, "spectator.info");
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
        player.setCanPickupItems(true);
        Compat.ghostFlags(player, false);
        for (Player other : Bukkit.getOnlinePlayers()) {
            Compat.show(other, plugin, player);
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

    public void openPlayers(final Player viewer) {
        new Menu(6, plugin.lang().get("spectator.menu.title")) {
            @Override
            protected void render() {
                int slot = 0;
                for (final UUID id : plugin.game().alive()) {
                    Player target = Bukkit.getPlayer(id);
                    if (target == null || slot >= 45) {
                        continue;
                    }
                    Party team = plugin.teams().team(id);
                    String distance = target.getWorld().equals(viewer.getWorld())
                            ? String.valueOf((int) target.getLocation().distance(viewer.getLocation())) : "?";
                    set(slot++, Items.of(XMaterial.PLAYER_HEAD).owner(target.getName())
                            .name((team != null ? Text.code(team.color()) : "§f") + target.getName())
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
                    set(22, Items.of(XMaterial.BARRIER).name(plugin.lang().item("spectator.menu.empty")).build(), null);
                }
                set(49, Items.of(XMaterial.CLOCK).name(plugin.lang().item("spectator.menu.refresh")).build(), (p, c) -> refresh());
            }
        }.open(viewer);
    }

    /** Inventario de un jugador, solo para mirar (es una copia; los clics no hacen nada). */
    public void openInventory(Player viewer, final Player target) {
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
                copy(40, Compat.offHand(target));
                set(45, Items.of(Compat.firstItem(XMaterial.RED_DYE, XMaterial.REDSTONE)).name(plugin.lang().item("spectator.inventory.health",
                        Lang.p("hearts", Format.hearts(target.getHealth())),
                        Lang.p("max", Format.hearts(Compat.maxHealth(target))))).build(), null);
                set(46, Items.of(XMaterial.COOKED_BEEF).name(plugin.lang().item("spectator.inventory.food",
                        Lang.p("food", target.getFoodLevel()))).build(), null);
                set(47, Items.of(XMaterial.EXPERIENCE_BOTTLE).name(plugin.lang().item("spectator.inventory.level",
                        Lang.p("level", target.getLevel()))).build(), null);
                List<String> effects = new ArrayList<>();
                for (PotionEffect effect : target.getActivePotionEffects()) {
                    effects.add("§7• " + pretty(effect.getType().getName()) + " " + (effect.getAmplifier() + 1)
                            + " (" + Format.time(effect.getDuration() / 20) + ")");
                }
                set(48, Items.of(XMaterial.POTION).name(plugin.lang().item("spectator.inventory.effects", Lang.p("count", effects.size())))
                        .lore(effects).hideAll().build(), null);
                set(49, Items.of(XMaterial.CLOCK).name(plugin.lang().item("spectator.menu.refresh")).build(), (p, c) -> refresh());
                set(53, Items.of(XMaterial.ARROW).name(plugin.lang().item("spectator.inventory.back")).build(), (p, c) -> openPlayers(p));
            }

            private void copy(int slot, ItemStack item) {
                if (item != null && !Compat.isAir(item.getType())) {
                    set(slot, item.clone(), null);
                }
            }
        }.open(viewer);
    }

    private static String pretty(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : key.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (!word.isEmpty()) {
                out.append(out.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    // ── Brújula ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isGhost(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction().name().startsWith("RIGHT") && !Compat.isOffHand(event)
                && COMPASS.equals(ItemTag.read(event.getItem()))) {
            openPlayers(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!isGhost(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        if (!Compat.isOffHand(event) && event.getRightClicked() instanceof Player
                && plugin.game().isAlive(event.getRightClicked().getUniqueId())) {
            openInventory(event.getPlayer(), (Player) event.getRightClicked());
        }
    }

    // ── Que no toquen nada ───────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOwnInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && isGhost((Player) event.getWhoClicked())
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

    /** Recoger ítems: EntityPickupItemEvent desde 1.12, PlayerPickupItemEvent antes. */
    @SuppressWarnings("unchecked")
    private void registerPickup() {
        Class<? extends Event> type = null;
        for (String name : new String[] {"org.bukkit.event.entity.EntityPickupItemEvent", "org.bukkit.event.player.PlayerPickupItemEvent"}) {
            try {
                type = (Class<? extends Event>) Class.forName(name);
                break;
            } catch (ClassNotFoundException ignored) {
                // Se prueba el siguiente.
            }
        }
        if (type == null) {
            return;
        }
        EventExecutor executor = (listener, event) -> {
            Object entity = event instanceof EntityEvent ? ((EntityEvent) event).getEntity()
                    : event instanceof PlayerEvent ? ((PlayerEvent) event).getPlayer() : null;
            if (entity instanceof Player && isGhost((Player) entity) && event instanceof Cancellable) {
                ((Cancellable) event).setCancelled(true);
            }
        };
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.LOWEST, executor, plugin, false);
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
        if (event.getEntity() instanceof Player && isGhost((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Entity) {
            damager = (Entity) ((Projectile) damager).getShooter();
        }
        if (damager instanceof Player && isGhost((Player) damager)) {
            event.setCancelled(true);
        }
    }

    private static final Method HIT_ENTITY = hitEntityMethod();

    private static Method hitEntityMethod() {
        try {
            return ProjectileHitEvent.class.getMethod("getHitEntity");
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /** Versiones donde se puede: las flechas atraviesan a los fantasmas en vez de chocar con ellos. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectile(ProjectileHitEvent event) {
        if (HIT_ENTITY == null || !(event instanceof Cancellable)) {
            return;
        }
        try {
            Object hit = HIT_ENTITY.invoke(event);
            if (hit instanceof Player && isGhost((Player) hit)) {
                ((Cancellable) event).setCancelled(true);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Sin datos del impacto.
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player && isGhost((Player) event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && isGhost((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to != null && to.getY() < 0 && isGhost(event.getPlayer()) && to.getY() < Compat.minHeight(to.getWorld()) - 5) {
            Location up = to.clone();
            up.setY(to.getWorld().getHighestBlockYAt(to) + 10);
            event.setTo(up);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID id : ghosts) {
            Player ghost = Bukkit.getPlayer(id);
            if (ghost != null && !ghost.equals(joined) && !isGhost(joined)) {
                Compat.hide(joined, plugin, ghost);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ghosts.remove(event.getPlayer().getUniqueId());
        event.getPlayer().setCanPickupItems(true);
        Compat.ghostFlags(event.getPlayer(), false);
    }
}
