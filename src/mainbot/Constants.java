package mainbot;

/**
 * Kelas Constants menyimpan semua konstanta konfigurasi untuk bot.
 * Nilai-nilai ini digunakan sebagai parameter dalam berbagai algoritma greedy
 * untuk menentukan threshold, skor, dan bonus.
 */
public final class Constants {

    private Constants() {}

    /** Array 8 arah cardinal dan diagonal yang sering digunakan untuk iterasi greedy. */
    public static final battlecode.common.Direction[] directions = {
        battlecode.common.Direction.NORTH,
        battlecode.common.Direction.NORTHEAST,
        battlecode.common.Direction.EAST,
        battlecode.common.Direction.SOUTHEAST,
        battlecode.common.Direction.SOUTH,
        battlecode.common.Direction.SOUTHWEST,
        battlecode.common.Direction.WEST,
        battlecode.common.Direction.NORTHWEST,
    };


    /** Threshold paint rendah untuk soldier (di bawah ini → kembali ke tower). */
    public static final int LOW_PAINT_THRESHOLD = 20;
    /** Threshold paint rendah untuk splasher. */
    public static final int SPLASHER_LOW_PAINT = 75;
    /** Threshold uang; jika uang di atas ini, soldier tidak perlu refill paint. */
    public static final int LOW_PAINT_MONEY_THRESHOLD = 5000;
    /** Minimum paint yang harus dimiliki tower untuk memberi ke soldier. */
    public static final int MIN_PAINT_GIVE = 50;


    /** Skor dasar greedy untuk spawn soldier. */
    public static final int SOLDIER_BASE_SCORE = 100;
    /** Skor dasar greedy untuk spawn splasher. */
    public static final int SPLASHER_BASE_SCORE = 40;
    /** Skor dasar greedy untuk spawn mopper. */
    public static final int MOPPER_BASE_SCORE = 30;
    /** Jumlah soldier minimum yang harus di-spawn sebelum mempertimbangkan tipe lain. */
    public static final int SOLDIER_FIRST_COUNT = 3;
    /** Bonus skor splasher per kunjungan musuh (greedy adjustment). */
    public static final int ENEMY_VISIT_SPLASHER_BONUS = 30;
    /** Bonus skor splasher jika sudah banyak soldier yang di-spawn. */
    public static final int MANY_SOLDIERS_BONUS = 20;
    /** Bonus skor mopper jika banyak paint musuh di sekitar tower. */
    public static final int ENEMY_PAINT_MOPPER_BONUS = 25;
    /** Threshold uang untuk memaksa spawn unit (greedy force spawn). */
    public static final int FORCE_SPAWN_CHIPS = 1500;
    /** Minimum uang yang dibutuhkan paint tower untuk spawn. */
    public static final int SPAWN_MIN_MONEY = 400;


    /** Threshold uang untuk upgrade paint tower level 1 → 2. */
    public static final int UPGRADE_PAINT_L1 = 5000;
    /** Threshold uang untuk upgrade money tower level 1 → 2. */
    public static final int UPGRADE_MONEY_L1 = 7500;
    /** Threshold uang untuk upgrade paint tower level 2 → 3. */
    public static final int UPGRADE_PAINT_L2 = 7500;
    /** Threshold uang untuk upgrade money tower level 2 → 3. */
    public static final int UPGRADE_MONEY_L2 = 10000;
    /** Threshold uang untuk upgrade defense tower level 1 → 2. */
    public static final int UPGRADE_DEFENSE_L1 = 5000;
    /** Threshold uang untuk upgrade defense tower level 2 → 3. */
    public static final int UPGRADE_DEFENSE_L2 = 7500;


    /** Bonus skor greedy jika intermediate target lebih dekat ke tujuan. */
    public static final int EXPLORE_CLOSER_BONUS = 20;
    /** Bonus skor greedy jika intermediate target jarak sama ke tujuan. */
    public static final int EXPLORE_EQUAL_BONUS = 10;
    /** Bonus skor greedy untuk tile kosong saat eksplorasi. */
    public static final int EXPLORE_EMPTY_TILE = 3;
    /** Bonus skor greedy untuk tile dengan paint musuh saat eksplorasi. */
    public static final int EXPLORE_ENEMY_PAINT = 5;
    /** Penalti skor greedy jika ada robot ally di tile yang dievaluasi. */
    public static final int EXPLORE_ALLY_ROBOT_PENALTY = 3;
    /** Threshold skor untuk memutuskan apakah harus break intermediate target. */
    public static final int EXPLORE_BREAK_THRESHOLD = 45;


    /** Jumlah turn sebelum developer soldier berubah tipe. */
    public static final int DEV_LIFE_CYCLE_TURNS = 30;
    /** Jumlah turn sebelum SRP builder berubah tipe. */
    public static final int SRP_LIFE_CYCLE_TURNS = 30;
    /** Batas lebar peta untuk mode SRP peta kecil. */
    public static final int SRP_MAP_WIDTH = 95;
    /** Batas tinggi peta untuk mode SRP peta kecil. */
    public static final int SRP_MAP_HEIGHT = 95;


    /** Skor grid untuk tile kosong (netral, tidak prioritas). */
    public static final int SPLASH_EMPTY = 0;
    /** Skor grid untuk tile enemy (positif, prioritas tinggi untuk di-splash). */
    public static final int SPLASH_ENEMY = 2;
    /** Skor grid untuk tile ally (negatif, hindari splash tile sendiri). */
    public static final int SPLASH_ALLY = -1;


    /** Jumlah tower awal yang harus berupa money tower. */
    public static final int MONEY_TOWER_FIRST_N = 3;
    /** Persentase target paint tower dari total tower (digunakan untuk greedy scoring). */
    public static final double PERCENT_PAINT = 0.7;


    /** Set koordinat yang harus dicat primary dalam pola SRP 4x4. */
    public static final java.util.Set<HashableCoords> primarySRP = java.util.Set.of(
        new HashableCoords(2,0),
        new HashableCoords(1,1), new HashableCoords(2,1), new HashableCoords(3,1),
        new HashableCoords(0,2), new HashableCoords(1,2), new HashableCoords(3,2),
        new HashableCoords(1,3), new HashableCoords(2,3), new HashableCoords(3,3),
        new HashableCoords(2,4), new HashableCoords(4,2)
    );


    /** Pola cat 5x5 untuk paint tower. */
    public static final battlecode.common.PaintType[][] paintTowerPattern = {
        {battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY},
        {battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY},
        {battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.EMPTY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY},
        {battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY},
        {battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY}
    };

    /** Pola cat 5x5 untuk money tower. */
    public static final battlecode.common.PaintType[][] moneyTowerPattern = {
        {battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY},
        {battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY},
        {battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.EMPTY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY},
        {battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY},
        {battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY}
    };

    /** Pola cat 5x5 untuk defense tower. */
    public static final battlecode.common.PaintType[][] defenseTowerPattern = {
        {battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY},
        {battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY},
        {battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.EMPTY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY},
        {battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY},
        {battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_SECONDARY, battlecode.common.PaintType.ALLY_PRIMARY, battlecode.common.PaintType.ALLY_PRIMARY}
    };
}
