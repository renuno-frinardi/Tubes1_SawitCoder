package third_bot;

import battlecode.common.*;
import third_bot.util.*;

/**
 * Soldier
 * 
 * Greedy priority:
 * 1. If very low on paint -> go refill at closest friendly tower
 * 2. If enemy tower visible -> attack it (greedy: fight closest enemy)
 * 3. If unclaimed ruin visible -> capture it (greedy: closest unclaimed ruin)
 * 4. If SRP opportunity visible -> paint it
 * 5. Otherwise -> explore towards closest unexplored area 
 */
public final class Soldier extends Robot {

  private static final int REFILL_PAINT_THRESHOLD = GameConstants.INCREASED_COOLDOWN_THRESHOLD;

  // greedy targets
  private MapLocation currentTarget;
  private RobotInfo currentEnemyTower;
  private MapLocation currentRuin;
  private MapLocation currentSRP;

  private enum GreedyAction {
    REFILL_PAINT,
    FIGHT_TOWER,
    CAPTURE_RUIN,
    CAPTURE_SRP,
    EXPLORE
  }

  private GreedyAction chosenAction;

  public Soldier(RobotController rc_) throws GameActionException {
    super(rc_);
  }

  protected void doMicro() throws GameActionException {
    currentEnemyTower = null;
    currentRuin = null;
    currentSRP = null;
    chosenAction = GreedyAction.EXPLORE;

    // Cant do anything
    if (!rc.isMovementReady() && !rc.isActionReady()) { return; }

    if (rc.senseNearbyRobots(9, TEAM).length > 24) {
      rc.disintegrate();
      return;
    }

    // Update nearby ruins
    if (rc.getRoundNum() > 10) {
      for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
        MapData.updateData(rc.senseMapInfo(ruin));
      }
    }

    // === GREEDY DECISION ===
    // Priority 1: Refill paint if low
    if (rc.getPaint() < REFILL_PAINT_THRESHOLD * rc.getType().paintCapacity / 100) {
      MapLocation tower = MapData.closestFriendlyTower();
      if (tower != null) {
        chosenAction = GreedyAction.REFILL_PAINT;
        currentTarget = tower;
      }
    }

    // Priority 2: Fight enemy tower if visible (overrides refill if we see one)
    if (chosenAction != GreedyAction.REFILL_PAINT || rc.getPaint() > 20) {
      MapLocation[] ruins = rc.senseNearbyRuins(-1);
      for (MapLocation ruin : ruins) {
        RobotInfo info = rc.senseRobotAtLocation(ruin);
        if (info != null && info.getTeam() == OPPONENT) {
          chosenAction = GreedyAction.FIGHT_TOWER;
          currentTarget = ruin;
          currentEnemyTower = info;
          break;
        }
      }
    }

    // Priority 3: Capture unclaimed ruin if visible
    if (chosenAction != GreedyAction.FIGHT_TOWER && chosenAction != GreedyAction.REFILL_PAINT) {
      MapLocation[] ruins = rc.senseNearbyRuins(-1);
      MapLocation bestRuin = null;
      int bestDist = Integer.MAX_VALUE;
      for (MapLocation ruin : ruins) {
        RobotInfo info = rc.senseRobotAtLocation(ruin);
        if (info == null) {
          int dist = rc.getLocation().distanceSquaredTo(ruin);
          if (dist < bestDist) {
            bestRuin = ruin;
            bestDist = dist;
          }
        }
      }
      if (bestRuin != null) {
        chosenAction = GreedyAction.CAPTURE_RUIN;
        currentTarget = bestRuin;
        currentRuin = bestRuin;
      }
    }

    // Priority 4: SRP if nothing better to do
    if (chosenAction == GreedyAction.EXPLORE) {
      for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), GameConstants.MARK_RADIUS_SQUARED)) {
        if (MapData.tryMarkSRP(loc)) {
          chosenAction = GreedyAction.CAPTURE_SRP;
          currentTarget = loc;
          currentSRP = loc;
          break;
        }
      }
    }

    // If still exploring, check foundSRP from MapData
    if (chosenAction == GreedyAction.EXPLORE && MapData.foundSRP != null) {
      chosenAction = GreedyAction.CAPTURE_SRP;
      currentTarget = MapData.foundSRP;
      currentSRP = MapData.foundSRP;
    }

    // Default: Explore
    if (chosenAction == GreedyAction.EXPLORE) {
      currentTarget = MapData.getExploreTarget();
    }

    Pathfinding.setTarget(currentTarget);
    rc.setIndicatorString("GREEDY: " + chosenAction + " -> " + currentTarget);

    // Cant move
    if (!rc.isMovementReady() && rc.isActionReady()) { Painter.paintAny(); return; }

    // === EXECUTE ACTION ===
    switch (chosenAction) {
      case FIGHT_TOWER:
        Painter.paintFight(currentEnemyTower);
        break;

      case CAPTURE_RUIN:
        Painter.paintCaptureRuin(currentRuin);
        break;

      case CAPTURE_SRP:
        Painter.paintCaptureSRP(currentSRP);
        break;

      case REFILL_PAINT:
        refillPaint();
        break;

      case EXPLORE:
        break;
    }
  }

  protected void doMacro() throws GameActionException {
    if (rc.isMovementReady()) {
      Direction dir = Pathfinding.getMove();
      if (dir != null && rc.canMove(dir)) {
        MapData.move(dir);
        // Check for SRP after moving
        if (MapData.foundSRP != null && chosenAction == GreedyAction.EXPLORE) {
          Pathfinding.setTarget(MapData.foundSRP);
        }
      }
    }
    // Always try to paint when we can
    if (chosenAction != GreedyAction.REFILL_PAINT) {
      Painter.paintAny();
    }
  }

  /**
   * Greedy refill: move towards closest friendly tower and take paint
   */
  private void refillPaint() throws GameActionException {
    if (currentTarget == null) { return; }
    if (rc.getLocation().isWithinDistanceSquared(currentTarget, GameConstants.PAINT_TRANSFER_RADIUS_SQUARED)) {
      RobotInfo tower = rc.senseRobotAtLocation(currentTarget);
      if (tower == null) {
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, currentTarget)) {
          rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, currentTarget);
          tower = rc.senseRobotAtLocation(currentTarget);
        } else {
          // Try next closest tower
          currentTarget = MapData.closestFriendlyTower();
          Pathfinding.setTarget(currentTarget);
          return;
        }
      }
      if (tower != null) {
        int paintAmount = rc.getType().paintCapacity - rc.getPaint();
        if (tower.getPaintAmount() < paintAmount) { paintAmount = tower.getPaintAmount(); }
        if (rc.canTransferPaint(currentTarget, -paintAmount)) {
          rc.transferPaint(currentTarget, -paintAmount);
        }
      }
    }
  }
}
