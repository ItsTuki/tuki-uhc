package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jspecify.annotations.Nullable;

/**
 * Trolls y Ratas: en cada equipo hay un Troll (busca a la Rata, no puede dañar a su equipo),
 * una Rata (elimina a sus compañeros solo de forma indirecta) y Normales (pueden ejecutar a sospechosos).
 */
public final class TrollsModule implements UhcModule {
    public enum Role { TROLL, RATA, NORMAL }

    private static final int ROLES_EPISODE = 2;
    private static final double TRAP_DISTANCE = 6;
    private final TukiUHC plugin;
    private static final long TORCH_COOLDOWN = 5 * 60 * 1000L;
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Set<UUID> markedNormals = new HashSet<>();
    private final Map<UUID, Long> torchUsed = new HashMap<>();
    private final org.bukkit.NamespacedKey torchKey;
    private boolean ratsGrouped;

    public TrollsModule(TukiUHC plugin) {
        this.plugin = plugin;
        this.torchKey = plugin.key("troll_torch");
    }

    private boolean active() {
        return plugin.settings().mode() == UhcMode.TROLLS_RATAS;
    }

    public @Nullable Role role(UUID id) {
        return roles.get(id);
    }

    public boolean isRat(UUID id) {
        return roles.get(id) == Role.RATA;
    }

    @Override
    public void onStart() {
        roles.clear();
        markedNormals.clear();
        torchUsed.clear();
        ratsGrouped = false;
        if (active()) {
            plugin.lang().broadcast("trolls.intro", Lang.p("episode", ROLES_EPISODE));
        }
    }

    @Override
    public void onEpisodeStart(int episode) {
        if (!active()) {
            return;
        }
        int penultimate = Math.max(ROLES_EPISODE + 1, plugin.settings().finalEpisode() - 1);
        if (episode == penultimate && !ratsGrouped && !roles.isEmpty()) {
            groupRats();
        }
        // Desde la parte 2, en cuanto existan equipos (con equipos al azar pueden revelarse más tarde).
        if (episode < ROLES_EPISODE || !roles.isEmpty() || plugin.teams().teams().isEmpty()) {
            return;
        }
        for (Party team : plugin.teams().teams()) {
            List<UUID> members = new ArrayList<>(team.members().stream().filter(plugin.game()::isAlive).toList());
            Collections.shuffle(members);
            for (int i = 0; i < members.size(); i++) {
                Role role = members.size() < 2 ? Role.NORMAL : i == 0 ? Role.RATA : i == 1 ? Role.TROLL : Role.NORMAL;
                roles.put(members.get(i), role);
            }
        }
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                tellRole(player, entry.getValue());
                if (entry.getValue() == Role.TROLL) {
                    player.getInventory().addItem(torch()).values()
                            .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
                }
            }
        }
        plugin.lang().broadcast("trolls.assigned");
    }

    /** En la penúltima parte las Ratas vivas se revelan y forman su propio equipo. */
    private void groupRats() {
        ratsGrouped = true;
        java.util.List<UUID> rats = roles.entrySet().stream()
                .filter(e -> e.getValue() == Role.RATA && plugin.game().isAlive(e.getKey()))
                .map(Map.Entry::getKey).toList();
        if (rats.isEmpty()) {
            return;
        }
        Party ratTeam = plugin.teams().create(Party.Kind.TEAM, plugin.lang().raw("trolls.rat-team"));
        for (UUID rat : rats) {
            plugin.teams().add(ratTeam, rat);
        }
        String names = String.join(", ", rats.stream().map(plugin.stats()::name).toList());
        plugin.lang().broadcast("trolls.rats-revealed", Lang.p("players", names));
    }

    // ── Antorcha troll ───────────────────────────────────────

    private org.bukkit.inventory.ItemStack torch() {
        return cl.itstuki.uhc.util.Items.of(Material.COPPER_TORCH).name(plugin.lang().item("trolls.torch.name"))
                .lore(plugin.lang().itemList("trolls.torch.lore")).glow(true).tag(torchKey, "1").build();
    }

    private boolean isTorch(org.bukkit.inventory.@Nullable ItemStack item) {
        return cl.itstuki.uhc.util.Items.tag(item, torchKey) != null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTorch(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!isTorch(event.getItem()) || !event.getAction().isRightClick()) {
            return;
        }
        event.setCancelled(true);
        Player troll = event.getPlayer();
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || roles.get(troll.getUniqueId()) != Role.TROLL) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = torchUsed.getOrDefault(troll.getUniqueId(), 0L);
        if (now - last < TORCH_COOLDOWN) {
            plugin.lang().send(troll, "trolls.torch.cooldown", Lang.p("seconds", (TORCH_COOLDOWN - (now - last)) / 1000));
            return;
        }
        Player rat = null;
        for (UUID mate : plugin.teams().teammates(troll.getUniqueId())) {
            if (isRat(mate) && plugin.game().isAlive(mate)) {
                rat = Bukkit.getPlayer(mate);
            }
        }
        if (rat == null) {
            plugin.lang().send(troll, "trolls.torch.no-rat");
            return;
        }
        torchUsed.put(troll.getUniqueId(), now);
        plugin.lang().send(troll, "trolls.torch.used");
        // La Rata tiene 50 % de probabilidad de delatarse con un ruido.
        if (java.util.concurrent.ThreadLocalRandom.current().nextBoolean()) {
            Location where = rat.getLocation();
            where.getWorld().playSound(where, org.bukkit.Sound.ENTITY_ARMADILLO_HURT, org.bukkit.SoundCategory.PLAYERS, 5f, 1f);
            where.getWorld().spawnParticle(org.bukkit.Particle.ITEM, where.clone().add(0, 1.8, 0), 25, 0.3, 0.3, 0.3, 0.05,
                    org.bukkit.inventory.ItemStack.of(Material.YELLOW_DYE));
            plugin.lang().send(rat, "trolls.torch.rat-noise");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTorchPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (isTorch(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onSecond(int secondsToNextEpisode) {
        if (!active()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : torchUsed.entrySet()) {
            long left = TORCH_COOLDOWN - (now - entry.getValue());
            Player troll = Bukkit.getPlayer(entry.getKey());
            if (troll != null && left > 0 && isTorch(troll.getInventory().getItemInMainHand())) {
                troll.sendActionBar(plugin.lang().get("trolls.torch.actionbar", Lang.p("seconds", left / 1000)));
            }
        }
    }

    /** Si solo quedan Ratas vivas, ganan ellas. */
    public boolean onlyRatsAlive() {
        if (!active() || roles.isEmpty() || plugin.game().alive().isEmpty()) {
            return false;
        }
        return plugin.game().alive().stream().allMatch(this::isRat);
    }

    public void tellRole(Player player, Role role) {
        String key = "trolls.role." + role.name().toLowerCase(java.util.Locale.ROOT);
        player.showTitle(Title.title(plugin.lang().get(key + ".title"), plugin.lang().get(key + ".subtitle"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))));
        plugin.lang().sendList(player, key + ".description");
    }

    public void reveal(Player player) {
        Role role = roles.get(player.getUniqueId());
        if (role == null) {
            plugin.lang().send(player, "trolls.no-role");
            return;
        }
        for (UUID mate : plugin.teams().teammates(player.getUniqueId())) {
            Player other = Bukkit.getPlayer(mate);
            if (other != null) {
                plugin.lang().send(other, "trolls.revealed", Lang.p("player", player.getName()),
                        Lang.c("role", plugin.lang().get("trolls.role." + role.name().toLowerCase(java.util.Locale.ROOT) + ".name")));
            }
        }
        plugin.lang().send(player, "trolls.you-revealed");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeamDamage(EntityDamageByEntityEvent event) {
        if (!active() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = RulesModule.attacker(event);
        if (attacker == null || attacker.equals(victim) || !plugin.teams().sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        Role role = roles.get(attacker.getUniqueId());
        if (role == null) {
            event.setCancelled(true); // Antes de repartir roles no hay fuego amigo.
        } else if (role == Role.TROLL) {
            event.setCancelled(true);
            plugin.lang().send(attacker, "trolls.troll-cannot");
        } else if (role == Role.RATA && !markedNormals.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            plugin.lang().send(attacker, "trolls.rat-cannot");
        }
    }

    private boolean ratNearTeammate(Player rat, Location where) {
        for (UUID mate : plugin.teams().teammates(rat.getUniqueId())) {
            Player other = Bukkit.getPlayer(mate);
            if (other != null && plugin.game().isAlive(mate) && other.getWorld().equals(where.getWorld())
                    && other.getLocation().distance(where) <= TRAP_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLava(PlayerBucketEmptyEvent event) {
        if (active() && event.getBucket() == Material.LAVA_BUCKET && isRat(event.getPlayer().getUniqueId())
                && ratNearTeammate(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.lang().send(event.getPlayer(), "trolls.rat-cannot");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (active() && event.getPlayer() != null && isRat(event.getPlayer().getUniqueId())
                && ratNearTeammate(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.lang().send(event.getPlayer(), "trolls.rat-cannot");
        }
    }

    @Override
    public void onEliminate(UUID victim, @Nullable Player killer) {
        if (!active() || killer == null || roles.get(victim) != Role.TROLL
                || roles.get(killer.getUniqueId()) != Role.NORMAL || !plugin.teams().sameTeam(victim, killer.getUniqueId())) {
            return;
        }
        markedNormals.add(killer.getUniqueId());
        AttributeInstance max = killer.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && max.getModifier(plugin.key("troll_penalty")) == null) {
            max.addModifier(new AttributeModifier(plugin.key("troll_penalty"), -0.5,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
            killer.setHealth(Math.min(killer.getHealth(), max.getValue()));
        }
        plugin.lang().send(killer, "trolls.killed-troll");
        for (UUID mate : plugin.teams().teammates(killer.getUniqueId())) {
            if (isRat(mate)) {
                Player rat = Bukkit.getPlayer(mate);
                if (rat != null) {
                    plugin.lang().send(rat, "trolls.rat-can-attack", Lang.p("player", killer.getName()));
                }
            }
        }
    }

    @Override
    public void onReset() {
        roles.clear();
        markedNormals.clear();
        torchUsed.clear();
        ratsGrouped = false;
    }
}
