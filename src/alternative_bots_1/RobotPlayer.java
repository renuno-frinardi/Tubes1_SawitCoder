package alternative_bots_1;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {

	static final Direction[] DIRS = {
		Direction.NORTH,
		Direction.NORTHEAST,
		Direction.EAST,
		Direction.SOUTHEAST,
		Direction.SOUTH,
		Direction.SOUTHWEST,
		Direction.WEST,
		Direction.NORTHWEST,
	};

	static final Direction[] CARDINALS = {
		Direction.NORTH,
		Direction.EAST,
		Direction.SOUTH,
		Direction.WEST,
	};

	static final class SharedState {
		final Team team;
		final Team opponent;
		final Random rng;
		final MapLocation birthLocation;

		MapLocation knownFriendlyTower;
		int towerSpawnCount;

		SharedState(RobotController rc) {
			this.team = rc.getTeam();
			this.opponent = team.opponent();
			this.rng = new Random(rc.getID() * 97L + 13L);
			this.birthLocation = rc.getLocation();
		}
	}

	private static SharedState state;
	private static Soldier soldier;
	private static Splasher splasher;
	private static Mopper mopper;

	@SuppressWarnings("unused")
	public static void run(RobotController rc) {
		state = new SharedState(rc);

		if (rc.getType() == UnitType.SOLDIER) {
			soldier = new Soldier(rc, state);
		} else if (rc.getType() == UnitType.SPLASHER) {
			splasher = new Splasher(rc, state);
		} else if (rc.getType() == UnitType.MOPPER) {
			mopper = new Mopper(rc, state);
		}

		while (true) {
			try {
				if (rc.getType() == UnitType.SOLDIER) {
					soldier.runTurn();
				} else if (rc.getType() == UnitType.SPLASHER) {
					splasher.runTurn();
				} else if (rc.getType() == UnitType.MOPPER) {
					mopper.runTurn();
				} else {
					runTowerTurn(rc, state);
				}
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

	static void runTowerTurn(RobotController rc, SharedState s) throws GameActionException {
		MapLocation myLoc = rc.getLocation();

		if (rc.isActionReady()) {
			RobotInfo target = pickBestTowerAttackTarget(rc, s.opponent);
			if (target != null && rc.canAttack(target.getLocation())) {
				rc.attack(target.getLocation());
			}
		}

		if (!rc.isActionReady()) {
			return;
		}

		UnitType nextUnit = chooseTowerSpawnGreedy(rc, s);
		MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
		Direction preferred = myLoc.directionTo(center);
		if (tryBuildRobotGreedy(rc, nextUnit, preferred)) {
			s.towerSpawnCount++;
		}

		if (rc.getChips() > 5000 && rc.canUpgradeTower(myLoc)) {
			rc.upgradeTower(myLoc);
		}
	}

	private static RobotInfo pickBestTowerAttackTarget(RobotController rc, Team opponent)
			throws GameActionException {
		RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);
		RobotInfo best = null;
		int bestScore = Integer.MIN_VALUE;

		for (RobotInfo enemy : enemies) {
			if (!rc.canAttack(enemy.getLocation())) {
				continue;
			}

			int score = 0;
			score += enemy.getType().isTowerType() ? 25 : 10;
			score += Math.max(0, 80 - enemy.getHealth());
			score -= rc.getLocation().distanceSquaredTo(enemy.getLocation());

			if (score > bestScore) {
				bestScore = score;
				best = enemy;
			}
		}

		return best;
	}

	private static UnitType chooseTowerSpawnGreedy(RobotController rc, SharedState s)
			throws GameActionException {
		RobotInfo[] enemies = rc.senseNearbyRobots(-1, s.opponent);
		int enemyCombat = 0;
		int enemyTower = 0;
		for (RobotInfo enemy : enemies) {
			if (enemy.getType().isTowerType()) {
				enemyTower++;
			} else {
				enemyCombat++;
			}
		}

		if (enemyCombat >= 2) {
			return UnitType.MOPPER;
		}

		int round = rc.getRoundNum();
		if (round < 120 && s.towerSpawnCount < 4) {
			return UnitType.SOLDIER;
		}
		if (enemyTower > 0) {
			return UnitType.SOLDIER;
		}

		int roll = s.rng.nextInt(10);
		if (round < 250) {
			if (roll < 5) {
				return UnitType.SOLDIER;
			}
			if (roll < 8) {
				return UnitType.MOPPER;
			}
			return UnitType.SPLASHER;
		}

		if (roll < 4) {
			return UnitType.SOLDIER;
		}
		if (roll < 7) {
			return UnitType.MOPPER;
		}
		return UnitType.SPLASHER;
	}

	static boolean tryBuildRobotGreedy(RobotController rc, UnitType type, Direction preferred)
			throws GameActionException {
		Direction[] priority = {
				preferred,
				preferred.rotateLeft(),
				preferred.rotateRight(),
				preferred.rotateLeft().rotateLeft(),
				preferred.rotateRight().rotateRight(),
		};

		for (Direction d : priority) {
			if (d == Direction.CENTER) {
				continue;
			}
			MapLocation spawn = rc.getLocation().add(d);
			if (rc.canBuildRobot(type, spawn)) {
				rc.buildRobot(type, spawn);
				return true;
			}
		}

		for (Direction d : DIRS) {
			MapLocation spawn = rc.getLocation().add(d);
			if (rc.canBuildRobot(type, spawn)) {
				rc.buildRobot(type, spawn);
				return true;
			}
		}
		return false;
	}

	static MapLocation nearestFriendlyTowerVisible(RobotController rc, SharedState s) throws GameActionException {
		MapLocation best = null;
		int bestDist = Integer.MAX_VALUE;

		for (RobotInfo ally : rc.senseNearbyRobots(-1, s.team)) {
			if (!ally.getType().isTowerType()) {
				continue;
			}
			int d = rc.getLocation().distanceSquaredTo(ally.getLocation());
			if (d < bestDist) {
				bestDist = d;
				best = ally.getLocation();
			}
		}

		if (best != null) {
			s.knownFriendlyTower = best;
			return best;
		}
		return s.knownFriendlyTower != null ? s.knownFriendlyTower : s.birthLocation;
	}

	static boolean lowPaint(RobotController rc, int thresholdPercent) {
		return rc.getPaint() * 100 <= rc.getType().paintCapacity * thresholdPercent;
	}

	static Direction greedyMoveToward(
			RobotController rc,
			SharedState s,
			MapLocation target,
			boolean avoidTowerRange,
			int enemyPaintPenalty,
			int allyPaintBonus)
			throws GameActionException {
		if (target == null || !rc.isMovementReady()) {
			return Direction.CENTER;
		}

		Direction bestDir = Direction.CENTER;
		int bestScore = Integer.MIN_VALUE;
		MapLocation current = rc.getLocation();

		for (Direction d : DIRS) {
			if (!rc.canMove(d)) {
				continue;
			}
			MapLocation next = current.add(d);
			int score = 0;
			score -= next.distanceSquaredTo(target) * 3;

			if (rc.canSenseLocation(next)) {
				PaintType paint = rc.senseMapInfo(next).getPaint();
				if (paint.isEnemy()) {
					score -= enemyPaintPenalty;
				} else if (paint.isAlly()) {
					score += allyPaintBonus;
				}
			}

			if (avoidTowerRange) {
				score += towerDangerAdjustment(rc, s.opponent, next);
			}

			score -= localCrowdingPenalty(rc, s.team, next);

			if (score > bestScore) {
				bestScore = score;
				bestDir = d;
			}
		}

		if (bestDir != Direction.CENTER && rc.canMove(bestDir)) {
			rc.move(bestDir);
		}
		return bestDir;
	}

	private static int towerDangerAdjustment(RobotController rc, Team opponent, MapLocation next)
			throws GameActionException {
		int adjustment = 0;
		for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
			if (!rc.canSenseRobotAtLocation(ruin)) {
				continue;
			}
			RobotInfo robot = rc.senseRobotAtLocation(ruin);
			if (robot == null || !robot.getType().isTowerType() || robot.getTeam() != opponent) {
				continue;
			}
			int dist = next.distanceSquaredTo(ruin);
			if (dist <= 9) {
				adjustment -= 35;
			} else if (dist <= 16) {
				adjustment -= 10;
			}
		}
		return adjustment;
	}

	private static int localCrowdingPenalty(RobotController rc, Team team, MapLocation next) throws GameActionException {
		int crowd = 0;
		for (RobotInfo ally : rc.senseNearbyRobots(8, team)) {
			if (ally.getLocation().equals(rc.getLocation())) {
				continue;
			}
			if (next.isWithinDistanceSquared(ally.getLocation(), 2)) {
				crowd += 2;
			}
		}
		return crowd;
	}
}
