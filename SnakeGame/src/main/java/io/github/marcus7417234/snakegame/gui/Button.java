package io.github.marcus7417234.snakegame.gui;

import io.github.marcus7417234.snakegame.engine.Direction;

/** Everything a player can click in the game menu besides the board itself. */
public enum Button {
    UP(Direction.UP),
    DOWN(Direction.DOWN),
    LEFT(Direction.LEFT),
    RIGHT(Direction.RIGHT),
    /** Start / pause / resume / play again, depending on the game's state. */
    PRIMARY,
    SCORE,
    BEST,
    SPEED,
    THEME,
    STATS,
    HELP,
    SOUND,
    QUIT,
    /** Shown on the board on the game over screen. */
    PLAY_AGAIN;

    private final Direction direction;

    Button() {
        this(null);
    }

    Button(Direction direction) {
        this.direction = direction;
    }

    /** The direction an arrow button steers in, or null for other buttons. */
    public Direction direction() {
        return direction;
    }
}
