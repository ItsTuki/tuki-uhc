package cl.itstuki.uhc.map;

import cl.itstuki.uhc.compat.Compat;
import com.cryptomorin.xseries.XMaterial;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.noise.SimplexNoiseGenerator;

/**
 * Cambios bajo tierra de un chunk: cuevas más grandes (ruido 3D, sin cortes entre chunks) y vetas extra de
 * minerales. Todo se hace dentro del propio chunk para no generar los vecinos en cadena.
 */
public final class ChunkShaper {
    private static final Set<Material> STONE = materials("STONE", "DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE", "TUFF",
            "CALCITE", "SMOOTH_BASALT");
    private static final Set<Material> CARVABLE = materials("STONE", "DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE", "TUFF",
            "DIRT", "COAL_ORE", "DEEPSLATE_COAL_ORE", "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "GRAVEL", "CALCITE");
    private static final Set<Material> UNSAFE = materials("WATER", "STATIONARY_WATER", "LAVA", "STATIONARY_LAVA", "SAND",
            "RED_SAND", "GRAVEL", "BEDROCK", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL", "POWDER_SNOW");
    private static final Material DEEPSLATE = Material.getMaterial("DEEPSLATE");
    private static final Material TUFF = Material.getMaterial("TUFF");
    private static final Material CAVE_AIR = Material.getMaterial("CAVE_AIR") != null ? Material.getMaterial("CAVE_AIR") : Material.AIR;

    /** Altura mínima del mundo principal (se calcula una vez; 0 antes de 1.18). */
    private static int MIN_Y = 0;

    private static Set<Material> materials(String... names) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                set.add(material);
            }
        }
        return set;
    }

    /** Un tipo de mineral con su veta: cuántas por chunk (antes del porcentaje), tamaño y alturas. */
    public static final class Ore {
        final Material normal;
        final Material deep;
        final double perChunk;
        final int size;
        final int minY;
        final int maxY;

        Ore(Material normal, Material deep, double perChunk, int size, int minY, int maxY) {
            this.normal = normal;
            this.deep = deep;
            this.perChunk = perChunk;
            this.size = size;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private final long seed;
    private final SimplexNoiseGenerator cheese;
    private final SimplexNoiseGenerator detail;
    private final SimplexNoiseGenerator spaghettiA;
    private final SimplexNoiseGenerator spaghettiB;

    public ChunkShaper(World world) {
        MIN_Y = Compat.minHeight(world);
        this.seed = world.getSeed();
        this.cheese = new SimplexNoiseGenerator(seed ^ 0x5DEECE66DL);
        this.detail = new SimplexNoiseGenerator(seed * 31 + 7);
        this.spaghettiA = new SimplexNoiseGenerator(seed * 17 + 11);
        this.spaghettiB = new SimplexNoiseGenerator(seed * 13 + 3);
    }

    private Random random(Chunk chunk, long salt) {
        return new Random(seed ^ (chunk.getX() * 341873128712L + chunk.getZ() * 132897987541L) ^ salt);
    }

    // ── Cuevas ───────────────────────────────────────────────

    /** Cuevas tipo "queso" (salas amplias) y "espagueti" (túneles largos). {@code strength} 1.0 = normal. */
    public int carveCaves(Chunk chunk, double strength, int topOffset) {
        World world = chunk.getWorld();
        int minY = MIN_Y + 6;
        int seaTop = world.getSeaLevel() - 8;
        double threshold = 0.62 - 0.08 * (strength - 1);
        double tunnel = 0.045 * strength;
        int carved = 0;
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int surface = world.getHighestBlockYAt(baseX + x, baseZ + z);
                int top = Math.min(seaTop, surface - topOffset);
                for (int y = minY; y <= top; y++) {
                    double wx = baseX + x;
                    double wz = baseZ + z;
                    double squash = y < 0 ? 18 : 14;
                    double c = cheese.noise(wx / 52.0, y / squash, wz / 52.0) + 0.25 * detail.noise(wx / 14.0, y / 10.0, wz / 14.0);
                    boolean open = c > threshold;
                    if (!open) {
                        double a = spaghettiA.noise(wx / 72.0, y / 36.0, wz / 72.0);
                        double b = spaghettiB.noise(wx / 72.0, y / 36.0, wz / 72.0);
                        open = Math.abs(a) < tunnel && Math.abs(b) < tunnel;
                    }
                    if (open && carve(chunk, x, y, z)) {
                        carved++;
                    }
                }
            }
        }
        return carved;
    }

    private static boolean carve(Chunk chunk, int x, int y, int z) {
        Block block = chunk.getBlock(x, y, z);
        if (!CARVABLE.contains(block.getType())) {
            return false;
        }
        if (unsafe(chunk, x, y + 1, z) || unsafe(chunk, x, y - 1, z) || unsafe(chunk, x + 1, y, z) || unsafe(chunk, x - 1, y, z)
                || unsafe(chunk, x, y, z + 1) || unsafe(chunk, x, y, z - 1)) {
            return false;
        }
        block.setType(CAVE_AIR, false);
        return true;
    }

    private static boolean unsafe(Chunk chunk, int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15) {
            return false; // Fuera del chunk: no se mira para no cargar el vecino.
        }
        return UNSAFE.contains(chunk.getBlock(x, y, z).getType());
    }

    // ── Minerales ────────────────────────────────────────────

    /** Vetas extra: {@code boostPercent} 100 = el doble de vetas de cada mineral de la lista. */
    public int addOres(Chunk chunk, List<Ore> ores, int boostPercent) {
        Random random = random(chunk, 0x0E5L);
        World world = chunk.getWorld();
        int placed = 0;
        for (Ore ore : ores) {
            double veins = ore.perChunk * boostPercent / 100.0;
            int count = (int) veins + (random.nextDouble() < veins - (int) veins ? 1 : 0);
            int low = Math.max(MIN_Y + 1, ore.minY);
            int high = Math.min(world.getMaxHeight() - 1, ore.maxY);
            if (high <= low) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                placed += vein(chunk, random, ore, 1 + random.nextInt(14), low + random.nextInt(high - low), 1 + random.nextInt(14));
            }
        }
        return placed;
    }

    private static int vein(Chunk chunk, Random random, Ore ore, int x, int y, int z) {
        int placed = 0;
        int minY = MIN_Y + 1;
        int maxY = chunk.getWorld().getMaxHeight() - 2;
        for (int i = 0; i < ore.size * 2 && placed < ore.size; i++) {
            y = clamp(y, minY, maxY);
            Block block = chunk.getBlock(x, y, z);
            Material type = block.getType();
            if (STONE.contains(type)) {
                boolean deep = (DEEPSLATE != null && type == DEEPSLATE) || (TUFF != null && type == TUFF) || y < 0;
                block.setType(deep && ore.deep != null ? ore.deep : ore.normal, false);
                placed++;
            }
            x = clamp(x + random.nextInt(3) - 1, 0, 15);
            y += random.nextInt(3) - 1;
            z = clamp(z + random.nextInt(3) - 1, 0, 15);
        }
        return placed;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Material block(String name) {
        Optional<XMaterial> match = XMaterial.matchXMaterial(name);
        if (!match.isPresent() || !match.get().isSupported()) {
            return null;
        }
        Material material = match.get().parseMaterial();
        return material != null && material.isBlock() ? material : null;
    }

    /** Lista de minerales desde config.yml (map.ores). */
    public static List<Ore> ores(ConfigurationSection section) {
        List<Ore> list = new ArrayList<>();
        if (section == null) {
            return list;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection ore = section.getConfigurationSection(key);
            if (ore == null) {
                continue;
            }
            String upper = key.toUpperCase(Locale.ROOT);
            Material normal = block(ore.getString("block", upper + "_ORE"));
            Material deep = block(ore.getString("deep-block", "DEEPSLATE_" + upper + "_ORE"));
            if (normal == null) {
                continue;
            }
            list.add(new Ore(normal, deep, Math.max(0, ore.getDouble("veins", 1)), Math.max(1, Math.min(32, ore.getInt("size", 6))),
                    ore.getInt("min-y", -60), ore.getInt("max-y", 32)));
        }
        return list;
    }
}
