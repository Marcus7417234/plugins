package io.github.marcus7417234.snakegame.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A player's Snake preferences and statistics. */
public final class PlayerData {

    private final UUID uuid;
    String name;
    boolean sound = true;
    String speed;
    String theme;
    int games;
    long apples;
    long secondsPlayed;
    final Map<String, Integer> best = new HashMap<>();

    PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public boolean soundEnabled() {
        return sound;
    }

    public String speed() {
        return speed;
    }

    public String theme() {
        return theme;
    }

    public int games() {
        return games;
    }

    public long apples() {
        return apples;
    }

    public long secondsPlayed() {
        return secondsPlayed;
    }

    public int best(String speed) {
        Integer score = best.get(speed);
        return score != null ? score : 0;
    }
}
