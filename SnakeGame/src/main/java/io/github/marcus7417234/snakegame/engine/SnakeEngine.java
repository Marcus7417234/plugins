package io.github.marcus7417234.snakegame.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * The rules of Snake on a small rectangular board, independent of Bukkit.
 * <p>
 * Cells are addressed by index ({@code y * width + x}), which maps directly onto
 * chest inventory slots when the board is 9 cells wide.
 */
public final class SnakeEngine {

    /** Outcome of a single {@link #step()}. */
    public enum Result {
        MOVED,
        ATE,
        DIED,
        WON
    }

    /** How many turns can be queued ahead, so quick double-turns are not lost between moves. */
    static final int MAX_QUEUED_TURNS = 3;

    private final int width;
    private final int height;
    private final boolean wrap;
    private final int growthPerApple;
    private final Random random;

    private final Deque<Integer> body = new ArrayDeque<>(); // head first
    private final boolean[] occupied;
    private final Deque<Direction> queuedTurns = new ArrayDeque<>();

    private Direction direction = Direction.RIGHT;
    private int apple = -1;
    private int score;
    private int pendingGrowth;
    private int crashCell = -1;
    private boolean dead;
    private boolean won;

    public SnakeEngine(int width, int height, int startLength, int growthPerApple, boolean wrap, Random random) {
        if (width < 4 || height < 1) {
            throw new IllegalArgumentException("Board must be at least 4x1");
        }
        this.width = width;
        this.height = height;
        this.wrap = wrap;
        this.growthPerApple = Math.max(1, growthPerApple);
        this.random = random;
        this.occupied = new boolean[width * height];

        // Start on the middle row, facing right, with room in front of the head.
        int length = Math.max(1, Math.min(startLength, width - 3));
        int row = (height - 1) / 2;
        for (int x = length; x >= 1; x--) {
            int cell = row * width + x;
            body.addLast(cell);
            occupied[cell] = true;
        }
        int preferredApple = row * width + (width - 3);
        apple = occupied[preferredApple] ? spawnApple() : preferredApple;
    }

    /** Builds a board in an exact state. Only meant for tests. */
    static SnakeEngine withSnake(int width, int height, boolean wrap, List<Integer> bodyHeadFirst,
                                 Direction direction, int apple, int pendingGrowth, int growthPerApple, Random random) {
        SnakeEngine engine = new SnakeEngine(width, height, 1, growthPerApple, wrap, random);
        engine.body.clear();
        java.util.Arrays.fill(engine.occupied, false);
        for (int cell : bodyHeadFirst) {
            engine.body.addLast(cell);
            engine.occupied[cell] = true;
        }
        engine.direction = direction;
        engine.apple = apple;
        engine.pendingGrowth = pendingGrowth;
        return engine;
    }

    /**
     * Queues a turn for an upcoming move. Turning back into the snake's own neck and
     * repeating the previous turn are ignored.
     *
     * @return whether the turn was accepted
     */
    public boolean turn(Direction newDirection) {
        if (dead || won) {
            return false;
        }
        Direction reference = plannedDirection();
        if (newDirection == reference) {
            return false;
        }
        if (newDirection == reference.opposite() && body.size() > 1) {
            return false;
        }
        if (queuedTurns.size() >= MAX_QUEUED_TURNS) {
            return false;
        }
        queuedTurns.addLast(newDirection);
        return true;
    }

    /** Moves the snake one cell. */
    public Result step() {
        if (dead) {
            return Result.DIED;
        }
        if (won) {
            return Result.WON;
        }
        Direction next = queuedTurns.pollFirst();
        if (next != null) {
            direction = next;
        }

        int head = body.peekFirst();
        int x = head % width + direction.dx();
        int y = head / width + direction.dy();
        if (x < 0 || y < 0 || x >= width || y >= height) {
            if (!wrap) {
                dead = true;
                crashCell = -1;
                return Result.DIED;
            }
            x = Math.floorMod(x, width);
            y = Math.floorMod(y, height);
        }
        int target = y * width + x;
        boolean eating = target == apple;
        if (eating) {
            pendingGrowth += growthPerApple;
        }
        boolean growing = pendingGrowth > 0;
        int tail = body.peekLast();

        // The tail moves out of the way this turn unless the snake is growing.
        if (occupied[target] && (growing || target != tail)) {
            dead = true;
            crashCell = target;
            return Result.DIED;
        }

        if (growing) {
            pendingGrowth--;
        } else {
            body.pollLast();
            occupied[tail] = false;
        }
        body.addFirst(target);
        occupied[target] = true;

        if (!eating) {
            return Result.MOVED;
        }
        score++;
        apple = spawnApple();
        if (apple < 0) {
            won = true;
            return Result.WON;
        }
        return Result.ATE;
    }

    private int spawnApple() {
        int free = occupied.length - body.size();
        if (free <= 0) {
            return -1;
        }
        int pick = random.nextInt(free);
        for (int cell = 0; cell < occupied.length; cell++) {
            if (!occupied[cell] && pick-- == 0) {
                return cell;
            }
        }
        return -1;
    }

    /** The direction the snake will be heading once every queued turn has been taken. */
    public Direction plannedDirection() {
        Direction last = queuedTurns.peekLast();
        return last != null ? last : direction;
    }

    public Direction direction() {
        return direction;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int head() {
        return body.peekFirst();
    }

    /** Cells of the snake from head to tail. */
    public List<Integer> body() {
        return Collections.unmodifiableList(new ArrayList<>(body));
    }

    public int length() {
        return body.size();
    }

    public boolean isSnake(int cell) {
        return cell >= 0 && cell < occupied.length && occupied[cell];
    }

    /** The apple's cell, or -1 when there is no room left for one. */
    public int apple() {
        return apple;
    }

    public int score() {
        return score;
    }

    public boolean isDead() {
        return dead;
    }

    public boolean isWon() {
        return won;
    }

    /** The snake cell the head crashed into, or -1 if it hit a wall (or has not crashed). */
    public int crashCell() {
        return crashCell;
    }
}
