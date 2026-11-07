package cl.itstuki.uhc.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Estadísticas de la partida actual. */
public final class StatsTracker {
    public record TeamResult(String name, long seconds) {
    }

    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Double> damageTaken = new HashMap<>();
    private final Map<UUID, Long> deathSecond = new HashMap<>();
    private final Map<String, Integer> teamKills = new HashMap<>();
    private final List<TeamResult> teamEliminations = new ArrayList<>();

    public void reset() {
        names.clear();
        kills.clear();
        damageTaken.clear();
        deathSecond.clear();
        teamKills.clear();
        teamEliminations.clear();
    }

    public void register(UUID id, String name) {
        names.put(id, name);
        kills.putIfAbsent(id, 0);
        damageTaken.putIfAbsent(id, 0.0);
    }

    public void addKill(UUID killer, String teamName) {
        kills.merge(killer, 1, Integer::sum);
        if (teamName != null) {
            teamKills.merge(teamName, 1, Integer::sum);
        }
    }

    public void addDamage(UUID player, double amount) {
        damageTaken.merge(player, amount, Double::sum);
    }

    public void death(UUID player, long second) {
        deathSecond.put(player, second);
    }

    public void teamEliminated(String name, long second) {
        teamEliminations.add(new TeamResult(name, second));
    }

    public String name(UUID id) {
        return names.getOrDefault(id, "?");
    }

    public int kills(UUID id) {
        return kills.getOrDefault(id, 0);
    }

    public double damage(UUID id) {
        return damageTaken.getOrDefault(id, 0.0);
    }

    public Optional<Map.Entry<UUID, Integer>> topKiller() {
        return kills.entrySet().stream().filter(e -> e.getValue() > 0).max(Map.Entry.comparingByValue());
    }

    public Optional<Map.Entry<UUID, Double>> mostDamaged() {
        return damageTaken.entrySet().stream().filter(e -> e.getValue() > 0).max(Map.Entry.comparingByValue());
    }

    public Optional<Map.Entry<String, Integer>> topTeam() {
        return teamKills.entrySet().stream().max(Map.Entry.comparingByValue());
    }

    /** Jugador que menos daño recibió entre los que sobrevivieron. */
    public Optional<UUID> ironMan(List<UUID> survivors) {
        return survivors.stream().min((a, b) -> Double.compare(damage(a), damage(b)));
    }

    /** Jugador que murió primero. */
    public Optional<Map.Entry<UUID, Long>> strawMan() {
        return deathSecond.entrySet().stream().min(Map.Entry.comparingByValue());
    }

    /** Equipos ordenados por cuánto aguantaron (el último eliminado primero). */
    public List<TeamResult> podium() {
        List<TeamResult> copy = new ArrayList<>(teamEliminations);
        copy.sort((a, b) -> Long.compare(b.seconds(), a.seconds()));
        return copy;
    }

    public Map<UUID, Integer> allKills() {
        return kills;
    }
}
