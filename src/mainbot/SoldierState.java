package mainbot;

/**
 * Enum yang merepresentasikan state FSM (Finite State Machine) soldier.
 * Transisi antar state menggunakan algoritma greedy berdasarkan kondisi saat ini.
 */
public enum SoldierState {
    /** Paint rendah: greedy kembali ke tower terdekat untuk refill. */
    LOWONPAINT,
    /** Mengirim pesan ke tower: greedy navigasi ke tower terdekat. */
    DELIVERINGMESSAGE,
    /** Mengisi tower: greedy cat tile ruin sesuai pola. */
    FILLINGTOWER,
    /** Terjebak: greedy unstuck ke corner peta. */
    STUCK,
    /** Menjelajah: greedy explore ke area terbaik. */
    EXPLORING,
    /** Mengisi SRP: greedy cat pola resource pattern. */
    FILLINGSRP
}
