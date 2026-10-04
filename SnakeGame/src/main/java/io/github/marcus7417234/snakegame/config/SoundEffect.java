package io.github.marcus7417234.snakegame.config;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.logging.Logger;

/** A sound parsed from a config line such as {@code "NOTE_PLING 1.0 1.2"}. */
public final class SoundEffect {

    public static final SoundEffect NONE = new SoundEffect(null, 0, 0);

    private final Sound sound;
    private final float volume;
    private final float pitch;

    private SoundEffect(Sound sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static SoundEffect parse(String value, String path, Logger logger) {
        if (value == null || value.trim().isEmpty()) {
            return NONE;
        }
        String[] parts = value.trim().split("[\\s,:]+");
        try {
            Sound sound = Sound.valueOf(parts[0].toUpperCase(Locale.ROOT));
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            return new SoundEffect(sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid sound '" + value + "' at " + path + " - it will be silent.");
            return NONE;
        }
    }

    public void play(Player player) {
        play(player, 0f);
    }

    /** Plays the sound with its pitch raised by {@code pitchOffset}. */
    public void play(Player player, float pitchOffset) {
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, Math.min(2.0f, pitch + pitchOffset));
        }
    }
}
