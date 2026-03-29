package alternative_bots_2.util;

import battlecode.common.*;
import alternative_bots_2.*;

/**
 * Painting and mopping utilities for the greedy bot.
 */
public class Painter {
  /** Paint a tile with its expected color. */
  public static boolean paint(MapLocation loc) throws GameActionException {
    boolean sec = MapData.useSecondaryPaint(loc);
    if (!Robot.rc.canAttack(loc))
      return false;
    if (!Robot.rc.canPaint(loc)
        && !(Robot.rc.canSenseRobotAtLocation(loc)
            && Robot.rc.senseRobotAtLocation(loc).getTeam() == Robot.OPPONENT))
      return false;
    PaintType cur = Robot.rc.senseMapInfo(loc).getPaint();
    if (cur.isAlly() && !(MapData.knownPaintColor(loc) && cur.isSecondary() != sec))
      return false;
    Robot.rc.attack(loc, sec);
    return true;
  }

  /** Mop a tile if it has enemy paint or an enemy unit. */
  public static boolean mop(MapLocation loc) throws GameActionException {
    if (!Robot.rc.canAttack(loc))
      return false;
    boolean hasEnemy = Robot.rc.canSenseRobotAtLocation(loc)
        && Robot.rc.senseRobotAtLocation(loc).getTeam() == Robot.OPPONENT;
    if (!hasEnemy && !Robot.rc.senseMapInfo(loc).getPaint().isEnemy())
      return false;
    Robot.rc.attack(loc);
    return true;
  }

  // ----- Generic actions (no specific target) -----

  /** Greedily paint: self → enemies → any reachable tile. */
  public static boolean paintAny() throws GameActionException {
    if (!Robot.rc.isActionReady())
      return false;
    if (paint(Robot.rc.getLocation()))
      return true;
    for (RobotInfo r : Robot.rc.senseNearbyRobots(Robot.rc.getType().actionRadiusSquared, Robot.OPPONENT))
      if (paint(r.getLocation()))
        return true;
    for (MapLocation l : Robot.rc.getAllLocationsWithinRadiusSquared(
        Robot.rc.getLocation(), Robot.rc.getType().actionRadiusSquared))
      if (paint(l))
        return true;
    return false;
  }

  /** Greedily mop: self → enemies → any reachable tile. */
  public static boolean mopAny() throws GameActionException {
    if (!Robot.rc.isActionReady())
      return false;
    if (mop(Robot.rc.getLocation()))
      return true;
    for (RobotInfo r : Robot.rc.senseNearbyRobots(Robot.rc.getType().actionRadiusSquared, Robot.OPPONENT))
      if (mop(r.getLocation()))
        return true;
    for (MapLocation l : Robot.rc.getAllLocationsWithinRadiusSquared(
        Robot.rc.getLocation(), Robot.rc.getType().actionRadiusSquared))
      if (mop(l))
        return true;
    return false;
  }

  // ----- Tower kiting -----

  /** Approach enemy tower, attack, then retreat. */
  public static void paintFight(RobotInfo enemy) throws GameActionException {
    if (enemy == null)
      return;
    MapLocation eLoc = enemy.getLocation();
    int dist = Robot.rc.getLocation().distanceSquaredTo(eLoc);
    int eRange = enemy.getType().actionRadiusSquared;

    // Step closer
    if (dist > Robot.rc.getType().actionRadiusSquared && Robot.rc.isMovementReady()) {
      Direction d = Pathfinding.getGreedyMove(Robot.rc.getLocation(), eLoc, true, Pathfinding.Mode.NO_ENEMY);
      if (d != null && Robot.rc.canMove(d)
          && (Robot.rc.getLocation().add(d).distanceSquaredTo(eLoc) > eRange || Robot.rc.isActionReady()))
        MapData.move(d);
    }
    // Attack
    if (Robot.rc.canAttack(eLoc))
      paint(eLoc);
    // Retreat
    if (dist <= eRange && Robot.rc.isMovementReady()) {
      Direction d = Pathfinding.getGreedyMove(Robot.rc.getLocation(),
          eLoc.directionTo(Robot.rc.getLocation()), true, Pathfinding.Mode.ANY);
      if (d != null && Robot.rc.canMove(d))
        MapData.move(d);
    }
    // Paint under self
    if (Robot.rc.canPaint(Robot.rc.getLocation()))
      paint(Robot.rc.getLocation());
  }

  // ----- Pattern capture (shared logic) -----

  /**
   * Build the 5×5 tile list around a center location.
   * Returns the array
   */
  private static final MapLocation[] patternTiles = new MapLocation[GameConstants.PATTERN_SIZE
      * GameConstants.PATTERN_SIZE];
  private static MapLocation cachedCenter;

  private static MapLocation[] getPatternTiles(MapLocation center) {
    if (center.equals(cachedCenter))
      return patternTiles;
    cachedCenter = center;
    int lx = center.x - GameConstants.PATTERN_SIZE / 2;
    int ly = center.y - GameConstants.PATTERN_SIZE / 2;
    for (int dx = 0; dx < GameConstants.PATTERN_SIZE; dx++)
      for (int dy = 0; dy < GameConstants.PATTERN_SIZE; dy++)
        patternTiles[dx * GameConstants.PATTERN_SIZE + dy] = new MapLocation(lx + dx, ly + dy);
    return patternTiles;
  }

  /** Returns true when a tile still needs painting for a pattern. */
  private static boolean needsPaint(MapLocation loc) throws GameActionException {
    if (!Robot.rc.canSenseLocation(loc))
      return true; // unknown = maybe
    PaintType p = Robot.rc.senseMapInfo(loc).getPaint();
    if (p.isEnemy())
      return false; // soldier cant fix enemy paint
    if (!p.isAlly())
      return true; // empty = needs paint
    return MapData.knownPaintColor(loc) && p.isSecondary() != MapData.useSecondaryPaint(loc); // wrong color
  }

  /** Returns true when a tile still needs mopping for a pattern. */
  private static boolean needsMop(MapLocation loc) throws GameActionException {
    if (!Robot.rc.canSenseLocation(loc))
      return true;
    return Robot.rc.senseMapInfo(loc).getPaint().isEnemy();
  }

  /**
   * Move greedily towards a tile, then act on it.
   * isPaint true = paint action, false = mop action
   * return true if action was taken this call
   */
  private static boolean moveAndAct(MapLocation loc, boolean isPaint) throws GameActionException {
    MapLocation cur = Robot.rc.getLocation();
    if (Robot.rc.isMovementReady() && cur.distanceSquaredTo(loc) > Robot.rc.getType().actionRadiusSquared) {
      Pathfinding.Mode mode = isPaint
          ? (Robot.rc.isActionReady() ? Pathfinding.Mode.ANY : Pathfinding.Mode.NO_ENEMY)
          : (Robot.rc.senseMapInfo(cur).getPaint().isAlly() ? Pathfinding.Mode.ALLY_ONLY : Pathfinding.Mode.NO_ENEMY);
      Direction d = Pathfinding.getGreedyMove(cur, loc, true, mode);
      if (d != null && Robot.rc.canMove(d)) {
        MapData.move(d);
        // After moving, act on current tile
        if (isPaint) {
          if (paint(Robot.rc.getLocation()))
            return true;
        } else {
          if (mop(Robot.rc.getLocation()))
            return true;
        }
      }
      return false;
    }
    return isPaint ? paint(loc) : mop(loc);
  }

  // ----- Ruin capture -----

  /** Paint a tower pattern around a ruin. Returns true when done. */
  public static boolean paintCaptureRuin(MapLocation ruin) throws GameActionException {
    // Already captured
    if (Robot.rc.canSenseRobotAtLocation(ruin)) {
      MapData.updateData(Robot.rc.senseMapInfo(ruin));
      cachedCenter = null;
      return true;
    }
    MapLocation[] tiles = getPatternTiles(ruin);

    // Paint tile under self if in pattern
    paintSelfInPattern(ruin);

    // Find first tile that needs work and handle it
    boolean allDone = true;
    for (MapLocation t : tiles) {
      if (t.equals(ruin))
        continue;
      if (needsPaint(t)) {
        allDone = false;
        if (moveAndAct(t, true))
          break;
      }
    }

    // Try completing
    UnitType goal = MapData.getGoalTowerType(ruin);
    if (goal != null && Robot.rc.canCompleteTowerPattern(goal, ruin)) {
      Robot.rc.completeTowerPattern(goal, ruin);
      MapData.updateData(Robot.rc.senseMapInfo(ruin));
      cachedCenter = null;
      return true;
    }
    return allDone;
  }

  /** Paint an SRP pattern. Returns true when done. */
  public static boolean paintCaptureSRP(MapLocation srp) throws GameActionException {
    // Already complete
    if (Robot.rc.canSenseLocation(srp) && Robot.rc.senseMapInfo(srp).isResourcePatternCenter()) {
      MapData.updateData(Robot.rc.senseMapInfo(srp));
      cachedCenter = null;
      return true;
    }
    MapLocation[] tiles = getPatternTiles(srp);

    paintSelfInPattern(srp);

    if (Robot.rc.isActionReady()) {
      for (MapLocation t : tiles) {
        if (needsPaint(t) && moveAndAct(t, true))
          break;
      }
    }

    if (Robot.rc.canCompleteResourcePattern(srp)) {
      Robot.rc.completeResourcePattern(srp);
      MapData.updateData(Robot.rc.senseMapInfo(srp));
      cachedCenter = null;
      return true;
    }
    return false;
  }

  // ----- Mop capture -----

  /** Mop enemy paint around a ruin. Returns true when clean. */
  public static boolean mopCaptureRuin(MapLocation ruin) throws GameActionException {
    if (Robot.rc.canSenseRobotAtLocation(ruin)) {
      MapData.updateData(Robot.rc.senseMapInfo(ruin));
      cachedCenter = null;
      return true;
    }
    MapLocation[] tiles = getPatternTiles(ruin);

    mopSelfInPattern(ruin);

    boolean allClean = true;
    for (MapLocation t : tiles) {
      if (needsMop(t)) {
        allClean = false;
        if (moveAndAct(t, false))
          break;
      }
    }

    UnitType goal = MapData.getGoalTowerType(ruin);
    if (goal != null && Robot.rc.canCompleteTowerPattern(goal, ruin)) {
      Robot.rc.completeTowerPattern(goal, ruin);
      MapData.updateData(Robot.rc.senseMapInfo(ruin));
      cachedCenter = null;
      return true;
    }
    return allClean;
  }

  /** Mop enemy paint around an SRP. Returns true when clean. */
  public static boolean mopCaptureSRP(MapLocation srp) throws GameActionException {
    if (Robot.rc.canSenseLocation(srp) && Robot.rc.senseMapInfo(srp).isResourcePatternCenter()) {
      MapData.updateData(Robot.rc.senseMapInfo(srp));
      cachedCenter = null;
      return true;
    }
    MapLocation[] tiles = getPatternTiles(srp);

    mopSelfInPattern(srp);

    boolean allClean = true;
    for (MapLocation t : tiles) {
      if (needsMop(t)) {
        allClean = false;
        if (moveAndAct(t, false))
          break;
      }
    }

    if (Robot.rc.canCompleteResourcePattern(srp)) {
      Robot.rc.completeResourcePattern(srp);
      MapData.updateData(Robot.rc.senseMapInfo(srp));
      cachedCenter = null;
      return true;
    }
    return allClean;
  }

  // ----- helpers -----

  private static void paintSelfInPattern(MapLocation center) throws GameActionException {
    MapLocation me = Robot.rc.getLocation();
    int dx = me.x - (center.x - GameConstants.PATTERN_SIZE / 2);
    int dy = me.y - (center.y - GameConstants.PATTERN_SIZE / 2);
    if (dx >= 0 && dx < GameConstants.PATTERN_SIZE && dy >= 0 && dy < GameConstants.PATTERN_SIZE)
      paint(me);
  }

  private static void mopSelfInPattern(MapLocation center) throws GameActionException {
    MapLocation me = Robot.rc.getLocation();
    int dx = me.x - (center.x - GameConstants.PATTERN_SIZE / 2);
    int dy = me.y - (center.y - GameConstants.PATTERN_SIZE / 2);
    if (dx >= 0 && dx < GameConstants.PATTERN_SIZE && dy >= 0 && dy < GameConstants.PATTERN_SIZE)
      mop(me);
  }
}