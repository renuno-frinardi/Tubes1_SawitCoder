package third_bot;

import battlecode.common.*;

public class RobotPlayer {

  private static Robot robot;

  public static void run(RobotController rc) {
    try {
      robot = switch (rc.getType()) {
        case SOLDIER -> new Soldier(rc);
        case MOPPER -> new Mopper(rc);
        case SPLASHER -> new Splasher(rc);
        default -> new Tower(rc);
      };
    } catch (GameActionException e) {
      System.out.println(rc.getType() + " GameActionException");
      e.printStackTrace();
      rc.disintegrate();
    }

    while (true) {
      try {
        robot.run();
      } catch (GameActionException e) {
        System.out.println(rc.getType() + " GameActionException");
        e.printStackTrace();
      } catch (Exception e) {
        System.out.println(rc.getType() + " Exception");
        e.printStackTrace();
      } finally {
        Clock.yield();
      }
    }
  }
}

// Credits : justinottesen/battlecode25