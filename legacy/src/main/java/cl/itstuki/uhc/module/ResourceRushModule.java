package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

/** Resource Rush: conseguir una lista de objetos en orden de dificultad antes que el resto. */
public final class ResourceRushModule implements UhcModule {
    public static final int TIERS = 4;
    public static final int TOTAL_ITEMS = 12;

    private final TukiUHC plugin;
    private final File file;
    private final TreeMap<Integer, List<XMaterial>> pools = new TreeMap<>();
    private final List<XMaterial> sequence = new ArrayList<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final Map<UUID, List<Long>> sneaks = new HashMap<>();

    public ResourceRushModule(TukiUHC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "resource-rush.yml");
        reloadPools();
    }

    private boolean active() {
        return plugin.settings().mode() == UhcMode.RESOURCE_RUSH;
    }

    /** Lee resource-rush.yml; los objetos que no existen en esta versión se saltan. */
    public void reloadPools() {
        if (!file.exists()) {
            plugin.saveResource("resource-rush.yml", false);
        }
        pools.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("pools");
        for (int tier = 1; tier <= TIERS; tier++) {
            List<XMaterial> list = new ArrayList<>();
            if (section != null) {
                for (String raw : section.getStringList(String.valueOf(tier))) {
                    Optional<XMaterial> material = XMaterial.matchXMaterial(raw);
                    if (material.isPresent() && material.get().isSupported() && !list.contains(material.get())) {
                        list.add(material.get());
                    }
                }
            }
            pools.put(tier, list);
        }
    }

    public void savePools() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.options().header("Objetos de Resource Rush por nivel de dificultad (1 = fácil, 4 = difícil).\n"
                + "Se eligen 3 de cada nivel. También se editan en el juego: /uhcconfig > Modos > Resource Rush.");
        for (Map.Entry<Integer, List<XMaterial>> entry : pools.entrySet()) {
            // Se conservan los de versiones más nuevas que este servidor no conoce.
            List<String> kept = new ArrayList<>();
            for (String raw : yaml.getStringList("pools." + entry.getKey())) {
                Optional<XMaterial> m = XMaterial.matchXMaterial(raw);
                if (!m.isPresent() || !m.get().isSupported()) {
                    kept.add(raw);
                }
            }
            kept.addAll(entry.getValue().stream().map(XMaterial::name).collect(Collectors.toList()));
            yaml.set("pools." + entry.getKey(), kept);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar resource-rush.yml: " + ex.getMessage());
        }
    }

    public List<XMaterial> pool(int tier) {
        return pools.computeIfAbsent(tier, t -> new ArrayList<>());
    }

    // ── Partida ──────────────────────────────────────────────

    @Override
    public void onStart() {
        sequence.clear();
        progress.clear();
        if (!active()) {
            return;
        }
        int perTier = TOTAL_ITEMS / TIERS;
        for (int tier = 1; tier <= TIERS; tier++) {
            List<XMaterial> pool = new ArrayList<>(pool(tier));
            pool.removeAll(sequence);
            Collections.shuffle(pool);
            sequence.addAll(pool.subList(0, Math.min(perTier, pool.size())));
        }
        plugin.lang().broadcast("rush.start", Lang.p("count", sequence.size()));
        if (!sequence.isEmpty()) {
            plugin.lang().broadcast("rush.first", Lang.c("item", itemName(sequence.get(0))));
        }
    }

    private String side(UUID id) {
        Party team = plugin.teams().team(id);
        return team != null ? "t" + team.id() : "p" + id;
    }

    public XMaterial target(UUID player) {
        int index = progress.getOrDefault(side(player), 0);
        return index < sequence.size() ? sequence.get(index) : null;
    }

    public int progress(UUID player) {
        return progress.getOrDefault(side(player), 0);
    }

    public int total() {
        return sequence.size();
    }

    private static boolean has(Player player, XMaterial target) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !Compat.isAir(item.getType()) && XMaterial.matchXMaterial(item) == target) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onSecond(int secondsToNextEpisode) {
        if (!active() || sequence.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(plugin.game().alive())) {
            Player player = Bukkit.getPlayer(id);
            XMaterial target = target(id);
            if (player == null || target == null || !has(player, target)) {
                continue;
            }
            int done = progress.merge(side(id), 1, Integer::sum);
            Party team = plugin.teams().team(id);
            String who = team != null ? team.name() : player.getName();
            plugin.lang().broadcast("rush.got", Lang.p("who", who), Lang.c("item", itemName(target)),
                    Lang.p("done", done), Lang.p("total", sequence.size()));
            Compat.sound(player, XSound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
            if (done >= sequence.size()) {
                List<UUID> winners = new ArrayList<>();
                if (team != null) {
                    for (UUID member : team.members()) {
                        if (plugin.game().isAlive(member)) {
                            winners.add(member);
                        }
                    }
                } else {
                    winners.add(id);
                }
                plugin.game().end(winners, "rush.win");
                return;
            }
            Component next = itemName(sequence.get(done));
            for (UUID member : team != null ? team.members() : Collections.singletonList(id)) {
                Player mate = Bukkit.getPlayer(member);
                if (mate != null) {
                    plugin.lang().send(mate, "rush.next", Lang.c("item", next));
                }
            }
        }
    }

    /** Agacharse 3 veces seguidas muestra los objetivos que faltan. */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!active() || !event.isSneaking() || !plugin.game().isRunning() || sequence.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        List<Long> times = sneaks.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        times.removeIf(t -> now - t > 1200);
        times.add(now);
        if (times.size() < 3) {
            return;
        }
        times.clear();
        int done = progress(player.getUniqueId());
        plugin.lang().send(player, "rush.pending-header", Lang.p("done", done), Lang.p("total", sequence.size()));
        for (int i = done; i < sequence.size(); i++) {
            plugin.lang().send(player, i == done ? "rush.pending-current" : "rush.pending-entry",
                    Lang.p("number", i + 1), Lang.c("item", itemName(sequence.get(i))));
        }
    }

    public static Component itemName(XMaterial material) {
        return Component.text(prettyName(material));
    }

    @Override
    public void onReset() {
        sequence.clear();
        progress.clear();
    }

    public static String prettyName(XMaterial material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    static List<XMaterial> list(XMaterial... items) {
        return Arrays.asList(items);
    }
}
