package first_bot;

/**
 * Wrapper immutable untuk koordinat (x, y) yang mendukung hashing.
 * Digunakan dalam Set untuk mengecek apakah koordinat termasuk
 * dalam pola SRP primary tiles.
 */
public class HashableCoords {
    /** Koordinat x. */
    private final int x;
    /** Koordinat y. */
    private final int y;

    /**
     * Membuat HashableCoords baru dengan koordinat (x, y).
     *
     * @param x koordinat x
     * @param y koordinat y
     */
    public HashableCoords(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HashableCoords point = (HashableCoords) o;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}
