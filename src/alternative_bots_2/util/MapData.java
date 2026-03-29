package alternative_bots_2.util;

import battlecode.common.*;
import alternative_bots_2.*;

/**
 * Encodes and updates known map state, tower targets, and exploration data.
 */
public class MapData {

  public static int MAP_WIDTH;
  public static int MAP_HEIGHT;
  public static MapLocation MAP_CENTER;

  public static int MAX_DISTANCE_SQ;

  private static final int EXPLORE_CHUNK_SIZE = 5;

  private static boolean[][] SRP_ARRAY;
  private static boolean[][] PAINT_ARRAY;
  private static boolean[][] MONEY_ARRAY;
  private static boolean[][] DEFENSE_ARRAY;

  public static MapLocation foundSRP = null;

  private static int symmetryType     = 0b111;
  private static final int ROTATIONAL = 0b001;
  private static final int HORIZONTAL = 0b010;
  private static final int VERTICAL   = 0b100;

  private static int[] knownRuins;
  private static int ruinIndex;

  private static int[] mapData;
  private static final int UNKNOWN = 0b0;

  // Bits 0-1: Immutable characteristics
  private static final int EMPTY             = 0b01;
  private static final int RUIN              = 0b10;
  private static final int WALL              = 0b11;
  private static final int TILE_TYPE_BITMASK = 0b11;
  
  // Bits 2-4: Tower Type Data (Only applicable for ruins)
  private static final int UNCLAIMED_RUIN     = 0b001_00;
  private static final int MONEY_TOWER        = 0b010_00;
  private static final int PAINT_TOWER        = 0b011_00;
  private static final int DEFENSE_TOWER      = 0b100_00;
  private static final int TOWER_TYPE_BITMASK = 0b111_00;

  // Bit 5: Friendly = 1, foe = 0
  private static final int FRIENDLY_TOWER = 0b1_000_00;

  // Bits 6-16: Last round updated
  private static final int LAST_UPDATED_BITMASK = 0b11111111111_0_000_00;
  private static final int LAST_UPDATED_BITSHIFT = 6;

  // Bits 19-20: Goal Tower Type
  private static final int GOAL_MONEY_TOWER   = 0b01_00_00000000000_0_000_00;
  private static final int GOAL_PAINT_TOWER   = 0b10_00_00000000000_0_000_00;
  private static final int GOAL_DEFENSE_TOWER = 0b11_00_00000000000_0_000_00;
  private static final int GOAL_TOWER_BITMASK = 0b11_00_00000000000_0_000_00;

  // Bit 21: Goal Paint Color
  private static final int GOAL_SECONDARY_PAINT = 0b001_00_00_00000000000_0_000_00;
  private static final int GOAL_COLOR_KNOWN     = 0b010_00_00_00000000000_0_000_00;
  private static final int GOAL_COLOR_CANDIDATE = 0b100_00_00_00000000000_0_000_00;

  // Bit 22: Contested
  private static final int CONTESTED_TARGET = 0b1_000_00_00_00000000000_0_000_00;

  public static void init() throws GameActionException {
    MAP_WIDTH = Robot.rc.getMapWidth();
    MAP_HEIGHT = Robot.rc.getMapHeight();
    MAP_CENTER = new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2);
    MAX_DISTANCE_SQ = MAP_WIDTH * MAP_WIDTH + MAP_HEIGHT * MAP_HEIGHT;

    SRP_ARRAY = Robot.rc.getResourcePattern();
    PAINT_ARRAY = Robot.rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
    MONEY_ARRAY = Robot.rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER);
    DEFENSE_ARRAY = Robot.rc.getTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER);

    mapData = new int[MAP_WIDTH * MAP_HEIGHT];
    knownRuins = new int[MAP_WIDTH / GameConstants.PATTERN_SIZE * MAP_HEIGHT / GameConstants.PATTERN_SIZE];
  }

  public static void move(Direction dir) throws GameActionException {
    Robot.rc.move(dir);
    updateNewlyVisible(dir);
  }

  public static void updateAllVisible() throws GameActionException {
    foundSRP = null;
    for (MapInfo info : Robot.rc.senseNearbyMapInfos()) {
      updateData(info);
    }
  }
  
  public static void updateNewlyVisible(Direction lastDir) throws GameActionException {
    foundSRP = null;
    if (lastDir == Direction.CENTER) { return; }
    assert GameConstants.VISION_RADIUS_SQUARED == 20;
    
    MapLocation current = Robot.rc.getLocation();
    Direction leftDir = lastDir.rotateLeft().rotateLeft();
    Direction rightDir = lastDir.rotateRight().rotateRight();
    if (lastDir.dx == 0 ^ lastDir.dy == 0) {
      MapLocation center = current.translate(4 * lastDir.dx, 4 * lastDir.dy);
      MapLocation leftLoc = center.add(leftDir);
      MapLocation rightLoc = center.add(rightDir);
      if (Robot.rc.onTheMap(center)) { updateData(Robot.rc.senseMapInfo(center)); }
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftDir = leftDir.rotateLeft();
      rightDir = rightDir.rotateRight();
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
    } else {
      MapLocation center = current.translate(3 * lastDir.dx, 3 * lastDir.dy);
      MapLocation leftLoc = center.add(leftDir);
      MapLocation rightLoc = center.add(rightDir);
      if (Robot.rc.onTheMap(center)) { updateData(Robot.rc.senseMapInfo(center)); }
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftDir = leftDir.rotateLeft();
      rightDir = rightDir.rotateRight();
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      if (Robot.rc.onTheMap(center.add(leftDir))) { updateData(Robot.rc.senseMapInfo(center.add(leftDir))); }
      if (Robot.rc.onTheMap(center.add(rightDir))) { updateData(Robot.rc.senseMapInfo(center.add(rightDir))); }
    }
  }

  public static void updateData(MapInfo info) throws GameActionException {
    MapLocation loc = info.getMapLocation();
    int index = getIndex(loc);

    if (mapData[index] == 0) {
      if (info.hasRuin()) { mapData[index] = RUIN; knownRuins[ruinIndex++] = index; }
      else if (info.isWall()) { mapData[index] = WALL; }
      else { mapData[index] = EMPTY; }
      
      if (!symmetryKnown() && Robot.rc.getRoundNum() > 2) {
        if ((symmetryType & HORIZONTAL) > 0) {
          int h_index = symmetryIndex(index, HORIZONTAL);
          if (mapData[h_index] != UNKNOWN && mapData[h_index] != mapData[index]) {
            symmetryType ^= HORIZONTAL;
          }
        }
        if ((symmetryType & VERTICAL) > 0) {
          int v_index = symmetryIndex(index, VERTICAL);
          if (mapData[v_index] != UNKNOWN && mapData[v_index] != mapData[index]) {
            symmetryType ^= VERTICAL;
          }
        }
        if ((symmetryType & ROTATIONAL) > 0) {
          int r_index = symmetryIndex(index, ROTATIONAL);
          if (mapData[r_index] != UNKNOWN && mapData[r_index] != mapData[index]) {
            symmetryType ^= ROTATIONAL;
          }
        }
      }
    }

    mapData[index] &= ~LAST_UPDATED_BITMASK;
    mapData[index] |= Robot.rc.getRoundNum() << LAST_UPDATED_BITSHIFT;
      
    if (symmetryKnown()) {
      int symIndex = symmetryIndex(index, symmetryType);
      if (symIndex == UNKNOWN) {
        mapData[symIndex] = mapData[index] & TILE_TYPE_BITMASK;
        if (info.hasRuin()) { knownRuins[ruinIndex++] = index; }
      }
    }
    
    if ((mapData[index] & TILE_TYPE_BITMASK) == RUIN) {
      RobotInfo towerInfo = Robot.rc.senseRobotAtLocation(loc);
      if (towerInfo != null) {
        mapData[index] &= ~TILE_TYPE_BITMASK;
        mapData[index] |= switch (towerInfo.getType().getBaseType()) {
          case UnitType.LEVEL_ONE_DEFENSE_TOWER -> DEFENSE_TOWER;
          case UnitType.LEVEL_ONE_PAINT_TOWER -> PAINT_TOWER;
          case UnitType.LEVEL_ONE_MONEY_TOWER -> MONEY_TOWER;
          default -> 0;
        };
        if (towerInfo.getTeam().equals(Robot.TEAM)) { 
          mapData[index] |= FRIENDLY_TOWER;
        }
        if ((mapData[index] & GOAL_TOWER_BITMASK) == 0) {
          setGoalTowerType(index, towerInfo.getType().getBaseType());
        }
      } else {
        mapData[index] |= UNCLAIMED_RUIN;
        if (Robot.rc.getNumberTowers() < 5) {
          setGoalTowerType(index, UnitType.LEVEL_ONE_MONEY_TOWER);
        } else {
          setGoalTowerType(index, UnitType.LEVEL_ONE_PAINT_TOWER);
        }
      }
    } else if ((mapData[index] & TILE_TYPE_BITMASK) == EMPTY) {
      if (info.isResourcePatternCenter()) {
        markSRP(loc, false);
        foundSRP = loc;
      } else {
        switch (info.getMark()) {
          case ALLY_PRIMARY:
            markSRP(loc, false);
            foundSRP = loc;
            break;
          default: break;
        }
      }
    } 
  }

  public static boolean passable(MapLocation loc) { return (readData(loc) & TILE_TYPE_BITMASK) < RUIN; }
  public static boolean known(MapLocation loc) { return readData(loc) != UNKNOWN; }

  private static int getIndex(MapLocation loc) { return getIndex(loc.x, loc.y); }
  private static int getIndex(int x, int y) { return x + y * MAP_WIDTH; }
  private static int getX(int index) { return index % MAP_WIDTH; }
  private static int getY(int index) { return index / MAP_WIDTH; }
  private static MapLocation getLoc(int index) { return new MapLocation(getX(index), getY(index)); }
  private static int readData(MapLocation loc) { return mapData[getIndex(loc)]; }
  private static int readData(int x, int y) { return mapData[getIndex(x, y)]; }

  public static boolean symmetryKnown() { return (symmetryType == 1) || (symmetryType & (symmetryType - 1)) == 0; }

  public static MapLocation symmetryLoc(MapLocation loc, int symmetryType) {
    return switch (symmetryType) {
      case HORIZONTAL -> new MapLocation(loc.x, MAP_HEIGHT - (loc.y + 1));
      case VERTICAL -> new MapLocation(MAP_WIDTH - (loc.x + 1), loc.y);
      case ROTATIONAL -> new MapLocation(MAP_WIDTH - (loc.x + 1), MAP_HEIGHT - (loc.y + 1));
      default -> null; 
    };
  }

  private static int symmetryIndex(int index, int symmetryType) {
    int x = getX(index);
    int y = getY(index);
    return switch (symmetryType) {
      case HORIZONTAL -> x + (MAP_HEIGHT - (y + 1)) * MAP_WIDTH;
      case VERTICAL -> MAP_WIDTH - (x + 1) + y * MAP_WIDTH;
      case ROTATIONAL -> MAP_WIDTH - (x + 1) + (MAP_HEIGHT - (y + 1)) * MAP_WIDTH;
      default -> -1;
    };
  }

  public static MapLocation closestFriendlyTower() {
    if (ruinIndex == 0) { return null; }
    MapLocation current = Robot.rc.getLocation();
    MapLocation closestTower = null;
    int closestDist = 0;
    for (int i = 0; i < ruinIndex; ++i) {
      if ((mapData[knownRuins[i]] & TOWER_TYPE_BITMASK) == 0) { continue; }
      if ((mapData[knownRuins[i]] & TOWER_TYPE_BITMASK) == UNCLAIMED_RUIN) { continue; }
      if ((mapData[knownRuins[i]] & FRIENDLY_TOWER) == 0) { continue; }
      MapLocation towerLoc = getLoc(knownRuins[i]);
      int ruinDist = current.distanceSquaredTo(towerLoc);
      if (closestTower == null || ruinDist < closestDist) {
        closestTower = towerLoc;
        closestDist = ruinDist;
      }
    }
    return closestTower;
  }

  public static MapLocation closestEnemyTower() {
    if (ruinIndex == 0) { return null; }
    MapLocation current = Robot.rc.getLocation();
    MapLocation closestTower = null;
    int closestDist = 0;
    for (int i = 0; i < ruinIndex; ++i) {
      if ((mapData[knownRuins[i]] & TOWER_TYPE_BITMASK) == 0) { continue; }
      if ((mapData[knownRuins[i]] & TOWER_TYPE_BITMASK) == UNCLAIMED_RUIN) { continue; }
      if ((mapData[knownRuins[i]] & FRIENDLY_TOWER) > 0) { continue; }
      MapLocation towerLoc = getLoc(knownRuins[i]);
      int ruinDist = current.distanceSquaredTo(towerLoc);
      if (closestTower == null || ruinDist < closestDist) {
        closestTower = towerLoc;
        closestDist = ruinDist;
      }
    }
    return closestTower;
  }

  public static boolean isContested(MapLocation loc) { return (mapData[getIndex(loc)] & CONTESTED_TARGET) > 0; }

  public static boolean setContested(MapLocation loc) {
    int index = getIndex(loc);
    if ((mapData[index] & CONTESTED_TARGET) > 0) { return false; }
    mapData[index] |= CONTESTED_TARGET;
    return true;
  }

  public static boolean useSecondaryPaint(MapLocation loc) {
    return knownPaintColor(loc) &&
           (readData(loc) & (GOAL_SECONDARY_PAINT)) > 0;
  }

  public static boolean knownPaintColor(MapLocation loc) {
    return (readData(loc) & (GOAL_COLOR_KNOWN | GOAL_COLOR_CANDIDATE)) != 0;
  }

  public static boolean setGoalTowerType(MapLocation loc, UnitType towerType) {
    UnitType baseType = towerType.getBaseType();
    boolean[][] pattern = switch (baseType) {
      case UnitType.LEVEL_ONE_PAINT_TOWER -> PAINT_ARRAY;
      case UnitType.LEVEL_ONE_MONEY_TOWER -> MONEY_ARRAY;
      case UnitType.LEVEL_ONE_DEFENSE_TOWER -> DEFENSE_ARRAY;
      default -> null;
    };
    if (pattern == null) { return false; }
    int towerIndex = getIndex(loc);
    mapData[towerIndex] &= ~GOAL_TOWER_BITMASK;
    mapData[towerIndex] |= switch (baseType) {
      case UnitType.LEVEL_ONE_PAINT_TOWER -> GOAL_PAINT_TOWER;
      case UnitType.LEVEL_ONE_MONEY_TOWER -> GOAL_MONEY_TOWER;
      case UnitType.LEVEL_ONE_DEFENSE_TOWER -> GOAL_DEFENSE_TOWER;
      default -> 0;
    };
    // Set the goal paint types - Unroll loop for bytecode
    int index = getIndex(loc.x - (GameConstants.PATTERN_SIZE / 2), loc.y - (GameConstants.PATTERN_SIZE / 2));
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[0][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[1][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[2][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[3][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[4][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[0][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[1][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[2][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[3][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[4][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[0][2]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[1][2]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += 2;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[3][2]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[4][2]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[0][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[1][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[2][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[3][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[4][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[0][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[1][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[2][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[3][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= GOAL_COLOR_KNOWN;
    if (pattern[4][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    return true;
  }

  private static boolean setGoalTowerType(int index, UnitType towerType) { return setGoalTowerType(getLoc(index), towerType); }

  public static UnitType getGoalTowerType(MapLocation loc) { return getGoalTowerType(getIndex(loc)); }

  private static UnitType getGoalTowerType(int index) {
    return switch (mapData[index] & GOAL_TOWER_BITMASK) {
      case GOAL_MONEY_TOWER -> UnitType.LEVEL_ONE_MONEY_TOWER;
      case GOAL_PAINT_TOWER -> UnitType.LEVEL_ONE_PAINT_TOWER;
      case GOAL_DEFENSE_TOWER -> UnitType.LEVEL_ONE_DEFENSE_TOWER;
      default -> null;
    };
  }

  public static MapLocation getExploreTarget() {
    MapLocation closest = null;
    int closest_dist = MAX_DISTANCE_SQ;
    for (int x = EXPLORE_CHUNK_SIZE / 2; x < MAP_WIDTH; x += EXPLORE_CHUNK_SIZE) {
      for (int y = EXPLORE_CHUNK_SIZE / 2; y < MAP_HEIGHT; y += EXPLORE_CHUNK_SIZE) {
        if ((readData(x, y) & LAST_UPDATED_BITMASK) != 0) { continue; }
        MapLocation newLoc = new MapLocation(x, y);
        int dist = Robot.rc.getLocation().distanceSquaredTo(newLoc);
        if (dist < closest_dist) {
          closest = newLoc;
          closest_dist = dist;
        }
      }
    }
    return closest != null ? closest : MAP_CENTER;
  }

  public static boolean tryMarkSRP(MapLocation loc) throws GameActionException {
    if (canMarkSRP(loc)) {
      markSRP(loc);
      return true;
    }
    return false;
  }

  private static boolean canMarkSRP(MapLocation loc) throws GameActionException {
    if (!Robot.rc.canMarkResourcePattern(loc)) { return false; }

    MapInfo info = Robot.rc.senseMapInfo(loc);
    if (info.isResourcePatternCenter()) { return true; }
    PaintType mark = Robot.rc.senseMapInfo(loc).getMark();
    if (mark == PaintType.ALLY_PRIMARY) {
      return true;
    }

    // Check if there are possible ruin conflicts we can't see
    MapLocation checkLoc = loc.translate(-3, -4);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(-4, -3);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(3, -4);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(4, -3);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(3, 4);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(4, 3);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(-3, 4);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(-4, 3);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }

    // Check if there are any nearby marks we don't know about
    for (MapInfo nearby_info : Robot.rc.senseNearbyMapInfos()) {
      if (!nearby_info.getMark().isAlly()) { continue; }
      markSRP(nearby_info.getMapLocation(), false);
    }

    // Check the goal paint for squares around it (unrolled for bytecode)
    int index = getIndex(loc.x - (GameConstants.PATTERN_SIZE / 2), loc.y - (GameConstants.PATTERN_SIZE / 2));
    for (int row = 0; row < GameConstants.PATTERN_SIZE; ++row) {
      for (int col = 0; col < GameConstants.PATTERN_SIZE; ++col) {
        int tileData = mapData[index];
        if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
        if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
            ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[col][row]) { return false; }
        ++index;
      }
      index += MAP_WIDTH - GameConstants.PATTERN_SIZE;
    }
      
    return true;
  }

  private static void markSRP(MapLocation loc) throws GameActionException { markSRP(loc, true); }

  private static void markSRP(MapLocation loc, boolean first) throws GameActionException {
    if (first && Robot.rc.canSenseLocation(loc)) {
      PaintType mark = Robot.rc.senseMapInfo(loc).getMark();
      if (mark != PaintType.ALLY_PRIMARY) {
        Robot.rc.mark(loc, false);
      }
    }

    int index = getIndex(loc.x - (GameConstants.PATTERN_SIZE / 2), loc.y - (GameConstants.PATTERN_SIZE / 2));
    for (int row = 0; row < GameConstants.PATTERN_SIZE; ++row) {
      for (int col = 0; col < GameConstants.PATTERN_SIZE; ++col) {
        mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[col][row] ? GOAL_SECONDARY_PAINT : 0));
        ++index;
      }
      index += MAP_WIDTH - GameConstants.PATTERN_SIZE;
    }
  }
}

// Credits: justinottesen/battlecode25