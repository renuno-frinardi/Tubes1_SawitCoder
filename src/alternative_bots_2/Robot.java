package alternative_bots_2;

import battlecode.common.*;
import alternative_bots_2.util.*;

public abstract class Robot {

  public static RobotController rc;
  
  public static Team TEAM;
  public static Team OPPONENT;
  
  public static int CREATED_ROUND;

  public Robot(RobotController rc_) throws GameActionException {
    rc = rc_;
    
    TEAM = rc.getTeam();
    OPPONENT = TEAM.opponent();

    CREATED_ROUND = rc.getRoundNum();
    
    MapData.init();
    MapData.updateAllVisible();
  };

  final public void run() throws GameActionException {
    doMicro(); // Act based on immediate surroundings
    doMacro(); // Act based on long term
  }

  /**
   * Handles the micro game of the robot.
   */
  protected abstract void doMicro() throws GameActionException;

  /**
   * Handles the macro game of the robot.
   */
  protected abstract void doMacro() throws GameActionException;
}

// Credits: justinottesen/battlecode25