package alternative_bots_2;

import alternative_bots_2.util.*;

import battlecode.common.*;

/**
 * Greedy Tower
 * 
 * Greedy decisions:
 * - Attack: target the weakest/one-shottable enemy
 * - Spawn: greedily pick what to build based on immediate needs
 * - Suicide: if out of paint and pattern is complete, sacrifice for rebuild
 */
public final class Tower extends Robot {

  private final int TOWER_ATTACK_RADIUS;
  private final MapLocation LOCATION;

  public Tower(RobotController rc_) throws GameActionException {
    super(rc_);
    TOWER_ATTACK_RADIUS = rc.getType().actionRadiusSquared;
    LOCATION = rc.getLocation();
  }

  protected void doMicro() throws GameActionException {
    attackEnemies();

    // Suicide for paint if worth it
    if (rc.getPaint() == 0 &&
        rc.senseNearbyRobots(-1, OPPONENT).length == 0 &&
        rc.getChips() > rc.getType().moneyCost * 2 &&
        towerPatternComplete(UnitType.LEVEL_ONE_MONEY_TOWER) &&
        Communication.trySendAllMessage(
          Communication.addCoordinates(Communication.SUICIDE, LOCATION), rc.senseNearbyRobots(2, TEAM))) {
      rc.disintegrate();
      return;
    }

    // Read incoming messages
    for (Message m : rc.readMessages(-1)) {
      switch (m.getBytes() & Communication.MESSAGE_TYPE_BITMASK) {
        case Communication.REQUEST_MOPPER:
          MapLocation spawnLoc = trySpawn(UnitType.MOPPER, Communication.getCoordinates(m.getBytes()));
          if (spawnLoc != null) {
            Communication.trySendMessage(m.getBytes(), spawnLoc);
          }
          break;
        default: break;
      }
    }
  }

  protected void doMacro() throws GameActionException {
    spawnRobots();
  }

  /**
   * Greedy attack: AoE first, then target weakest/one-shottable enemy
   */
  private void attackEnemies() throws GameActionException {
    RobotInfo[] enemies = rc.senseNearbyRobots(TOWER_ATTACK_RADIUS, OPPONENT);
    if (enemies.length == 0) { return; }
    
    // AoE attack
    rc.attack(null);

    // Find weakest or one-shot enemy (greedy: maximize damage efficiency)
    RobotInfo target = enemies[0];
    if (target.getHealth() > rc.getType().attackStrength) {
      for (RobotInfo enemy : enemies) {
        if (enemy.getHealth() < target.getHealth()) {
          target = enemy;
          if (target.getHealth() < rc.getType().attackStrength) {
            break; // Found a one-shot target, take it immediately
          }
        }
      }
    }

    if (rc.canAttack(target.getLocation())) {
      rc.attack(target.getLocation());
    }
  }

  /**
   * Greedy spawning: produce the unit that gives the most immediate value
   */
  private void spawnRobots() throws GameActionException {
    // Early game: always spawn soldiers
    if (rc.getRoundNum() <= 2) {
      trySpawn(UnitType.SOLDIER, MapData.MAP_CENTER);
      return;
    }

    // Spawn decision: greedy based on resources
    if (rc.getChips() > rc.getType().moneyCost * 2) {
      // Every fifth spawn is a mopper.
      if (rc.getRoundNum() % 5 == 0) {
        trySpawn(UnitType.MOPPER, MapData.MAP_CENTER);
      } else {
        trySpawn(UnitType.SOLDIER, MapData.MAP_CENTER);
      }
    }
  }

  private MapLocation trySpawn(UnitType type, MapLocation target) throws GameActionException {
    MapLocation loc = getSpawnLoc(type, target);
    if (loc != null && rc.canBuildRobot(type, loc)) { rc.buildRobot(type, loc); }
    return loc;
  }

  private MapLocation getSpawnLoc(UnitType type, MapLocation target) throws GameActionException {
    if (rc.canBuildRobot(UnitType.SOLDIER, target)) { return target; }

    MapLocation closest = null;
    int closest_dist = MapData.MAX_DISTANCE_SQ;
    for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(LOCATION, GameConstants.BUILD_ROBOT_RADIUS_SQUARED)) {
      int dist = loc.distanceSquaredTo(target);
      if (rc.canBuildRobot(UnitType.SOLDIER, loc) && dist < closest_dist) {
        closest = loc;
        closest_dist = dist;
        if (closest_dist < 3) { break; }
      }
    }
    return closest;
  }

  private boolean towerPatternComplete(UnitType type) throws GameActionException {
    if (!type.isTowerType()) { return false; }
    boolean[][] pattern = rc.getTowerPattern(type);

    int x = LOCATION.x - (GameConstants.PATTERN_SIZE / 2);
    int y = LOCATION.y - (GameConstants.PATTERN_SIZE / 2);

    for (MapInfo tile : rc.senseNearbyMapInfos(rc.getLocation(), 8)){
      MapLocation loc = tile.getMapLocation();
      if (loc.equals(LOCATION)) { continue; }
      PaintType paint = tile.getPaint();
      if (!paint.isAlly() || pattern[loc.x - x][loc.y - y] != paint.isSecondary()) { return false; }
    }

    return true;
  }
}
