package second_bot;

import battlecode.common.*;

final class Splasher {

  private enum Mode {
    REFILL,
    PRESSURE_TOWER,
    CLEAR_ENEMY_PAINT,
    EXPLORE
  }

  private static final int LOW_PAINT_PERCENT = 30;

  private final RobotController rc;
  private final RobotPlayer.SharedState state;

  private Mode mode = Mode.EXPLORE;
  private MapLocation target;
  private MapLocation exploreTarget;

  Splasher(RobotController rc, RobotPlayer.SharedState state) {
    this.rc = rc;
    this.state = state;
  }

  void runTurn() throws GameActionException {
    chooseGreedyTask();
    rc.setIndicatorString("REBORN SPLASHER " + mode + " -> " + target);

    if (rc.isActionReady()) {
      if (mode == Mode.REFILL) {
        tryRefill();
      } else {
        attackBestSplashTarget();
      }
    }

    if (rc.isMovementReady() && target != null) {
      RobotPlayer.greedyMoveToward(
          rc,
          state,
          target,
          mode == Mode.PRESSURE_TOWER,
          7,
          3);
    }

    if (rc.isActionReady()) {
      if (mode == Mode.REFILL) {
        tryRefill();
      } else {
        attackBestSplashTarget();
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
      mode = Mode.PRESSURE_TOWER;
      target = enemyTower;
      return;
    }

    MapLocation paintCluster = nearestEnemyPaintTile();
    if (paintCluster != null) {
      mode = Mode.CLEAR_ENEMY_PAINT;
      target = paintCluster;
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
      RobotInfo tower = rc.senseRobotAtLocation(ruin);
      if (tower == null || !tower.getType().isTowerType() || tower.getTeam() != state.opponent) {
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

  private MapLocation nearestEnemyPaintTile() throws GameActionException {
    MapLocation me = rc.getLocation();
    MapLocation best = null;
    int bestDist = Integer.MAX_VALUE;

    for (MapInfo info : rc.senseNearbyMapInfos()) {
      if (!info.getPaint().isEnemy()) {
        continue;
      }
      int d = me.distanceSquaredTo(info.getMapLocation());
      if (d < bestDist) {
        bestDist = d;
        best = info.getMapLocation();
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

  private void attackBestSplashTarget() throws GameActionException {
    if (!rc.isActionReady()) {
      return;
    }

    MapLocation[] candidates =
        rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), rc.getType().actionRadiusSquared);

    MapLocation best = null;
    int bestScore = Integer.MIN_VALUE;

    for (MapLocation loc : candidates) {
      if (!rc.canAttack(loc)) {
        continue;
      }

      int score = splashScore(loc);
      if (target != null) {
        score += Math.max(0, 20 - target.distanceSquaredTo(loc));
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

  private int splashScore(MapLocation center) throws GameActionException {
    int score = 0;

    for (Direction d : RobotPlayer.DIRS) {
      MapLocation tile = center.add(d);
      if (!rc.canSenseLocation(tile)) {
        continue;
      }

      PaintType paint = rc.senseMapInfo(tile).getPaint();
      if (paint.isEnemy()) {
        score += 6;
      } else if (paint == PaintType.EMPTY) {
        score += 1;
      } else {
        score -= 2;
      }

      RobotInfo robot = rc.senseRobotAtLocation(tile);
      if (robot != null) {
        if (robot.getTeam() == state.opponent) {
          score += robot.getType().isTowerType() ? 12 : 8;
        } else {
          score -= 3;
        }
      }
    }

    for (Direction d : RobotPlayer.CARDINALS) {
      MapLocation farTile = center.add(d).add(d);
      if (!rc.canSenseLocation(farTile)) {
        continue;
      }
      PaintType paint = rc.senseMapInfo(farTile).getPaint();
      if (paint.isEnemy()) {
        score += 2;
      } else if (paint.isAlly()) {
        score -= 1;
      }
    }

    return score;
  }
}