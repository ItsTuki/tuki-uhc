package cl.itstuki.uhc.config;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Configuración editable de la partida (config.yml y menú /uhcconfig). */
public final class UhcSettings {
    private String matchName = "Partida UHC";
    private String language = "es";
    private UhcMode mode = UhcMode.PVP;
    private TeamMode teamMode = TeamMode.MANUAL;
    private int teamSize = 2;
    private int episodeMinutes = 20;
    private int episodes = 5;
    private int borderRadius = 1500;
    private int borderFinalRadius = 50;
    private int borderShrinkEpisode = 0;
    private int pvpEpisode = 2;
    private int randomRevealEpisode = 2;
    private int proximityRadius = 100;
    private MatchFormat format = MatchFormat.EPISODES;
    private int timedMinutes = 120;
    private int borderStartMinutes = 30;
    private final Set<Scenario> scenarios = EnumSet.noneOf(Scenario.class);
    private final Map<Feature, FeatureState> features = new EnumMap<>(Feature.class);

    public static final class FeatureState {
        public boolean enabled;
        public int value;

        FeatureState(boolean enabled, int value) {
            this.enabled = enabled;
            this.value = value;
        }
    }

    public UhcSettings() {
        for (Feature feature : Feature.values()) {
            features.put(feature, new FeatureState(feature.defaultEnabled(), feature.defaultValue()));
        }
    }

    public void load(FileConfiguration config) {
        matchName = config.getString("match.name", matchName);
        language = config.getString("match.language", language).toLowerCase(Locale.ROOT);
        mode = parseEnum(UhcMode.class, config.getString("match.mode"), mode);
        teamMode = parseEnum(TeamMode.class, config.getString("teams.mode"), teamMode);
        teamSize = clamp(config.getInt("teams.size", teamSize), 1, 50);
        randomRevealEpisode = clamp(config.getInt("teams.random-reveal-episode", randomRevealEpisode), 1, 99);
        proximityRadius = clamp(config.getInt("teams.proximity-radius", proximityRadius), 5, 1000);
        episodeMinutes = clamp(config.getInt("episodes.minutes", episodeMinutes), 1, 180);
        episodes = clamp(config.getInt("episodes.count", episodes), 1, 99);
        pvpEpisode = clamp(config.getInt("episodes.pvp-episode", pvpEpisode), 1, 99);
        borderRadius = clamp(config.getInt("border.radius", borderRadius), 50, 30_000);
        borderFinalRadius = clamp(config.getInt("border.final-radius", borderFinalRadius), 5, borderRadius);
        borderShrinkEpisode = clamp(config.getInt("border.shrink-episode", borderShrinkEpisode), 0, 99);
        format = parseEnum(MatchFormat.class, config.getString("match.format"), format);
        timedMinutes = clamp(config.getInt("timed.minutes", timedMinutes), 10, 600);
        borderStartMinutes = clamp(config.getInt("timed.border-start-minutes", borderStartMinutes), 0, 600);
        scenarios.clear();
        for (Scenario scenario : Scenario.values()) {
            if (config.getBoolean("scenarios." + scenario.key(), false)) {
                scenarios.add(scenario);
            }
        }
        ConfigurationSection section = config.getConfigurationSection("features");
        for (Feature feature : Feature.values()) {
            FeatureState state = features.get(feature);
            if (section != null && section.isConfigurationSection(feature.key())) {
                state.enabled = section.getBoolean(feature.key() + ".enabled", feature.defaultEnabled());
                state.value = feature.clampValue(section.getInt(feature.key() + ".value", feature.defaultValue()));
            }
        }
    }

    public void save(FileConfiguration config) {
        config.set("match.name", matchName);
        config.set("match.language", language);
        config.set("match.mode", mode.name());
        config.set("teams.mode", teamMode.name());
        config.set("teams.size", teamSize);
        config.set("teams.random-reveal-episode", randomRevealEpisode);
        config.set("teams.proximity-radius", proximityRadius);
        config.set("episodes.minutes", episodeMinutes);
        config.set("episodes.count", episodes);
        config.set("episodes.pvp-episode", pvpEpisode);
        config.set("border.radius", borderRadius);
        config.set("border.final-radius", borderFinalRadius);
        config.set("border.shrink-episode", borderShrinkEpisode);
        config.set("match.format", format.name());
        config.set("timed.minutes", timedMinutes);
        config.set("timed.border-start-minutes", borderStartMinutes);
        for (Scenario scenario : Scenario.values()) {
            config.set("scenarios." + scenario.key(), scenarios.contains(scenario));
        }
        for (Feature feature : Feature.values()) {
            FeatureState state = features.get(feature);
            config.set("features." + feature.key() + ".enabled", state.enabled);
            if (feature.valueType() != Feature.ValueType.NONE) {
                config.set("features." + feature.key() + ".value", state.value);
            }
        }
    }

    // ── Episodios ────────────────────────────────────────────

    /**
     * Episodios totales, incluido el episodio extra si está activo. Por tiempo, las partes son tramos
     * internos de {@link #episodeMinutes()} (sin títulos ni descansos) que cubren la duración total.
     */
    public int finalEpisode() {
        if (timed()) {
            return Math.max(1, (timedMinutes + episodeMinutes - 1) / episodeMinutes);
        }
        return episodes + (enabled(Feature.EXTRA_EPISODE) ? 1 : 0);
    }

    public boolean timed() {
        return format == MatchFormat.TIMED;
    }

    /** Duración total de la partida por tiempo, en segundos. */
    public int timedSeconds() {
        return timedMinutes * 60;
    }

    /** Segundo en que empieza a cerrarse el borde (por tiempo). */
    public int borderStartSecond() {
        return Math.min(borderStartMinutes, timedMinutes) * 60;
    }

    /** Convierte un valor de episodio (0 = último) en un número de episodio real. */
    public int resolveEpisode(int value) {
        return value <= 0 ? finalEpisode() : Math.min(value, finalEpisode());
    }

    public int featureEpisode(Feature feature) {
        return resolveEpisode(value(feature));
    }

    public int shrinkEpisode() {
        return borderShrinkEpisode <= 0 ? Math.max(1, finalEpisode() - 1) : resolveEpisode(borderShrinkEpisode);
    }

    public int episodeSeconds() {
        return episodeMinutes * 60;
    }

    // ── Opciones ─────────────────────────────────────────────

    public boolean scenario(Scenario scenario) {
        return scenarios.contains(scenario);
    }

    public void scenario(Scenario scenario, boolean active) {
        if (active) {
            scenarios.add(scenario);
            // Doble y triple se excluyen.
            if (scenario == Scenario.DOUBLE_ORES) {
                scenarios.remove(Scenario.TRIPLE_ORES);
            } else if (scenario == Scenario.TRIPLE_ORES) {
                scenarios.remove(Scenario.DOUBLE_ORES);
            } else if (scenario == Scenario.DOUBLE_XP) {
                scenarios.remove(Scenario.TRIPLE_XP);
            } else if (scenario == Scenario.TRIPLE_XP) {
                scenarios.remove(Scenario.DOUBLE_XP);
            }
        } else {
            scenarios.remove(scenario);
        }
    }

    public Set<Scenario> scenarios() {
        return java.util.Collections.unmodifiableSet(scenarios);
    }

    public boolean enabled(Feature feature) {
        return features.get(feature).enabled;
    }

    public int value(Feature feature) {
        return features.get(feature).value;
    }

    public void setEnabled(Feature feature, boolean enabled) {
        features.get(feature).enabled = enabled;
    }

    public void setValue(Feature feature, int value) {
        features.get(feature).value = feature.clampValue(value);
    }

    public String matchName() { return matchName; }
    public void matchName(String name) { this.matchName = name; }
    public String language() { return language; }
    public void language(String language) { this.language = language; }
    public UhcMode mode() { return mode; }
    public void mode(UhcMode mode) {
        this.mode = mode;
        if (mode == UhcMode.SOLITARIOS) {
            teamMode = TeamMode.SOLO;
        } else if (mode == UhcMode.TROLLS_RATAS) {
            teamSize = 3;
            if (teamMode == TeamMode.SOLO || teamMode == TeamMode.PROXIMITY) {
                teamMode = TeamMode.RANDOM;
            }
        }
    }
    /** Trolls y Ratas necesita equipos: en solitario se juega con equipos al azar. */
    public TeamMode teamMode() { return mode == UhcMode.TROLLS_RATAS && teamMode == TeamMode.SOLO ? TeamMode.RANDOM : teamMode; }
    public void teamMode(TeamMode teamMode) { this.teamMode = teamMode; }
    /** Trolls y Ratas siempre juega con equipos de 3. */
    public int teamSize() { return mode == UhcMode.TROLLS_RATAS ? 3 : teamSize; }
    public void teamSize(int size) { this.teamSize = clamp(size, 1, 50); }
    public int episodeMinutes() { return episodeMinutes; }
    public void episodeMinutes(int minutes) { this.episodeMinutes = clamp(minutes, 1, 180); }
    public int episodes() { return episodes; }
    public void episodes(int count) { this.episodes = clamp(count, 1, 99); }
    public int borderRadius() { return borderRadius; }
    public void borderRadius(int radius) {
        this.borderRadius = clamp(radius, 50, 30_000);
        this.borderFinalRadius = Math.min(borderFinalRadius, borderRadius);
    }
    public int borderFinalRadius() { return borderFinalRadius; }
    public void borderFinalRadius(int radius) { this.borderFinalRadius = clamp(radius, 5, borderRadius); }
    public int borderShrinkEpisodeRaw() { return borderShrinkEpisode; }
    public void borderShrinkEpisode(int episode) { this.borderShrinkEpisode = clamp(episode, 0, 99); }
    public int pvpEpisode() { return pvpEpisode; }
    public void pvpEpisode(int episode) { this.pvpEpisode = clamp(episode, 1, 99); }
    public int randomRevealEpisode() { return randomRevealEpisode; }
    public void randomRevealEpisode(int episode) { this.randomRevealEpisode = clamp(episode, 1, 99); }
    public int proximityRadius() { return proximityRadius; }
    public MatchFormat format() { return format; }
    public void format(MatchFormat format) { this.format = format; }
    public int timedMinutes() { return timedMinutes; }
    public void timedMinutes(int minutes) { this.timedMinutes = clamp(minutes, 10, 600); }
    public int borderStartMinutes() { return borderStartMinutes; }
    public void borderStartMinutes(int minutes) { this.borderStartMinutes = clamp(minutes, 0, 600); }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
