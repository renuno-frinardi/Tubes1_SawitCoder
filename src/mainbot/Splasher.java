package mainbot;

import battlecode.common.*;
import static mainbot.RobotPlayer.*;

/**
 * Kelas Splasher mengatur perilaku robot tipe Splasher.
 *
 * Semua keputusan Splasher menggunakan algoritma greedy:
 * - Pemilihan tile target splash (greedy by maximum splash score)
 * - Evaluasi setiap tile berdasarkan jumlah tile empty dan enemy di area splash
 * - Navigasi ke target menggunakan greedy pathfind
 */
public class Splasher {

    /**
     * Entry point utama untuk logika Splasher setiap turn.
     * Menggunakan pendekatan greedy:
     * 1. Cek paint rendah → kembali ke tower (greedy refill)
     * 2. Hindari tower musuh (greedy retreat)
     * 3. Cari tile splash terbaik (greedy by score)
     * 4. Navigasi ke target atau explore (greedy pathfind)
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void run(RobotController rc) throws GameActionException {
        // Baca pesan dari tower
        receiveMessages(rc);

        // Update tower cat terdekat (greedy minimum distance)
        Soldier.updateLastPaintTower(rc);

        // Validasi last tower masih ada
        if (lastTower != null && rc.canSenseLocation(lastTower.getMapLocation())) {
            if (!rc.canSenseRobotAtLocation(lastTower.getMapLocation())) {
                lastTower = null;
            }
        }

        // Greedy refill: kembali ke tower jika paint rendah
        if (rc.getPaint() < Constants.SPLASHER_LOW_PAINT && rc.getMoney() < Constants.LOW_PAINT_MONEY_THRESHOLD) {
            if (!isLowPaint) {
                prevLocInfo = rc.senseMapInfo(rc.getLocation());
            }
            Soldier.lowPaintBehavior(rc);
            return;
        } else if (isLowPaint) {
            if (removePaint == null) removePaint = prevLocInfo;
            prevLocInfo = null;
            isLowPaint = false;
        }

        // Greedy retreat: hindari tower musuh
        for (RobotInfo bot : rc.senseNearbyRobots()) {
            if (bot.getType().isTowerType() && !bot.getTeam().equals(rc.getTeam())) {
                Direction dir = rc.getLocation().directionTo(bot.getLocation()).rotateRight().rotateRight().rotateRight();
                if (rc.canMove(dir)) rc.move(dir);
                if (removePaint != null && removePaint.getMapLocation().distanceSquaredTo(bot.getLocation()) <= 9) {
                    removePaint = null;
                }
            }
        }

        // Greedy: cari tile splash terbaik berdasarkan scoring
        MapInfo bestSplash = scoreSplasherTiles(rc);

        // Reset removePaint jika sudah berwarna ally
        if (removePaint != null && rc.canSenseLocation(removePaint.getMapLocation())
            && rc.senseMapInfo(removePaint.getMapLocation()).getPaint().isAlly()) {
            removePaint = null;
        }

        // Greedy: splash tile terbaik, atau bergerak mendekati
        if (bestSplash != null && rc.canAttack(bestSplash.getMapLocation())) {
            rc.attack(bestSplash.getMapLocation());
            return;
        } else if (bestSplash != null) {
            if (removePaint == null) removePaint = bestSplash;
            Direction dir = Pathfinding.pathfind(rc, bestSplash.getMapLocation());
            if (dir != null) rc.move(dir);
            return;
        } else if (removePaint != null) {
            if (rc.canAttack(removePaint.getMapLocation())) {
                rc.attack(removePaint.getMapLocation());
                return;
            }
            Direction dir = Pathfinding.pathfind(rc, removePaint.getMapLocation());
            if (rc.getActionCooldownTurns() < 10 && dir != null) rc.move(dir);
            return;
        }

        // Fallback: greedy explore ke area baru
        if (botRoundNum > 1) {
            Direction dir = Pathfinding.getUnstuck(rc);
            if (dir != null && rc.canMove(dir)) rc.move(dir);
        }
    }

    /**
     * Membaca pesan dari tower dan mengupdate target paint musuh atau ruin musuh.
     * Greedy: pilih target terdekat dari semua pesan yang diterima.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void receiveMessages(RobotController rc) throws GameActionException {
        for (Message msg : rc.readMessages(-1)) {
            int bytes = msg.getBytes();
            if (bytes == 4) continue;
            if (MapInfoCodec.isRobotInfo(bytes)) continue;

            MapInfo message = MapInfoCodec.decode(bytes);
            if (message.getPaint().isEnemy()) {
                MapLocation robotLoc = rc.getLocation();
                if (removePaint == null || robotLoc.distanceSquaredTo(message.getMapLocation()) < robotLoc.distanceSquaredTo(removePaint.getMapLocation())) {
                    removePaint = message;
                    resetVariables();
                }
            } else if (message.hasRuin()) {
                if (removePaint == null) {
                    removePaint = message;
                    resetVariables();
                }
            }
        }
    }

    /**
     * Greedy scoring untuk menentukan tile splash terbaik.
     *
     * Tahap 1: Bangun grid skor untuk setiap tile terlihat berdasarkan paint:
     *   - EMPTY & passable → SPLASH_EMPTY (0)
     *   - Enemy paint → SPLASH_ENEMY (2, positif = bagus untuk di-splash)
     *   - Ally paint → SPLASH_ALLY (-1, negatif = jangan di-splash)
     *
     * Tahap 2: Evaluasi setiap tile yang bisa diserang, hitung total skor
     *   splash area (13 tile cross pattern). Pilih tile dengan skor tertinggi.
     *
     * Tahap 3: Jika tidak ada tile dalam jangkauan, cek 4 tile jauh di arah cardinal.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return MapInfo tile splash terbaik, atau null jika skor semua <= 0
     * @throws GameActionException jika terjadi error akses game state
     */
    private static MapInfo scoreSplasherTiles(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Tahap 1: Greedy grid scoring - klasifikasi setiap tile
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            PaintType paint = tile.getPaint();

            if (paint == PaintType.EMPTY && tile.isPassable()) {
                currGrid[loc.x][loc.y] = Constants.SPLASH_EMPTY;
            } else if (paint.isEnemy()) {
                currGrid[loc.x][loc.y] = Constants.SPLASH_ENEMY;
            } else if (paint.isAlly()) {
                currGrid[loc.x][loc.y] = Constants.SPLASH_ALLY;
            } else {
                currGrid[loc.x][loc.y] = Constants.SPLASH_ALLY;
            }
        }

        // Tahap 2: Greedy tile selection - pilih tile dengan skor splash tertinggi
        MapInfo best = null;
        int bestScore = -1;
        for (MapInfo tile : nearbyTiles) {
            if (tile.isPassable() && rc.canAttack(tile.getMapLocation())) {
                int score = scoreSplash(rc, tile);
                if (score > bestScore) {
                    bestScore = score;
                    best = tile;
                }
            }
        }

        // Tahap 3: Fallback greedy - cek tile jauh jika tidak ada dalam jangkauan
        if (best == null) {
            int x = rc.getLocation().x, y = rc.getLocation().y;
            int h = rc.getMapHeight(), w = rc.getMapWidth();
            int[][] farDirs = {{0,4},{4,0},{0,-4},{-4,0}};
            for (int[] d : farDirs) {
                int nx = x + d[0], ny = y + d[1];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                    MapInfo tile = rc.senseMapInfo(new MapLocation(nx, ny));
                    int score = scoreSplash(rc, tile);
                    if (score > bestScore) {
                        bestScore = score;
                        best = tile;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Menghitung skor splash untuk sebuah tile berdasarkan cross pattern (13 tile).
     * Skor dihitung dengan menjumlahkan nilai grid dari semua tile yang terkena splash.
     * Tile enemy memberikan skor positif, tile ally memberikan skor negatif.
     *
     * @param rc   RobotController untuk mengakses informasi game
     * @param tile MapInfo tile pusat yang akan dievaluasi
     * @return skor total splash (semakin tinggi semakin baik)
     * @throws GameActionException jika terjadi error akses game state
     */
    private static int scoreSplash(RobotController rc, MapInfo tile) throws GameActionException {
        int out = 0;
        MapLocation loc = tile.getMapLocation();
        int x = loc.x, y = loc.y;
        int h = rc.getMapHeight(), w = rc.getMapWidth();

        // Greedy sum: jumlahkan skor 13 tile dalam cross pattern
        if (y > 1) out += currGrid[x][y-2];
        if (x > 0 && y > 0) out += currGrid[x-1][y-1];
        if (y > 0) out += currGrid[x][y-1];
        if (x < w-1 && y > 0) out += currGrid[x+1][y-1];
        if (x > 1) out += currGrid[x-2][y];
        if (x > 0) out += currGrid[x-1][y];
        out += currGrid[x][y];
        if (x < w-1) out += currGrid[x+1][y];
        if (x < w-2) out += currGrid[x+2][y];
        if (x > 0 && y < h-1) out += currGrid[x-1][y+1];
        if (y < h-1) out += currGrid[x][y+1];
        if (x < w-1 && y < h-1) out += currGrid[x+1][y+1];
        if (y < h-2) out += currGrid[x][y+2];

        return out;
    }
}
