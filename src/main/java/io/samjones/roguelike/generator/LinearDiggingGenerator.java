package io.samjones.roguelike.generator;

import io.samjones.roguelike.dungeon.CardinalDirection;
import io.samjones.roguelike.dungeon.Coordinate;
import io.samjones.roguelike.dungeon.Room;
import io.samjones.roguelike.dungeon.tiles.Door;
import io.samjones.roguelike.dungeon.tiles.Tile;
import io.samjones.roguelike.dungeon.tiles.Wall;

import java.util.Random;

/**
 * A simple implementation of a digging generator. This generator starts with a room, then for each subsequent room it
 * randomly tries to dig a corridor and room, stopping when it can successfully do so.
 */
public class LinearDiggingGenerator extends DiggingGenerator {
    public static final int MIN_ROOM_HEIGHT = 5;
    public static final int MIN_ROOM_WIDTH = 5;
    public static final int MAX_ROOM_HEIGHT = 15;
    public static final int MAX_ROOM_WIDTH = 15;
    public static final int MIN_CORRIDOR_LENGTH = 1;
    public static final int MAX_CORRIDOR_LENGTH = 10;
    public static final int MAX_DOOR_TRIES = 2 * (MAX_ROOM_HEIGHT + MAX_ROOM_WIDTH);
    public static final int MAX_ROOM_TRIES = 25;
    private Random random = new Random();
    private Coordinate previousOffset = new Coordinate(0, 0);

    /**
     * Calculates the offset for a corridor. Depends on the corridor being either one tile wide or one tile tall.
     *
     * @param room           the room the corridor is coming from
     * @param doorLocation   the door where the corridor starts, relative to the room
     * @param roomOffset     the offset for the room
     * @param corridorLength the length of the corridor
     * @return the offset for the corridor
     */
    private static Coordinate calculateCorridorOffset(Room room, Coordinate doorLocation, Coordinate roomOffset, int corridorLength) {
        Coordinate doorLocationInDungeon = doorLocation.add(roomOffset);
        if (doorLocation.getRow() == 0) { // top wall
            return new Coordinate(doorLocationInDungeon.getRow() - corridorLength, doorLocationInDungeon.getColumn());
        } else if (doorLocation.getRow() == room.getHeight() - 1) { // bottom wall
            return new Coordinate(doorLocationInDungeon.getRow() + 1, doorLocationInDungeon.getColumn());
        } else if (doorLocation.getColumn() == 0) { // left wall
            return new Coordinate(doorLocationInDungeon.getRow(), doorLocationInDungeon.getColumn() - corridorLength);
        } else { // right wall
            return new Coordinate(doorLocationInDungeon.getRow(), doorLocationInDungeon.getColumn() + 1);
        }
    }

    /**
     * Calculates the coordinates of the last tile of a corridor. Depends on the corridor being either one tile wide or
     * one tile tall.
     *
     * @param corridor  the corridor
     * @param direction the direction the corridor is going
     * @param offset    the offset of the corridor
     * @return the coordinates of last tile of the corridor
     */
    private static Coordinate calculateLastCorridorTile(Room corridor, CardinalDirection direction, Coordinate offset) {
        if (direction == CardinalDirection.NORTH || direction == CardinalDirection.WEST) {
            return new Coordinate(offset.getRow(), offset.getColumn());
        } else if (direction == CardinalDirection.SOUTH) {
            return new Coordinate(offset.getRow() + corridor.getHeight() - 1, offset.getColumn());
        } else { // CorridorDirection.EAST
            return new Coordinate(offset.getRow(), offset.getColumn() + corridor.getWidth() - 1);
        }
    }

    @Override
    protected Room digRoom(Room previousRoom) throws Exception {
        // TODO -refactor this beast
        Room room = generateRoom();
        if (previousRoom == null) {
            dungeon.addRoom(room, new Coordinate(0, 0));
            return room;
        } else {
            for (int i = 0; i < MAX_ROOM_TRIES; i++) {
                Coordinate doorLocation = chooseDoorLocation(previousRoom);

                int corridorLength = random.nextInt(MAX_CORRIDOR_LENGTH - MIN_CORRIDOR_LENGTH) + MIN_CORRIDOR_LENGTH;
                Coordinate corridorOffset = calculateCorridorOffset(previousRoom, doorLocation, this.previousOffset, corridorLength);

                // if corridor can be placed, attempt to place a room
                Room corridor = generateCorridor(previousRoom, doorLocation, corridorLength);
                if (this.dungeon.canAddRoom(corridor, corridorOffset)) {
                    CardinalDirection corridorDirection = previousRoom.determineWallDirection(doorLocation);
                    Coordinate lastCorridorTile = calculateLastCorridorTile(corridor, corridorDirection, corridorOffset);
                    Coordinate roomOffset = chooseRoomOffset(room, corridorDirection, lastCorridorTile);

                    if (dungeon.canAddRoom(room, roomOffset)) {
                        this.dungeon.addTile(doorLocation.add(this.previousOffset), generateDoor());
                        this.dungeon.addRoom(corridor, corridorOffset);
                        this.dungeon.addRoom(room, roomOffset);

                        Coordinate otherDoorCoords = lastCorridorTile.calculateNeighborCoordinate(corridorDirection);
                        this.dungeon.addTile(otherDoorCoords, generateDoor());

                        this.previousOffset = roomOffset;

                        return room;
                    }
                }
            }
            throw new Exception("too many tries to find a room location");
        }
    }

    private Door generateDoor() {
        return new Door(false); // TODO - generate locked and secret doors
    }

    /**
     * Generates a corridor based on the location of a door in a room.
     *
     * @param room         the room where the corridor is coming from
     * @param doorLocation the location of the door where the corridor starts
     * @param length       the length of the corridor
     * @return the Room that contains the corridor tiles
     */
    private Room generateCorridor(Room room, Coordinate doorLocation, int length) {
        if (doorLocation.getRow() == 0 || doorLocation.getRow() == room.getHeight() - 1) { // top or bottom wall
            return Room.createCorridor(length, 1);
        } else { // left or right wall
            return Room.createCorridor(1, length);
        }
    }

    /**
     * Randomly chooses the location of the door to place in the room.
     *
     * @param room the room to place a door in
     * @return the coordinate of the door, relative to the room
     * @throws Exception if there has been too many tries finding a location for the door
     */
    private Coordinate chooseDoorLocation(Room room) throws Exception {
        for (int i = 0; i < MAX_DOOR_TRIES; i++) {
            int row = random.nextInt(room.getHeight());
            int col = random.nextInt(room.getWidth());
            if (room.isNotACorner(new Coordinate(row, col))) {
                Tile tile = room.getTile(row, col);
                if (tile instanceof Wall) {
                    return new Coordinate(row, col);
                }
            }
        }
        throw new Exception("too many tries to choose a door location");
    }

    /**
     * Chooses the offset of the room, depending on where a corridor's last tile is located. The coordinate of the side adjacent to the corridor is randomly chosen.
     *
     * @param room              the room
     * @param corridorDirection the direction of the corridor going into the room
     * @param lastCorridorTile  the coordinates of the last tile in the corridor
     * @return the coordinates of the offset for the room
     */
    private Coordinate chooseRoomOffset(Room room, CardinalDirection corridorDirection, Coordinate lastCorridorTile) {
        if (corridorDirection == CardinalDirection.NORTH) {
            int row = lastCorridorTile.getRow() - room.getHeight();
            int col = random.nextInt(room.getWidth() - 2) + lastCorridorTile.getColumn() - (room.getWidth() - 2);
            return new Coordinate(row, col);
        } else if (corridorDirection == CardinalDirection.SOUTH) {
            int row = lastCorridorTile.getRow() + 1;
            int col = random.nextInt(room.getWidth() - 2) + lastCorridorTile.getColumn() - (room.getWidth() - 2);
            return new Coordinate(row, col);
        } else if (corridorDirection == CardinalDirection.WEST) {
            int row = random.nextInt(room.getHeight() - 2) + lastCorridorTile.getRow() - (room.getHeight() - 2);
            int col = lastCorridorTile.getColumn() - room.getWidth();
            return new Coordinate(row, col);
        } else { // CorridorDirection.EAST
            int row = random.nextInt(room.getHeight() - 2) + lastCorridorTile.getRow() - (room.getHeight() - 2);
            int col = lastCorridorTile.getColumn() + 1;
            return new Coordinate(row, col);
        }
    }

    private Room generateRoom() {
        int height = random.nextInt(MAX_ROOM_HEIGHT - MIN_ROOM_HEIGHT + 1) + MIN_ROOM_HEIGHT;
        int width = random.nextInt(MAX_ROOM_WIDTH - MIN_ROOM_WIDTH + 1) + MIN_ROOM_WIDTH;
        return Room.createEmptyRoom(height, width);
    }
}
