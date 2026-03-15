package second_bot;

import battlecode.common.*;

final class Mopper {

  private enum Mode {
    REFILL,
    CLEAN_RUIN,
    HUNT_ENEMY_PAINT,
    SUPPORT_ALLY,
    EXPLORE
  }

  private static final int LOW_PAINT_PERCENT = 22;
  private static final int SELF_PAINT_RESERVE = 45;

  private final RobotController rc;
  private final RobotPlayer.SharedState state;

  private Mode mode = Mode.EXPLORE;
  private MapLocation target;
  private MapLocation exploreTarget;
  private MapLocation lastCleanedRuin;

  Mopper(RobotController rc, RobotPlayer.SharedState state) {
    this.rc = rc;
    this.state = state;
  }

  void runTurn() throws GameActionException {
    chooseGreedyTask();
    rc.setIndicatorString("REBORN MOPPER " + mode + " -> " + target);

    if (rc.isActionReady()) {
      sharePaintToAllyIfNeeded();
    }

    if (rc.isActionReady()) {
      Direction swing = pickBestSwingDirection();
      if (swing != Direction.CENTER && rc.canMopSwing(swing)) {
        rc.mopSwing(swing);
      }
    }

    if (rc.isActionReady()) {
      if (mode == Mode.REFILL) {
        tryRefill();
      } else if (mode == Mode.CLEAN_RUIN) {
        mopAroundRuin(target);
      } else {
        mopBestLocalTile();
      }
    }

    if (rc.isMovementReady() && target != null) {
      RobotPlayer.greedyMoveToward(
          rc,
          state,
          target,
          false,
          -4,
          1);
    }

    if (rc.isActionReady()) {
      if (mode == Mode.REFILL) {
        tryRefill();
      } else if (mode == Mode.CLEAN_RUIN) {
        mopAroundRuin(target);
      } else {
        mopBestLocalTile();
      }
    }
  }

  private void chooseGreedyTask() throws GameActionException {
    if (RobotPlayer.lowPaint(rc, LOW_PAINT_PERCENT)) {
      mode = Mode.REFILL;
      target = RobotPlayer.nearestFriendlyTowerVisible(rc, state);
      return;
    }

    MapLocation dirtyRuin = nearestDirtyRuin();
    if (dirtyRuin != null) {
      mode = Mode.CLEAN_RUIN;
      target = dirtyRuin;
      return;
    }

    MapLocation enemyPaint = nearestEnemyPaintTile();
    if (enemyPaint != null) {
      mode = Mode.HUNT_ENEMY_PAINT;
      target = enemyPaint;
      return;
    }

    MapLocation weakAlly = nearestLowPaintAlly();
    if (weakAlly != null) {
      mode = Mode.SUPPORT_ALLY;
      target = weakAlly;
      return;
    }

    mode = Mode.EXPLORE;
    target = getExploreTarget();
  }

  private MapLocation nearestDirtyRuin() throws GameActionException {
    MapLocation me = rc.getLocation();
    MapLocation best = null;
    int bestDist = Integer.MAX_VALUE;

    for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
      if (ruin.equals(lastCleanedRuin)) {
        continue;
      }

      boolean hasEnemyPaint = false;
      for (MapInfo info : rc.senseNearbyMapInfos(ruin, 8)) {
        if (info.getPaint().isEnemy()) {
          hasEnemyPaint = true;
          break;
        }
      }
      if (!hasEnemyPaint) {
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

  private MapLocation nearestLowPaintAlly() throws GameActionException {
    MapLocation me = rc.getLocation();
    MapLocation best = null;
    int bestDist = Integer.MAX_VALUE;

    for (RobotInfo ally : rc.senseNearbyRobots(-1, state.team)) {
      if (ally.getType().isTowerType() || ally.getType() == UnitType.MOPPER) {
        continue;
      }
      if (ally.getPaintAmount() >= 40) {
        continue;
      }
      int d = me.distanceSquaredTo(ally.getLocation());
      if (d < bestDist) {
        bestDist = d;
        best = ally.getLocation();
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

  private void sharePaintToAllyIfNeeded() throws GameActionException {
    if (rc.getPaint() <= SELF_PAINT_RESERVE) {
      return;
    }

    for (RobotInfo ally : rc.senseNearbyRobots(GameConstants.PAINT_TRANSFER_RADIUS_SQUARED, state.team)) {
      if (ally.getType().isTowerType() || ally.getType() == UnitType.MOPPER) {
        continue;
      }

      int missing = ally.getType().paintCapacity - ally.getPaintAmount();
      if (missing <= 0 || ally.getPaintAmount() >= 35) {
        continue;
      }

      int give = Math.min(missing, rc.getPaint() - SELF_PAINT_RESERVE);
      if (give > 0 && rc.canTransferPaint(ally.getLocation(), give)) {
        rc.transferPaint(ally.getLocation(), give);
        return;
      }
    }
  }

  private void mopAroundRuin(MapLocation ruin) throws GameActionException {
    if (ruin == null || !rc.isActionReady()) {
      return;
    }

    MapLocation best = null;
    int bestScore = Integer.MIN_VALUE;

    for (int dx = -2; dx <= 2; dx++) {
      for (int dy = -2; dy <= 2; dy++) {
        MapLocation tile = new MapLocation(ruin.x + dx, ruin.y + dy);
        if (!rc.canSenseLocation(tile) || !rc.canAttack(tile)) {
          continue;
        }

        PaintType paint = rc.senseMapInfo(tile).getPaint();
        int score = paint.isEnemy() ? 9 : -2;

        RobotInfo robot = rc.senseRobotAtLocation(tile);
        if (robot != null) {
          if (robot.getTeam() == state.opponent) {
            score += robot.getType().isTowerType() ? 7 : 12;
          } else {
            score -= 3;
          }
        }

        if (score > bestScore) {
          bestScore = score;
          best = tile;
        }
      }
    }

    if (best != null && bestScore > 0) {
      rc.attack(best);
      return;
    }

    if (rc.canSenseLocation(ruin)) {
      boolean enemyLeft = false;
      for (MapInfo info : rc.senseNearbyMapInfos(ruin, 8)) {
        if (info.getPaint().isEnemy()) {
          enemyLeft = true;
          break;
        }
      }
      if (!enemyLeft) {
        lastCleanedRuin = ruin;
      }
    }
  }

  private void mopBestLocalTile() throws GameActionException {
    if (!rc.isActionReady()) {
      return;
    }

    MapLocation[] candidates =
        rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), rc.getType().actionRadiusSquared);
    MapLocation best = null;
    int bestScore = Integer.MIN_VALUE;

    for (MapLocation loc : candidates) {
      if (!rc.canSenseLocation(loc) || !rc.canAttack(loc)) {
        continue;
      }

      int score = 0;
      PaintType paint = rc.senseMapInfo(loc).getPaint();
      if (paint.isEnemy()) {
        score += 8;
      } else if (paint == PaintType.EMPTY) {
        score -= 1;
      } else {
        score -= 2;
      }

      RobotInfo robot = rc.senseRobotAtLocation(loc);
      if (robot != null) {
        if (robot.getTeam() == state.opponent) {
          score += robot.getType().isTowerType() ? 9 : 12;
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

  private Direction pickBestSwingDirection() throws GameActionException {
    RobotInfo[] enemies = rc.senseNearbyRobots(8, state.opponent);
    if (enemies.length == 0) {
      return Direction.CENTER;
    }

    Direction best = Direction.CENTER;
    int bestHits = 0;

    for (Direction d : RobotPlayer.CARDINALS) {
      int hits = 0;
      for (RobotInfo enemy : enemies) {
        Direction toEnemy = rc.getLocation().directionTo(enemy.getLocation());
        if (toEnemy.dx == d.dx || toEnemy.dy == d.dy) {
          hits++;
        }
      }
      if (hits > bestHits) {
        bestHits = hits;
        best = d;
      }
    }

    return best;
  }
}
