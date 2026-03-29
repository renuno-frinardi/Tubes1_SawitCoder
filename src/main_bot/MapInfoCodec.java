package main_bot;

import battlecode.common.*;

/**
 * Codec untuk mengkodekan dan mendekodekan MapInfo menjadi integer.
 * Menggunakan bit manipulation untuk menyimpan informasi tile map
 * (koordinat, passability, wall, paint, mark, ruin) dalam satu integer.
 */
public class MapInfoCodec {

    /**
     * Mengkodekan MapInfo menjadi integer menggunakan bit packing.
     * Format bit: x[0-5] | y[6-11] | passable[12] | wall[13] | paint[14-16] | mark[17-19] | ruin[20]
     *
     * @param mapInfo informasi tile yang akan dikodekan
     * @return integer yang merepresentasikan mapInfo
     */
    public static int encode(MapInfo mapInfo) {
        int i = 0;
        i += mapInfo.getMapLocation().x;
        i += mapInfo.getMapLocation().y << 6;
        if (mapInfo.isPassable()) i += 1 << 12;
        if (mapInfo.isWall()) i += 1 << 13;
        i += mapInfo.getPaint().ordinal() << 14;
        i += mapInfo.getMark().ordinal() << 17;
        if (mapInfo.hasRuin()) i += 1 << 20;
        return i;
    }

    /**
     * Mendekodekan integer kembali menjadi MapInfo menggunakan bit extraction.
     *
     * @param i integer yang merepresentasikan MapInfo
     * @return MapInfo yang didekodekan dari integer
     */
    public static MapInfo decode(int i) {
        int mask = (1 << 6) - 1;
        int x = i & mask;
        int y = (i >> 6) & mask;
        boolean isPassable = (i & (1 << 12)) != 0;
        boolean isWall = (i & (1 << 13)) != 0;
        PaintType paint = PaintType.values()[(i >> 14) & ((1 << 3) - 1)];
        PaintType mark = PaintType.values()[(i >> 17) & ((1 << 3) - 1)];
        boolean hasRuin = (i & (1 << 20)) != 0;
        return new MapInfo(new MapLocation(x, y), isPassable, isWall, paint, mark, hasRuin, false);
    }

    /**
     * Mengecek apakah pesan berisi RobotInfo (bukan MapInfo).
     * RobotInfo menggunakan bit di atas posisi 21.
     *
     * @param msg integer pesan yang akan dicek
     * @return true jika pesan adalah RobotInfo
     */
    public static boolean isRobotInfo(int msg) {
        return msg >>> 21 > 0;
    }
}
