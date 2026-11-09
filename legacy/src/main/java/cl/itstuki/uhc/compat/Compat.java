package cl.itstuki.uhc.compat;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Diferencias entre versiones de Minecraft (1.8.8 – 1.20.6). Todo lo que cambia de nombre o no existe
 * en alguna versión pasa por aquí, así el resto del plugin no tiene que preocuparse.
 */
public final class Compat {
    /**
     * Versión menor: 8 para 1.8.8, 12 para 1.12.2, 20 para 1.20.6. Con el nuevo esquema (26.1, 26.3…)
     * vale el año (26), que sigue siendo mayor que cualquier 1.x.
     */
    public static final int MINOR;
    private static final String NAME;
    public static final int PATCH;
    private static final Map<UUID, Double> BONUS = new HashMap<>();
    private static final Map<UUID, Double> FACTOR = new HashMap<>();

    static {
        String version = Bukkit.getBukkitVersion();
        Matcher m = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(version);
        int minor = 8;
        int patch = 0;
        String name = "1.8";
        if (m.find()) {
            int major = Integer.parseInt(m.group(1));
            int second = Integer.parseInt(m.group(2));
            int third = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            if (major == 1) {
                minor = second;
                patch = third;
            } else {
                minor = major;
                patch = second;
            }
            name = m.group(0);
        }
        MINOR = minor;
        PATCH = patch;
        NAME = name;
    }

    private Compat() {
    }

    public static boolean atLeast(int minor) {
        return MINOR >= minor;
    }

    public static String versionName() {
        return NAME;
    }

    // ── Materiales y sonidos ─────────────────────────────────

    public static Material mat(XMaterial material) {
        Material parsed = material.parseMaterial();
        return parsed != null ? parsed : Material.STONE;
    }

    /** El primero que exista en esta versión. */
    public static Material first(XMaterial... options) {
        for (XMaterial option : options) {
            Material parsed = option.parseMaterial();
            if (parsed != null) {
                return parsed;
            }
        }
        return Material.STONE;
    }

    /** El primero que exista en esta versión, como ítem (conserva el "data" de 1.8: cabezas, lana…). */
    public static ItemStack firstItem(XMaterial... options) {
        for (XMaterial option : options) {
            if (option.isSupported()) {
                ItemStack stack = option.parseItem();
                if (stack != null) {
                    return stack;
                }
            }
        }
        return new ItemStack(Material.STONE);
    }

    public static ItemStack item(XMaterial material) {
        ItemStack stack = material.parseItem();
        return stack != null ? stack : new ItemStack(Material.STONE);
    }

    public static boolean is(ItemStack stack, XMaterial material) {
        return stack != null && stack.getType() != Material.AIR && XMaterial.matchXMaterial(stack) == material;
    }

    public static boolean isAir(Material material) {
        return material == null || material == Material.AIR || material.name().endsWith("_AIR");
    }

    public static void sound(Player player, XSound sound, float volume, float pitch) {
        try {
            if (sound.isSupported()) {
                sound.play(player, volume, pitch);
            }
        } catch (RuntimeException ignored) {
            // Sonido inexistente en esta versión.
        }
    }

    public static void sound(Location where, XSound sound, float volume, float pitch) {
        try {
            if (sound.isSupported()) {
                where.getWorld().playSound(where, sound.parseSound(), volume, pitch);
            }
        } catch (RuntimeException ignored) {
            // Sonido inexistente en esta versión.
        }
    }

    // ── Efectos ──────────────────────────────────────────────

    public static void effect(Player player, XPotion potion, int ticks, int amplifier, boolean particles) {
        PotionEffectType type = potion.getPotionEffectType();
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, particles), true);
        }
    }

    public static void removeEffect(Player player, XPotion potion) {
        PotionEffectType type = potion.getPotionEffectType();
        if (type != null) {
            player.removePotionEffect(type);
        }
    }

    public static void clearEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    // ── Vida máxima ──────────────────────────────────────────

    /** Vida máxima con los corazones extra de la partida (manzanas, aumento de vida…). */
    public static double maxHealth(Player player) {
        return player.getMaxHealth();
    }

    public static void addMaxHealth(Player player, double amount) {
        BONUS.merge(player.getUniqueId(), amount, Double::sum);
        applyMaxHealth(player);
    }

    /** Multiplica la vida máxima (Trolls y Ratas: 0.5 = la mitad). */
    public static void setHealthFactor(Player player, double factor) {
        FACTOR.put(player.getUniqueId(), factor);
        applyMaxHealth(player);
    }

    public static boolean hasHealthFactor(Player player) {
        return FACTOR.containsKey(player.getUniqueId());
    }

    public static double bonus(Player player) {
        return BONUS.getOrDefault(player.getUniqueId(), 0.0);
    }

    private static void applyMaxHealth(Player player) {
        double max = (20 + bonus(player)) * FACTOR.getOrDefault(player.getUniqueId(), 1.0);
        player.setMaxHealth(Math.max(1, max));
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    public static void resetMaxHealth(Player player) {
        BONUS.remove(player.getUniqueId());
        FACTOR.remove(player.getUniqueId());
        player.setMaxHealth(20);
    }

    public static void heal(Player player, double amount) {
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
    }

    // ── Mundo ────────────────────────────────────────────────

    /** Cambia una regla del mundo si existe en esta versión. */
    @SuppressWarnings("deprecation")
    /** Nombres nuevos (1.21.11 / 26.x) de las reglas que usa el plugin. */
    private static final Map<String, String[]> RULE_NAMES = new HashMap<>();

    static {
        RULE_NAMES.put("naturalRegeneration", new String[] {"natural_health_regeneration", "minecraft:natural_health_regeneration"});
        RULE_NAMES.put("doImmediateRespawn", new String[] {"immediate_respawn", "minecraft:immediate_respawn"});
        RULE_NAMES.put("locatorBar", new String[] {"locator_bar", "minecraft:locator_bar"});
    }

    /** Cambia una regla por su nombre clásico; si no existe en esta versión, prueba los nombres nuevos. */
    @SuppressWarnings("deprecation")
    public static void gameRule(World world, String rule, boolean value) {
        List<String> names = new ArrayList<>();
        names.add(rule);
        if (RULE_NAMES.containsKey(rule)) {
            names.addAll(Arrays.asList(RULE_NAMES.get(rule)));
        }
        for (String name : names) {
            try {
                if (world.isGameRule(name) && world.setGameRuleValue(name, String.valueOf(value))) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // Nombre no válido en esta versión.
            }
        }
    }

    public static int minHeight(World world) {
        if (atLeast(17)) {
            try {
                return (int) World.class.getMethod("getMinHeight").invoke(world);
            } catch (ReflectiveOperationException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** TPS del último minuto (Paper/Spigot los exponen distinto según la versión). */
    public static double tps() {
        Object server = Bukkit.getServer();
        try {
            Object result = server.getClass().getMethod("getTPS").invoke(server);
            if (result instanceof double[]) {
                return ((double[]) result)[0];
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Se prueba con el servidor interno.
        }
        try {
            Object nms = server.getClass().getMethod("getServer").invoke(server);
            for (Class<?> c = nms.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field field = c.getDeclaredField("recentTps");
                    field.setAccessible(true);
                    return ((double[]) field.get(nms))[0];
                } catch (NoSuchFieldException ignored) {
                    // Sigue con la clase padre.
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Sin datos.
        }
        return 20;
    }

    /** Altura del bloque sólido más alto (en 1.8 getHighestBlockYAt devuelve el aire de encima). */
    public static int highestSolidY(World world, int x, int z) {
        int y = Math.min(world.getMaxHeight() - 1, world.getHighestBlockYAt(x, z));
        while (y > minHeight(world) && isAir(world.getBlockAt(x, y, z).getType())) {
            y--;
        }
        return y;
    }

    /**
     * Pone un bloque con estado: en 1.13+ con texto de BlockData ("end_portal_frame[eye=true]"),
     * antes con el número de "data" de 1.8–1.12.
     */
    @SuppressWarnings("deprecation")
    public static void setBlock(org.bukkit.block.Block block, Material type, String modernData, int legacyData) {
        if (atLeast(13)) {
            try {
                Object data = Bukkit.class.getMethod("createBlockData", String.class).invoke(null, modernData);
                Class<?> blockData = Class.forName("org.bukkit.block.data.BlockData");
                org.bukkit.block.Block.class.getMethod("setBlockData", blockData, boolean.class).invoke(block, data, false);
                return;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                block.setType(type, false);
                return;
            }
        }
        block.setType(type, false);
        try {
            org.bukkit.block.Block.class.getMethod("setData", byte.class, boolean.class).invoke(block, (byte) legacyData, false);
        } catch (ReflectiveOperationException ignored) {
            // Sin data: queda el bloque por defecto.
        }
    }

    // ── Eventos con mano (1.9+) ──────────────────────────────

    /** En 1.9+ los clics se disparan dos veces (una por mano): solo cuenta la principal. */
    public static boolean isOffHand(Event event) {
        if (!atLeast(9)) {
            return false;
        }
        try {
            Method getHand = event.getClass().getMethod("getHand");
            Object hand = getHand.invoke(event);
            return hand != null && hand.toString().equals("OFF_HAND");
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static ItemStack mainHand(Player player) {
        return player.getItemInHand();
    }

    // ── Jugadores: ocultar, colisiones y estados (por reflexión, cambian entre versiones) ──

    private static java.lang.reflect.Method method(Class<?> type, String name, Class<?>... args) {
        try {
            return type.getMethod(name, args);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private static final java.lang.reflect.Method HIDE = method(Player.class, "hidePlayer", org.bukkit.plugin.Plugin.class, Player.class);
    private static final java.lang.reflect.Method SHOW = method(Player.class, "showPlayer", org.bukkit.plugin.Plugin.class, Player.class);
    private static final java.lang.reflect.Method HIDE_OLD = method(Player.class, "hidePlayer", Player.class);
    private static final java.lang.reflect.Method SHOW_OLD = method(Player.class, "showPlayer", Player.class);

    private static void call(java.lang.reflect.Method method, Object target, Object... args) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // No disponible en esta versión.
        }
    }

    public static void hide(Player viewer, org.bukkit.plugin.Plugin plugin, Player target) {
        if (HIDE != null) {
            call(HIDE, viewer, plugin, target);
        } else {
            call(HIDE_OLD, viewer, target);
        }
    }

    public static void show(Player viewer, org.bukkit.plugin.Plugin plugin, Player target) {
        if (SHOW != null) {
            call(SHOW, viewer, plugin, target);
        } else {
            call(SHOW_OLD, viewer, target);
        }
    }

    /** 1.9+: setCollidable, setInvulnerable, setSilent. En 1.8 no hacen nada (el daño se cancela por evento). */
    public static void ghostFlags(Player player, boolean ghost) {
        call(method(Player.class, "setCollidable", boolean.class), player, !ghost);
        call(method(Player.class, "setInvulnerable", boolean.class), player, ghost);
        call(method(Player.class, "setSilent", boolean.class), player, ghost);
    }

    /** Ítem de la segunda mano (1.9+), o null. */
    public static ItemStack offHand(Player player) {
        java.lang.reflect.Method m = method(org.bukkit.inventory.PlayerInventory.class, "getItemInOffHand");
        if (m == null) {
            return null;
        }
        try {
            return (ItemStack) m.invoke(player.getInventory());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }
}
