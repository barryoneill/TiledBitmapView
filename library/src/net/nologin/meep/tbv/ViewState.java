package net.nologin.meep.tbv;


import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

/**
 * Represents the state of the tiles to be rendered in the TileBitmapView.
 *
 * TiledBitmapView maintains an instance of this class, and updates the content on lifecycle and
 * user interaction events (Eg, start, scroll etc).  The state contained in this mutable class should
 * be stored and read in a thread-safe atomic manner.  This means the rendering thread should take
 * snapshots of the 'state' that it needs to render in a synchronized manner, as the the UI thread
 * may have since updated the state.
 *
 */
@TargetApi(Build.VERSION_CODES.ECLAIR)
public final class ViewState {

    public final int tileWidth;

    public final int tilesHoriz, tilesVert;

    public final int screenW, screenH;

    private final Integer[] bounds;

    private final int gridBuffer;

    private int surfaceOffsetX, surfaceOffsetY;
    private int canvasOffsetX, canvasOffsetY;
    private TileRange visibleTileIdRange;
    private float zoomFactor = 1.0f; // ScaleListener sets this from 0.1 to 5.0

    private Snapshot snapshot;

    class Snapshot {

        public int surfaceOffsetX, surfaceOffsetY, canvasOffsetX, canvasOffsetY;
        public TileRange visibleTileIdRange;
        public float zoomFactor;

    }

    public ViewState(int screenW, int screenH, int tileWidth, Integer[] bounds, int tileGridBuffer) {

        this.screenW = screenW;
        this.screenH = screenH;
        this.tileWidth = tileWidth;

        if (bounds != null && bounds.length != 4) {
            Log.w(Utils.LOG_TAG, "Provider provided " + bounds.length + " elements, must be 4 - Ignoring.");
            bounds = null;
        }
        this.bounds = bounds;

        this.gridBuffer = Math.max(0, tileGridBuffer); // 0 or greater!

        tilesHoriz = calculateNumTiles(screenW);
        tilesVert = calculateNumTiles(screenH);

    }

    public synchronized Snapshot createSnapshot() {

        if (snapshot == null) {
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

    public synchronized TileRange getVisibleTileIdRange() {
        return visibleTileIdRange;
    }

    public synchronized TileRange setSurfaceOffsetRelative(int relOffsetX, int relOffsetY, boolean alwaysReturnRange) {

        return setSurfaceOffset(surfaceOffsetX + relOffsetX, surfaceOffsetY + relOffsetY, alwaysReturnRange);
    }

    public synchronized TileRange setSurfaceOffset(int offsetX, int offsetY, boolean alwaysReturnRange) {

        Pair<Integer, Integer> range_horiz = calculateTileIDRange(offsetX, tilesHoriz);
        Pair<Integer, Integer> range_vert = calculateTileIDRange(offsetY, tilesVert);

        if (bounds != null && visibleTileIdRange != null) {

            /* Important to check horizontal and vertical bounds independently, so that diagonal swipes that
               hit a boundary continue to update the scroll.  (Eg, if I'm at the top boundary, and swipe up-left,
               we still want the left part of that scroll to be obeyed.

               The bounds are defined against the visible range, not that with the gridBuffer, so compare accordingly.
            */
            if ((bounds[0] != null && range_horiz.first + gridBuffer < bounds[0])  // left
                    || (bounds[2] != null && range_horiz.second - gridBuffer > bounds[2])) {  // right
                // Horizontal check fails, keep existing values
                range_horiz = new Pair<Integer, Integer>(visibleTileIdRange.left, visibleTileIdRange.right);
                offsetX = surfaceOffsetX;
            }

            if ((bounds[1] != null && range_vert.first + gridBuffer < bounds[1]) // top
                    || (bounds[3] != null && range_vert.second - gridBuffer > bounds[3])) { // bottom
                // Vertical check fails, keep existing values
                range_vert = new Pair<Integer, Integer>(visibleTileIdRange.top, visibleTileIdRange.bottom);
                offsetY = surfaceOffsetY;
            }

        }

        TileRange newRange = new TileRange(range_horiz.first, range_vert.first, range_horiz.second, range_vert.second);
        surfaceOffsetX = offsetX;
        surfaceOffsetY = offsetY;

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

        // if the provider specifies a grid buffer, we need to move left/up that many tiles
        canvasOffsetX -= tileWidth * gridBuffer;
        canvasOffsetY -= tileWidth * gridBuffer;


        // call notify only on range change
        if (visibleTileIdRange == null || !newRange.equals(visibleTileIdRange)) {
            visibleTileIdRange = newRange;
            return newRange;
        } else {
            return alwaysReturnRange ? newRange : null; // signal that no change in range occured
        }

    }


    private Pair<Integer, Integer> calculateTileIDRange(int coordPx, int numTiles) {

        int startTileId = -(coordPx / tileWidth);
        //int startTileId = (-numTiles / 2) - (offset / tileWidth);

        // positive offset means one tile before (negative handled by numTiles)
        if (coordPx % tileWidth > 0) {
            startTileId--;
        }

        startTileId -= gridBuffer;

        int endTileId = startTileId + numTiles - 1;

        // include the buffer!
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

        num += gridBuffer * 2; // to cover buffer tiles either side of the range

        return num;
    }


    public synchronized float updateZoomFactor(float zoomFactor) {

        this.zoomFactor *= zoomFactor;

        // Don't let the object get too small or too large.
        zoomFactor = Math.max(1.0f, Math.min(zoomFactor, 5.0f));

        return zoomFactor;

    }


}
