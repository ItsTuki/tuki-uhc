package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Scenario;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Items;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Escenarios de acción y "más": TimeBomb, AirDrops, Radar, Rush, RedVsBlue, FinalUHC, Pyro, Superheroes,
 * Anonymous, WebCage, SafeLoot y Siphon.
 */
public final class ScenarioActionsModule implements UhcModule {
    /** Poderes de Superheroes. */
    public enum Power { SPEED, STRENGTH, RESISTANCE, JUMP, HEALTH, HASTE }

    private static boolean anonymous;

    private final TukiUHC plugin;
    private final NamespacedKey radarKey;
    private final Map<UUID, Power> powers = new HashMap<>();
    /** SafeLoot: bloque del cofre → equipo (o jugador) dueño y hasta cuándo (ms). */
    private final Map<Location, Lock> locks = new HashMap<>();
    private final List<BukkitTask> bombs = new ArrayList<>();
    private boolean finalHealDone;
    private @Nullable BukkitTask ticker;

    private record Lock(UUID killer, long until) {
    }

    public ScenarioActionsModule(TukiUHC plugin) {
        this.plugin = plugin;
        this.radarKey = plugin.key("radar");
    }

    /** Anonymous activo: los nombres se ocultan como en la rotación de skins (lo lee el scoreboard y el chat). */
    public static boolean anonymous() {
        return anonymous;
    }

    private boolean on(Scenario scenario) {
        return plugin.settings().scenario(scenario) && plugin.game().state() == GameState.PLAYING;
    }

    // ── Ciclo de la partida ──────────────────────────────────

    @Override
    public void onStart() {
        finalHealDone = false;
        anonymous = plugin.settings().scenario(Scenario.ANONYMOUS);
        powers.clear();
        if (plugin.settings().scenario(Scenario.SUPERHEROES)) {
            Power[] all = Power.values();
            for (UUID id : plugin.game().alive()) {
                Power power = all[ThreadLocalRandom.current().nextInt(all.length)];
                powers.put(id, power);
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    plugin.lang().send(player, "scenarios.superhero", Lang.c("power", plugin.lang().get("scenarios.powers." + power.name().toLowerCase())));
                }
            }
        }
        if (plugin.settings().scenario(Scenario.RADAR)) {
            for (UUID id : plugin.game().alive()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.getInventory().addItem(Items.of(Material.COMPASS).name(plugin.lang().get("scenarios.radar-item"))
                            .tag(radarKey, "1").build());
                }
            }
        }
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void stop() {
        anonymous = false;
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (BukkitTask bomb : bombs) {
            bomb.cancel();
        }
        bombs.clear();
        locks.clear();
        powers.clear();
    }

    @Override
    public void onEnd() {
        stop();
    }

    @Override
    public void onReset() {
        stop();
    }

    /** Cada segundo: efectos permanentes (Rush, Pyro, Superheroes), radar, FinalUHC y AirDrops. */
    private void tick() {
        if (plugin.game().state() != GameState.PLAYING) {
            return;
        }
        long second = plugin.game().totalSeconds();
        for (UUID id : plugin.game().alive()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (second % 5 == 0) {
                if (on(Scenario.RUSH)) {
                    keep(player, PotionEffectType.SPEED, 0);
                    keep(player, PotionEffectType.HASTE, 1);
                }
                if (on(Scenario.PYRO)) {
                    keep(player, PotionEffectType.FIRE_RESISTANCE, 0);
                }
                Power power = powers.get(id);
                if (power != null && on(Scenario.SUPERHEROES)) {
                    switch (power) {
                        case SPEED -> keep(player, PotionEffectType.SPEED, 1);
                        case STRENGTH -> keep(player, PotionEffectType.STRENGTH, 0);
                        case RESISTANCE -> keep(player, PotionEffectType.RESISTANCE, 0);
                        case JUMP -> keep(player, PotionEffectType.JUMP_BOOST, 2);
                        case HEALTH -> keep(player, PotionEffectType.HEALTH_BOOST, 4);
                        case HASTE -> keep(player, PotionEffectType.HASTE, 2);
                    }
                }
            }
            if (on(Scenario.RADAR)) {
                radar(player);
            }
        }
        if (on(Scenario.FINAL_UHC) && !finalHealDone && plugin.game().pvpAllowed()) {
            finalHealDone = true;
            for (UUID id : plugin.game().alive()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    ItemsModule.heal(player, 1000);
                    player.setFoodLevel(20);
                    player.setSaturation(20);
                }
            }
            plugin.lang().broadcast("scenarios.final-heal");
        }
        int interval = Math.max(1, plugin.getConfig().getInt("scenarios-settings.air-drop-minutes", 10)) * 60;
        if (on(Scenario.AIR_DROPS) && second > 0 && second % interval == 0) {
            airDrop();
        }
    }

    /** Efecto "permanente": se renueva antes de que se acabe (y no pisa uno más fuerte). */
    private static void keep(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current == null || current.getAmplifier() < amplifier
                || (current.getAmplifier() == amplifier && current.getDuration() < 200 && !current.isInfinite())) {
            player.addPotionEffect(new PotionEffect(type, 400, amplifier, true, false, true));
        }
    }

    // ── Radar ────────────────────────────────────────────────

    private void radar(Player player) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID id : plugin.game().alive()) {
            Player other = Bukkit.getPlayer(id);
            if (other == null || other.equals(player) || !other.getWorld().equals(player.getWorld())
                    || plugin.teams().sameTeam(player.getUniqueId(), id)) {
                continue;
            }
            double distance = other.getLocation().distanceSquared(player.getLocation());
            if (distance < best) {
                best = distance;
                nearest = other;
            }
        }
        if (nearest == null) {
            return;
        }
        player.setCompassTarget(nearest.getLocation());
        ItemStack hand = player.getInventory().getItemInMainHand();
        if ("1".equals(Items.tag(hand, radarKey))) {
            player.sendActionBar(plugin.lang().get("scenarios.radar-bar", Lang.p("distance", (int) Math.sqrt(best))));
        }
    }

    // ── AirDrops ─────────────────────────────────────────────

    private void airDrop() {
        Location spot = plugin.game().reviveSpot();
        Block block = spot.getBlock();
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            List<ItemStack> loot = new ArrayList<>(List.of(new ItemStack(Material.GOLDEN_APPLE, 2), new ItemStack(Material.DIAMOND, 3),
                    new ItemStack(Material.ARROW, 16), new ItemStack(Material.IRON_INGOT, 8), new ItemStack(Material.EXPERIENCE_BOTTLE, 8),
                    new ItemStack(Material.GOLD_INGOT, 6), randomBook(), new ItemStack(Material.ENDER_PEARL, 2)));
            Collections.shuffle(loot);
            for (int i = 0; i < 5; i++) {
                chest.getBlockInventory().setItem(ThreadLocalRandom.current().nextInt(27), loot.get(i));
            }
        }
        block.getWorld().spawnParticle(Particle.FIREWORK, block.getLocation().add(0.5, 1, 0.5), 60, 0.5, 2, 0.5, 0.05);
        plugin.lang().broadcast("scenarios.air-drop", Lang.p("x", block.getX()), Lang.p("y", block.getY()), Lang.p("z", block.getZ()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        }
    }

    static ItemStack randomBook() {
        List<Enchantment> all = new ArrayList<>();
        io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                .forEach(all::add);
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (!all.isEmpty()) {
            Enchantment enchantment = all.get(ThreadLocalRandom.current().nextInt(all.size()));
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(enchantment, 1 + ThreadLocalRandom.current().nextInt(enchantment.getMaxLevel()), true);
            book.setItemMeta(meta);
        }
        return book;
    }

    // ── Muerte: TimeBomb y SafeLoot (los llama RulesModule con los cofres) ──

    /** El cofre de muerte quedó listo en {@code chests}. */
    public void onDeathChest(List<Block> chests, String victim, @Nullable Player killer) {
        if (chests.isEmpty()) {
            return;
        }
        if (on(Scenario.SAFE_LOOT) && killer != null) {
            long until = System.currentTimeMillis() + Math.max(5, plugin.getConfig().getInt("scenarios-settings.safe-loot-seconds", 60)) * 1000L;
            for (Block block : chests) {
                locks.put(block.getLocation(), new Lock(killer.getUniqueId(), until));
            }
        }
        if (on(Scenario.TIME_BOMB)) {
            timeBomb(chests.get(0).getLocation().add(0.5, 0, 0.5), victim);
        }
    }

    private void timeBomb(Location where, String victim) {
        int seconds = Math.max(5, plugin.getConfig().getInt("scenarios-settings.time-bomb-seconds", 30));
        ArmorStand label = where.getWorld().spawn(where.clone().add(0, 1.6, 0), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setPersistent(false);
        });
        plugin.lang().broadcast("scenarios.time-bomb-start", Lang.p("player", victim), Lang.p("seconds", seconds));
        int[] left = {seconds};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (left[0] <= 0) {
                task[0].cancel();
                bombs.remove(task[0]);
                label.remove();
                float power = (float) plugin.getConfig().getDouble("scenarios-settings.time-bomb-power", 4);
                where.getWorld().createExplosion(where, power, false, true);
                where.getWorld().strikeLightningEffect(where);
                return;
            }
            label.customName(plugin.lang().get("scenarios.time-bomb-label", Lang.p("player", victim), Lang.p("seconds", left[0])));
            if (left[0] <= 5) {
                where.getWorld().playSound(where, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
            }
            left[0]--;
        }, 0L, 20L);
        bombs.add(task[0]);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (locks.isEmpty() || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        List<Location> spots = new ArrayList<>();
        if (holder instanceof DoubleChest doubleChest) {
            if (doubleChest.getLeftSide() instanceof BlockState left) {
                spots.add(left.getLocation());
            }
            if (doubleChest.getRightSide() instanceof BlockState right) {
                spots.add(right.getLocation());
            }
        } else if (holder instanceof BlockState state) {
            spots.add(state.getLocation());
        }
        long now = System.currentTimeMillis();
        for (Location spot : spots) {
            Lock lock = locks.get(spot);
            if (lock == null) {
                continue;
            }
            if (lock.until() < now) {
                locks.remove(spot);
                continue;
            }
            boolean allowed = lock.killer().equals(player.getUniqueId()) || plugin.teams().sameTeam(lock.killer(), player.getUniqueId());
            if (!allowed) {
                event.setCancelled(true);
                plugin.lang().send(player, "scenarios.safe-loot-locked", Lang.p("seconds", (lock.until() - now + 999) / 1000));
                return;
            }
        }
    }

    // ── Kills: WebCage y Siphon ──────────────────────────────

    @Override
    public void onEliminate(UUID player, @Nullable Player killer) {
        powers.remove(player);
        if (killer == null || killer.getUniqueId().equals(player) || plugin.game().state() != GameState.PLAYING) {
            return;
        }
        if (on(Scenario.WEB_CAGE)) {
            Location center = killer.getLocation();
            int radius = 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        Block block = center.getBlock().getRelative(dx, dy, dz);
                        if (distance > radius - 0.8 && distance <= radius + 0.3 && block.getType().isAir()) {
                            block.setType(Material.COBWEB);
                        }
                    }
                }
            }
        }
        if (on(Scenario.SIPHON)) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 0));
            killer.giveExpLevels(3);
            plugin.lang().send(killer, "scenarios.siphon-kill");
        }
    }

    /** Superheroes: el poder de salto también quita el daño de caída. */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && powers.get(player.getUniqueId()) == Power.JUMP && on(Scenario.SUPERHEROES)) {
            event.setCancelled(true);
        }
    }

    // ── RedVsBlue ────────────────────────────────────────────

    /** Arma dos equipos, Rojo y Azul, con los participantes mezclados (lo llama GameManager al preparar). */
    public static void redVsBlue(TukiUHC plugin, java.util.Collection<UUID> participants) {
        plugin.teams().clearTeams();
        Party red = plugin.teams().create(Party.Kind.TEAM, plugin.lang().raw("scenarios.red-team"));
        Party blue = plugin.teams().create(Party.Kind.TEAM, plugin.lang().raw("scenarios.blue-team"));
        List<UUID> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) {
            plugin.teams().add(i % 2 == 0 ? red : blue, shuffled.get(i));
        }
        plugin.lang().broadcast("scenarios.red-vs-blue-teams", Lang.p("red_count", (shuffled.size() + 1) / 2), Lang.p("blue_count", shuffled.size() / 2));
    }
}
