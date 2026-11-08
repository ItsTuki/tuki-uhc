package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Scenario;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;

/** Escenarios de UHC (CutClean, Timber, HasteyBoys…). Solo actúan con la partida en curso. */
public final class ScenariosModule implements UhcModule {
    private static final Map<Material, Material> SMELT = new EnumMap<>(Material.class);
    private static final Map<Material, Material> COOK = new EnumMap<>(Material.class);
    private static final Set<Material> ORES = new HashSet<>();

    static {
        SMELT.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        COOK.put(Material.BEEF, Material.COOKED_BEEF);
        COOK.put(Material.PORKCHOP, Material.COOKED_PORKCHOP);
        COOK.put(Material.CHICKEN, Material.COOKED_CHICKEN);
        COOK.put(Material.MUTTON, Material.COOKED_MUTTON);
        COOK.put(Material.RABBIT, Material.COOKED_RABBIT);
        COOK.put(Material.COD, Material.COOKED_COD);
        COOK.put(Material.SALMON, Material.COOKED_SALMON);
        for (Material material : Material.values()) {
            if (material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS) {
                ORES.add(material);
            }
        }
    }

    private final TukiUHC plugin;
    /** NoClean: quién es invulnerable y hasta cuándo (ms). */
    private final Map<UUID, Long> noClean = new HashMap<>();

    public ScenariosModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private boolean on(Scenario scenario) {
        return plugin.settings().scenario(scenario) && plugin.game().state() == GameState.PLAYING;
    }

    private boolean playing(Player player) {
        return plugin.game().isAlive(player.getUniqueId());
    }

    @Override
    public void onStart() {
        Set<Scenario> active = plugin.settings().scenarios();
        if (active.isEmpty()) {
            return;
        }
        List<Component> names = new ArrayList<>();
        for (Scenario scenario : active) {
            names.add(plugin.lang().get("scenarios." + scenario.key() + ".name"));
        }
        plugin.lang().broadcast("scenarios.active", Lang.c("list", Component.join(JoinConfiguration.commas(true), names)));
    }

    @Override
    public void onReset() {
        noClean.clear();
    }

    // ── Minería: CutClean, Doble/Triple, Diamondless, Goldless, BloodDiamonds ──

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        if (plugin.game().state() != GameState.PLAYING) {
            return;
        }
        Material type = event.getBlockState().getType();
        boolean ore = ORES.contains(type);
        if ((on(Scenario.DIAMONDLESS) && (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE))
                || (on(Scenario.GOLDLESS) && (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE
                        || type == Material.NETHER_GOLD_ORE))) {
            event.getItems().clear();
            return;
        }
        int multiplier = on(Scenario.TRIPLE_ORES) ? 3 : on(Scenario.DOUBLE_ORES) ? 2 : 1;
        boolean smelted = false;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (on(Scenario.CUT_CLEAN)) {
                Material result = SMELT.get(stack.getType());
                if (result == null && stack.getType() == Material.GRAVEL && type == Material.GRAVEL) {
                    result = Material.FLINT;
                }
                if (result != null) {
                    stack = stack.withType(result);
                    smelted = true;
                }
            }
            if (ore && multiplier > 1) {
                stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * multiplier));
            }
            item.setItemStack(stack);
        }
        if (smelted) {
            // La experiencia que daría el horno.
            Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
            at.getWorld().spawn(at, ExperienceOrb.class, orb -> orb.setExperience(1 + ThreadLocalRandom.current().nextInt(2)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();
        if (!playing(player)) {
            return;
        }
        if (on(Scenario.BLOOD_DIAMONDS) && (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE)) {
            player.damage(1.0);
        }
        if (on(Scenario.LUCKY_LEAVES) && Tag.LEAVES.isTagged(type)) {
            luckyApple(block);
        }
        if (on(Scenario.TIMBER) && Tag.LOGS.isTagged(type) && !player.isSneaking()) {
            Bukkit.getScheduler().runTask(plugin, () -> timber(block, player));
        }
    }

    // ── Timber ───────────────────────────────────────────────

    /** Tala el resto del árbol: troncos conectados hacia arriba y a los lados. */
    private void timber(Block start, Player player) {
        int limit = Math.max(16, plugin.getConfig().getInt("scenarios-settings.timber-max-logs", 256));
        Deque<Block> queue = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            queue.add(start.getRelative(face));
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        int broken = 0;
        while (!queue.isEmpty() && broken < limit) {
            Block block = queue.poll();
            if (!seen.add(block) || !Tag.LOGS.isTagged(block.getType())) {
                continue;
            }
            block.breakNaturally(tool);
            broken++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            queue.add(block.getRelative(dx, dy, dz));
                        }
                    }
                }
            }
        }
    }

    // ── LuckyLeaves ──────────────────────────────────────────

    private void luckyApple(Block block) {
        double chance = plugin.getConfig().getDouble("scenarios-settings.lucky-leaves-percent", 0.5) / 100.0;
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.GOLDEN_APPLE));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (on(Scenario.LUCKY_LEAVES)) {
            luckyApple(event.getBlock());
        }
    }

    // ── Comida cocinada (CutClean) ───────────────────────────

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (!on(Scenario.CUT_CLEAN) || event.getEntity() instanceof Player) {
            return;
        }
        java.util.ListIterator<ItemStack> it = event.getDrops().listIterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            Material cooked = COOK.get(drop.getType());
            if (cooked != null) {
                it.set(drop.withType(cooked));
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (on(Scenario.GOLDLESS) && playing(event.getEntity())) {
            int gold = Math.max(0, plugin.getConfig().getInt("scenarios-settings.goldless-gold-on-death", 8));
            if (gold > 0) {
                event.getDrops().add(new ItemStack(Material.GOLD_INGOT, gold));
            }
        }
    }

    // ── HasteyBoys, Bowless, Rodless ─────────────────────────

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || plugin.game().state() != GameState.PLAYING) {
            return;
        }
        Material type = result.getType();
        if ((on(Scenario.BOWLESS) && type == Material.BOW) || (on(Scenario.RODLESS) && type == Material.FISHING_ROD)) {
            event.getInventory().setResult(null);
            return;
        }
        if (on(Scenario.HASTEY_BOYS) && (Tag.ITEMS_PICKAXES.isTagged(type) || Tag.ITEMS_AXES.isTagged(type)
                || Tag.ITEMS_SHOVELS.isTagged(type))) {
            ItemStack hastey = result.clone();
            ItemMeta meta = hastey.getItemMeta();
            meta.addEnchant(Enchantment.EFFICIENCY, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            hastey.setItemMeta(meta);
            event.getInventory().setResult(hastey);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (on(Scenario.BOWLESS) && event.getEntity() instanceof Player player && event.getBow() != null
                && event.getBow().getType() == Material.BOW) {
            event.setCancelled(true);
            plugin.lang().send(player, "scenarios.blocked", Lang.c("scenario", plugin.lang().get("scenarios.bowless.name")));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (on(Scenario.RODLESS) && event.getState() == PlayerFishEvent.State.FISHING) {
            event.setCancelled(true);
            plugin.lang().send(event.getPlayer(), "scenarios.blocked", Lang.c("scenario", plugin.lang().get("scenarios.rodless.name")));
        }
    }

    // ── Horseless ────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onMount(VehicleEnterEvent event) {
        if (on(Scenario.HORSELESS) && event.getVehicle() instanceof AbstractHorse && event.getEntered() instanceof Player player) {
            event.setCancelled(true);
            plugin.lang().send(player, "scenarios.blocked", Lang.c("scenario", plugin.lang().get("scenarios.horseless.name")));
        }
    }

    // ── Daño: Fireless, NoFall, NoClean ──────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !playing(player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (on(Scenario.FIRELESS) && (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA || cause.name().equals("HOT_FLOOR") || cause.name().equals("CAMPFIRE"))) {
            event.setCancelled(true);
            player.setFireTicks(0);
        } else if (on(Scenario.NO_FALL) && cause == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    private static @Nullable Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!on(Scenario.NO_CLEAN) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long protectedUntil = noClean.get(victim.getUniqueId());
        if (protectedUntil != null && protectedUntil > now) {
            event.setCancelled(true);
            plugin.lang().send(attacker, "scenarios.no-clean-protected", Lang.p("player", victim.getName()),
                    Lang.p("seconds", (protectedUntil - now + 999) / 1000));
            return;
        }
        if (noClean.remove(attacker.getUniqueId()) != null) {
            plugin.lang().send(attacker, "scenarios.no-clean-lost");
        }
    }

    @Override
    public void onEliminate(UUID player, @Nullable Player killer) {
        if (killer != null && on(Scenario.NO_CLEAN) && !killer.getUniqueId().equals(player)) {
            int seconds = Math.max(1, plugin.getConfig().getInt("scenarios-settings.no-clean-seconds", 20));
            noClean.put(killer.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
            plugin.lang().send(killer, "scenarios.no-clean-start", Lang.p("seconds", seconds));
        }
    }
}
