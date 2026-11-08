package cl.itstuki.uhc.map;

import java.util.EnumSet;
import java.util.List;
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
    /** Bloques que se pueden excavar o convertir en mineral. */
    private static final Set<Material> STONE = EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.ANDESITE,
            Material.DIORITE, Material.GRANITE, Material.TUFF, Material.CALCITE, Material.SMOOTH_BASALT);
    private static final Set<Material> CARVABLE = EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.ANDESITE,
            Material.DIORITE, Material.GRANITE, Material.TUFF, Material.DIRT, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.GRAVEL, Material.CALCITE);
    /** Si hay uno de estos al lado no se excava (no abrir agua, lava ni arena colgando). */
    private static final Set<Material> UNSAFE = EnumSet.of(Material.WATER, Material.LAVA, Material.SAND, Material.RED_SAND,
            Material.GRAVEL, Material.BEDROCK, Material.SUSPICIOUS_SAND, Material.SUSPICIOUS_GRAVEL, Material.POWDER_SNOW);

    /** Un tipo de mineral con su veta: cuántas por chunk (antes del multiplicador), tamaño y alturas. */
    public record Ore(Material normal, Material deep, double perChunk, int size, int minY, int maxY) {
    }

    private final long seed;
    private final SimplexNoiseGenerator cheese;
    private final SimplexNoiseGenerator detail;
    private final SimplexNoiseGenerator spaghettiA;
    private final SimplexNoiseGenerator spaghettiB;

    public ChunkShaper(World world) {
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

    /**
     * Cuevas tipo "queso" (salas amplias) y "espagueti" (túneles largos). {@code strength} 1.0 = normal;
     * más alto, más y más grandes.
     */
    public int carveCaves(Chunk chunk, double strength, int topOffset) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight() + 6;
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
                    // Salas más altas en lo profundo, más planas cerca de la superficie.
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
        block.setType(Material.CAVE_AIR, false);
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
            double veins = ore.perChunk() * boostPercent / 100.0;
            int count = (int) veins + (random.nextDouble() < veins - (int) veins ? 1 : 0);
            int low = Math.max(world.getMinHeight() + 1, ore.minY());
            int high = Math.min(world.getMaxHeight() - 1, ore.maxY());
            if (high <= low) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                placed += vein(chunk, random, ore, 1 + random.nextInt(14), low + random.nextInt(high - low), 1 + random.nextInt(14));
            }
        }
        return placed;
    }

    /** Veta: un recorrido corto y aleatorio desde (x, y, z), siempre dentro del chunk. */
    private static int vein(Chunk chunk, Random random, Ore ore, int x, int y, int z) {
        int placed = 0;
        int minY = chunk.getWorld().getMinHeight() + 1;
        int maxY = chunk.getWorld().getMaxHeight() - 2;
        for (int i = 0; i < ore.size() * 2 && placed < ore.size(); i++) {
            y = clamp(y, minY, maxY);
            Block block = chunk.getBlock(x, y, z);
            Material type = block.getType();
            if (STONE.contains(type)) {
                boolean deep = type == Material.DEEPSLATE || type == Material.TUFF || y < 0;
                block.setType(deep && ore.deep() != null ? ore.deep() : ore.normal(), false);
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

    /** Lista de minerales desde config.yml (map.ores). */
    public static List<Ore> ores(ConfigurationSection section) {
        List<Ore> list = new java.util.ArrayList<>();
        if (section == null) {
            return list;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection ore = section.getConfigurationSection(key);
            if (ore == null) {
                continue;
            }
            Material normal = Material.matchMaterial(ore.getString("block", key.toUpperCase() + "_ORE"));
            Material deep = Material.matchMaterial(ore.getString("deep-block", "DEEPSLATE_" + key.toUpperCase() + "_ORE"));
            if (normal == null || !normal.isBlock()) {
                continue;
            }
            list.add(new Ore(normal, deep != null && deep.isBlock() ? deep : null, Math.max(0, ore.getDouble("veins", 1)),
                    Math.max(1, Math.min(32, ore.getInt("size", 6))), ore.getInt("min-y", -60), ore.getInt("max-y", 32)));
        }
        return list;
    }
}
