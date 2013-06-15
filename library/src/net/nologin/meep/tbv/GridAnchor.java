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
    public final Pair<Integer, Integer> getPosition(int surfaceWidth, int surfaceHeight, int tileWidth) {

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
