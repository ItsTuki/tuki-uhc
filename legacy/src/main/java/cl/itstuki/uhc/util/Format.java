package cl.itstuki.uhc.util;

import org.bukkit.Location;

/** Formatos de tiempo, vida y dirección. */
public final class Format {
    private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    private Format() {
    }

    /** 3725 → "01:02:05"; 125 → "02:05". */
    public static String time(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    public static String hearts(double health) {
        return String.format(java.util.Locale.ROOT, "%.1f", health / 2.0);
    }

    /** Flecha que apunta desde la mirada de {@code from} hacia {@code to}. */
    public static String arrow(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = (targetYaw - from.getYaw()) % 360;
        if (relative < 0) {
            relative += 360;
        }
        int index = (int) Math.round(relative / 45.0) % 8;
        return ARROWS[index];
    }
}
