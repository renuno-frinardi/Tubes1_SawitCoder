package first_bot;

import battlecode.common.*;
import static first_bot.RobotPlayer.*;

/**
 * Kelas Mopper mengatur perilaku robot tipe Mopper.
 *
 * Semua keputusan Mopper menggunakan algoritma greedy:
 * - Pemilihan target mop (greedy by closest enemy paint)
 * - Pemilihan arah swing (greedy by maximum enemies in direction)
 * - Navigasi menggunakan greedy scoring (greedyMopperWalk, mopperScoring)
 */
public class Mopper {

    /**
     * Entry point utama untuk logika Mopper setiap turn.
     * Menggunakan pendekatan greedy untuk menentukan aksi terbaik:
     * 1. Hindari tower musuh (greedy retreat)
     * 2. Bergerak ke tile ally jika tidak di tile ally (greedy walk)
     * 3. Serang/swing robot musuh terdekat (greedy attack)
     * 4. Hapus paint musuh terdekat (greedy mop)
     * 5. Bergerak ke target paint musuh atau explore (greedy explore)
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void run(RobotController rc) throws GameActionException {
        // Debug indicator
        if (removePaint != null) {
            rc.setIndicatorString(removePaint.toString());
        } else {
            rc.setIndicatorString("null");
        }
        if (botRoundNum == 3 && rc.senseMapInfo(rc.getLocation()).getPaint().isEnemy()) {
            rc.attack(rc.getLocation());
            return;
        }

        // Baca pesan dan coba complete resource pattern
        receiveMessages(rc);
        Helper.tryCompleteResourcePattern(rc);

        MapInfo[] all = rc.senseNearbyMapInfos();

        // Greedy retreat: hindari tower musuh, bergerak ke arah berlawanan
        for (MapInfo nearbyTile : all) {
            RobotInfo bot = rc.senseRobotAtLocation(nearbyTile.getMapLocation());
            if (bot != null && bot.getType().isTowerType() && !bot.getTeam().equals(rc.getTeam())) {
                if (removePaint != null && removePaint.getMapLocation().distanceSquaredTo(bot.getLocation()) <= 9) {
                    removePaint = null;
                }
                Direction dir = rc.getLocation().directionTo(nearbyTile.getMapLocation()).rotateRight().rotateRight().rotateRight();
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    break;
                }
            }
        }

        // Greedy: jika tidak di tile ally, cari tile ally terdekat
        if (!rc.senseMapInfo(rc.getLocation()).getPaint().isAlly()) {
            Direction dir = greedyMopperWalk(rc);
            if (dir != null && rc.canMove(dir)) {
                rc.move(dir);
            }
        }

        MapLocation currPaint = null;

        // Greedy attack: cari robot musuh terdekat dan serang/swing
        for (MapInfo tile : rc.senseNearbyMapInfos(2)) {
            RobotInfo bot = rc.senseRobotAtLocation(tile.getMapLocation());
            if (bot != null) {
                if (bot.getType().isRobotType() && !bot.getTeam().equals(rc.getTeam()) && bot.getPaintAmount() > 0) {
                    if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation());
                    }
                    Direction dir = rc.getLocation().directionTo(bot.location);
                    switch (dir) {
                        case NORTH: case EAST: case SOUTH: case WEST:
                            if (rc.canMopSwing(dir)) {
                                rc.mopSwing(dir);
                                oppositeCorner = null;
                            }
                            break;
                        default:
                            if (rc.canMopSwing(dir.rotateRight())) {
                                rc.mopSwing(dir.rotateRight());
                                oppositeCorner = null;
                            }
                            break;
                    }
                    return;
                }
            }

            if (tile.getPaint().isEnemy()) {
                if (rc.canAttack(tile.getMapLocation())) {
                    currPaint = tile.getMapLocation();
                }
                oppositeCorner = null;
            }
        }

        // Greedy: cari paint musuh atau robot musuh di area yang lebih luas
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.getPaint().isEnemy()) {
                oppositeCorner = null;
                if (currPaint == null) {
                    currPaint = tile.getMapLocation();
                }
            }
            RobotInfo bot = rc.senseRobotAtLocation(tile.getMapLocation());
            if (bot != null) {
                if (bot.getType().isRobotType() && !bot.getTeam().equals(rc.getTeam()) && !tile.getPaint().isEnemy()) {
                    Direction enemyDir = Pathfinding.pathfind(rc, tile.getMapLocation());
                    if (enemyDir != null) {
                        oppositeCorner = null;
                        rc.move(enemyDir);
                        break;
                    }
                }
            }
        }

        // Greedy mop: hapus paint musuh terdekat
        if (currPaint != null) {
            if (rc.canAttack(currPaint)) {
                oppositeCorner = null;
                rc.attack(currPaint);
                return;
            } else if (rc.isActionReady()) {
                Direction dir = Pathfinding.pathfind(rc, currPaint);
                if (dir != null) {
                    oppositeCorner = null;
                    rc.move(dir);
                    if (rc.canAttack(currPaint)) {
                        rc.attack(currPaint);
                    }
                }
            }
            return;
        }

        // Greedy navigate ke target paint musuh yang diterima via pesan
        if (removePaint != null) {
            oppositeCorner = null;
            removePaintTarget(rc, removePaint);
        } else {
            // Fallback: greedy explore ke area baru
            Direction exploreDir = Pathfinding.getUnstuck(rc);
            if (exploreDir != null && rc.canMove(exploreDir)) {
                rc.move(exploreDir);
            }
        }
    }

    /**
     * Membaca pesan dari tower dan mengupdate target paint musuh.
     * Greedy: pilih target terdekat dari semua pesan yang diterima.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void receiveMessages(RobotController rc) throws GameActionException {
        for (Message msg : rc.readMessages(-1)) {
            int bytes = msg.getBytes();
            if (bytes == 3) continue;
            if (MapInfoCodec.isRobotInfo(bytes)) continue;

            MapInfo message = MapInfoCodec.decode(bytes);
            if (message.getPaint().isEnemy()) {
                MapLocation robotLoc = rc.getLocation();
                if (removePaint == null || robotLoc.distanceSquaredTo(message.getMapLocation()) < robotLoc.distanceSquaredTo(removePaint.getMapLocation())) {
                    removePaint = message;
                    resetVariables();
                }
            } else if (message.hasRuin()) {
                MapLocation robotLoc = rc.getLocation();
                if (removePaint == null || robotLoc.distanceSquaredTo(message.getMapLocation()) < robotLoc.distanceSquaredTo(removePaint.getMapLocation())) {
                    removePaint = message;
                    resetVariables();
                }
            }
        }
    }

    /**
     * Bergerak ke dan menghapus paint musuh di lokasi target.
     * Greedy: jika bisa menyerang langsung, serang. Jika tidak, bergerak mendekat.
     *
     * @param rc         RobotController untuk mengakses informasi game
     * @param enemyPaint MapInfo lokasi paint musuh yang menjadi target
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void removePaintTarget(RobotController rc, MapInfo enemyPaint) throws GameActionException {
        MapLocation enemyLoc = enemyPaint.getMapLocation();
        if (rc.canAttack(enemyLoc) && enemyPaint.getPaint().isEnemy()) {
            rc.attack(enemyLoc);
            removePaint = null;
            resetVariables();
        } else {
            Direction moveDir = Pathfinding.pathfind(rc, enemyLoc);
            if (moveDir != null) {
                rc.move(moveDir);
            }
        }
    }

    /**
     * Greedy swing: memilih arah mop swing yang mengenai musuh terbanyak.
     * Menghitung jumlah musuh di setiap arah cardinal, lalu swing ke arah
     * dengan jumlah musuh terbanyak (minimal 2 musuh).
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void trySwing(RobotController rc) throws GameActionException {
        if (rc.getActionCooldownTurns() > 10) return;

        int north = 0, east = 0, south = 0, west = 0;
        MapLocation loc = rc.getLocation();

        for (RobotInfo enemy : rc.senseNearbyRobots(2, rc.getTeam().opponent())) {
            Direction dir = loc.directionTo(enemy.getLocation());
            switch (dir) {
                case NORTH: north++; break;
                case SOUTH: south++; break;
                case WEST: west++; break;
                case EAST: east++; break;
                case NORTHWEST: north++; west++; break;
                case NORTHEAST: north++; east++; break;
                case SOUTHWEST: south++; west++; break;
                case SOUTHEAST: south++; east++; break;
                default: break;
            }
        }

        // Greedy: pilih arah swing dengan musuh terbanyak (minimal 2)
        if (north > 1 && north >= east && north >= south && north >= west) {
            if (rc.canMopSwing(Direction.NORTH)) rc.mopSwing(Direction.NORTH);
            return;
        }
        if (south > 1 && south >= east && south >= west) {
            if (rc.canMopSwing(Direction.SOUTH)) rc.mopSwing(Direction.SOUTH);
            return;
        }
        if (east > 1 && east >= west) {
            if (rc.canMopSwing(Direction.EAST)) rc.mopSwing(Direction.EAST);
            return;
        }
        if (west > 1) {
            if (rc.canMopSwing(Direction.WEST)) rc.mopSwing(Direction.WEST);
        }
    }

    /**
     * Greedy walk khusus mopper: memilih tile ally terdekat yang belum dikunjungi.
     * Skor dihitung berdasarkan:
     * - Bonus 10 untuk tile ally
     * - Penalti berdasarkan jarak (semakin jauh semakin buruk)
     * - Skip tile yang baru dikunjungi (ada di last8)
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return Direction terbaik menuju tile ally, atau null jika tidak ada
     * @throws GameActionException jika terjadi error akses game state
     */
    private static Direction greedyMopperWalk(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo tile : rc.senseNearbyMapInfos(2)) {
            if (!tile.getPaint().isAlly()) continue;
            MapLocation tileLoc = tile.getMapLocation();
            if (last8.contains(tileLoc)) continue;

            int score = 10; // Base score untuk tile ally
            int dist = myLoc.distanceSquaredTo(tileLoc);
            score -= dist; // Penalti jarak

            if (score > bestScore) {
                bestScore = score;
                bestDir = myLoc.directionTo(tileLoc);
            }
        }
        return bestDir;
    }

    /**
     * Greedy scoring untuk menentukan lokasi terbaik bagi mopper.
     * Mengevaluasi semua tile sekitar, memberi skor berdasarkan keberadaan
     * robot musuh (+100 untuk robot, -100 untuk tower).
     * Memilih lokasi dengan skor tertinggi.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return MapLocation dengan skor terbaik untuk mopper
     * @throws GameActionException jika terjadi error akses game state
     */
    public static MapLocation mopperScoring(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo map : nearbyTiles) {
            int curr = 0;
            RobotInfo bot = rc.senseRobotAtLocation(map.getMapLocation());
            if (bot != null) {
                if (!bot.getTeam().isPlayer()) {
                    if (bot.type.isRobotType()) curr += 100;
                    if (bot.type.isTowerType()) curr -= 100;
                }
            }
            if (curr > bestScore) {
                best = map.getMapLocation();
                bestScore = curr;
            }
        }
        return best;
    }
}
