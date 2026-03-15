package third_bot.util;

import third_bot.*;

import battlecode.common.*;

public class Pathfinding {

  private static MapLocation target;

  public static enum Mode {
    ANY,
    NO_ENEMY,
    ALLY_ONLY
  }

  public static void setTarget(MapLocation newTarget) { target = newTarget; }
  public static MapLocation getTarget() { return target; }
  public static void clearTarget() { target = null; }

  /** Move towards target; falls back to any nearby direction. */
  public static Direction getMove() throws GameActionException { return getMove(Mode.ANY); }

  public static Direction getMove(Mode mode) throws GameActionException {
    if (target == null) return null;
    MapLocation current = Robot.rc.getLocation();
    if (current.isAdjacentTo(target)) return Direction.CENTER;

    Direction dir = getGreedyMove(current, target, true, mode);
    if (dir != null) return dir;

    dir = closestAvailableDirection(current, current.directionTo(target));
    if (dir != null && Robot.rc.canMove(dir)) return dir;
    return null;
  }

  /** Check if a tile passes the paint mode filter. */
  private static boolean checkMode(MapLocation loc, Mode mode) throws GameActionException {
    return switch (mode) {
      case ANY      -> true;
      case ALLY_ONLY -> Robot.rc.senseMapInfo(loc).getPaint().isAlly();
      case NO_ENEMY  -> !Robot.rc.senseMapInfo(loc).getPaint().isEnemy();
    };
  }

  /** Try moving one step along dir; returns dir if passable and mode-valid. */
  private static Direction tryDir(MapLocation loc, Direction dir, boolean check, Mode mode) throws GameActionException {
    MapLocation next = loc.add(dir);
    if (Robot.rc.onTheMap(next) && MapData.passable(next)
        && (!check || Robot.rc.canMove(dir))
        && checkMode(next, mode))
      return dir;
    return null;
  }

  /** Greedy towards a goal location. */
  public static Direction getGreedyMove(MapLocation loc, MapLocation goal, boolean check, Mode mode) throws GameActionException {
    Direction direct = loc.directionTo(goal);
    Direction d = tryDir(loc, direct, check, mode);
    if (d != null) return d;

    Direction left  = direct.rotateLeft();
    Direction right = direct.rotateRight();
    MapLocation lNext = loc.add(left);
    MapLocation rNext = loc.add(right);

    if (lNext.distanceSquaredTo(goal) < rNext.distanceSquaredTo(goal)) {
      d = tryDir(loc, left, check, mode);  if (d != null) return d;
      d = tryDir(loc, right, check, mode); if (d != null) return d;
    } else {
      d = tryDir(loc, right, check, mode); if (d != null) return d;
      d = tryDir(loc, left, check, mode);  if (d != null) return d;
    }
    return null;
  }

  public static Direction getGreedyMove(MapLocation loc, MapLocation goal, boolean check) throws GameActionException {
    return getGreedyMove(loc, goal, check, Mode.ANY);
  }

  /** Greedy towards a direction. */
  public static Direction getGreedyMove(MapLocation loc, Direction dir, boolean check, Mode mode) throws GameActionException {
    Direction d = tryDir(loc, dir, check, mode);
    if (d != null) return d;
    d = tryDir(loc, dir.rotateLeft(), check, mode);
    if (d != null) return d;
    d = tryDir(loc, dir.rotateRight(), check, mode);
    return d;
  }

  /** Spiral outward from preferred direction to find any passable tile. */
  public static Direction closestAvailableDirection(MapLocation loc, Direction dir) {
    for (int i = 0; i < 8; i++) {
      MapLocation next = loc.add(dir);
      if (Robot.rc.onTheMap(next) && MapData.passable(next)) return dir;
      dir = (i % 2 == 0) ? dir.rotateLeft() : dir.rotateRight();
    }
    return null;
  }
}
