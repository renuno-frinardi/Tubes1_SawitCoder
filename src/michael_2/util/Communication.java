package michael_2.util;

import michael_2.*;

import battlecode.common.*;

public class Communication {

  // Bits 0-3: Message Type
  public static final int MESSAGE_TYPE_BITMASK = 0b1111;
  // Bits 4-15: Coordinates
  public static final int X_COORDINATE_MASK = 0b000000_111111_0000;
  public static final int X_COORDINATE_BITSHIFT = 4;
  public static final int Y_COORDINATE_MASK = 0b111111_000000_0000;
  public static final int Y_COORDINATE_BITSHIFT = 10;

  public static final int SYMMETRY_KNOWLEDGE = 0b0001;
  public static final int REQUEST_MOPPER = 0b0010;
  public static final int SUICIDE = 0b0011;

  public static int getMessageType(int message) { return message & MESSAGE_TYPE_BITMASK; }

  public static MapLocation getCoordinates(int message) { 
    return new MapLocation((message & X_COORDINATE_MASK) >> X_COORDINATE_BITSHIFT, 
                           (message & Y_COORDINATE_MASK) >> Y_COORDINATE_BITSHIFT);
  }

  public static int addCoordinates(int message, MapLocation loc) { return addCoordinates(message, loc.x, loc.y); }

  public static int addCoordinates(int message, int x, int y) {
    message &= ~(X_COORDINATE_MASK | Y_COORDINATE_MASK);
    message |= (x << X_COORDINATE_BITSHIFT) | (y << Y_COORDINATE_BITSHIFT);
    return message;
  }

  public static boolean trySendMessage(int message, MapLocation target) throws GameActionException {
    if (!Robot.rc.canSendMessage(target)) { return false; }
    Robot.rc.sendMessage(target, message);
    return true;
  }

  public static boolean trySendAllMessage(int message, RobotInfo[] targets) throws GameActionException {
    boolean success = false;
    for (RobotInfo robot : targets) {
      success = trySendMessage(message, robot.getLocation()) || success;
    }
    return success;
  }
}

// Credits: justinottesen/battlecode25