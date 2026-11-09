package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.compat.ItemTag;
import cl.itstuki.uhc.compat.Text;
import cl.itstuki.uhc.config.Scenario;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Items;
import cl.itstuki.uhc.util.Show;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

/**
 * Escenarios de acción y "más": TimeBomb, AirDrops, Radar, Rush, RedVsBlue, FinalUHC, Pyro, Superheroes,
 * Anonymous, WebCage, SafeLoot y Siphon.
 */
public final class ScenarioActionsModule implements UhcModule {
    /** Poderes de Superheroes. */
    public enum Power { SPEED, STRENGTH, RESISTANCE, JUMP, HEALTH, HASTE }

    private static final String RADAR = "uhc_radar";
    private static boolean anonymous;

    private final TukiUHC plugin;
    private final Map<UUID, Power> powers = new HashMap<>();
    private final Map<Location, Lock> locks = new HashMap<>();
    private final List<BukkitTask> bombs = new ArrayList<>();
    private boolean finalHealDone;
    private BukkitTask ticker;

    private static final class Lock {
        final UUID killer;
        final long until;

        Lock(UUID killer, long until) {
            this.killer = killer;
            this.until = until;
        }
    }

    public ScenarioActionsModule(TukiUHC plugin) {
        this.plugin = plugin;
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
                    plugin.lang().send(player, "scenarios.superhero",
                            Lang.c("power", plugin.lang().get("scenarios.powers." + power.name().toLowerCase(java.util.Locale.ROOT))));
                }
            }
        }
        if (plugin.settings().scenario(Scenario.RADAR)) {
            for (UUID id : plugin.game().alive()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.getInventory().addItem(Items.of(XMaterial.COMPASS).name(plugin.lang().item("scenarios.radar-item"))
                            .tag(RADAR).build());
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
                    keep(player, XPotion.SPEED, 0);
                    keep(player, XPotion.HASTE, 1);
                }
                if (on(Scenario.PYRO)) {
                    keep(player, XPotion.FIRE_RESISTANCE, 0);
                }
                Power power = powers.get(id);
                if (power != null && on(Scenario.SUPERHEROES)) {
                    switch (power) {
                        case SPEED:
                            keep(player, XPotion.SPEED, 1);
                            break;
                        case STRENGTH:
                            keep(player, XPotion.STRENGTH, 0);
                            break;
                        case RESISTANCE:
                            keep(player, XPotion.RESISTANCE, 0);
                            break;
                        case JUMP:
                            keep(player, XPotion.JUMP_BOOST, 2);
                            break;
                        case HEALTH:
                            keep(player, XPotion.HEALTH_BOOST, 4);
                            break;
                        default:
                            keep(player, XPotion.HASTE, 2);
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
                    Compat.heal(player, 1000);
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
    private static void keep(Player player, XPotion potion, int amplifier) {
        if (potion.getPotionEffectType() == null) {
            return;
        }
        PotionEffect current = null;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(potion.getPotionEffectType())) {
                current = effect;
            }
        }
        if (current == null || current.getAmplifier() < amplifier || (current.getAmplifier() == amplifier && current.getDuration() < 200)) {
            Compat.effect(player, potion, 400, amplifier, false);
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
        if (RADAR.equals(ItemTag.read(Compat.mainHand(player)))) {
            Show.actionBar(player, plugin.lang().get("scenarios.radar-bar", Lang.p("distance", (int) Math.sqrt(best))));
        }
    }

    // ── AirDrops ─────────────────────────────────────────────

    private void airDrop() {
        Location spot = plugin.game().reviveSpot();
        Block block = spot.getBlock();
        block.setType(Material.CHEST);
        BlockState state = block.getState();
        if (state instanceof Chest) {
            List<ItemStack> loot = new ArrayList<>(Arrays.asList(new ItemStack(Material.GOLDEN_APPLE, 2), new ItemStack(Material.DIAMOND, 3),
                    new ItemStack(Material.ARROW, 16), new ItemStack(Material.IRON_INGOT, 8), Compat.item(XMaterial.EXPERIENCE_BOTTLE),
                    new ItemStack(Material.GOLD_INGOT, 6), ScenariosModule.randomBook(), new ItemStack(Material.ENDER_PEARL, 2)));
            loot.get(4).setAmount(8);
            Collections.shuffle(loot);
            for (int i = 0; i < 5; i++) {
                ((Chest) state).getBlockInventory().setItem(ThreadLocalRandom.current().nextInt(27), loot.get(i));
            }
        }
        plugin.lang().broadcast("scenarios.air-drop", Lang.p("x", block.getX()), Lang.p("y", block.getY()), Lang.p("z", block.getZ()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            Compat.sound(player, XSound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        }
    }

    // ── Muerte: TimeBomb y SafeLoot (los llama RulesModule con los cofres) ──

    public void onDeathChest(List<Block> chests, String victim, Player killer) {
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

    private void timeBomb(final Location where, final String victim) {
        int seconds = Math.max(5, plugin.getConfig().getInt("scenarios-settings.time-bomb-seconds", 30));
        final ArmorStand label = where.getWorld().spawn(where.clone().add(0, 1.6, 0), ArmorStand.class);
        label.setVisible(false);
        label.setGravity(false);
        label.setMarker(true);
        label.setCustomNameVisible(true);
        plugin.lang().broadcast("scenarios.time-bomb-start", Lang.p("player", victim), Lang.p("seconds", seconds));
        final int[] left = {seconds};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (left[0] <= 0) {
                task[0].cancel();
                bombs.remove(task[0]);
                label.remove();
                float power = (float) plugin.getConfig().getDouble("scenarios-settings.time-bomb-power", 4);
                where.getWorld().createExplosion(where.getX(), where.getY(), where.getZ(), power, false, true);
                where.getWorld().strikeLightningEffect(where);
                return;
            }
            label.setCustomName(Text.legacy(plugin.lang().get("scenarios.time-bomb-label", Lang.p("player", victim), Lang.p("seconds", left[0]))));
            if (left[0] <= 5) {
                Compat.sound(where, XSound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
            }
            left[0]--;
        }, 0L, 20L);
        bombs.add(task[0]);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (locks.isEmpty() || !(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        InventoryHolder holder = event.getInventory().getHolder();
        List<Location> spots = new ArrayList<>();
        if (holder instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) holder;
            if (doubleChest.getLeftSide() instanceof BlockState) {
                spots.add(((BlockState) doubleChest.getLeftSide()).getLocation());
            }
            if (doubleChest.getRightSide() instanceof BlockState) {
                spots.add(((BlockState) doubleChest.getRightSide()).getLocation());
            }
        } else if (holder instanceof BlockState) {
            spots.add(((BlockState) holder).getLocation());
        }
        long now = System.currentTimeMillis();
        for (Location spot : spots) {
            Lock lock = locks.get(spot);
            if (lock == null) {
                continue;
            }
            if (lock.until < now) {
                locks.remove(spot);
                continue;
            }
            boolean allowed = lock.killer.equals(player.getUniqueId()) || plugin.teams().sameTeam(lock.killer, player.getUniqueId());
            if (!allowed) {
                event.setCancelled(true);
                plugin.lang().send(player, "scenarios.safe-loot-locked", Lang.p("seconds", (lock.until - now + 999) / 1000));
                return;
            }
        }
    }

    // ── Kills: WebCage y Siphon ──────────────────────────────

    @Override
    public void onEliminate(UUID player, Player killer) {
        powers.remove(player);
        if (killer == null || killer.getUniqueId().equals(player) || plugin.game().state() != GameState.PLAYING) {
            return;
        }
        if (on(Scenario.WEB_CAGE)) {
            Material web = Compat.mat(XMaterial.COBWEB);
            Location center = killer.getLocation();
            int radius = 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        Block block = center.getBlock().getRelative(dx, dy, dz);
                        if (distance > radius - 0.8 && distance <= radius + 0.3 && Compat.isAir(block.getType())) {
                            block.setType(web);
                        }
                    }
                }
            }
        }
        if (on(Scenario.SIPHON)) {
            Compat.effect(killer, XPotion.REGENERATION, 160, 1, true);
            Compat.effect(killer, XPotion.ABSORPTION, 1200, 0, true);
            killer.giveExpLevels(3);
            plugin.lang().send(killer, "scenarios.siphon-kill");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player
                && powers.get(event.getEntity().getUniqueId()) == Power.JUMP && on(Scenario.SUPERHEROES)) {
            event.setCancelled(true);
        }
    }

    // ── RedVsBlue ────────────────────────────────────────────

    public static void redVsBlue(TukiUHC plugin, Collection<UUID> participants) {
        plugin.teams().clearTeams();
        Party red = plugin.teams().create(Party.Kind.TEAM, plugin.lang().raw("scenarios.red-team"));
        Party blue = plugin.teams().create(Party.Kind.TEAM, plugin.lang().raw("scenarios.blue-team"));
        List<UUID> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) {
            plugin.teams().add(i % 2 == 0 ? red : blue, shuffled.get(i));
        }
        plugin.lang().broadcast("scenarios.red-vs-blue-teams", Lang.p("red_count", (shuffled.size() + 1) / 2),
                Lang.p("blue_count", shuffled.size() / 2));
    }
}
