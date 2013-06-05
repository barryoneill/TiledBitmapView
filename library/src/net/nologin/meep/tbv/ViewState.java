package net.nologin.meep.tbv;


import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

/**
 * TODO: document this well
 */
@TargetApi(Build.VERSION_CODES.ECLAIR)
public final class ViewState {

    public final int tileWidth;

    public final int tilesHoriz, tilesVert;

    public final int screenW, screenH;

    private final Integer[] bounds;

    private final int gridBuffer;

    private int xCoordinate, yCoordinate;
    private int xCanvasOffset, yCanvasOffset;
    private TileRange visibleTileIdRange;
    private float zoomFactor = 1.0f; // ScaleListener sets this from 0.1 to 5.0

    private Snapshot snapshot;

    class Snapshot {

        public int xCoordinate, yCoordinate, xCanvasOffset, yCanvasOffset;
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

        this.gridBuffer = Math.max(0,tileGridBuffer); // 0 or greater!

        tilesHoriz = calculateNumTiles(screenW);
        tilesVert = calculateNumTiles(screenH);

    }

    public synchronized Snapshot updateRenderSnapshot(){

        if(snapshot == null){
            snapshot = new Snapshot();
        }
        snapshot.visibleTileIdRange = this.visibleTileIdRange;
        snapshot.xCoordinate = this.xCoordinate;
        snapshot.yCoordinate = this.yCoordinate;
        snapshot.xCanvasOffset = this.xCanvasOffset;
        snapshot.yCanvasOffset = this.yCanvasOffset;
        snapshot.zoomFactor = this.zoomFactor;

        return snapshot;
    }

    public synchronized TileRange getVisibleTileIdRange(){
        return visibleTileIdRange;
    }

    public synchronized TileRange goToCoordinatesOffset(int newOffX, int newOffY) {

        return goToCoordinates(xCoordinate + newOffX, yCoordinate + newOffY);
    }

    public synchronized TileRange goToCoordinates(int newXCoord, int newYCoord) {

        Pair<Integer, Integer> range_horiz = calculateTileIDRange(newXCoord, tilesHoriz);
        Pair<Integer, Integer> range_vert = calculateTileIDRange(newYCoord, tilesVert);

//        if (bounds != null) {
//
//            /* Important to check horizontal and vertical bounds independently, so that diagonal swipes that
//               hit a boundary continue to update the scroll.  (Eg, if I'm at the top boundary, and swipe up-left,
//               I still want the left part of that scroll to be obeyed */
//            // left, top, right, and bottom ID
//            if ((bounds[0] != null && range_horiz.first < bounds[0])
//                    || (bounds[2] != null && range_horiz.second > bounds[2])) {
//                // Horizontal check fails, keep existing values
//                range_horiz = new Pair<Integer, Integer>(visibleTileIdRange.left, visibleTileIdRange.right);
//                newXCoord = xCoordinate;
//            }
//
//            if ((bounds[1] != null && range_vert.first < bounds[1])
//                    || (bounds[3] != null && range_vert.second > bounds[3])) {
//                // Vertical check fails, keep existing values
//                range_vert = new Pair<Integer, Integer>(visibleTileIdRange.top, visibleTileIdRange.bottom);
//                newYCoord = yCoordinate;
//            }
//
//        }

        TileRange newRange = new TileRange(range_horiz.first, range_vert.first, range_horiz.second, range_vert.second);
        xCoordinate = newXCoord;
        yCoordinate = newYCoord;

        xCanvasOffset = xCoordinate % tileWidth;
        yCanvasOffset = yCoordinate % tileWidth;

        // in the case we're offset to the right, we need to start rendering 'back' a tile (the longer tile range
        // handles the case of left offset)
        if (xCanvasOffset > 0) {
            xCanvasOffset -= tileWidth;
        }
        if (yCanvasOffset > 0) {
            yCanvasOffset -= tileWidth;
        }

        // if the provider specifies a grid buffer, we need to move left/up that many tiles
        xCanvasOffset -= tileWidth * gridBuffer;
        yCanvasOffset -= tileWidth * gridBuffer;


        // call notify only on range change
        if (visibleTileIdRange == null || !newRange.equals(visibleTileIdRange)) {
            visibleTileIdRange = newRange;
            return newRange;
        } else {
            return null; // signal that no change in range occured
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
