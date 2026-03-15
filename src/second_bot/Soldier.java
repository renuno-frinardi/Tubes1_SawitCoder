package second_bot;

import battlecode.common.*;

final class Soldier {

  private enum Mode {
    REFILL,
    ATTACK_TOWER,
    CAPTURE_RUIN,
    EXPLORE
  }

  private static final int LOW_PAINT_PERCENT = 26;

  private final RobotController rc;
  private final RobotPlayer.SharedState state;

  private Mode mode = Mode.EXPLORE;
  private MapLocation target;
  private MapLocation exploreTarget;

  Soldier(RobotController rc, RobotPlayer.SharedState state) {
    this.rc = rc;
    this.state = state;
  }

  void runTurn() throws GameActionException {
    chooseGreedyTask();
    rc.setIndicatorString("REBORN SOLDIER " + mode + " -> " + target);

    if (rc.isActionReady()) {
      if (mode == Mode.REFILL) {
        tryRefill();
      } else if (mode == Mode.CAPTURE_RUIN) {
        greedyCaptureRuin();
      } else if (mode == Mode.ATTACK_TOWER) {
        greedyDirectAttack();
      }
    }

    if (rc.isActionReady()) {
      paintBestLocal();
    }

    if (rc.isMovementReady() && target != null) {
      RobotPlayer.greedyMoveToward(
          rc,
          state,
          target,
          mode == Mode.ATTACK_TOWER,
          8,
          4);
    }

    if (rc.isActionReady()) {
      if (mode == Mode.CAPTURE_RUIN) {
        greedyCaptureRuin();
      } else if (mode == Mode.REFILL) {
        tryRefill();
      } else {
        paintBestLocal();
      }
    }
  }

  private void chooseGreedyTask() throws GameActionException {
    if (RobotPlayer.lowPaint(rc, LOW_PAINT_PERCENT)) {
      mode = Mode.REFILL;
      target = RobotPlayer.nearestFriendlyTowerVisible(rc, state);
      return;
    }

    MapLocation enemyTower = nearestEnemyTower();
    if (enemyTower != null) {
      mode = Mode.ATTACK_TOWER;
      target = enemyTower;
      return;
    }

    MapLocation neutralRuin = nearestNeutralRuin();
    if (neutralRuin != null) {
      mode = Mode.CAPTURE_RUIN;
      target = neutralRuin;
      return;
    }

    mode = Mode.EXPLORE;
    target = getExploreTarget();
  }

  private MapLocation nearestEnemyTower() throws GameActionException {
    MapLocation me = rc.getLocation();
    MapLocation best = null;
    int bestDist = Integer.MAX_VALUE;

    for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
      if (!rc.canSenseRobotAtLocation(ruin)) {
        continue;
      }
      RobotInfo atRuin = rc.senseRobotAtLocation(ruin);
      if (atRuin == null || !atRuin.getType().isTowerType() || atRuin.getTeam() != state.opponent) {
        continue;
      }
      int d = me.distanceSquaredTo(ruin);
      if (d < bestDist) {
        bestDist = d;
        best = ruin;
      }
    }
    return best;
  }

  private MapLocation nearestNeutralRuin() throws GameActionException {
    MapLocation me = rc.getLocation();
    MapLocation best = null;
    int bestDist = Integer.MAX_VALUE;

    for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
      if (rc.canSenseRobotAtLocation(ruin) && rc.senseRobotAtLocation(ruin) != null) {
        continue;
      }
      int d = me.distanceSquaredTo(ruin);
      if (d < bestDist) {
        bestDist = d;
        best = ruin;
      }
    }
    return best;
  }

  private MapLocation getExploreTarget() {
    if (exploreTarget == null
        || rc.getLocation().isWithinDistanceSquared(exploreTarget, GameConstants.VISION_RADIUS_SQUARED)) {
      exploreTarget = new MapLocation(state.rng.nextInt(rc.getMapWidth()), state.rng.nextInt(rc.getMapHeight()));
    }
    return exploreTarget;
  }

  private void tryRefill() throws GameActionException {
    if (target == null) {
      return;
    }
    if (!rc.getLocation().isWithinDistanceSquared(target, GameConstants.PAINT_TRANSFER_RADIUS_SQUARED)) {
      return;
    }
    if (!rc.canSenseRobotAtLocation(target)) {
      return;
    }
    RobotInfo tower = rc.senseRobotAtLocation(target);
    if (tower == null || tower.getTeam() != state.team || !tower.getType().isTowerType()) {
      return;
    }

    int need = rc.getType().paintCapacity - rc.getPaint();
    if (need <= 0) {
      return;
    }

    int take = Math.min(need, tower.getPaintAmount());
    if (take > 0 && rc.canTransferPaint(target, -take)) {
      rc.transferPaint(target, -take);
    }
  }

  private void greedyDirectAttack() throws GameActionException {
    if (target == null || !rc.canAttack(target)) {
      return;
    }
    rc.attack(target);
  }

  private static final UnitType[] ALL_TOWER_TYPES = {
      UnitType.LEVEL_ONE_PAINT_TOWER,
      UnitType.LEVEL_ONE_MONEY_TOWER,
      UnitType.LEVEL_ONE_DEFENSE_TOWER,
  };

  private void greedyCaptureRuin() throws GameActionException {
    if (target == null) {
      return;
    }

    for (UnitType type : ALL_TOWER_TYPES) {
      if (rc.canCompleteTowerPattern(type, target)) {
        rc.completeTowerPattern(type, target);
        return;
      }
    }

    boolean marksExist = hasExistingMarks(target);

    if (!marksExist) {
      UnitType towerType = pickBestTowerType();
      if (rc.canMarkTowerPattern(towerType, target)) {
        rc.markTowerPattern(towerType, target);
      }
    }

    if (rc.isActionReady()) {
      fillTowerPattern(target);
    }
  }

  private boolean hasExistingMarks(MapLocation ruinLoc) throws GameActionException {
    for (MapInfo tile : rc.senseNearbyMapInfos(ruinLoc, 8)) {
      if (tile.getMark() != PaintType.EMPTY) {
        return true;
      }
    }
    return false;
  }

  private UnitType pickBestTowerType() throws GameActionException {
    int paintTowers = 0;
    int moneyTowers = 0;
    int defenseTowers = 0;

    for (RobotInfo ally : rc.senseNearbyRobots(-1, state.team)) {
      UnitType t = ally.getType();
      if (t == UnitType.LEVEL_ONE_PAINT_TOWER || t == UnitType.LEVEL_TWO_PAINT_TOWER
          || t == UnitType.LEVEL_THREE_PAINT_TOWER) {
        paintTowers++;
      } else if (t == UnitType.LEVEL_ONE_MONEY_TOWER || t == UnitType.LEVEL_TWO_MONEY_TOWER
          || t == UnitType.LEVEL_THREE_MONEY_TOWER) {
        moneyTowers++;
      } else if (t == UnitType.LEVEL_ONE_DEFENSE_TOWER || t == UnitType.LEVEL_TWO_DEFENSE_TOWER
          || t == UnitType.LEVEL_THREE_DEFENSE_TOWER) {
        defenseTowers++;
      }
    }

    if (paintTowers <= moneyTowers && paintTowers <= defenseTowers) {
      return UnitType.LEVEL_ONE_PAINT_TOWER;
    }
    if (moneyTowers <= paintTowers && moneyTowers <= defenseTowers) {
      return UnitType.LEVEL_ONE_MONEY_TOWER;
    }
    return UnitType.LEVEL_ONE_DEFENSE_TOWER;
  }

  private void fillTowerPattern(MapLocation ruinLoc) throws GameActionException {
    if (!rc.isActionReady()) {
      return;
    }

    MapInfo[] patternTiles = rc.senseNearbyMapInfos(ruinLoc, 8);
    MapLocation best = null;
    int bestScore = Integer.MIN_VALUE;

    for (MapInfo tile : patternTiles) {
      PaintType mark = tile.getMark();
      if (mark == PaintType.EMPTY || mark == tile.getPaint()) {
        continue;
      }
      MapLocation loc = tile.getMapLocation();
      if (!rc.canAttack(loc)) {
        continue;
      }

      int score = 0;
      PaintType currentPaint = tile.getPaint();
      if (currentPaint.isEnemy()) {
        score += 10;
      } else if (currentPaint == PaintType.EMPTY) {
        score += 7;
      } else {
        score += 3;
      }

      score -= rc.getLocation().distanceSquaredTo(loc);

      if (score > bestScore) {
        bestScore = score;
        best = loc;
      }
    }

    if (best != null) {
      MapInfo bestInfo = rc.senseMapInfo(best);
      boolean useSecondary = (bestInfo.getMark() == PaintType.ALLY_SECONDARY);
      rc.attack(best, useSecondary);
    }
  }

  private void paintBestLocal() throws GameActionException {
    MapLocation[] candidates =
        rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), rc.getType().actionRadiusSquared);
    MapLocation best = null;
    int bestScore = Integer.MIN_VALUE;

    for (MapLocation loc : candidates) {
      if (!rc.canSenseLocation(loc) || !rc.canAttack(loc)) {
        continue;
      }
      PaintType paint = rc.senseMapInfo(loc).getPaint();
      int score = 0;
      if (paint.isEnemy()) {
        score += 6;
      } else if (paint == PaintType.EMPTY) {
        score += 3;
      } else {
        score -= 2;
      }
      if (loc.equals(rc.getLocation())) {
        score += 1;
      }

      RobotInfo robot = rc.senseRobotAtLocation(loc);
      if (robot != null) {
        if (robot.getTeam() == state.opponent) {
          score += robot.getType().isTowerType() ? 12 : 7;
        } else {
          score -= 3;
        }
      }

      if (score > bestScore) {
        bestScore = score;
        best = loc;
      }
    }

    if (best != null && bestScore > 0) {
      rc.attack(best);
    }
  }
}
