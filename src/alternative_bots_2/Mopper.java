package alternative_bots_2;

import battlecode.common.*;
import alternative_bots_2.util.*;

/**
 * Mopper
 * 
 * Greedy Priority
 * 1. Refill paint if low
 * 2. Mop enemy paint near unclaimed ruins (help capture)
 * 3. Transfer paint to nearby allies who need it
 * 4. Mop nearby enemy paint
 * 5. Explore
 */
public final class Mopper extends Robot {

  private static final int REFILL_PAINT_THRESHOLD = GameConstants.INCREASED_COOLDOWN_THRESHOLD / 2;

  private enum GreedyAction {
    REFILL_PAINT,
    HELP_RUIN,
    HELP_SRP,
    EXPLORE
  }

  private GreedyAction chosenAction;
  private MapLocation currentTarget;
  private MapLocation currentRuinTarget;
  private MapLocation currentSRPTarget;

  public Mopper(RobotController rc_) throws GameActionException {
    super(rc_);

    // Load queued requests sent by nearby towers.
    for (Message m : rc.readMessages(-1)) {
      switch (m.getBytes() & Communication.MESSAGE_TYPE_BITMASK) {
        case Communication.REQUEST_MOPPER:
          currentRuinTarget = Communication.getCoordinates(m.getBytes());
          break;
        default: break;
      }
    }
  }

  protected void doMicro() throws GameActionException {
    chosenAction = GreedyAction.EXPLORE;
    currentTarget = null;

    // Anti-clump
    if (rc.senseNearbyRobots(9, TEAM).length > 24) {
      rc.disintegrate();
      return;
    }

    if (!rc.isMovementReady() && !rc.isActionReady()) { return; }

    // Update nearby ruins
    if (rc.getRoundNum() > 10) {
      for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
        MapData.updateData(rc.senseMapInfo(ruin));
      }
    }

    // Read messages from last round
    Message[] messages = rc.readMessages(rc.getRoundNum() - 1);
    for (Message m : messages) {
      if (Communication.getMessageType(m.getBytes()) == Communication.REQUEST_MOPPER) {
        currentRuinTarget = Communication.getCoordinates(m.getBytes());
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

    // Priority 2: Help unclaimed ruin (mop enemy paint)
    if (chosenAction != GreedyAction.REFILL_PAINT) {
      // Use message target if we got one
      if (currentRuinTarget != null && rc.canSenseLocation(currentRuinTarget)) {
        RobotInfo ruinBot = rc.senseRobotAtLocation(currentRuinTarget);
        if (ruinBot == null) {
          chosenAction = GreedyAction.HELP_RUIN;
          currentTarget = currentRuinTarget;
        } else {
          currentRuinTarget = null; // Ruin is already claimed
        }
      }
      
      // Otherwise look for nearby unclaimed ruins 
      if (chosenAction != GreedyAction.HELP_RUIN) {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        for (MapLocation ruin : ruins) {
          RobotInfo info = rc.senseRobotAtLocation(ruin);
          if (info == null) {
            chosenAction = GreedyAction.HELP_RUIN;
            currentTarget = ruin;
            currentRuinTarget = ruin;
            break;
          }
        }
      }
    }

    // Priority 3: Help SRP if found
    if (chosenAction == GreedyAction.EXPLORE && MapData.foundSRP != null) {
      chosenAction = GreedyAction.HELP_SRP;
      currentTarget = MapData.foundSRP;
      currentSRPTarget = MapData.foundSRP;
    }

    // Default: Explore
    if (chosenAction == GreedyAction.EXPLORE) {
      currentTarget = MapData.getExploreTarget();
    }

    Pathfinding.setTarget(currentTarget);
    rc.setIndicatorString("GREEDY: " + chosenAction + " -> " + currentTarget);

    // Transfer paint to nearby allies who need it
    if (rc.isActionReady() && rc.getPaint() > REFILL_PAINT_THRESHOLD * rc.getType().paintCapacity / 100) {
      for (RobotInfo robot : rc.senseNearbyRobots(GameConstants.PAINT_TRANSFER_RADIUS_SQUARED, rc.getTeam())) {
        if (robot.type == UnitType.MOPPER || robot.getType().isTowerType() || robot.getPaintAmount() > REFILL_PAINT_THRESHOLD) { continue; }
        int transfer_amt = Math.min(robot.getType().paintCapacity - robot.getPaintAmount(), rc.getPaint() - REFILL_PAINT_THRESHOLD);
        if (rc.canTransferPaint(robot.getLocation(), transfer_amt)) {
          rc.transferPaint(robot.getLocation(), transfer_amt);
          break;
        }
      }
    }

    // Cant move
    if (!rc.isMovementReady() && rc.isActionReady()) { Painter.mopAny(); return; }

    // === EXECUTE ACTION ===
    switch (chosenAction) {
      case HELP_RUIN:
        if (Painter.mopCaptureRuin(currentRuinTarget)) {
          currentRuinTarget = null;
        }
        break;

      case HELP_SRP:
        if (Painter.mopCaptureSRP(currentSRPTarget)) {
          currentSRPTarget = null;
        }
        break;

      case REFILL_PAINT:
        refillPaint();
        break;

      case EXPLORE:
        break;
    }

    // Try mop swing
    if (rc.isActionReady()) {
      Direction mopSwingDirection = pickMopSwingDirection();
      if (mopSwingDirection != Direction.CENTER && rc.canMopSwing(mopSwingDirection)) {
        rc.mopSwing(mopSwingDirection);
      }
    }
  }

  protected void doMacro() throws GameActionException {
    if (rc.isMovementReady()) {
      Direction dir = Pathfinding.getMove(Pathfinding.Mode.NO_ENEMY);
      if (dir != null && rc.canMove(dir)) {
        MapData.move(dir);
      }
    }
  }

  private void refillPaint() throws GameActionException {
    if (currentTarget == null) { return; }
    if (rc.getLocation().isWithinDistanceSquared(currentTarget, GameConstants.PAINT_TRANSFER_RADIUS_SQUARED)) {
      RobotInfo tower = rc.senseRobotAtLocation(currentTarget);
      if (tower == null) {
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, currentTarget)) {
          rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, currentTarget);
          tower = rc.senseRobotAtLocation(currentTarget);
        } else {
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

  /**
   * Greedy pick: mop swing in direction hitting the most enemies
   */
  private Direction pickMopSwingDirection() throws GameActionException {
    RobotInfo[] enemies = rc.senseNearbyRobots(8, rc.getTeam().opponent());
    if (enemies.length == 0) return Direction.CENTER;
    Direction[] cardinals = Direction.cardinalDirections();

    int mostHit = 0;
    Direction chosen = Direction.CENTER;
    for (Direction d : cardinals) {
      int hits = 0;
      for (RobotInfo enemy : enemies) {
        Direction dirToEnemy = rc.getLocation().directionTo(enemy.getLocation());
        if (dirToEnemy.dx == d.dx || dirToEnemy.dy == d.dy) ++hits;
      }
      if (hits > mostHit) {
        mostHit = hits;
        chosen = d;
      }
    }
    return chosen;
  }
}
