# SnakeGame

The classic Snake game for **Paper / Spigot 1.8.8**, played inside a chest menu.
Type `/game`, eat the apples, grow longer, and don't hit the walls or yourself.

The snake is built from [HeadDatabase](https://www.spigotmc.org/resources/head-database.14280/) heads:
**Snake Head** for the head and **Green Block** for the body.

## Installing

1. Put `SnakeGame-1.0.0.jar` in your server's `plugins` folder (HeadDatabase goes there too).
2. Restart the server.
3. Type `/game`.

HeadDatabase is optional. Without it the game uses plain items (a creeper head and lime wool)
until it is installed. The console says which heads were picked:

```
[SnakeGame] Using HeadDatabase head 'Snake (head)' (#8352) for snake-head.
[SnakeGame] Using HeadDatabase head 'Green Block' (#1220) for snake-body.
```

## Playing

The board takes up the whole chest. The controls are shown in your own inventory below it
while you play; your items are stored safely and given back as soon as the menu closes.

```
 Chest (the board)                       Your inventory (the controls)
 . , . , . , . , .                       [Apples]  .   .   .  [ ↑ ]  .   .   .  [Best]
 , . , . , . , . ,                       [Speed]   .   .  [ ← ][ ▶ ][ → ]  .   .  [Stats]
 . S S S . , A , .   S = snake           [Theme]   .   .   .  [ ↓ ]  .   .   .  [Help]
 , . , . , . , . ,   A = apple           Hotbar: [←1][↑2][↓3][→4]  .  [▶6]  .  [♫8][✖9]
 . , . , . , . , .
 , . , . , . , . ,
```

Three ways to steer:

- Click the arrow buttons.
- Click any square on the board, and the snake turns towards it.
- Hover over the board and press number keys **1 ← 2 ↑ 3 ↓ 4 →** (6 = start/pause, 8 = sound, 9 = quit).

Also in the menu:

- **Speed**: Slow, Normal or Fast. Each speed has its own high score, and the snake speeds up a little with every apple.
- **Theme**: Grass, Night, Desert, Ocean or Snow board colours.
- **Sound**: on or off per player.
- **Best**: your best score plus the top 5 players. **Stats**: games, apples, time played.
- When the snake crashes you get a results screen (apples, time, best, *New best!*) with **Play again**.
- Closing the menu pauses the game; `/game` continues it (for up to 10 minutes by default).

## Commands and permissions

| Command | What it does | Permission |
|---|---|---|
| `/game` (or `/snake`) | Play | `snakegame.play` (everyone) |
| `/game top [speed]` | Top 10 for a speed | `snakegame.play` |
| `/game stats [player]` | Statistics | `snakegame.play` |
| `/game help` | Command list | none |
| `/game reload` | Reload `config.yml` and `messages.yml` | `snakegame.admin` (op) |

## Configuration

`config.yml` covers rules, speeds, themes, sounds, the leaderboard and which heads are used.
`messages.yml` holds every text players see, so the game can be translated.

The heads are chosen in `config.yml`. Each entry is a list of HeadDatabase head **names or IDs**,
tried from top to bottom. Names ignore case and brackets, so `Snake Head` also finds `Snake (head)`.

```yaml
items:
  snake-head:
    hdb:
      - "Snake Head"
      - "8352"
    material: SKULL_ITEM   # used without HeadDatabase
    data: 4
  snake-body:
    hdb:
      - "Green Block"
      - "1220"
```

To use a different head, look it up with `/hdb search <name>` and put its ID (or exact name)
at the top of the list, then run `/game reload`.

Prefer not to touch players' inventories? Set `menu.use-player-inventory: false`. The board then
shrinks to 9x5 and the controls move into the bottom row of the chest.

### Safety

- Items are given back on close, quit, `/reload`, shutdown and death. On death they drop
  normally, or stay with keepInventory, and no menu items ever drop.
- While playing, a copy of the stored items is kept in `plugins/SnakeGame/inventories/`.
  If the server crashes mid-game, the items come back on the next join, and nothing is
  duplicated if the server had already saved the player before the game started.

## Building

```
mvn package
```

This needs Java 8 or newer and produces `target/SnakeGame-1.0.0.jar`. The engine has unit tests
(`mvn test`).
