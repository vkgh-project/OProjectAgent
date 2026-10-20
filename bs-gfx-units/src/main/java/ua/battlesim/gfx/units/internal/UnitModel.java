package ua.battlesim.gfx.units.internal;

import java.util.List;
import java.util.Objects;

/**
 * The complete procedural geometry of one unit class: an ordered, immutable
 * list of {@link Piece}s.
 *
 * <p>Construction is pure CPU work (no OpenGL) so the builders can be unit
 * tested without a GPU.
 *
 * <p>Immutable and thread-safe.
 */
public final class UnitModel {

    private final int type;
    private final List<Piece> pieces;

    public UnitModel(int type, List<Piece> pieces) {
        Objects.requireNonNull(pieces, "pieces");
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("pieces must not be empty");
        }
        for (Piece p : pieces) {
            Objects.requireNonNull(p, "piece");
        }
        this.type = type;
        this.pieces = List.copyOf(pieces);
    }

    /** @return the unit-type index ({@code 0} tank, {@code 1} APC, {@code 2} infantry) */
    public int type() {
        return type;
    }

    /** @return the ordered pieces (immutable) */
    public List<Piece> pieces() {
        return pieces;
    }

    /** @return the number of pieces */
    public int pieceCount() {
        return pieces.size();
    }

    /** @return the total number of vertices across all pieces */
    public int totalVertexCount() {
        int total = 0;
        for (Piece p : pieces) {
            total += p.vertexCount();
        }
        return total;
    }

    /** @return the number of pieces that have the given role */
    public int countRole(Role role) {
        int count = 0;
        for (Piece p : pieces) {
            if (p.role == role) {
                count++;
            }
        }
        return count;
    }
}
