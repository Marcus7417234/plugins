package io.github.marcus7417234.snakegame.engine;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SnakeEngineTest {

    private static final int W = 9;

    private static int cell(int x, int y) {
        return cell(W, x, y);
    }

    private static int cell(int width, int x, int y) {
        return y * width + x;
    }

    private static SnakeEngine engine(int height, int startLength, boolean wrap) {
        return new SnakeEngine(W, height, startLength, 1, wrap, new Random(42));
    }

    @Test
    public void startsOnMiddleRowFacingRightWithAppleAhead() {
        SnakeEngine engine = engine(6, 3, false);
        assertEquals(Arrays.asList(cell(3, 2), cell(2, 2), cell(1, 2)), engine.body());
        assertEquals(Direction.RIGHT, engine.direction());
        assertEquals(cell(6, 2), engine.apple());
        assertEquals(0, engine.score());
    }

    @Test
    public void startLengthIsCappedToLeaveRoomAhead() {
        SnakeEngine engine = engine(5, 20, false);
        assertEquals(W - 3, engine.length());
    }

    @Test
    public void movesForwardWithoutGrowing() {
        SnakeEngine engine = engine(6, 3, false);
        assertEquals(SnakeEngine.Result.MOVED, engine.step());
        assertEquals(Arrays.asList(cell(4, 2), cell(3, 2), cell(2, 2)), engine.body());
        assertFalse(engine.isSnake(cell(1, 2)));
    }

    @Test
    public void eatingGrowsByOneAndSpawnsTheNextAppleOnAFreeCell() {
        SnakeEngine engine = engine(6, 3, false);
        engine.step();
        engine.step();
        assertEquals(SnakeEngine.Result.ATE, engine.step()); // head reaches the apple at (6,2)
        assertEquals(1, engine.score());
        assertEquals(4, engine.length());
        assertTrue(engine.apple() >= 0);
        assertFalse(engine.isSnake(engine.apple()));
        assertEquals(SnakeEngine.Result.MOVED, engine.step());
        assertEquals(4, engine.length());
    }

    @Test
    public void growthPerAppleAddsSeveralSegmentsOverTheNextMoves() {
        // New apples always spawn in the last free cell (bottom right), out of the way.
        Random lastFreeCell = new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };
        SnakeEngine engine = SnakeEngine.withSnake(W, 6, false,
                Arrays.asList(cell(3, 2)), Direction.RIGHT, cell(4, 2), 0, 3, lastFreeCell);
        assertEquals(SnakeEngine.Result.ATE, engine.step());
        assertEquals(cell(8, 5), engine.apple());
        assertEquals(2, engine.length());
        engine.step();
        assertEquals(3, engine.length());
        engine.step();
        assertEquals(4, engine.length());
        engine.step();
        assertEquals(4, engine.length());
    }

    @Test
    public void hittingTheWallEndsTheGame() {
        SnakeEngine engine = engine(6, 3, false);
        SnakeEngine.Result result = null;
        for (int i = 0; i < 10 && !engine.isDead(); i++) {
            result = engine.step();
        }
        assertEquals(SnakeEngine.Result.DIED, result);
        assertTrue(engine.isDead());
        assertEquals(-1, engine.crashCell());
        assertEquals(cell(8, 2), engine.head());
        assertEquals(SnakeEngine.Result.DIED, engine.step()); // stays dead
    }

    @Test
    public void wrapsAroundWhenWallsAreOff() {
        SnakeEngine engine = SnakeEngine.withSnake(W, 6, true,
                Arrays.asList(cell(8, 0)), Direction.RIGHT, cell(4, 4), 0, 1, new Random(0));
        assertEquals(SnakeEngine.Result.MOVED, engine.step());
        assertEquals(cell(0, 0), engine.head());
        engine.turn(Direction.UP);
        assertEquals(SnakeEngine.Result.MOVED, engine.step());
        assertEquals(cell(0, 5), engine.head());
    }

    @Test
    public void cannotReverseIntoItself() {
        SnakeEngine engine = engine(6, 3, false);
        assertFalse(engine.turn(Direction.LEFT));
        assertFalse(engine.turn(Direction.RIGHT)); // already heading right
        assertTrue(engine.turn(Direction.UP));
        assertFalse(engine.turn(Direction.DOWN)); // opposite of the queued turn
        engine.step();
        assertEquals(cell(3, 1), engine.head());
    }

    @Test
    public void aSingleSegmentSnakeMayReverse() {
        SnakeEngine engine = SnakeEngine.withSnake(W, 6, false,
                Arrays.asList(cell(4, 2)), Direction.RIGHT, cell(0, 0), 0, 1, new Random(0));
        assertTrue(engine.turn(Direction.LEFT));
        engine.step();
        assertEquals(cell(3, 2), engine.head());
    }

    @Test
    public void quickTurnsAreQueuedForTheNextMoves() {
        SnakeEngine engine = engine(6, 3, false);
        assertTrue(engine.turn(Direction.UP));
        assertTrue(engine.turn(Direction.LEFT));
        assertEquals(Direction.LEFT, engine.plannedDirection());
        engine.step();
        assertEquals(cell(3, 1), engine.head());
        engine.step();
        assertEquals(cell(2, 1), engine.head());
        assertEquals(Direction.LEFT, engine.direction());
    }

    @Test
    public void theTurnQueueIsLimited() {
        SnakeEngine engine = engine(6, 3, false);
        assertTrue(engine.turn(Direction.UP));
        assertTrue(engine.turn(Direction.LEFT));
        assertTrue(engine.turn(Direction.DOWN));
        assertFalse(engine.turn(Direction.RIGHT));
    }

    @Test
    public void runningIntoItsOwnBodyEndsTheGame() {
        // U-shape: head at (2,2) heading left, body curls up and around.
        SnakeEngine engine = SnakeEngine.withSnake(W, 6, false,
                Arrays.asList(cell(2, 2), cell(3, 2), cell(3, 1), cell(2, 1), cell(1, 1)),
                Direction.LEFT, cell(7, 4), 0, 1, new Random(0));
        engine.turn(Direction.UP); // into (2,1), which is body, not the tail
        assertEquals(SnakeEngine.Result.DIED, engine.step());
        assertEquals(cell(2, 1), engine.crashCell());
    }

    @Test
    public void theHeadMayMoveIntoTheCellTheTailIsLeaving() {
        // A 2x2 loop of four segments: the head chases its tail.
        SnakeEngine engine = SnakeEngine.withSnake(W, 6, false,
                Arrays.asList(cell(0, 1), cell(1, 1), cell(1, 0), cell(0, 0)),
                Direction.LEFT, cell(5, 5), 0, 1, new Random(0));
        engine.turn(Direction.UP);
        assertEquals(SnakeEngine.Result.MOVED, engine.step());
        assertEquals(cell(0, 0), engine.head());
        assertEquals(4, engine.length());
    }

    @Test
    public void butNotWhileGrowing() {
        SnakeEngine engine = SnakeEngine.withSnake(W, 6, false,
                Arrays.asList(cell(0, 1), cell(1, 1), cell(1, 0), cell(0, 0)),
                Direction.LEFT, cell(5, 5), 1, 1, new Random(0));
        engine.turn(Direction.UP);
        assertEquals(SnakeEngine.Result.DIED, engine.step());
        assertEquals(cell(0, 0), engine.crashCell());
    }

    @Test
    public void fillingTheWholeBoardWinsTheGame() {
        // Follow a loop through every cell of a 4x2 board until no room is left for an apple.
        int width = 4;
        SnakeEngine engine = new SnakeEngine(width, 2, 1, 1, false, new Random(11));
        SnakeEngine.Result result = null;
        for (int moves = 0; moves < 500 && !engine.isWon() && !engine.isDead(); moves++) {
            int head = engine.head();
            if (head == cell(width, 3, 0)) {
                engine.turn(Direction.DOWN);
            } else if (head == cell(width, 3, 1)) {
                engine.turn(Direction.LEFT);
            } else if (head == cell(width, 0, 1)) {
                engine.turn(Direction.UP);
            } else if (head == cell(width, 0, 0)) {
                engine.turn(Direction.RIGHT);
            }
            result = engine.step();
        }
        assertTrue(engine.isWon());
        assertFalse(engine.isDead());
        assertEquals(SnakeEngine.Result.WON, result);
        assertEquals(8, engine.length());
        assertEquals(7, engine.score());
        assertEquals(-1, engine.apple());
        assertFalse(engine.turn(Direction.UP));
    }

    @Test
    public void applesAlwaysLandOnFreeCells() {
        for (long seed = 0; seed < 200; seed++) {
            SnakeEngine engine = new SnakeEngine(W, 5, 3, 1, true, new Random(seed));
            Random steering = new Random(seed);
            for (int moves = 0; moves < 300 && !engine.isDead() && !engine.isWon(); moves++) {
                engine.turn(Direction.values()[steering.nextInt(4)]);
                engine.step();
                if (!engine.isDead() && engine.apple() >= 0) {
                    assertFalse(engine.isSnake(engine.apple()));
                }
                assertEquals(engine.length(), engine.body().size());
            }
        }
    }
}
