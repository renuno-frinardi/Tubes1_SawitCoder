package main_bot;

import battlecode.common.*;
import static main_bot.RobotPlayer.*;

/**
 * Kelas Tower mengatur perilaku tower (bangunan pertahanan/ekonomi).
 *
 * Semua keputusan Tower menggunakan algoritma greedy:
 * - Pemilihan unit yang di-spawn (greedyPickUnit) menggunakan scoring
 * - Serangan memilih target dengan HP terendah (greedy by minimum HP)
 * - Upgrade dilakukan berdasarkan threshold uang
 */
public class Tower {

    /** Jumlah soldier yang telah di-spawn oleh tower ini. */
    private static int soldiersBuilt = 0;
    /** Jumlah splasher yang telah di-spawn oleh tower ini. */
    private static int splashersBuilt = 0;
    /** Jumlah mopper yang telah di-spawn oleh tower ini. */
    private static int moppersBuilt = 0;

    /**
     * Entry point utama untuk logika Tower setiap turn.
     * Menggunakan pendekatan greedy: mengevaluasi kondisi terkini dan memilih
     * aksi terbaik (spawn, attack, upgrade, broadcast) secara berurutan.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void run(RobotController rc) throws GameActionException {
        // Greedy: pilih arah spawn terbaik (sekali saja, saat pertama kali)
        if (spawnDirection == null) {
            spawnDirection = pickSpawnDirection(rc);
        }

        roundsWithoutEnemy++;

        // Baca pesan dari unit-unit sekitar
        readMessages(rc);

        // Broadcast info musuh jika ada
        if (broadcast) {
            rc.broadcastMessage(MapInfoCodec.encode(enemyTarget));
            broadcast = false;
        }

        // Kirim pesan tipe ke unit yang baru di-spawn
        if (sendTypeMessage && !spawnQueue.isEmpty()) {
            sendTypeMessageToSpawn(rc, spawnQueue.getFirst());
        }
        // Eksekusi spawn queue
        else if (!spawnQueue.isEmpty()) {
            executeSpawnQueue(rc);
        }
        // Greedy: tentukan apakah perlu spawn unit baru
        else if (shouldSpawn(rc)) {
            int bestType = greedyPickUnit(rc);
            spawnQueue.add(bestType);
            executeSpawnQueue(rc);
        }

        // Broadcast info musuh ke unit-unit sekitar
        if (enemyTarget != null && alertRobots) {
            broadcastNearbyBots(rc);
        }
        if (enemyTower != null && rc.getRoundNum() % 50 == 0) {
            broadcastEnemyTower(rc);
        }

        // Greedy upgrade berdasarkan threshold uang
        tryUpgrade(rc);

        // Greedy attack: serang musuh dengan HP terendah
        attackLowestHP(rc);
        aoeAttack(rc);
    }

    /**
     * Membaca pesan dari unit-unit sekitar dan mengupdate state tower.
     * Greedy: langsung proses setiap pesan dan update state yang sesuai.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void readMessages(RobotController rc) throws GameActionException {
        boolean isMoneyTower = rc.getType().getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER;

        for (Message message : rc.readMessages(rc.getRoundNum() - 1)) {
            int bytes = message.getBytes();
            if (MapInfoCodec.isRobotInfo(bytes)) continue;

            MapInfo msg = MapInfoCodec.decode(bytes);

            if (msg.hasRuin()) {
                roundsWithoutEnemy = 0;
                alertRobots = true;
                enemyTarget = msg;
                enemyTower = msg;

                if (!isMoneyTower && isRobot(rc, message.getSenderID())) {
                    broadcast = true;
                    alertAttackSoldiers = true;
                    spawnQueue.add(3); // Mopper
                    spawnQueue.add(4); // Splasher
                    numEnemyVisits++;
                }
            } else if (msg.getPaint().isEnemy()) {
                roundsWithoutEnemy = 0;
                alertRobots = true;
                enemyTarget = msg;

                if (!isMoneyTower && isRobot(rc, message.getSenderID())) {
                    broadcast = true;
                    numEnemyVisits++;
                }
            }
        }

        // Money tower: spawn developer jika paint penuh
        if (isMoneyTower && rc.getPaint() == 500 && spawnQueue.isEmpty()) {
            spawnQueue.add(0);
        }
    }

    /**
     * Mengecek apakah ID tertentu adalah robot (bukan tower).
     *
     * @param rc      RobotController untuk mengakses informasi game
     * @param robotId ID robot yang akan dicek
     * @return true jika ID tersebut adalah robot type
     * @throws GameActionException jika terjadi error akses game state
     */
    private static boolean isRobot(RobotController rc, int robotId) throws GameActionException {
        if (rc.canSenseRobot(robotId)) {
            return rc.senseRobot(robotId).getType().isRobotType();
        }
        return false;
    }

    /**
     * Menentukan apakah tower harus spawn unit baru.
     * Greedy: spawn jika uang cukup dan paint tersedia.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return true jika tower harus spawn unit
     * @throws GameActionException jika terjadi error akses game state
     */
    private static boolean shouldSpawn(RobotController rc) throws GameActionException {
        boolean forceSpawn = rc.getMoney() >= Constants.FORCE_SPAWN_CHIPS;
        if (forceSpawn) return true;

        // Paint tower menahan spawn jika uang terlalu sedikit
        if (rc.getType().getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER && rc.getMoney() <= Constants.SPAWN_MIN_MONEY) {
            return false;
        }

        return rc.getMoney() > 200 && rc.getPaint() > 50;
    }

    /**
     * Greedy pemilihan tipe unit untuk di-spawn.
     * Menghitung skor untuk setiap tipe unit berdasarkan:
     * - Base score masing-masing tipe
     * - Bonus berdasarkan jumlah kunjungan musuh
     * - Bonus jika sudah banyak soldier
     * - Bonus jika banyak paint musuh di sekitar
     * - Penalti jika rasio tipe sudah terlalu tinggi
     * Memilih tipe dengan skor tertinggi (greedy by maximum score).
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return kode tipe unit: 0 = soldier, 3 = mopper, 4 = splasher
     * @throws GameActionException jika terjadi error akses game state
     */
    private static int greedyPickUnit(RobotController rc) throws GameActionException {
        int total = soldiersBuilt + splashersBuilt + moppersBuilt + 1;

        // Prioritas awal: selalu spawn soldier dulu
        if (soldiersBuilt < Constants.SOLDIER_FIRST_COUNT) return 0;

        // Ronde sangat awal: spawn soldier
        if (rc.getRoundNum() <= 2) return 0;

        // Hitung skor greedy untuk setiap tipe unit
        int soldierScore = Constants.SOLDIER_BASE_SCORE;
        int splasherScore = Constants.SPLASHER_BASE_SCORE;
        int mopperScore = Constants.MOPPER_BASE_SCORE;

        // Bonus splasher berdasarkan jumlah kunjungan musuh
        splasherScore += numEnemyVisits * Constants.ENEMY_VISIT_SPLASHER_BONUS;

        // Bonus splasher jika sudah banyak soldier
        if (soldiersBuilt > 8) {
            splasherScore += Constants.MANY_SOLDIERS_BONUS;
        }

        // Bonus mopper jika banyak paint musuh di sekitar
        int enemyPaintCount = countEnemyPaint(rc);
        if (enemyPaintCount > 3) {
            mopperScore += Constants.ENEMY_PAINT_MOPPER_BONUS;
        }

        // Penalti jika rasio tipe terlalu tinggi (menjaga keseimbangan)
        double soldierPct = (double) soldiersBuilt / total;
        double splasherPct = (double) splashersBuilt / total;
        double mopperPct = (double) moppersBuilt / total;

        if (soldierPct > 0.65) soldierScore -= 40;
        if (splasherPct > 0.30) splasherScore -= 40;
        if (mopperPct > 0.25) mopperScore -= 40;

        // Greedy: pilih tipe dengan skor tertinggi
        if (soldierScore >= splasherScore && soldierScore >= mopperScore) return 0;
        if (splasherScore >= mopperScore) return 4;
        return 3;
    }

    /**
     * Mengeksekusi spawn queue: spawn unit pertama dalam antrian.
     * Greedy: jika tile spawn berisi paint musuh, langsung spawn mopper.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void executeSpawnQueue(RobotController rc) throws GameActionException {
        if (spawnQueue.isEmpty()) return;
        int type = spawnQueue.getFirst();

        // Greedy: jika spawn tile berwarna enemy, langsung spawn mopper
        MapLocation spawnLoc = rc.getLocation().add(spawnDirection);
        if (rc.canSenseLocation(spawnLoc) && rc.senseMapInfo(spawnLoc).getPaint().isEnemy()) {
            if (rc.canBuildRobot(UnitType.MOPPER, spawnLoc)) {
                rc.buildRobot(UnitType.MOPPER, spawnLoc);
                moppersBuilt++;
                return;
            }
        }

        UnitType unitType;
        switch (type) {
            case 0: case 1: case 2: unitType = UnitType.SOLDIER; break;
            case 3: unitType = UnitType.MOPPER; break;
            case 4: unitType = UnitType.SPLASHER; break;
            default: unitType = UnitType.SOLDIER; break;
        }

        if (rc.canBuildRobot(unitType, spawnLoc)) {
            rc.buildRobot(unitType, spawnLoc);
            sendTypeMessage = true;

            switch (unitType) {
                case SOLDIER: soldiersBuilt++; break;
                case SPLASHER: splashersBuilt++; break;
                case MOPPER: moppersBuilt++; break;
                default: break;
            }
        }
    }

    /**
     * Mengirim pesan tipe ke unit yang baru di-spawn.
     * Juga mengirim info target musuh jika ada.
     *
     * @param rc        RobotController untuk mengakses informasi game
     * @param robotType kode tipe robot yang di-spawn
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void sendTypeMessageToSpawn(RobotController rc, int robotType) throws GameActionException {
        MapLocation spawnLoc = rc.getLocation().add(spawnDirection);
        if (rc.canSenseRobotAtLocation(spawnLoc) && rc.canSendMessage(spawnLoc)) {
            rc.sendMessage(spawnLoc, robotType);
            if ((robotType == 2 || robotType == 3 || robotType == 4) && enemyTarget != null) {
                if (rc.canSendMessage(spawnLoc, MapInfoCodec.encode(enemyTarget))) {
                    rc.sendMessage(spawnLoc, MapInfoCodec.encode(enemyTarget));
                }
            }
        }
        sendTypeMessage = false;
        spawnQueue.removeFirst();
    }

    /**
     * Greedy attack: menyerang musuh dengan HP terendah yang bisa dijangkau.
     * Prioritas: target yang bisa dibunuh (lethal) didahulukan.
     * Jika ada beberapa target lethal, pilih yang HP-nya paling rendah.
     * Jika tidak ada yang lethal, pilih target dengan HP terendah.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void attackLowestHP(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        if (enemies.length == 0 || !rc.isActionReady()) return;

        RobotInfo best = null;
        int lowestHP = Integer.MAX_VALUE;
        boolean foundLethal = false;

        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.location)) continue;
            boolean canKill = e.health <= rc.getType().aoeAttackStrength;
            if (canKill && !foundLethal) {
                foundLethal = true;
                best = e;
                lowestHP = e.health;
            } else if (canKill == foundLethal && e.health < lowestHP) {
                lowestHP = e.health;
                best = e;
            }
        }
        if (best != null) rc.attack(best.location);
    }

    /**
     * Melakukan serangan AOE jika memungkinkan.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void aoeAttack(RobotController rc) throws GameActionException {
        if (rc.canAttack(null)) rc.attack(null);
    }

    /**
     * Greedy upgrade: upgrade tower jika uang melebihi threshold tertentu.
     * Cek tipe tower saat ini dan bandingkan uang dengan threshold upgrade.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void tryUpgrade(RobotController rc) throws GameActionException {
        UnitType t = rc.getType();
        int money = rc.getMoney();
        MapLocation loc = rc.getLocation();

        if (t == UnitType.LEVEL_ONE_PAINT_TOWER && money > Constants.UPGRADE_PAINT_L1) rc.upgradeTower(loc);
        else if (t == UnitType.LEVEL_ONE_MONEY_TOWER && money > Constants.UPGRADE_MONEY_L1) rc.upgradeTower(loc);
        else if (t == UnitType.LEVEL_TWO_PAINT_TOWER && money > Constants.UPGRADE_PAINT_L2) rc.upgradeTower(loc);
        else if (t == UnitType.LEVEL_TWO_MONEY_TOWER && money > Constants.UPGRADE_MONEY_L2) rc.upgradeTower(loc);
        else if (t == UnitType.LEVEL_ONE_DEFENSE_TOWER && money > Constants.UPGRADE_DEFENSE_L1) rc.upgradeTower(loc);
        else if (t == UnitType.LEVEL_TWO_DEFENSE_TOWER && money > Constants.UPGRADE_DEFENSE_L2) rc.upgradeTower(loc);
    }

    /**
     * Broadcast info musuh ke semua unit terdekat yang bertipe attack.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void broadcastNearbyBots(RobotController rc) throws GameActionException {
        for (RobotInfo bot : rc.senseNearbyRobots()) {
            if (rc.canSendMessage(bot.getLocation()) && isAttackType(bot)) {
                rc.sendMessage(bot.getLocation(), MapInfoCodec.encode(enemyTarget));
            }
        }
        alertRobots = false;
        alertAttackSoldiers = false;
    }

    /**
     * Broadcast lokasi tower musuh ke semua unit terdekat.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void broadcastEnemyTower(RobotController rc) throws GameActionException {
        for (RobotInfo bot : rc.senseNearbyRobots()) {
            if (rc.canSendMessage(bot.getLocation())) {
                rc.sendMessage(bot.getLocation(), MapInfoCodec.encode(enemyTower));
            }
        }
    }

    /**
     * Mengecek apakah robot memiliki tipe yang cocok untuk menerima alert attack.
     *
     * @param bot RobotInfo robot yang akan dicek
     * @return true jika robot adalah mopper, splasher, atau soldier (jika alertAttackSoldiers aktif)
     */
    private static boolean isAttackType(RobotInfo bot) {
        return bot.getType() == UnitType.MOPPER || bot.getType() == UnitType.SPLASHER
            || (bot.getType() == UnitType.SOLDIER && alertAttackSoldiers);
    }

    /**
     * Greedy pemilihan arah spawn: pilih arah menuju pusat peta.
     * Jika arah ke center adalah diagonal, rotasi ke cardinal terdekat.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return Direction terbaik untuk spawn unit
     */
    private static Direction pickSpawnDirection(RobotController rc) {
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        Direction toCenter = rc.getLocation().directionTo(center);
        if (toCenter.getDeltaX() != 0 && toCenter.getDeltaY() != 0) {
            toCenter = toCenter.rotateLeft();
        }
        return toCenter;
    }

    /**
     * Menghitung jumlah tile dengan paint musuh di sekitar tower.
     * Digunakan sebagai input untuk greedy scoring pada pemilihan unit.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return jumlah tile dengan paint musuh
     * @throws GameActionException jika terjadi error akses game state
     */
    private static int countEnemyPaint(RobotController rc) throws GameActionException {
        int count = 0;
        for (MapInfo map : rc.senseNearbyMapInfos()) {
            if (map.getPaint().isEnemy()) count++;
        }
        return count;
    }
}
