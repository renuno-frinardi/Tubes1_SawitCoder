package main_bot;

import battlecode.common.*;
import static main_bot.RobotPlayer.*;

/**
 * Kelas Soldier mengatur perilaku robot tipe Soldier.
 *
 * Semua keputusan yang diambil oleh Soldier menggunakan algoritma greedy:
 * setiap situasi dievaluasi berdasarkan kondisi lokal saat ini,
 * lalu dipilih aksi terbaik tanpa perencanaan jangka panjang.
 * Contoh: pemilihan ruin terdekat, evaluasi tile terbaik untuk dicat,
 * dan penentuan tower type yang optimal.
 */
public class Soldier {

    /**
     * Tipe soldier menentukan perilaku utama:
     * 0 = developer, 1 = regular, 2 = attacker, 3 = SRP builder, 4 = early rusher
     */
    private static int soldierType = 1;

    /**
     * Entry point utama untuk logika Soldier setiap turn.
     * Mengupdate state, membaca pesan, lalu menjalankan behavior sesuai soldierType.
     * Menggunakan pendekatan greedy: state diupdate berdasarkan kondisi terkini,
     * lalu aksi terbaik dipilih untuk turn ini.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void run(RobotController rc) throws GameActionException {
        // Update tower cat terdekat (greedy by jarak minimum)
        updateLastPaintTower(rc);

        // Baca pesan baru dari tower
        readNewMessages(rc);

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation initLocation = rc.getLocation();

        // Inisialisasi turn pertama
        if (botRoundNum == 1) {
            paintIfPossible(rc, rc.getLocation());
            wanderTarget = new MapLocation(rc.getMapWidth() - initLocation.x, rc.getMapHeight() - initLocation.y);
        }

        // Early rusher pada ronde awal
        if (rc.getRoundNum() <= 3) {
            soldierType = 4;
            wanderTarget = new MapLocation(rc.getMapWidth() - initLocation.x, rc.getMapHeight() - initLocation.y);
        }

        switch (soldierType) {
            case 4: // Early rusher: greedy rush ke arah musuh
                if (rc.getRoundNum() >= (rc.getMapHeight() + rc.getMapWidth()) / 2) {
                    soldierType = 1;
                    return;
                }
                updateStateOsama(rc, initLocation, nearbyTiles);
                executeSoldierState(rc, initLocation, nearbyTiles, true);
                break;

            case 0: // Developer: greedy explore dan build
                updateState(rc, initLocation, nearbyTiles);
                Helper.tryCompleteResourcePattern(rc);

                // Greedy switch: jika melihat musuh, langsung jadi regular soldier
                if (seesEnemy(rc)) {
                    numTurnsAlive = 0;
                    soldierType = 1;
                    soldierState = SoldierState.EXPLORING;
                    resetVariables();
                    return;
                }

                // Lifecycle transition berdasarkan turn count (greedy threshold)
                if (numTurnsAlive > Constants.DEV_LIFE_CYCLE_TURNS && soldierState == SoldierState.STUCK) {
                    numTurnsAlive = 0;
                    if (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT) {
                        soldierState = SoldierState.STUCK;
                    } else {
                        soldierState = SoldierState.FILLINGSRP;
                    }
                    soldierType = 3;
                    resetVariables();
                    return;
                }
                executeSoldierState(rc, initLocation, nearbyTiles, false);
                break;

            case 1: // Regular soldier
                updateState(rc, initLocation, nearbyTiles);
                executeSoldierState(rc, initLocation, nearbyTiles, true);
                break;

            case 2: // Attacker: greedy attack tower musuh
                runAttack(rc, nearbyTiles);
                break;

            case 3: // SRP builder: greedy fill resource pattern
                runSRP(rc, initLocation, nearbyTiles);
                break;
        }
    }

    /**
     * Mengeksekusi aksi berdasarkan state soldier saat ini.
     * Setiap state memiliki behavior greedy masing-masing:
     * - LOWONPAINT: greedy kembali ke tower terdekat
     * - DELIVERINGMESSAGE: greedy kirim info ke tower
     * - FILLINGTOWER: greedy isi ruin terdekat
     * - EXPLORING: greedy explore ke arah terbaik
     * - STUCK: greedy unstuck ke corner terjauh
     *
     * @param rc              RobotController untuk mengakses informasi game
     * @param initLocation    lokasi awal robot pada turn ini
     * @param nearbyTiles     array MapInfo tile sekitar
     * @param useWanderTarget apakah menggunakan wanderTarget untuk eksplorasi
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void executeSoldierState(RobotController rc, MapLocation initLocation, MapInfo[] nearbyTiles, boolean useWanderTarget) throws GameActionException {
        switch (soldierState) {
            case LOWONPAINT:
                lowPaintBehavior(rc);
                break;
            case DELIVERINGMESSAGE:
                msgTower(rc);
                break;
            case FILLINGTOWER:
                fillInRuin(rc, ruinToFill);
                break;
            case EXPLORING:
                if (useWanderTarget && wanderTarget != null) {
                    Direction dir = Pathfinding.betterExplore(rc, initLocation, wanderTarget, false);
                    if (dir != null) {
                        rc.move(dir);
                        paintIfPossible(rc, rc.getLocation());
                    }
                } else if (!useWanderTarget) {
                    // Greedy explore ke tile unpainted terbaik
                    Direction dir = Pathfinding.exploreUnpainted(rc);
                    if (dir != null) {
                        rc.move(dir);
                        paintIfPossible(rc, rc.getLocation());
                    } else if (rc.getMovementCooldownTurns() < 10) {
                        soldierState = SoldierState.STUCK;
                        resetVariables();
                    }
                } else {
                    intermediateTarget = null;
                    soldierState = SoldierState.STUCK;
                    resetVariables();
                }
                break;
            case STUCK:
                stuckBehavior(rc);
                if (!useWanderTarget) {
                    // Greedy: cek apakah ada tile yang bisa dicat di sekitar
                    if (findPaintableTile(rc, rc.getLocation(), 20) != null) {
                        soldierState = SoldierState.EXPLORING;
                        resetVariables();
                    }
                }
                break;
            case FILLINGSRP:
                break;
        }
    }

    /**
     * Logika greedy untuk menyerang tower musuh.
     * Jika tower musuh dalam jangkauan serang, serang lalu mundur (greedy retreat).
     * Jika tidak, bergerak mendekat menggunakan greedy pathfind.
     *
     * @param rc          RobotController untuk mengakses informasi game
     * @param nearbyTiles array MapInfo tile sekitar
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void runAttack(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        if (enemyTower == null) {
            soldierType = 1;
            soldierState = SoldierState.EXPLORING;
            resetVariables();
            return;
        }

        // Greedy: update target tower jika menemukan tower musuh baru yang lebih dekat
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (tile.hasRuin() && rc.canSenseRobotAtLocation(loc) && !rc.senseRobotAtLocation(loc).getTeam().isPlayer()) {
                enemyTower = tile;
                return;
            }
        }

        // Greedy attack-retreat behavior
        MapLocation enemyLoc = enemyTower.getMapLocation();
        if (rc.canSenseRobotAtLocation(enemyLoc) && rc.canAttack(enemyLoc)) {
            rc.attack(enemyLoc);
            Direction back = enemyLoc.directionTo(rc.getLocation());
            if (rc.canMove(back)) rc.move(back);
            else if (rc.canMove(back.rotateLeft())) rc.move(back.rotateLeft());
            else if (rc.canMove(back.rotateRight())) rc.move(back.rotateRight());
        } else {
            Direction dir = Pathfinding.pathfind(rc, enemyLoc);
            if (dir != null) {
                rc.move(dir);
                if (rc.canAttack(enemyLoc)) rc.attack(enemyLoc);
            }
            if (rc.canSenseLocation(enemyLoc) && !rc.canSenseRobotAtLocation(enemyLoc)) {
                enemyTower = null;
            }
        }
    }

    /**
     * Logika greedy untuk membangun Special Resource Pattern (SRP).
     * Mengelola state dan memanggil fillSRP atau fillSRPLargeMap sesuai ukuran peta.
     *
     * @param rc            RobotController untuk mengakses informasi game
     * @param initLocation  lokasi awal robot pada turn ini
     * @param nearbyTiles   array MapInfo tile sekitar
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void runSRP(RobotController rc, MapLocation initLocation, MapInfo[] nearbyTiles) throws GameActionException {
        updateSRPState(rc, initLocation, nearbyTiles);
        Helper.tryCompleteResourcePattern(rc);

        if (seesEnemy(rc) || (numTurnsAlive > Constants.SRP_LIFE_CYCLE_TURNS && soldierState == SoldierState.STUCK)) {
            soldierType = 1;
            soldierState = SoldierState.EXPLORING;
            numTurnsAlive = 0;
            resetVariables();
            return;
        }

        switch (soldierState) {
            case LOWONPAINT:
                lowPaintBehavior(rc);
                break;
            case FILLINGSRP:
                if (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT) {
                    fillSRP(rc);
                } else {
                    fillSRPLargeMap(rc, nearbyTiles);
                }
                break;
            case STUCK:
                stuckBehavior(rc);
                // Greedy: cek apakah ada tile SRP yang perlu dicat di sekitar
                if (!(rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT)) {
                    for (MapInfo tile : nearbyTiles) {
                        PaintType paint = Helper.resourcePatternType(rc, tile.getMapLocation());
                        if ((tile.getPaint() == PaintType.EMPTY && tile.isPassable())
                            || (tile.getPaint().isAlly() && !paint.equals(tile.getPaint()))) {
                            resetVariables();
                            soldierState = SoldierState.FILLINGSRP;
                            numTurnsAlive = 0;
                            break;
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * Greedy SRP fill untuk peta besar.
     * Mencari tile terdekat yang perlu dicat sesuai pola SRP, lalu mengecat atau
     * bergerak mendekat. Greedy: selalu pilih tile pertama yang bisa dicat.
     *
     * @param rc          RobotController untuk mengakses informasi game
     * @param nearbyTiles array MapInfo tile sekitar
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void fillSRPLargeMap(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        boolean hasPainted = false;
        if (rc.getActionCooldownTurns() < 10) {
            for (MapInfo tile : rc.senseNearbyMapInfos(20)) {
                MapLocation loc = tile.getMapLocation();
                PaintType paint = Helper.resourcePatternType(rc, loc);
                if ((tile.getPaint() == PaintType.EMPTY && tile.isPassable())
                    || (tile.getPaint().isAlly() && !paint.equals(tile.getPaint()))) {
                    if (rc.canAttack(loc)) {
                        rc.attack(loc, paint == PaintType.ALLY_SECONDARY);
                        hasPainted = true;
                        break;
                    }
                }
            }
        }
        if (!hasPainted) {
            for (MapInfo tile : nearbyTiles) {
                if (rc.getLocation().isWithinDistanceSquared(tile.getMapLocation(), 20)) continue;
                MapLocation loc = tile.getMapLocation();
                PaintType paint = Helper.resourcePatternType(rc, loc);
                if ((tile.getPaint() == PaintType.EMPTY && tile.isPassable())
                    || (tile.getPaint().isAlly() && !paint.equals(tile.getPaint()))) {
                    Direction dir = Pathfinding.pathfind(rc, loc);
                    if (dir != null && rc.canMove(dir)) rc.move(dir);
                    hasPainted = true;
                    break;
                }
            }
        }
        if (SRPLocation != null) {
            Direction dir = Pathfinding.pathfind(rc, SRPLocation);
            if (dir != null && rc.canMove(dir)) rc.move(dir);
        } else if (!hasPainted) {
            soldierState = SoldierState.STUCK;
        }
    }

    /**
     * Update state soldier berdasarkan kondisi saat ini (greedy state transition).
     * Prioritas greedy:
     * 1. Paint rendah → LOWONPAINT
     * 2. Ada tile musuh → DELIVERINGMESSAGE (kirim info ke tower)
     * 3. Ada ruin kosong terdekat → FILLINGTOWER
     *
     * @param rc          RobotController untuk mengakses informasi game
     * @param curLocation lokasi robot saat ini
     * @param nearbyTiles array MapInfo tile sekitar
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void updateState(RobotController rc, MapLocation curLocation, MapInfo[] nearbyTiles) throws GameActionException {
        if (hasLowPaint(rc) && (rc.getMoney() < Constants.LOW_PAINT_MONEY_THRESHOLD || soldierState == SoldierState.FILLINGTOWER)) {
            if (soldierState != SoldierState.LOWONPAINT) {
                intermediateTarget = null;
                resetVariables();
                storedState = soldierState;
                soldierState = SoldierState.LOWONPAINT;
            }
        } else if (soldierState != SoldierState.DELIVERINGMESSAGE && soldierState != SoldierState.LOWONPAINT) {
            enemyTile = updateEnemyTiles(rc, nearbyTiles);
            if (enemyTile != null && lastTower != null) {
                if (soldierState == SoldierState.EXPLORING) {
                    prevLocation = rc.getLocation();
                    resetVariables();
                } else {
                    intermediateTarget = null;
                    resetVariables();
                }
                storedState = soldierState;
                soldierState = SoldierState.DELIVERINGMESSAGE;
            } else if (soldierState != SoldierState.FILLINGTOWER) {
                MapInfo bestRuin = findBestRuin(rc, curLocation, nearbyTiles);
                if (bestRuin != null) {
                    ruinToFill = bestRuin.getMapLocation();
                    soldierState = SoldierState.FILLINGTOWER;
                    resetVariables();
                }
            }
        }
    }

    /**
     * Update state untuk early rusher (Osama mode) menggunakan greedy evaluation.
     * Prioritas greedy:
     * 1. Paint rendah → LOWONPAINT
     * 2. Tower musuh dalam jangkauan → switch ke regular soldier
     * 3. Ada ruin kosong → FILLINGTOWER (jika bisa build)
     *
     * @param rc          RobotController untuk mengakses informasi game
     * @param curLocation lokasi robot saat ini
     * @param nearbyTiles array MapInfo tile sekitar
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void updateStateOsama(RobotController rc, MapLocation curLocation, MapInfo[] nearbyTiles) throws GameActionException {
        if (hasLowPaint(rc)) {
            if (soldierState != SoldierState.LOWONPAINT) {
                intermediateTarget = null;
                resetVariables();
                storedState = soldierState;
                soldierState = SoldierState.LOWONPAINT;
            }
        } else if (soldierState != SoldierState.DELIVERINGMESSAGE && soldierState != SoldierState.LOWONPAINT) {
            // Greedy: cek tower musuh terdekat dalam jangkauan 20
            RobotInfo closestEnemyTower = towerInRange(rc, 20, false);
            if (closestEnemyTower != null) {
                enemyTile = rc.senseMapInfo(closestEnemyTower.getLocation());
                soldierType = 1;
                resetVariables();
            }
            if (soldierState != SoldierState.FILLINGTOWER) {
                MapInfo bestRuin = findAnyRuin(rc, curLocation, nearbyTiles);
                if (bestRuin != null) {
                    if (!canBuildTower(rc, bestRuin.getMapLocation())) {
                        soldierType = 1;
                        resetVariables();
                    } else {
                        ruinToFill = bestRuin.getMapLocation();
                        soldierState = SoldierState.FILLINGTOWER;
                        resetVariables();
                    }
                }
            } else {
                if (!canBuildTower(rc, ruinToFill)) {
                    soldierType = 1;
                    resetVariables();
                }
            }
        }
    }

    /**
     * Update state untuk SRP builder menggunakan greedy evaluation.
     * Memeriksa apakah lokasi saat ini cocok untuk membangun SRP pattern.
     * Greedy: langsung mulai build jika semua 25 tile dalam radius valid.
     *
     * @param rc          RobotController untuk mengakses informasi game
     * @param curLocation lokasi robot saat ini
     * @param nearbyTiles array MapInfo tile sekitar
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void updateSRPState(RobotController rc, MapLocation curLocation, MapInfo[] nearbyTiles) throws GameActionException {
        if (rc.getLocation().equals(SRPLocation)) SRPLocation = null;

        if (soldierState != SoldierState.LOWONPAINT && hasLowPaint(rc)) {
            if (soldierState != SoldierState.STUCK) SRPLocation = rc.getLocation();
            resetVariables();
            storedState = soldierState;
            soldierState = SoldierState.LOWONPAINT;
        } else if (soldierState == SoldierState.STUCK) {
            if (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT
                && !rc.senseMapInfo(curLocation).getMark().isAlly()) {
                MapInfo[] possSRP = rc.senseNearbyMapInfos(8);
                boolean canBuild = true;
                for (MapInfo map : possSRP) {
                    if (!map.isPassable() || map.getPaint().isEnemy()) {
                        canBuild = false;
                        break;
                    }
                }
                if (canBuild && possSRP.length == 25 && !conflictsSRP(rc)) {
                    resetVariables();
                    soldierState = SoldierState.FILLINGSRP;
                    srpCenter = rc.getLocation();
                    rc.mark(rc.getLocation(), false);
                }
            }
        }
    }

    /**
     * Behavior saat paint rendah: greedy kembali ke tower terdekat untuk refill.
     * Prioritas greedy:
     * 1. Serang tower musuh yang ada di jangkauan (greedy opportunistic attack)
     * 2. Bergerak ke tower ally terdekat menggunakan greedy pathfind
     * 3. Transfer paint dari tower
     * 4. Restore state sebelumnya jika paint sudah cukup
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void lowPaintBehavior(RobotController rc) throws GameActionException {
        isLowPaint = true;
        // Greedy: serang tower musuh pertama yang ditemukan dalam jangkauan
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (enemy.getType().isTowerType() && rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                break;
            }
        }
        if (lastTower == null) {
            Direction dir = Pathfinding.greedyWalk(rc);
            if (dir != null && rc.canMove(dir)) rc.move(dir);
            return;
        }
        Direction dir = Pathfinding.returnToTower(rc);
        if (dir != null) rc.move(dir);

        MapLocation towerLoc = lastTower.getMapLocation();
        completeRuinIfPossible(rc, towerLoc);

        int amtToTransfer = rc.getPaint() - rc.getType().paintCapacity;
        if (rc.canSenseRobotAtLocation(towerLoc)) {
            int towerPaint = rc.senseRobotAtLocation(towerLoc).paintAmount;
            if (rc.getPaint() < 5 && rc.canTransferPaint(towerLoc, -towerPaint) && towerPaint > Constants.MIN_PAINT_GIVE) {
                rc.transferPaint(towerLoc, -towerPaint);
            }
        }
        if (rc.canTransferPaint(towerLoc, amtToTransfer)) {
            rc.transferPaint(towerLoc, amtToTransfer);
        }

        if (rc.getPaint() > Constants.LOW_PAINT_THRESHOLD) {
            if (soldierState != storedState) {
                soldierState = storedState;
            } else if (ruinToFill != null) {
                soldierState = SoldierState.FILLINGTOWER;
            } else {
                soldierState = SoldierState.STUCK;
            }
            resetVariables();
        }
    }

    /**
     * Mengisi ruin dengan pola tower yang sesuai menggunakan greedy fill.
     * Greedy: menentukan tipe tower berdasarkan marking yang ada, lalu
     * mengecat tile pertama yang belum sesuai pola.
     *
     * @param rc           RobotController untuk mengakses informasi game
     * @param ruinLocation lokasi ruin yang akan diisi
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void fillInRuin(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        if (!canBuildTower(rc, ruinLocation)) {
            if (rc.canSenseRobotAtLocation(ruinLocation) && rc.senseRobotAtLocation(ruinLocation).getType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                soldierState = SoldierState.LOWONPAINT;
                storedState = SoldierState.EXPLORING;
                fillTowerType = null;
                ruinToFill = null;
            } else {
                soldierState = SoldierState.EXPLORING;
                fillTowerType = null;
                ruinToFill = null;
            }
        }

        if (fillTowerType != null) {
            PaintType[][] pattern = (fillTowerType == UnitType.LEVEL_ONE_PAINT_TOWER) ? Constants.paintTowerPattern
                : (fillTowerType == UnitType.LEVEL_ONE_MONEY_TOWER) ? Constants.moneyTowerPattern
                : Constants.defenseTowerPattern;

            // Greedy: cari tile pertama yang belum sesuai pola dan cat
            int[] tileToPaint = findPaintableRuinTile(rc, ruinLocation, pattern);
            if (tileToPaint != null) {
                MapLocation tile = ruinLocation.translate(tileToPaint[0], tileToPaint[1]);
                if (rc.canPaint(tile) && rc.canAttack(tile)) {
                    rc.attack(tile, pattern[tileToPaint[0] + 2][tileToPaint[1] + 2] == PaintType.ALLY_SECONDARY);
                }
            }
            Direction moveDir = Pathfinding.pathfind(rc, ruinLocation);
            if (moveDir != null) rc.move(moveDir);
            completeRuinIfPossible(rc, ruinLocation);
        } else {
            // Greedy: tentukan tipe tower berdasarkan marking yang sudah ada
            MapLocation northTower = ruinLocation.add(Direction.NORTH);
            if (rc.canSenseLocation(northTower)) {
                PaintType towerMarking = rc.senseMapInfo(northTower).getMark();
                if (towerMarking == PaintType.ALLY_PRIMARY) {
                    fillTowerType = UnitType.LEVEL_ONE_PAINT_TOWER;
                } else if (towerMarking == PaintType.EMPTY) {
                    MapLocation defenseMarkLoc = northTower.add(Direction.EAST);
                    if (rc.canSenseLocation(defenseMarkLoc)) {
                        if (rc.senseMapInfo(defenseMarkLoc).getMark() == PaintType.ALLY_PRIMARY) {
                            fillTowerType = UnitType.LEVEL_ONE_DEFENSE_TOWER;
                        } else {
                            // Greedy: pilih tipe tower terbaik via greedyTowerType
                            UnitType towerType = Helper.greedyTowerType(rc, ruinLocation);
                            if (towerType == UnitType.LEVEL_ONE_DEFENSE_TOWER && rc.canMark(defenseMarkLoc)) {
                                rc.mark(defenseMarkLoc, false);
                                fillTowerType = UnitType.LEVEL_ONE_DEFENSE_TOWER;
                            } else if (rc.canMark(northTower) && towerType != UnitType.LEVEL_ONE_DEFENSE_TOWER) {
                                if (seenPaintTower) {
                                    rc.mark(northTower, towerType == UnitType.LEVEL_ONE_MONEY_TOWER);
                                    fillTowerType = towerType;
                                } else {
                                    rc.mark(northTower, false);
                                    fillTowerType = UnitType.LEVEL_ONE_PAINT_TOWER;
                                }
                            } else {
                                Direction moveDir = Pathfinding.pathfind(rc, ruinLocation);
                                if (moveDir != null) rc.move(moveDir);
                            }
                        }
                    } else {
                        Direction moveDir = Pathfinding.pathfind(rc, ruinLocation);
                        if (moveDir != null) rc.move(moveDir);
                    }
                } else {
                    fillTowerType = UnitType.LEVEL_ONE_MONEY_TOWER;
                }
            } else {
                Direction moveDir = Pathfinding.pathfind(rc, ruinLocation);
                if (moveDir != null) rc.move(moveDir);
            }
        }
    }

    /**
     * Mengisi SRP pattern pada peta kecil.
     * Greedy: iterasi seluruh 5x5 grid, cat tile pertama yang belum sesuai pola.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void fillSRP(RobotController rc) throws GameActionException {
        if (!rc.getLocation().equals(srpCenter)) {
            Direction dir = Pathfinding.pathfind(rc, srpCenter);
            if (dir != null && rc.canMove(dir)) rc.move(dir);
        } else {
            boolean finished = true;
            boolean srpComplete = true;
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    if (!rc.onTheMap(rc.getLocation().translate(i - 2, j - 2))) continue;
                    MapInfo srpLoc = rc.senseMapInfo(rc.getLocation().translate(i - 2, j - 2));
                    boolean isPrimary = Constants.primarySRP.contains(new HashableCoords(i, j));
                    if ((srpLoc.getPaint() == PaintType.ALLY_PRIMARY && isPrimary)
                        || (srpLoc.getPaint() == PaintType.ALLY_SECONDARY && !isPrimary)) continue;
                    srpComplete = false;
                    if (!rc.canAttack(srpLoc.getMapLocation())) continue;

                    if (srpLoc.getPaint() == PaintType.EMPTY) {
                        rc.attack(srpLoc.getMapLocation(), !isPrimary);
                        finished = false;
                        break;
                    } else if (srpLoc.getPaint() == PaintType.ALLY_PRIMARY && !isPrimary) {
                        rc.attack(srpLoc.getMapLocation(), true);
                        finished = false;
                        break;
                    } else if (srpLoc.getPaint() == PaintType.ALLY_SECONDARY && isPrimary) {
                        rc.attack(srpLoc.getMapLocation(), false);
                        finished = false;
                        break;
                    }
                }
            }
            if (finished) {
                if (srpComplete) {
                    soldierState = SoldierState.STUCK;
                    srpCenter = null;
                    numTurnsAlive = 0;
                }
                if (rc.canCompleteResourcePattern(rc.getLocation())) {
                    rc.completeResourcePattern(rc.getLocation());
                    soldierState = SoldierState.STUCK;
                    srpCenter = null;
                    numTurnsAlive = 0;
                }
            }
        }
    }

    /**
     * Mengirim pesan ke tower ally terdekat tentang posisi musuh.
     * Greedy: serang tower musuh yang ada di jangkauan (opportunistic),
     * lalu bergerak ke tower ally untuk mengirim pesan.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void msgTower(RobotController rc) throws GameActionException {
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (enemy.getType().isTowerType() && rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                break;
            }
        }
        MapLocation towerLoc = lastTower.getMapLocation();
        if (rc.canSenseRobotAtLocation(towerLoc) && rc.canSendMessage(towerLoc)) {
            int encoded = MapInfoCodec.encode(enemyTile);
            if (rc.canSendMessage(towerLoc, encoded)) {
                rc.sendMessage(towerLoc, encoded);
            }
            enemyTile = null;
            if (soldierState != storedState) {
                soldierState = storedState;
            } else if (ruinToFill != null) {
                soldierState = SoldierState.FILLINGTOWER;
            } else {
                soldierState = SoldierState.STUCK;
            }
            resetVariables();
            if (prevLocation != null) {
                intermediateTarget = prevLocation;
                prevLocation = null;
            }
            return;
        }
        Direction dir = Pathfinding.returnToTower(rc);
        if (dir != null) rc.move(dir);
    }

    /**
     * Membaca pesan yang diterima dari tower dan mengupdate state sesuai isinya.
     * Greedy: langsung mengubah soldierType berdasarkan pesan pertama yang diterima.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    private static void readNewMessages(RobotController rc) throws GameActionException {
        for (Message message : rc.readMessages(rc.getRoundNum() - 1)) {
            int bytes = message.getBytes();
            if (bytes == 0 || bytes == 1 || bytes == 2) {
                switch (bytes) {
                    case 0:
                        // Greedy: jadi developer soldier pada peta kecil/besar
                        if (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT) {
                            soldierType = 0;
                        } else {
                            soldierType = 0;
                        }
                        break;
                    case 1: soldierType = 1; break; // Regular soldier
                    case 2: soldierType = 2; break; // Attack soldier
                }
            } else if (soldierType == 1 || soldierType == 2) {
                if (!MapInfoCodec.isRobotInfo(bytes)) {
                    MapInfo tile = MapInfoCodec.decode(bytes);
                    if (tile.hasRuin()) {
                        enemyTower = tile;
                        soldierType = 2;
                        resetVariables();
                    }
                    wanderTarget = tile.getMapLocation();
                }
            }
        }
    }

    /**
     * Mengecat tile di lokasi tertentu jika memungkinkan.
     * Greedy: langsung cat jika tile kosong, tanpa mark, dan bisa diserang.
     * Pada peta besar, menggunakan resource pattern grid untuk menentukan warna.
     *
     * @param rc            RobotController untuk mengakses informasi game
     * @param paintLocation lokasi yang akan dicat
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void paintIfPossible(RobotController rc, MapLocation paintLocation) throws GameActionException {
        if (!rc.canSenseLocation(paintLocation)) return;
        MapInfo paintTile = rc.senseMapInfo(paintLocation);
        if (paintTile.getPaint() == PaintType.EMPTY && rc.canAttack(paintLocation) && paintTile.getMark() == PaintType.EMPTY) {
            if (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT) {
                rc.attack(paintLocation, false);
            } else {
                rc.attack(paintLocation, !Helper.resourcePatternGrid(rc, paintLocation));
            }
        }
    }

    /**
     * Behavior saat robot stuck: greedy navigasi ke corner peta.
     * Soldier tipe 0/3 gunakan findOwnCorner (corner terdekat),
     * tipe lainnya gunakan getUnstuck (corner terjauh).
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void stuckBehavior(RobotController rc) throws GameActionException {
        Direction newDir;
        if (soldierType == 0 || soldierType == 3) {
            newDir = Pathfinding.findOwnCorner(rc);
        } else {
            newDir = Pathfinding.getUnstuck(rc);
        }
        if (newDir != null) {
            rc.move(newDir);
            paintIfPossible(rc, rc.getLocation());
        }
    }

    /**
     * Mengecek apakah robot memiliki paint di bawah threshold.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return true jika paint robot < LOW_PAINT_THRESHOLD
     */
    private static boolean hasLowPaint(RobotController rc) {
        return rc.getPaint() < Constants.LOW_PAINT_THRESHOLD;
    }

    /**
     * Mengecek apakah ada robot musuh yang terlihat.
     * Greedy scan: iterasi semua robot terdekat, return true pada musuh pertama.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return true jika ada robot musuh dalam jangkauan sensor
     * @throws GameActionException jika terjadi error akses game state
     */
    private static boolean seesEnemy(RobotController rc) throws GameActionException {
        for (RobotInfo bot : rc.senseNearbyRobots()) {
            if (bot.getTeam().opponent() == rc.getTeam()) return true;
        }
        return false;
    }

    /**
     * Update tower cat ally terdekat menggunakan greedy minimum distance.
     * Scan semua tile sekitar, cari tower cat ally dengan jarak terkecil.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void updateLastPaintTower(RobotController rc) throws GameActionException {
        int minDist = -1;
        MapInfo best = null;
        for (MapInfo loc : rc.senseNearbyMapInfos()) {
            if (loc.hasRuin() && rc.canSenseRobotAtLocation(loc.getMapLocation())
                && rc.senseRobotAtLocation(loc.getMapLocation()).getTeam() == rc.getTeam()) {
                UnitType t = rc.senseRobotAtLocation(loc.getMapLocation()).getType();
                if (t.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER.getBaseType()) {
                    seenPaintTower = true;
                    int d = loc.getMapLocation().distanceSquaredTo(rc.getLocation());
                    if (minDist == -1 || d < minDist) {
                        best = loc;
                        minDist = d;
                    }
                }
            }
        }
        if (minDist != -1) {
            lastTower = best;
        } else if (lastTower != null && lastTower.getMapLocation().isWithinDistanceSquared(rc.getLocation(), 20)) {
            lastTower = null;
        }
    }

    /**
     * Mencari informasi tile musuh terdekat menggunakan greedy evaluation.
     * Prioritas: tower musuh dalam jangkauan 20, lalu paint musuh terdekat.
     *
     * @param rc          RobotController untuk mengakses informasi game
     * @param nearbyTiles array MapInfo tile sekitar
     * @return MapInfo tile musuh yang ditemukan, atau null jika tidak ada
     * @throws GameActionException jika terjadi error akses game state
     */
    private static MapInfo updateEnemyTiles(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        RobotInfo closestEnemy = towerInRange(rc, 20, false);
        if (closestEnemy != null) return rc.senseMapInfo(closestEnemy.getLocation());
        if (soldierMsgCooldown == -1) {
            MapInfo enemyPaint = findEnemyPaint(rc, nearbyTiles);
            if (enemyPaint != null) {
                soldierMsgCooldown = 30;
                return enemyPaint;
            }
        }
        return null;
    }

    /**
     * Mencoba menyelesaikan tower pattern pada lokasi ruin.
     * Mencoba ketiga tipe tower pattern secara berurutan.
     *
     * @param rc           RobotController untuk mengakses informasi game
     * @param ruinLocation lokasi ruin yang akan dicoba diselesaikan
     * @throws GameActionException jika terjadi error akses game state
     */
    public static void completeRuinIfPossible(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation))
            rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation);
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation))
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation);
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation))
            rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation);

        if (rc.canSenseRobotAtLocation(ruinLocation)) {
            soldierState = SoldierState.LOWONPAINT;
            storedState = SoldierState.EXPLORING;
            ruinToFill = null;
            fillTowerType = null;
        }
    }

    /**
     * Mencari tower (ally/enemy) terdekat dalam jangkauan tertentu.
     * Greedy: return tower pertama yang ditemukan (linear scan).
     *
     * @param rc    RobotController untuk mengakses informasi game
     * @param range jarak kuadrat maksimum pencarian
     * @param ally  true untuk mencari tower ally, false untuk tower musuh
     * @return RobotInfo tower yang ditemukan, atau null jika tidak ada
     * @throws GameActionException jika terjadi error akses game state
     */
    private static RobotInfo towerInRange(RobotController rc, int range, boolean ally) throws GameActionException {
        RobotInfo[] robots = ally ? rc.senseNearbyRobots(range, rc.getTeam()) : rc.senseNearbyRobots(range, rc.getTeam().opponent());
        for (RobotInfo r : robots) {
            if (r.getType().isTowerType()) return r;
        }
        return null;
    }

    /**
     * Mencari tile pertama dengan paint musuh di sekitar robot.
     * Greedy scan: return tile pertama yang memiliki paint enemy.
     *
     * @param rc          RobotController untuk mengakses informasi game
     * @param nearbyTiles array MapInfo tile sekitar
     * @return MapInfo tile musuh pertama, atau null jika tidak ditemukan
     */
    private static MapInfo findEnemyPaint(RobotController rc, MapInfo[] nearbyTiles) {
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy()) return tile;
        }
        return null;
    }

    /**
     * Mencari ruin terbaik (terdekat) yang bisa dibangun tower.
     * Greedy by minimum distance: iterasi semua tile, pilih ruin terdekat
     * yang belum ditempati, bisa dibangun, dan tidak ada ally nearby.
     *
     * @param rc            RobotController untuk mengakses informasi game
     * @param robotLocation lokasi robot saat ini
     * @param nearbyTiles   array MapInfo tile sekitar
     * @return MapInfo ruin terbaik, atau null jika tidak ditemukan
     * @throws GameActionException jika terjadi error akses game state
     */
    private static MapInfo findBestRuin(RobotController rc, MapLocation robotLocation, MapInfo[] nearbyTiles) throws GameActionException {
        MapInfo curRuin = null;
        int minDis = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation loc = tile.getMapLocation();
                if (!rc.canSenseRobotAtLocation(loc) && canBuildTower(rc, loc)
                    && rc.senseNearbyRobots(loc, 2, rc.getTeam()).length < 1) {
                    int d = robotLocation.distanceSquaredTo(loc);
                    if (d < minDis) {
                        curRuin = tile;
                        minDis = d;
                    }
                }
            }
        }
        return curRuin;
    }

    /**
     * Mencari ruin terdekat manapun (tanpa cek canBuildTower).
     * Greedy by minimum distance: iterasi semua tile, pilih ruin terdekat
     * yang belum ditempati dan tidak ada ally nearby.
     *
     * @param rc            RobotController untuk mengakses informasi game
     * @param robotLocation lokasi robot saat ini
     * @param nearbyTiles   array MapInfo tile sekitar
     * @return MapInfo ruin terdekat, atau null jika tidak ditemukan
     * @throws GameActionException jika terjadi error akses game state
     */
    private static MapInfo findAnyRuin(RobotController rc, MapLocation robotLocation, MapInfo[] nearbyTiles) throws GameActionException {
        MapInfo curRuin = null;
        int minDis = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation loc = tile.getMapLocation();
                if (!rc.canSenseRobotAtLocation(loc) && rc.senseNearbyRobots(loc, 2, rc.getTeam()).length < 1) {
                    int d = robotLocation.distanceSquaredTo(loc);
                    if (d < minDis) {
                        curRuin = tile;
                        minDis = d;
                    }
                }
            }
        }
        return curRuin;
    }

    /**
     * Mengecek apakah tower bisa dibangun di lokasi tertentu.
     * Greedy validasi: cek semua tile sekitar ruin, pastikan tidak ada
     * ruin lain yang sudah terisi atau paint enemy.
     *
     * @param rc            RobotController untuk mengakses informasi game
     * @param towerLocation lokasi tower yang akan divalidasi
     * @return true jika tower bisa dibangun di lokasi tersebut
     * @throws GameActionException jika terjadi error akses game state
     */
    private static boolean canBuildTower(RobotController rc, MapLocation towerLocation) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos(towerLocation, 8)) {
            if (tile.hasRuin()) {
                if (rc.canSenseRobotAtLocation(tile.getMapLocation())) return false;
            } else if (tile.getPaint().isEnemy()) return false;
        }
        return true;
    }

    /**
     * Mencari tile yang bisa dicat dalam jangkauan tertentu.
     * Greedy: return tile pertama yang kosong atau tidak sesuai mark.
     *
     * @param rc           RobotController untuk mengakses informasi game
     * @param location     lokasi pusat pencarian
     * @param rangeSquared jarak kuadrat maksimum pencarian
     * @return MapInfo tile yang bisa dicat, atau null jika tidak ada
     * @throws GameActionException jika terjadi error akses game state
     */
    private static MapInfo findPaintableTile(RobotController rc, MapLocation location, int rangeSquared) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos(location, rangeSquared)) {
            if (rc.canPaint(tile.getMapLocation())
                && (tile.getPaint() == PaintType.EMPTY
                    || (tile.getMark() != tile.getPaint() && tile.getMark() != PaintType.EMPTY))) {
                return tile;
            }
        }
        return null;
    }

    /**
     * Mencari tile ruin yang perlu dicat sesuai pola tower.
     * Greedy: iterasi grid 5x5 sekitar ruin, return tile pertama yang
     * warnanya tidak sesuai pattern dan bisa dicat.
     *
     * @param rc           RobotController untuk mengakses informasi game
     * @param ruinLocation lokasi pusat ruin
     * @param pattern      pola warna 5x5 untuk tipe tower yang dibangun
     * @return int[2] offset {dx, dy} dari ruin, atau null jika semua sudah sesuai
     * @throws GameActionException jika terjadi error akses game state
     */
    private static int[] findPaintableRuinTile(RobotController rc, MapLocation ruinLocation, PaintType[][] pattern) throws GameActionException {
        for (int i = -2; i < 3; i++) {
            for (int j = -2; j < 3; j++) {
                MapLocation tile = ruinLocation.translate(i, j);
                if (rc.canPaint(tile) && pattern[i + 2][j + 2] != rc.senseMapInfo(tile).getPaint()) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    /**
     * Mengecek apakah lokasi SRP saat ini konflik dengan SRP lain yang sudah ada.
     * Greedy scan: jika ada mark ally di sekitar yang bukan bagian dari ruin,
     * maka ada konflik.
     *
     * @param rc RobotController untuk mengakses informasi game
     * @return true jika terdapat konflik SRP
     * @throws GameActionException jika terjadi error akses game state
     */
    private static boolean conflictsSRP(RobotController rc) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.getMark().isAlly()) {
                MapLocation south = tile.getMapLocation().add(Direction.SOUTH);
                MapLocation southwest = south.add(Direction.WEST);
                if (rc.canSenseLocation(south)) {
                    if (!rc.senseMapInfo(south).hasRuin()) {
                        if (rc.canSenseLocation(southwest)) {
                            if (!rc.senseMapInfo(southwest).hasRuin()) return true;
                        } else return true;
                    }
                } else return true;
            }
        }
        return false;
    }
}
