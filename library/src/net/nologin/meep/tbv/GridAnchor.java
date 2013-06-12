package net.nologin.meep.tbv;

import android.util.Pair;

/**
 * When repositioning the grid to display a certain tile, this enum can be used to instruct the creation
 * of the grid in such a way that the desired tile is located at the desired position on the view surface.
 */
public enum GridAnchor {

    TopLeft, TopCenter, TopRight,
    CenterLeft, Center, CenterRight,
    BottomLeft, BottomCenter, BottomRight;

    /**
     * Calculate the x,y (or left,top in canvas speak) coordinates at which a tile would need
     * to be drawn to match this constant.
     *
     * @param surfaceWidth  The screen width in px
     * @param surfaceHeight The screen height in px
     * @param tileWidth    The width of a tile in px
     * @return A {@link Pair} containing the (left,top) position as described.
     */
    final Pair<Integer, Integer> getPosition(int surfaceWidth, int surfaceHeight, int tileWidth) {

        // default to 0,0 (covers left hand side and across top)
        int x = 0, y = 0;

        // anchors vertical through the middle
        if (TopCenter == this || Center == this || BottomCenter == this) {
            x = (surfaceWidth - tileWidth) / 2;
        }

        // anchors down the right hand side
        if (TopRight == this || CenterRight == this || BottomRight == this) {
            x = surfaceWidth - tileWidth;
        }

        // anchors horizontal through the middle
        if (CenterLeft == this || Center == this || CenterRight == this) {
            y = (surfaceHeight - tileWidth) / 2;
        }

        // anchors along the bottom
        if (BottomLeft == this || BottomCenter == this || BottomRight == this) {
            y = surfaceHeight - tileWidth;
        }

        // Aside: debate on '==' vs equals for enums at the following page:
        // http://stackoverflow.com/questions/1750435/comparing-java-enum-members-or-equals

        // my kingdom for a core language tuple
        return new Pair<Integer, Integer>(x, y);
    }
}
