package cl.itstuki.uhc.compat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Conversión de textos a "§" para ítems, scoreboard e inventarios (en 1.8 no hay componentes ahí). */
public final class Text {
    /** 1.16+ admite colores hexadecimales; antes se usa el color más parecido. */
    private static final LegacyComponentSerializer SECTION = Compat.atLeast(16)
            ? LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build()
            : LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private Text() {
    }

    public static String legacy(Component component) {
        return component == null ? "" : SECTION.serialize(component);
    }

    public static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Texto con códigos & (y &#RRGGBB) a componente: prefijos de rango, etc. */
    public static Component amp(String text) {
        return text == null || text.isEmpty() ? Component.empty() : AMPERSAND.deserialize(text.replace('§', '&'));
    }

    /** Código "§x" de un color con nombre. */
    public static String code(net.kyori.adventure.text.format.NamedTextColor color) {
        return LegacyComponentSerializer.legacySection().serialize(Component.text(" ", color)).replace(" ", "");
    }

    /**
     * Cabe en {@code max} caracteres: si el texto con colores (p. ej. un degradado, que pone un color por letra)
     * es muy largo, se usan los primeros códigos de color y el texto plano.
     */
    public static String fit(Component component, int max) {
        String full = legacy(component);
        if (full.length() <= max) {
            return full;
        }
        StringBuilder codes = new StringBuilder();
        for (int i = 0; i + 1 < full.length() && full.charAt(i) == '§'; i += 2) {
            codes.append(full, i, i + 2);
        }
        return cut(codes + plain(component), max);
    }

    public static String cut(String legacy, int max) {
        if (legacy.length() <= max) {
            return legacy;
        }
        String cut = legacy.substring(0, max);
        return cut.endsWith("§") ? cut.substring(0, max - 1) : cut;
    }
}
