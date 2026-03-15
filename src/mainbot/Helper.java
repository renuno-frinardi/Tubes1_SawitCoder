package mainbot;

import battlecode.common.*;

/**
 * Kelas Helper menyediakan utility function yang digunakan oleh berbagai robot.
 *
 * Semua fungsi keputusan menggunakan algoritma greedy:
 * - greedyTowerType memilih tipe tower terbaik berdasarkan skor
 * - resourcePatternGrid menentukan warna cat berdasarkan posisi modulo
 */
public class Helper {

    /**
     * Menentukan apakah tile pada lokasi tertentu harus dicat dengan warna primary
     * dalam pola resource pattern global.
     * Menggunakan operasi modulo pada koordinat untuk menentukan pola berulang 4x4.
     *
     * @param rc  RobotController untuk mengakses informasi game
     * @param loc lokasi tile yang akan dicek
     * @return true jika tile harus dicat primary, false jika secondary
     */
    public static boolean resourcePatternGrid(RobotController rc, MapLocation loc) {
        int x = loc.x % 4;
        int y = loc.y % 4;
        return Constants.primarySRP.contains(new HashableCoords(x, y));
    }

    /**
     * Mendapatkan tipe PaintType yang sesuai untuk lokasi tertentu
     * berdasarkan pola resource pattern global.
     *
     * @param rc  RobotController untuk mengakses informasi game
     * @param loc lokasi tile yang akan dicek
     * @return PaintType.ALLY_PRIMARY atau PaintType.ALLY_SECONDARY
     */
    public static PaintType resourcePatternType(RobotController rc, MapLocation loc) {
        int x = loc.x % 4;
        int y = loc.y % 4;
        if (Constants.primarySRP.contains(new HashableCoords(x, y))) {
            return PaintType.ALLY_PRIMARY;
        }
        return PaintType.ALLY_SECONDARY;
    }

    /**
     * Mencoba menyelesaikan resource pattern di semua lokasi terdekat.
     * Greedy: cek setiap tile dalam radius 16, complete jika bisa.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void tryCompleteResourcePattern(RobotController rc) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos(16)) {
            if (rc.canCompleteResourcePattern(tile.getMapLocation())) {
                rc.completeResourcePattern(tile.getMapLocation());
            }
        }
    }

    /**
     * Mengecek apakah lokasi m berada di dalam bounding box dari c1 dan c2.
     *
     * @param m  lokasi yang akan dicek
     * @param c1 lokasi corner pertama
     * @param c2 lokasi corner kedua
     * @return true jika m berada di dalam bounding box c1-c2
     */
    public static boolean isBetween(MapLocation m, MapLocation c1, MapLocation c2) {
        int minX = Math.min(c1.x, c2.x), maxX = Math.max(c1.x, c2.x);
        int minY = Math.min(c1.y, c2.y), maxY = Math.max(c1.y, c2.y);
        return m.x >= minX && m.x <= maxX && m.y >= minY && m.y <= maxY;
    }

    /**
     * Greedy pemilihan tipe tower berdasarkan kondisi saat ini.
     * Skor dihitung berdasarkan:
     * - Jumlah tower yang sudah ada (money tower diprioritaskan di awal)
     * - Posisi ruin relatif terhadap pusat peta (defense tower untuk posisi sentral)
     * - Apakah sudah pernah melihat paint tower
     * - Bonus paint tower jika sudah banyak tower
     * Memilih tipe tower dengan skor tertinggi (greedy by maximum score).
     *
     * @param rc           RobotController untuk mengakses informasi game
     * @param ruinLocation lokasi ruin tempat tower akan dibangun
     * @return UnitType tipe tower level 1 yang dipilih
     * @throws GameActionException jika terjadi error akses game state
     */
    public static UnitType greedyTowerType(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        int numTowers = rc.getNumberTowers();

        // Greedy: prioritas money tower pada awal game
        if (numTowers <= Constants.MONEY_TOWER_FIRST_N) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        // Greedy: defense tower jika posisi dekat center dan sudah cukup tower
        int mapW = rc.getMapWidth(), mapH = rc.getMapHeight();
        int cx = mapW / 2, cy = mapH / 2;
        int dx = Math.abs(ruinLocation.x - cx), dy = Math.abs(ruinLocation.y - cy);
        boolean nearCenter = dx < mapW / 4 && dy < mapH / 4;

        if (nearCenter && numTowers >= 6) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }

        // Greedy: paint tower jika belum pernah lihat paint tower
        if (!RobotPlayer.seenPaintTower) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }

        // Greedy scoring: bandingkan paint vs money tower
        int paintScore = 50;
        int moneyScore = 70;

        // Bonus paint tower jika sudah banyak tower
        if (numTowers > 8) paintScore += 20;

        return moneyScore >= paintScore ? UnitType.LEVEL_ONE_MONEY_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER;
    }
}
