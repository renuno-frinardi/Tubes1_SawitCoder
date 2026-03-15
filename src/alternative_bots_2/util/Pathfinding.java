package alternative_bots_2.util;

import alternative_bots_2.*;

import battlecode.common.*;

public class Pathfinding {

  private static MapLocation target;

  public static enum Mode {
    ANY,
    NO_ENEMY,
    ALLY_ONLY
  }

  /**
   * Sets the target destination for pathfinding
   */
  public static void setTarget(MapLocation newTarget) {
    target = newTarget;
  }

  /**
   * Gets the current target
   */
  public static MapLocation getTarget() { return target; }

  /**
   * Clears the current target
   */
  public static void clearTarget() { target = null; }

  /**
   * Gets the next greedy move towards the target.
   */
  public static Direction getMove() throws GameActionException { return getMove(Mode.ANY); }

  /**
   * Gets the next greedy move towards the target with paint mode.
   */
  public static Direction getMove(Mode mode) throws GameActionException {
    if (target == null) { return null; }
    MapLocation current = Robot.rc.getLocation();

    // Already at target
    if (current.isAdjacentTo(target)) { return Direction.CENTER; }

    // Try greedy move towards the target
    Direction dir = getGreedyMove(current, target, true, mode);
    if (dir != null) { return dir; }

    // If greedy fails, try any available direction towards target
    dir = closestAvailableDirection(current, current.directionTo(target));
    if (dir != null && Robot.rc.canMove(dir)) { return dir; }

    return null;
  }

  /**
   * Gets the best greedy move towards a goal location.
   */
  public static Direction getGreedyMove(MapLocation loc, MapLocation goal, boolean checkCanMove, Mode mode) throws GameActionException {
    Direction l_dir = loc.directionTo(goal);
    MapLocation l_next = loc.add(l_dir);
    if (MapData.passable(l_next) && 
        (!checkCanMove || Robot.rc.canMove(l_dir)) &&
        (switch (mode) { 
          case Mode.ANY -> true;
          case Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(l_next).getPaint().isAlly(); 
          case Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(l_next).getPaint().isEnemy(); 
        })) { return l_dir; }
    
    // Figure out which direction is better
    Direction r_dir = l_dir.rotateRight();
    MapLocation r_next = loc.add(r_dir);
    l_dir = l_dir.rotateLeft();
    l_next = loc.add(l_dir);
    if (l_next.distanceSquaredTo(goal) < r_next.distanceSquaredTo(goal)) {
      if (Robot.rc.onTheMap(l_next) && MapData.passable(l_next) &&
          (!checkCanMove || Robot.rc.canMove(l_dir)) &&
          (switch (mode) { 
            case Mode.ANY -> true;
            case Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(l_next).getPaint().isAlly(); 
            case Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(l_next).getPaint().isEnemy(); 
          })) { return l_dir; }

      if (Robot.rc.onTheMap(r_next) && MapData.passable(r_next) &&
          (!checkCanMove || Robot.rc.canMove(r_dir)) &&
          (switch (mode) { 
            case Mode.ANY -> true;
            case Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(r_next).getPaint().isAlly(); 
            case Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(r_next).getPaint().isEnemy(); 
          })) { return r_dir; }
      
    } else {
      if (Robot.rc.onTheMap(r_next) && MapData.passable(r_next) &&
          (!checkCanMove || Robot.rc.canMove(r_dir)) &&
          (switch (mode) { 
            case Mode.ANY -> true;
            case Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(r_next).getPaint().isAlly(); 
            case Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(r_next).getPaint().isEnemy(); 
          })) { return r_dir; }

      if (Robot.rc.onTheMap(l_next) && MapData.passable(l_next) &&
          (!checkCanMove || Robot.rc.canMove(l_dir)) &&
          (switch (mode) { 
            case Mode.ANY -> true;
            case Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(l_next).getPaint().isAlly(); 
            case Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(l_next).getPaint().isEnemy(); 
          })) { return l_dir; }
    }
    return null;
  }

  /**
   * Gets the best greedy move towards a goal location using the default ANY mode.
   */
  public static Direction getGreedyMove(MapLocation loc, MapLocation goal, boolean checkCanMove) throws GameActionException {
    return getGreedyMove(loc, goal, checkCanMove, Mode.ANY);
  }

  /**
   * Greedy move towards a direction instead of a location
   */
  public static Direction getGreedyMove(MapLocation loc, Direction dir, boolean checkCanMove, Mode mode) throws GameActionException {
    MapLocation next = loc.add(dir);
    if (MapData.passable(next) && 
        (!checkCanMove || Robot.rc.canMove(dir)) &&
        (switch (mode) { 
          case Mode.ANY -> true;
          case Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(next).getPaint().isAlly(); 
          case Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(next).getPaint().isEnemy(); 
        })) { return dir; }
    
    Direction leftDir = dir.rotateLeft();
    next = loc.add(leftDir);
    if (Robot.rc.onTheMap(next) && MapData.passable(next) &&
        (!checkCanMove || Robot.rc.canMove(leftDir)) &&
        (switch (mode) { 
          case Mode.ANY -> true;
          case Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(next).getPaint().isAlly(); 
          case Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(next).getPaint().isEnemy(); 
        })) { return leftDir; }

    dir = dir.rotateRight();
    next = loc.add(dir);
    if (Robot.rc.onTheMap(next) && MapData.passable(next) &&
        (!checkCanMove || Robot.rc.canMove(dir)) &&
        (switch (mode) { 
          case Mode.ANY -> true;
          case Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(next).getPaint().isAlly(); 
          case Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(next).getPaint().isEnemy(); 
        })) { return dir; }

    return null;
  }

  /**
   * Gets the closest available direction to the target direction.
   */
  public static Direction closestAvailableDirection(MapLocation loc, Direction dir) {
    MapLocation resultLoc = loc.add(dir);
    if (Robot.rc.onTheMap(resultLoc) && MapData.passable(resultLoc)) { return dir; }

    Direction leftDir = dir.rotateLeft();
    resultLoc = loc.add(leftDir);
    if (Robot.rc.onTheMap(resultLoc) && MapData.passable(resultLoc)) { return leftDir; }

    dir = dir.rotateRight();
    resultLoc = loc.add(dir);
    if (Robot.rc.onTheMap(resultLoc) && MapData.passable(resultLoc)) { return dir; }

    leftDir = dir.rotateLeft();
    resultLoc = loc.add(leftDir);
    if (Robot.rc.onTheMap(resultLoc) && MapData.passable(resultLoc)) { return leftDir; }

    dir = dir.rotateRight();
    resultLoc = loc.add(dir);
    if (Robot.rc.onTheMap(resultLoc) && MapData.passable(resultLoc)) { return dir; }
    
    leftDir = dir.rotateLeft();
    resultLoc = loc.add(leftDir);
    if (Robot.rc.onTheMap(resultLoc) && MapData.passable(resultLoc)) { return leftDir; }

    dir = dir.rotateRight();
    resultLoc = loc.add(dir);
    if (Robot.rc.onTheMap(resultLoc) && MapData.passable(resultLoc)) { return dir; }

    leftDir = dir.rotateLeft();
    resultLoc = loc.add(leftDir);
    if (Robot.rc.onTheMap(resultLoc) && MapData.passable(resultLoc)) { return leftDir; }

    return null;
  }
}

// Credits: justinottesen/battlecode25