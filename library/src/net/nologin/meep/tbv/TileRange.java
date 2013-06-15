/*
 *    TiledBitmapView - A library providing a view component rendered dynamically with tile data
 *    Copyright 2013 Barry O'Neill (http://meep.nologin.net/)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package net.nologin.meep.tbv;

/**
 * Represents the range of tile IDs that make up the tile grid.  Immutable.
 */
public class TileRange {

    public final int left, top, right, bottom; // immutable
    public final String toStr;

    /**
     * Create a Tile range by specifying the boundary IDs
     *
     * @param left   The leftmost ID
     * @param top    The topmost ID
     * @param right  The rightmost ID
     * @param bottom The leftmost ID
     */
    public TileRange(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;

        // toString() might get called a lot by debug, take advantage of immutability
        this.toStr =  String.format("TR[x=%d to %d,y=%d to %d,n=%d*%d=%d]",
                left, right, top, bottom, numTilesHorizontal(), numTilesVertical(), numTiles());
    }

    /**
     * Conveniece call to {@link #contains(Tile, int)} with <code>rangePadding = 0</code>.
     */
    public boolean contains(Tile t) {
        return contains(t, 0);
    }

    /**
     * Conveniece call to {@link #contains(int, int, int)} with <code>t</code>'s coordinates
     */
    public boolean contains(Tile t, int padRange) {
        return t != null && contains(t.xId, t.yId, padRange);
    }

    /**
     * Convenience call to {@link #contains(int, int, int)} with <code>rangePadding = 0</code>.
     */
    public boolean contains(int x, int y) {
        return contains(x, y, 0);
    }

    /**
     * Determine whether the supplied (x,y) tile coordinates lie within this range.
     *
     * @param x            The x tile coordinate
     * @param y            The y tile coordinate
     * @param rangePadding This range will be increased in all directions by this amount before checking (x,y).
     *                     Negative values are treated as <code>0</code>.
     * @return <code>true</code> if the range contains (x,y), <code>false</code> otherwise.
     */
    public boolean contains(int x, int y, int rangePadding) {

        rangePadding = Math.max(0, rangePadding);

        return
                // check for empty first
                left < right && top < bottom
                        // then containment (inclusive of all boundaries, unlike android.graphics.Rect)
                        && x >= left - rangePadding
                        && x <= right + rangePadding
                        && y >= top - rangePadding
                        && y <= bottom + rangePadding;
    }


    /**
     * @return The width of this range, in tiles.
     */
    public int numTilesHorizontal() {
        if (left > right) {
            return 0;
        }
        return Math.abs(right - left + 1);
    }

    /**
     * @return The height of this range, in tiles.
     */
    public int numTilesVertical() {
        if (top > bottom) {
            return 0;
        }
        return Math.abs(bottom - top + 1);
    }

    /**
     * @return The number of tiles that make up this range (tiles wide * tiles high)
     */
    public int numTiles() {
        return numTilesHorizontal() * numTilesVertical();
    }

    public String toString() {
        return toStr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TileRange tileRange = (TileRange) o;

        return left == tileRange.left
                && top == tileRange.top
                && right == tileRange.right
                && bottom == tileRange.bottom;

    }

    @Override
    public int hashCode() {
        int result = left;
        result = 31 * result + top;
        result = 31 * result + right;
        result = 31 * result + bottom;
        return result;
    }

}
