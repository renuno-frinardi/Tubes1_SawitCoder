package main_bot;

import battlecode.common.*;

/**
 * Codec untuk mengkodekan dan mendekodekan RobotInfo menjadi integer.
 * Menggunakan bit manipulation untuk menyimpan informasi robot
 * (koordinat, tipe, health%, team, paint%) dalam satu integer.
 */
public class RobotInfoCodec {

    /**
     * Mengkodekan RobotInfo menjadi integer menggunakan bit packing.
     * Format bit: x[0-5] | y[6-11] | type[12-15] | health%[16-22] | team[23] | paint%[24-30]
     *
     * @param robotInfo informasi robot yang akan dikodekan
     * @return integer yang merepresentasikan robotInfo
     */
    public static int encode(RobotInfo robotInfo) {
        int i = 0;
        i += robotInfo.getLocation().x;
        i += robotInfo.getLocation().y << 6;
        i += robotInfo.getType().ordinal() << 12;
        int healthPercent = (100 * robotInfo.getHealth()) / robotInfo.getType().health;
        i += healthPercent << 16;
        i += robotInfo.getTeam().ordinal() << 23;
        int paintPercent = (100 * robotInfo.getPaintAmount()) / robotInfo.getType().paintCapacity;
        i += paintPercent << 24;
        return i;
    }

    /**
     * Mendekodekan integer kembali menjadi RobotInfo menggunakan bit extraction.
     *
     * @param i integer yang merepresentasikan RobotInfo
     * @return RobotInfo yang didekodekan dari integer
     */
    public static RobotInfo decode(int i) {
        int locationMask = (1 << 6) - 1;
        int x = i & locationMask;
        int y = (i >> 6) & locationMask;
        UnitType unitType = UnitType.values()[(i >> 12) & ((1 << 4) - 1)];
        int healthPercent = (i >> 16) & ((1 << 7) - 1);
        Team team = Team.values()[(i >> 23) & 1];
        int paintPercent = (i >> 24) & ((1 << 7) - 1);
        return new RobotInfo(0, team, unitType,
            (int) Math.ceil(((unitType.health / 100.0) * healthPercent)),
            new MapLocation(x, y),
            (int) Math.ceil(((unitType.paintCapacity / 100.0) * paintPercent)));
    }
}
