package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.config.Scenario;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Escenarios de UHC (CutClean, Timber, HasteyBoys…). Solo actúan con la partida en curso.
 * Edición Legacy: los drops de los bloques se cambian al aparecer el ítem (ItemSpawnEvent), que funciona igual
 * desde 1.8.
 */
public final class ScenariosModule implements UhcModule {
    private static final Map<XMaterial, XMaterial> SMELT = new EnumMap<>(XMaterial.class);
    private static final Map<XMaterial, XMaterial> COOK = new EnumMap<>(XMaterial.class);
    private static final Set<String> HORSES = new HashSet<>(Arrays.asList("HORSE", "DONKEY", "MULE", "SKELETON_HORSE",
            "ZOMBIE_HORSE", "LLAMA", "TRADER_LLAMA", "CAMEL"));

    static {
        SMELT.put(XMaterial.IRON_ORE, XMaterial.IRON_INGOT);
        SMELT.put(XMaterial.GOLD_ORE, XMaterial.GOLD_INGOT);
        SMELT.put(XMaterial.RAW_IRON, XMaterial.IRON_INGOT);
        SMELT.put(XMaterial.RAW_GOLD, XMaterial.GOLD_INGOT);
        SMELT.put(XMaterial.RAW_COPPER, XMaterial.COPPER_INGOT);
        SMELT.put(XMaterial.ANCIENT_DEBRIS, XMaterial.NETHERITE_SCRAP);
        COOK.put(XMaterial.BEEF, XMaterial.COOKED_BEEF);
        COOK.put(XMaterial.PORKCHOP, XMaterial.COOKED_PORKCHOP);
        COOK.put(XMaterial.CHICKEN, XMaterial.COOKED_CHICKEN);
        COOK.put(XMaterial.MUTTON, XMaterial.COOKED_MUTTON);
        COOK.put(XMaterial.RABBIT, XMaterial.COOKED_RABBIT);
        COOK.put(XMaterial.COD, XMaterial.COOKED_COD);
        COOK.put(XMaterial.SALMON, XMaterial.COOKED_SALMON);
    }

    /** Bloque recién roto en este tick: sus ítems se ajustan al aparecer. */
    private static final class Broken {
        final Location where;
        final String type;
        final long tick;

        Broken(Location where, String type, long tick) {
            this.where = where;
            this.type = type;
            this.tick = tick;
        }
    }

    private final TukiUHC plugin;
    private final Map<UUID, Long> noClean = new HashMap<>();
    private final List<Broken> broken = new ArrayList<>();
    private long tick;

    public ScenariosModule(TukiUHC plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick++;
            broken.clear();
        }, 1L, 1L);
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

    private static boolean isLog(Material type) {
        String name = type.name();
        return name.equals("LOG") || name.equals("LOG_2") || (name.endsWith("_LOG") && !name.startsWith("STRIPPED"));
    }

    private static boolean isLeaves(Material type) {
        String name = type.name();
        return name.equals("LEAVES") || name.equals("LEAVES_2") || name.endsWith("_LEAVES");
    }

    private static boolean isOre(String type) {
        return type.endsWith("_ORE") || type.equals("ANCIENT_DEBRIS") || type.equals("GLOWING_REDSTONE_ORE");
    }

    // ── Minería ──────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();
        if (!playing(player) || plugin.game().state() != GameState.PLAYING) {
            return;
        }
        broken.add(new Broken(block.getLocation(), type.name(), tick));
        if (on(Scenario.BLOOD_DIAMONDS) && type.name().contains("DIAMOND_ORE")) {
            player.damage(1.0);
        }
        if (on(Scenario.LUCKY_LEAVES) && isLeaves(type)) {
            luckyApple(block);
        }
        if (on(Scenario.TIMBER) && isLog(type) && !player.isSneaking()) {
            Bukkit.getScheduler().runTask(plugin, () -> timber(block, player));
        }
    }

    private Broken brokenAt(Location location) {
        for (Broken b : broken) {
            if (b.tick == tick && b.where.getWorld().equals(location.getWorld())
                    && b.where.getBlockX() == location.getBlockX() && b.where.getBlockY() == location.getBlockY()
                    && b.where.getBlockZ() == location.getBlockZ()) {
                return b;
            }
        }
        return null;
    }

    /** Los ítems que suelta un bloque recién roto: fundir, multiplicar o quitar. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (broken.isEmpty() || plugin.game().state() != GameState.PLAYING) {
            return;
        }
        Item item = event.getEntity();
        Broken source = brokenAt(item.getLocation());
        if (source == null) {
            return;
        }
        String type = source.type;
        if ((on(Scenario.DIAMONDLESS) && type.contains("DIAMOND_ORE")) || (on(Scenario.GOLDLESS) && type.contains("GOLD_ORE"))) {
            event.setCancelled(true);
            return;
        }
        ItemStack stack = item.getItemStack();
        XMaterial material = XMaterial.matchXMaterial(stack);
        boolean smelted = false;
        if (on(Scenario.CUT_CLEAN)) {
            XMaterial result = SMELT.get(material);
            if (result == null && material == XMaterial.GRAVEL && type.equals("GRAVEL")) {
                result = XMaterial.FLINT;
            }
            if (result != null && result.isSupported()) {
                ItemStack smeltedItem = result.parseItem();
                smeltedItem.setAmount(stack.getAmount());
                stack = smeltedItem;
                smelted = true;
            }
        }
        int multiplier = on(Scenario.TRIPLE_ORES) ? 3 : on(Scenario.DOUBLE_ORES) ? 2 : 1;
        if (isOre(type) && multiplier > 1) {
            stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * multiplier));
        }
        item.setItemStack(stack);
        if (smelted) {
            ExperienceOrb orb = item.getWorld().spawn(item.getLocation(), ExperienceOrb.class);
            orb.setExperience(1 + ThreadLocalRandom.current().nextInt(2));
        }
    }

    // ── Timber ───────────────────────────────────────────────

    private void timber(Block start, Player player) {
        int limit = Math.max(16, plugin.getConfig().getInt("scenarios-settings.timber-max-logs", 256));
        Deque<Block> queue = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            queue.add(start.getRelative(face));
        }
        ItemStack tool = Compat.mainHand(player);
        int count = 0;
        while (!queue.isEmpty() && count < limit) {
            Block block = queue.poll();
            if (!seen.add(block) || !isLog(block.getType())) {
                continue;
            }
            block.breakNaturally(tool);
            count++;
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

    // ── Comida cocinada (CutClean) y Goldless ────────────────

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (!on(Scenario.CUT_CLEAN) || event.getEntity() instanceof Player) {
            return;
        }
        ListIterator<ItemStack> it = event.getDrops().listIterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            XMaterial cooked = COOK.get(XMaterial.matchXMaterial(drop));
            if (cooked != null && cooked.isSupported()) {
                ItemStack result = cooked.parseItem();
                result.setAmount(drop.getAmount());
                it.set(result);
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

    private static boolean isTool(Material type) {
        String name = type.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SPADE") || name.endsWith("_SHOVEL");
    }

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
        if (on(Scenario.HASTEY_BOYS) && isTool(type)) {
            ItemStack hastey = result.clone();
            ItemMeta meta = hastey.getItemMeta();
            Enchantment efficiency = XEnchantment.EFFICIENCY.get();
            Enchantment unbreaking = XEnchantment.UNBREAKING.get();
            if (meta != null && efficiency != null && unbreaking != null) {
                meta.addEnchant(efficiency, 3, true);
                meta.addEnchant(unbreaking, 3, true);
                hastey.setItemMeta(meta);
                event.getInventory().setResult(hastey);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (on(Scenario.BOWLESS) && event.getEntity() instanceof Player && event.getBow() != null
                && event.getBow().getType() == Material.BOW) {
            event.setCancelled(true);
            plugin.lang().send((Player) event.getEntity(), "scenarios.blocked",
                    Lang.c("scenario", plugin.lang().get("scenarios.bowless.name")));
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
        if (on(Scenario.HORSELESS) && HORSES.contains(event.getVehicle().getType().name()) && event.getEntered() instanceof Player) {
            event.setCancelled(true);
            plugin.lang().send((Player) event.getEntered(), "scenarios.blocked",
                    Lang.c("scenario", plugin.lang().get("scenarios.horseless.name")));
        }
    }

    // ── Daño: Fireless, NoFall, NoClean ──────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || !playing((Player) event.getEntity())) {
            return;
        }
        Player player = (Player) event.getEntity();
        String cause = event.getCause().name();
        if (on(Scenario.FIRELESS) && (cause.equals("FIRE") || cause.equals("FIRE_TICK") || cause.equals("LAVA")
                || cause.equals("HOT_FLOOR") || cause.equals("CAMPFIRE"))) {
            event.setCancelled(true);
            player.setFireTicks(0);
        } else if (on(Scenario.NO_FALL) && cause.equals("FALL")) {
            event.setCancelled(true);
        }
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
            return (Player) ((Projectile) event.getDamager()).getShooter();
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!on(Scenario.NO_CLEAN) || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
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
    public void onEliminate(UUID player, Player killer) {
        if (killer != null && on(Scenario.NO_CLEAN) && !killer.getUniqueId().equals(player)) {
            int seconds = Math.max(1, plugin.getConfig().getInt("scenarios-settings.no-clean-seconds", 20));
            noClean.put(killer.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
            plugin.lang().send(killer, "scenarios.no-clean-start", Lang.p("seconds", seconds));
        }
    }
}
