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

import android.util.Log;
import android.util.Pair;

/**
 * An instance of this class is maintained by the {@link TiledBitmapView}.  It is created whenever there's
 * a new surface created, and contains both useful dimension values for that surface configuration (eg tiles wide)
 * but also maintains the various tile ranges and offsets that get changed every time the user interacts with the
 * UI.
 * <br/><br/>
 * All the public methods in this class are <i>synchronized</i>, as the view's instance will be written to by
 * the UI thread (as the user interacts with it), but read by the rendering thread.  In order for the rendering
 * thread to take a thread-safe copy of the variables it requires, this class also contains a {@link Snapshot}
 * class to encapsulate those objects for atomic delivery.
 */
public final class ViewState {

    // finaly variables that don't change for the life of the surface
    public final int tileWidth;
    public final int tilesHoriz, tilesVert;
    public final int surfaceW, surfaceH;
    private final Integer[] tileIDLimits;

    // mutable variables that change as the user interacts with the UI
    private int surfaceOffsetX, surfaceOffsetY;
    private int canvasOffsetX, canvasOffsetY;
    private TileRange visibleTileIdRange;
    private float zoomFactor = 1.0f;

    // a synchronized snapshot of state variables is made by the view on each rendering iteration
    private Snapshot snapshot;

    /**
     * simple wrapper class for delivery of all required variables in one synchronized call
     */
    class Snapshot {

        public int surfaceOffsetX, surfaceOffsetY, canvasOffsetX, canvasOffsetY;
        public TileRange visibleTileIdRange;
        public float zoomFactor;

    }

    /**
     * Create a new ViewState object, providing all the variables that won't change for the life of
     * this instance.
     *
     * @param surfaceW     The surface width (px)
     * @param surfaceH     The surface height (px)
     * @param tileWidth    The length of the tiles used in this surface (see
     *                     {@link net.nologin.meep.tbv.TileProvider#getConfigTileSize()})
     * @param tileIDLimits The tile id limits past which the user may not scroll (if not null, must
     *                     be a 4-element array, see {@link net.nologin.meep.tbv.TileProvider#getConfigTileIDLimits()}
     */
    public ViewState(int surfaceW, int surfaceH, int tileWidth, Integer[] tileIDLimits) {

        this.surfaceW = surfaceW;
        this.surfaceH = surfaceH;
        this.tileWidth = tileWidth;

        if (tileIDLimits != null && tileIDLimits.length != 4) {
            Log.w(Utils.LOG_TAG, "Provider provided " + tileIDLimits.length + " elements, must be 4 - Ignoring.");
            tileIDLimits = null;
        }
        this.tileIDLimits = tileIDLimits;

        tilesHoriz = calculateNumTiles(surfaceW);
        tilesVert = calculateNumTiles(surfaceH);

    }

    /**
     * @return Returns a wrapper object containing the current values of this state instance that are useful to the
     * rendering thread.
     */
    public synchronized Snapshot getUpdatedSnapshot() {

        if (snapshot == null) {

            /* we re-use the snapshot object. Ideally we'd keep creating new immutable objects, but this
             * snapshot method will be called for every surface render, and this keeps instantiations down. */
            snapshot = new Snapshot();
        }
        snapshot.visibleTileIdRange = this.visibleTileIdRange;
        snapshot.surfaceOffsetX = this.surfaceOffsetX;
        snapshot.surfaceOffsetY = this.surfaceOffsetY;
        snapshot.canvasOffsetX = this.canvasOffsetX;
        snapshot.canvasOffsetY = this.canvasOffsetY;
        snapshot.zoomFactor = this.zoomFactor;

        return snapshot;
    }

    /**
     * @return The current range of Tile IDs that will be fetched to render the surface
     */
    public synchronized TileRange getVisibleTileRange() {
        return visibleTileIdRange;
    }

    /**
     * Update all relevant state fields to reflect a change in surface offset by the specified amounts.
     * @param relOffsetX The number of pixels to adjust the current x-offset by
     * @param relOffsetY The number of pixels to adjust the curreny y-offset by
     * @return <code>true</code> if after moving to the new offset, the visible tile ID range has changed
     */
    public synchronized boolean applySurfaceOffsetRelative(int relOffsetX, int relOffsetY) {

        return applySurfaceOffset(surfaceOffsetX + relOffsetX, surfaceOffsetY + relOffsetY);
    }

    /**
     * Update all relevant state fields to reflect a move to the specified surface offsets
     * @param offsetX Set the current x-offset to this pixel value
     * @param offsetY Set the current y-offset to this pixel value
     * @return <code>true</code> if after moving to the new offset, the visible tile ID range has changed
     */
    public synchronized boolean applySurfaceOffset(int offsetX, int offsetY) {

        Pair<Integer, Integer> range_horiz = calculateTileIDRange(offsetX, tilesHoriz);
        Pair<Integer, Integer> range_vert = calculateTileIDRange(offsetY, tilesVert);

        if (tileIDLimits != null && visibleTileIdRange != null) {

            /* Important to check horizontal and vertical tileIDLimits independently, so that diagonal swipes that
               hit a boundary continue to update the scroll.  (Eg, if I'm at the top boundary, and swipe up-left,
               we still want the left part of that scroll to be obeyed.
            */
            if ((tileIDLimits[0] != null && range_horiz.first < tileIDLimits[0])  // left
                    || (tileIDLimits[2] != null && range_horiz.second > tileIDLimits[2])) {  // right
                // Horizontal check fails, keep existing values
                range_horiz = new Pair<Integer, Integer>(visibleTileIdRange.left, visibleTileIdRange.right);
                offsetX = surfaceOffsetX;
            }

            if ((tileIDLimits[1] != null && range_vert.first < tileIDLimits[1]) // top
                    || (tileIDLimits[3] != null && range_vert.second > tileIDLimits[3])) { // bottom
                // Vertical check fails, keep existing values
                range_vert = new Pair<Integer, Integer>(visibleTileIdRange.top, visibleTileIdRange.bottom);
                offsetY = surfaceOffsetY;
            }

        }

        TileRange newRange = new TileRange(range_horiz.first, range_vert.first, range_horiz.second, range_vert.second);
        boolean rangeHasChanged = (visibleTileIdRange == null || !newRange.equals(visibleTileIdRange));
        visibleTileIdRange = newRange;

        surfaceOffsetX = offsetX;
        surfaceOffsetY = offsetY;

        // the grid stays the same size, once the user moves tileWidth pixels in any direction, we reset the canvas
        // offset (the tile IDs will have shifted by one when this happens, so the user will see an 'infinite' grid.
        canvasOffsetX = surfaceOffsetX % tileWidth;
        canvasOffsetY = surfaceOffsetY % tileWidth;

        // in the case we're offset to the right, we need to start rendering 'back' a tile (the longer tile range
        // handles the case of left offset)
        if (canvasOffsetX > 0) {
            canvasOffsetX -= tileWidth;
        }
        if (canvasOffsetY > 0) {
            canvasOffsetY -= tileWidth;
        }

        return rangeHasChanged;

    }

    /**
     * For either the x or the y direction, work out which range of IDs would appear (for the specified number of
     * tiles) should the user scroll that many pixels in that direction.
     * @param coordPx The number of pixels for the x or y direction that the user has scrolled
     * @param numGridTiles The number of visible tiles in that same direction.
     * @return The range of tile IDs for that number of tiles
     */
    private Pair<Integer, Integer> calculateTileIDRange(int coordPx, int numGridTiles) {

        int startTileId = -(coordPx / tileWidth);

        // positive offset means one tile before (negative handled by numTiles)
        if (coordPx % tileWidth > 0) {
            startTileId--;
        }

        // just add the number of tiles (-1 because there is a 0 tile row/column)
        int endTileId = startTileId + numGridTiles - 1;

        return new Pair<Integer, Integer>(startTileId, endTileId);
    }


    /**
     * @return The largest possible number of tiles needed to render a row/column
     *         for the given tile size.  (eg, if two tiles fit perfectly, we'll still need
     *         3 for then the user scrolls slightly off to one side).
     */
    private int calculateNumTiles(int availablePx) {

        /* The + 1 ist to cover scrolling (eg, scroll left a bit, and part of a
         * new tile will appear on the right, but we still need the left tile */
        int num = (availablePx / tileWidth) + 1;

        /* An additional tile if the int division above floored */
        num += (availablePx % tileWidth == 0 ? 0 : 1);

        return num;
    }


    /**
     * Update the state for a pinch-to-zoom value.  This will be limited to the range 1.0 to 5.0.
     * @param zoomFactor The zoomFactor as provided by the view's scale gesture listener.
     * @return The value as stored in the state (may have been trimmed to the range 1.0 to 5.0)
     */
    public synchronized float updateZoomFactor(float zoomFactor) {

        this.zoomFactor *= zoomFactor;

        // Don't let the object get too small or too large.
        zoomFactor = Math.max(1.0f, Math.min(zoomFactor, 5.0f));

        return zoomFactor;

    }


}
