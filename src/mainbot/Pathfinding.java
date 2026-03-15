package mainbot;

import battlecode.common.*;
import java.util.ArrayList;
import java.util.List;

import static mainbot.RobotPlayer.*;

/**
 * Kelas Pathfinding menyediakan berbagai metode navigasi berbasis algoritma greedy.
 *
 * Semua metode pathfinding pada kelas ini menggunakan pendekatan greedy:
 * pada setiap langkah, robot mengevaluasi semua arah yang mungkin,
 * menghitung skor untuk masing-masing, lalu memilih arah dengan skor tertinggi.
 * Tidak ada algoritma search (BFS/DFS/A*) yang digunakan.
 */
public class Pathfinding {

    /** Offset arah eksplorasi 2 langkah ke 8 arah diagonal dan cardinal. */
    public static int[][] directions = {{-2,-2},{-2,0},{-2,2},{0,-2},{0,2},{2,-2},{2,0},{2,2}};

    /**
     * Greedy pathfinding dasar menuju target.
     * Mengevaluasi semua 8 arah yang bisa ditempuh, menghitung skor berdasarkan:
     * - Jarak kuadrat ke target (semakin dekat semakin baik)
     * - Bonus jika tile berwarna ally (+5)
     * - Penalti jika tile berwarna enemy (-8)
     * Memilih arah dengan skor tertinggi (greedy by best local score).
     *
     * @param rc     RobotController untuk mengakses informasi game
     * @param target lokasi tujuan yang ingin dicapai
     * @return Direction terbaik menuju target, atau null jika tidak ada arah valid
     * @throws GameActionException jika terjadi error akses game state
     */
    public static Direction greedyPathfind(RobotController rc, MapLocation target) throws GameActionException {
        int bestScore = Integer.MIN_VALUE;
        MapLocation curLocation = rc.getLocation();
        Direction bestDir = null;

        for (Direction dir : Constants.directions) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = curLocation.add(dir);
            MapInfo info = rc.senseMapInfo(next);
            int dist = next.distanceSquaredTo(target);

            // Greedy scoring: prioritaskan jarak terdekat dan tile ally
            int score = -dist * 2;
            PaintType paint = info.getPaint();
            if (paint.isAlly()) score += 5;
            else if (paint.isEnemy()) score -= 8;

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }
        return bestDir;
    }

    /**
     * Greedy pathfinding yang lebih memprioritaskan tile berwarna ally.
     * Mirip dengan greedyPathfind, namun memberikan bobot lebih besar pada warna paint.
     * Skor dihitung berdasarkan:
     * - Jarak kuadrat ke target (semakin dekat semakin baik)
     * - Bonus besar jika tile ally (+12)
     * - Bonus kecil jika tile kosong (+2)
     * - Penalti besar jika tile enemy (-10)
     * - Penalti jika tile sudah dikunjungi baru-baru ini (-4)
     * Memilih arah dengan skor tertinggi (greedy by paint-weighted score).
     *
     * @param rc     RobotController untuk mengakses informasi game
     * @param target lokasi tujuan yang ingin dicapai
     * @return Direction terbaik menuju target via tile ally, atau null jika tidak ada
     * @throws GameActionException jika terjadi error akses game state
     */
    public static Direction paintedPathfind(RobotController rc, MapLocation target) throws GameActionException {
        int bestScore = Integer.MIN_VALUE;
        MapLocation curLocation = rc.getLocation();
        Direction bestDir = null;

        for (Direction dir : Constants.directions) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = curLocation.add(dir);
            MapInfo info = rc.senseMapInfo(next);

            int score = -next.distanceSquaredTo(target) * 2;
            PaintType paint = info.getPaint();
            if (paint.isAlly()) score += 12;
            else if (paint == PaintType.EMPTY) score += 2;
            else score -= 10;
            if (last8.contains(next)) score -= 4;

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }
        return bestDir;
    }

    /**
     * Mengembalikan robot ke tower terdekat yang diketahui menggunakan greedy pathfinding.
     * Jika paint sangat rendah (< 6), menggunakan paintedPathfind agar menghindari tile enemy.
     * Jika paint cukup, menggunakan pathfind biasa (yang juga greedy).
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return Direction menuju tower terdekat, atau null jika tidak bisa bergerak
     * @throws GameActionException jika terjadi error akses game state
     */
    public static Direction returnToTower(RobotController rc) throws GameActionException {
        if (rc.getPaint() < 6) {
            return paintedPathfind(rc, lastTower.getMapLocation());
        }
        return pathfind(rc, lastTower.getMapLocation());
    }

    /**
     * Eksplorasi greedy dua tahap: memilih intermediate target terbaik, lalu bergerak ke sana.
     *
     * Tahap 1 - Pemilihan intermediate target (greedy):
     *   Mengevaluasi 8 posisi 2-langkah dari posisi saat ini, menghitung skor
     *   berdasarkan scoreTile() dan bonus kedekatan ke target utama.
     *   Memilih posisi dengan skor tertinggi sebagai intermediate target.
     *
     * Tahap 2 - Bergerak ke intermediate target menggunakan greedy pathfind.
     *
     * Jika skor area sekitar cukup tinggi (> EXPLORE_BREAK_THRESHOLD),
     * intermediate target di-reset agar robot dapat mengeksplorasi area baru.
     *
     * @param rc             RobotController untuk mengakses informasi game
     * @param curLocation    lokasi robot saat ini
     * @param target         lokasi tujuan akhir eksplorasi
     * @param careAboutEnemy apakah mempertimbangkan paint enemy dalam scoring
     * @return Direction menuju intermediate target, atau null jika tidak bisa bergerak
     * @throws GameActionException jika terjadi error akses game state
     */
    public static Direction betterExplore(RobotController rc, MapLocation curLocation, MapLocation target, boolean careAboutEnemy) throws GameActionException {
        // Greedy break: jika area sekitar sudah cukup baik, reset intermediate target
        if (intermediateTarget != null) {
            int breakScore = 0;
            int[][] breakDirs = {{-2,-2},{2,-2},{-2,2},{2,2}};
            for (int[] d : breakDirs) {
                MapLocation p = new MapLocation(curLocation.x + d[0], curLocation.y + d[1]);
                if (rc.onTheMap(p)) {
                    breakScore = Math.max(breakScore, scoreTile(rc, p, false));
                }
            }
            if (breakScore > Constants.EXPLORE_BREAK_THRESHOLD) {
                intermediateTarget = null;
                RobotPlayer.resetVariables();
            }
        }

        // Greedy: pilih intermediate target baru jika belum ada atau sudah tercapai
        if (intermediateTarget == null || curLocation.equals(intermediateTarget)
            || (curLocation.isWithinDistanceSquared(intermediateTarget, 2)
                && !rc.senseMapInfo(intermediateTarget).isPassable())) {

            if (curLocation.equals(intermediateTarget)) {
                RobotPlayer.resetVariables();
            }

            // Evaluasi 8 arah, pilih yang skornya tertinggi (greedy)
            int bestScore = Integer.MIN_VALUE;
            int bestIdx = -1;
            int curDistance = curLocation.distanceSquaredTo(target);

            for (int i = 0; i < 8; i++) {
                MapLocation possibleTarget = curLocation.translate(directions[i][0], directions[i][1]);
                if (!rc.onTheMap(possibleTarget)) continue;

                int score = scoreTile(rc, possibleTarget, careAboutEnemy);
                int newDistance = possibleTarget.distanceSquaredTo(target);

                if (curDistance > newDistance) score += Constants.EXPLORE_CLOSER_BONUS;
                else if (curDistance == newDistance) score += Constants.EXPLORE_EQUAL_BONUS;

                if (score > bestScore) {
                    bestScore = score;
                    bestIdx = i;
                }
            }

            if (bestIdx >= 0) {
                intermediateTarget = curLocation.translate(directions[bestIdx][0], directions[bestIdx][1]);
            }
        }

        if (intermediateTarget == null) return null;
        return pathfind(rc, intermediateTarget);
    }

    /**
     * Menghitung skor greedy untuk sebuah tile berdasarkan kondisi sekitarnya.
     * Skor dihitung dari jumlah tile kosong, paint enemy, tile tidak bisa dilewati,
     * dan keberadaan robot ally di sekitarnya.
     *
     * @param rc             RobotController untuk mengakses informasi game
     * @param tile           lokasi tile yang akan dievaluasi
     * @param careAboutEnemy apakah paint enemy mempengaruhi skor
     * @return skor integer untuk tile tersebut (semakin tinggi semakin baik)
     * @throws GameActionException jika terjadi error akses game state
     */
    public static int scoreTile(RobotController rc, MapLocation tile, boolean careAboutEnemy) throws GameActionException {
        MapInfo[] surroundingTiles = rc.senseNearbyMapInfos(tile, 2);
        int count = 30;
        for (MapInfo st : surroundingTiles) {
            if (st.getPaint().isEnemy() && careAboutEnemy) count += Constants.EXPLORE_ENEMY_PAINT;
            if (st.getPaint() == PaintType.EMPTY && st.isPassable()) count += Constants.EXPLORE_EMPTY_TILE;
            if (!st.isPassable()) count -= 2;
            MapLocation stLoc = st.getMapLocation();
            if (rc.canSenseRobotAtLocation(stLoc) && rc.senseRobotAtLocation(stLoc).getTeam() == rc.getTeam()) {
                count -= Constants.EXPLORE_ALLY_ROBOT_PENALTY;
            }
        }
        return count;
    }

    /**
     * Eksplorasi greedy menuju tile yang belum dicat (unpainted).
     * 
     * Tahap 1: Evaluasi tile adjacent (jarak ≤ 2) yang kosong dan passable,
     *          pilih yang memiliki countEmptyAround tertinggi (greedy).
     * Tahap 2: Jika tidak ada tile adjacent yang cocok, evaluasi tile satu langkah
     *          di semua 8 arah, pilih yang countEmptyAround tertinggi (greedy).
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return Direction menuju tile unpainted terbaik, atau null jika tidak ditemukan
     * @throws GameActionException jika terjadi error akses game state
     */
    public static Direction exploreUnpainted(RobotController rc) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        MapInfo bestTile = null;
        int bestScore = Integer.MIN_VALUE;

        // Greedy: cari tile adjacent kosong dengan area kosong terbanyak
        for (MapInfo adj : rc.senseNearbyMapInfos(2)) {
            if (adj.getPaint() == PaintType.EMPTY && adj.isPassable() && !last8.contains(adj.getMapLocation())) {
                int score = countEmptyAround(rc, adj.getMapLocation().add(curLoc.directionTo(adj.getMapLocation())));
                if (score > bestScore) {
                    bestScore = score;
                    bestTile = adj;
                }
            }
        }

        // Fallback greedy: jika tidak ada adjacent, cari di arah yang lebih jauh
        if (bestTile == null) {
            for (Direction dir : Constants.directions) {
                MapLocation farther = curLoc.add(dir);
                if (rc.onTheMap(farther) && rc.senseMapInfo(farther).isPassable()) {
                    int score = countEmptyAround(rc, farther);
                    if (score > bestScore) {
                        bestScore = score;
                        bestTile = rc.senseMapInfo(farther);
                    }
                }
            }
        }

        if (bestTile == null) return null;
        return pathfind(rc, bestTile.getMapLocation());
    }

    /**
     * Menghitung jumlah tile kosong dan passable di sekitar sebuah lokasi (radius 2).
     * Digunakan sebagai heuristik greedy untuk menentukan area mana yang paling
     * banyak tile kosong untuk dicat.
     *
     * @param rc     RobotController untuk mengakses informasi game
     * @param center lokasi pusat area yang akan dihitung
     * @return jumlah tile kosong dan passable di sekitar center
     * @throws GameActionException jika terjadi error akses game state
     */
    public static int countEmptyAround(RobotController rc, MapLocation center) throws GameActionException {
        if (!rc.onTheMap(center)) return 0;
        MapInfo[] surrounding = rc.senseNearbyMapInfos(center, 2);
        int count = 0;
        for (MapInfo st : surrounding) {
            if (st.getPaint() == PaintType.EMPTY && st.isPassable()
                && !rc.canSenseRobotAtLocation(st.getMapLocation())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Greedy unstuck: memilih corner peta terjauh dari posisi saat ini, lalu
     * bergerak ke sana menggunakan greedy pathfind.
     * Digunakan ketika robot terjebak dan perlu berpindah ke area baru.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return Direction menuju corner terjauh, atau null jika tidak bisa bergerak
     * @throws GameActionException jika terjadi error akses game state
     */
    public static Direction getUnstuck(RobotController rc) throws GameActionException {
        if (oppositeCorner == null || rc.getLocation().distanceSquaredTo(oppositeCorner) <= 20) {
            int x = rc.getLocation().x, y = rc.getLocation().y;
            int w = rc.getMapWidth(), h = rc.getMapHeight();

            // Greedy: pilih corner dengan jarak terbesar dari posisi saat ini
            MapLocation[] corners = {
                new MapLocation(0, 0), new MapLocation(w - 1, 0),
                new MapLocation(0, h - 1), new MapLocation(w - 1, h - 1)
            };
            int maxDist = -1;
            for (MapLocation c : corners) {
                int d = rc.getLocation().distanceSquaredTo(c);
                if (d > maxDist) {
                    maxDist = d;
                    oppositeCorner = c;
                }
            }
        }
        return pathfind(rc, oppositeCorner);
    }

    /**
     * Greedy: memilih corner terdekat yang belum dikunjungi (jarak > 8) dan
     * bergerak ke sana. Digunakan oleh soldier tipe dev/SRP untuk kembali ke area sendiri.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return Direction menuju corner terdekat yang valid, atau null jika tidak bisa
     * @throws GameActionException jika terjadi error akses game state
     */
    public static Direction findOwnCorner(RobotController rc) throws GameActionException {
        prevIntermediate = intermediateTarget;
        intermediateTarget = null;

        if (oppositeCorner == null || rc.getLocation().distanceSquaredTo(oppositeCorner) <= 8) {
            int x = rc.getLocation().x, y = rc.getLocation().y;
            int w = rc.getMapWidth(), h = rc.getMapHeight();

            // Greedy: pilih corner terdekat yang jaraknya > 8
            MapLocation[] corners = {
                new MapLocation(0, 0), new MapLocation(w - 1, 0),
                new MapLocation(0, h - 1), new MapLocation(w - 1, h - 1)
            };
            int bestDist = Integer.MAX_VALUE;
            for (MapLocation c : corners) {
                int d = rc.getLocation().distanceSquaredTo(c);
                if (d > 8 && d < bestDist) {
                    bestDist = d;
                    oppositeCorner = c;
                }
            }
            // Fallback: jika semua corner terlalu dekat, gunakan getUnstuck
            if (bestDist == Integer.MAX_VALUE) {
                return getUnstuck(rc);
            }
        }
        return pathfind(rc, oppositeCorner);
    }

    /**
     * Greedy walk sederhana: memilih arah terbaik berdasarkan warna paint saja,
     * tanpa target tertentu. Mengutamakan tile ally, menghindari tile enemy,
     * dan menghindari tile yang baru dikunjungi.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return Direction terbaik berdasarkan paint, atau null jika tidak ada arah valid
     * @throws GameActionException jika terjadi error akses game state
     */
    public static Direction greedyWalk(RobotController rc) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : Constants.directions) {
            MapLocation next = curLoc.add(dir);
            if (!rc.canMove(dir)) continue;
            if (last8.contains(next)) continue;
            MapInfo info = rc.senseMapInfo(next);
            int score = 0;
            if (info.getPaint().isAlly()) score += 10;
            if (info.getPaint() == PaintType.EMPTY) score += 3;
            if (info.getPaint().isEnemy()) score -= 5;

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }
        return bestDir;
    }

    /**
     * Entry point utama untuk pathfinding. Mendelegasikan ke greedyPathfind()
     * setelah melakukan pengecekan dasar (target null, sudah di lokasi, movement ready).
     *
     * @param rc     RobotController untuk mengakses informasi game
     * @param target lokasi tujuan yang ingin dicapai
     * @return Direction terbaik menuju target, atau null jika tidak bisa bergerak
     * @throws GameActionException jika terjadi error akses game state
     */
    public static Direction pathfind(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null || !rc.isMovementReady()) return null;
        if (rc.getLocation().equals(target)) {
            RobotPlayer.resetVariables();
            return null;
        }
        return greedyPathfind(rc, target);
    }
}
