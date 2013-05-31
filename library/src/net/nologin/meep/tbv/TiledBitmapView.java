package net.nologin.meep.tbv;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.*;

@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
public abstract class TiledBitmapView extends SurfaceView implements SurfaceHolder.Callback {

    //<editor-fold desc="setup stuff">
    final GestureDetector gestureDetector;
    final ScaleGestureDetector scaleDetector;

    final Paint paint_bg;
    final Paint paint_debugTileTxt;
    final Paint paint_debugBoxTxt;
    final Paint paint_errText;
    final Paint paint_gridLine;
    final Paint paint_debugBG;

    TileGridDrawThread tileDrawThread;
    TileManagementThread tileMgmtThread;

    final ViewState state;

    TileProvider tileProvider;

    protected boolean debugEnabled;

    public TiledBitmapView(Context context, AttributeSet attrs) {

        super(context, attrs);

        //noinspection ConstantConditions
        getHolder().addCallback(this);

        // TODO: persist/restore during suitable lifecycle events
        state = new ViewState();

        // background paint
        paint_bg = new Paint();
        paint_bg.setColor(Color.BLACK); // LTGRAY
        paint_bg.setStyle(Paint.Style.FILL);

        // background status text paint (needed?)
        paint_debugBoxTxt = new Paint();
        paint_debugBoxTxt.setColor(Color.WHITE);
        paint_debugBoxTxt.setTextSize(20);
        paint_debugBoxTxt.setAntiAlias(true);
        paint_debugBoxTxt.setTextAlign(Paint.Align.CENTER);

        paint_debugTileTxt = new Paint(paint_debugBoxTxt);
        paint_debugTileTxt.setTextSize(32);
        paint_debugTileTxt.setShadowLayer(5, 2, 2, Color.BLACK);

        paint_errText = new Paint(paint_debugBoxTxt);
        paint_errText.setColor(Color.RED);

        // background paint
        paint_debugBG = new Paint();
        paint_debugBG.setColor(Color.BLACK);
        paint_debugBG.setStyle(Paint.Style.FILL);
        paint_debugBG.setAlpha(140);

        // grid line
        paint_gridLine = new Paint();
        paint_gridLine.setColor(Color.LTGRAY); // DKGRAY
        paint_gridLine.setStyle(Paint.Style.STROKE);
        paint_gridLine.setStrokeWidth(1);

        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());


        debugEnabled = true; // TODO: Utils.Prefs.getPrefDebugEnabled(context);

    }

    protected void setTileProvider(TileProvider tileProvider) {
        this.tileProvider = tileProvider;
    }

    protected TileProvider getTileProvider() {
        return this.tileProvider;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean toggleDebugEnabled() {
        debugEnabled = !debugEnabled;

        if(tileDrawThread != null){
            tileDrawThread.requestRerender();
        }

        return debugEnabled;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (tileMgmtThread == null || !tileMgmtThread.isAlive()) {
            tileMgmtThread = new TileManagementThread();
            tileMgmtThread.setRunning(true);
            tileMgmtThread.start();
        }
        if (tileDrawThread == null || !tileDrawThread.isAlive()) {
            tileDrawThread = new TileGridDrawThread(holder);
            tileDrawThread.setRunning(true);
            tileDrawThread.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        /* Based on the surfaceDestroyed() method in the 'LunarLander' sample app,
         * Ensure we reconnect to both threads and have them see that we set the running flag to false.
         */
        boolean retry = true;
        tileDrawThread.setRunning(false);
        while (retry) {
            try {
                tileDrawThread.join();
                retry = false;
            } catch (InterruptedException ignored) {
            }
        }

        retry = true;
        tileMgmtThread.setRunning(false);
        while (retry) {
            try {
                tileMgmtThread.join();
                retry = false;
            } catch (InterruptedException ignored) {
            }
        }
    }
    //</editor-fold>


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        state.screenW = width;
        state.screenH = height;

        state.tileW = tileProvider.getTileWidthPixels();

        state.tilesW = maxTilesNeeded(width, state.tileW);
        state.tilesH = maxTilesNeeded(height, state.tileW);

        jumpToOriginTile();
    }

    /**
     * @return The largest possible number of tiles needed to render a row/column
     *         for the given tile size.  (eg, if two tiles fit perfectly, we'll still need
     *         3 for then the user scrolls slightly off to one side).
     */
    private int maxTilesNeeded(int availableSize, int tileWidth) {

        /* The + 1 ist to cover scrolling (eg, scroll left a bit, and part of a
         * new tile will appear on the right, but we still need the left tile */
        int num = (availableSize / tileWidth) + 1;

        /* An additional tile if the int division above floored */
        return num + (availableSize % tileWidth == 0 ? 0 : 1);
    }

    public void jumpToOriginTile() {

        jumpToTile(0, 0);

        if(tileDrawThread != null){
            tileDrawThread.requestRerender();
        }

    }

    public void jumpToTile(int x, int y) {

        if (tileProvider == null) {
            Log.d(Utils.LOG_TAG, "Provider not ready yet, cannot go to tile");
            return;
        }

        if (state.screenW == 0) {
            Log.d(Utils.LOG_TAG, "Surface not ready yet, cannot go to tile");
            return;
        }

        GridAnchor anchor = tileProvider.getGridAnchor();

        Pair<Integer, Integer> originOffset = anchor.getOriginOffset(state.screenW, state.screenH, state.tileW);

        int newOffX = originOffset.first - (state.tileW * x);
        int newOffY = originOffset.second - (state.tileW * y);

        validateAndApplyOffset(newOffX, newOffY);


    }

    private void validateAndApplyOffset(int newOffX, int newOffY) {

        Pair<Integer, Integer> range_horiz = getTileRangeForOffset(newOffX, state.tilesW, state.tileW);
        Pair<Integer, Integer> range_vert = getTileRangeForOffset(newOffY, state.tilesH, state.tileW);

        Integer[] bounds = tileProvider.getTileIndexBounds();
        if (bounds != null && bounds.length != 4) {
            Log.w(Utils.LOG_TAG, "Provider '" + tileProvider + "' provided " + bounds.length
                    + " elements, must be 4 - Ignoring.");
            bounds = null;
        }

        if (bounds != null && state.visibleTileIdRange != null) {

            /* Important to check horizontal and vertical bounds independently, so that diagonal swipes that
               hit a boundary continue to update the scroll.  (Eg, if I'm at the top boundary, and swipe up-left,
               I still want the left part of that scroll to be obeyed */
            // left, top, right, and bottom ID
            if ((bounds[0] != null && range_horiz.first < bounds[0])
                    || (bounds[2] != null && range_horiz.second > bounds[2])) {
                // Horizontal check fails, keep existing values
                range_horiz = new Pair<Integer, Integer>(state.visibleTileIdRange.left, state.visibleTileIdRange.right);
                newOffX = state.scrollOffsetX;
            }

            if ((bounds[1] != null && range_vert.first < bounds[1])
                    || (bounds[3] != null && range_vert.second > bounds[3])) {
                // Vertical check fails, keep existing values
                range_vert = new Pair<Integer, Integer>(state.visibleTileIdRange.top, state.visibleTileIdRange.bottom);
                newOffY = state.scrollOffsetY;
            }

        }

        TileRange newRange = new TileRange(range_horiz.first, range_vert.first, range_horiz.second, range_vert.second);
        state.scrollOffsetX = newOffX;
        state.scrollOffsetY = newOffY;

        state.xCanvasOffset = state.scrollOffsetX % state.tileW;
        state.yCanvasOffset = state.scrollOffsetY % state.tileW;

        // in the case we're offset to the right, we need to start rendering 'back' a tile (the longer tile range
        // handles the case of left offset)
        if (state.xCanvasOffset > 0) {
            state.xCanvasOffset -= state.tileW;
        }
        if (state.yCanvasOffset > 0) {
            state.yCanvasOffset -= state.tileW;
        }

        // call notify only on range change
        if (state.visibleTileIdRange == null || !newRange.equals(state.visibleTileIdRange)) {
            state.visibleTileIdRange = newRange;
            tileProvider.notifyTileIDRangeChange(state.visibleTileIdRange);
        }

    }

    private Pair<Integer, Integer> getTileRangeForOffset(int offset, int numTiles, int tileWidth) {

        int startTileId = -(offset / tileWidth);
        //int startTileId = (-numTiles / 2) - (offset / tileWidth);

        // positive offset means one tile before (negative handled by numTiles)
        if (offset % tileWidth > 0) {
            startTileId--;
        }

        return new Pair<Integer, Integer>(startTileId, startTileId + numTiles - 1);
    }

    private class MissingTile extends Tile {
        public MissingTile(int x, int y) {
            super(x, y, state.tileW);
        }
    }


    /**
     * Very simple thread class, whose only job is to run in the background and continually call the tileProvider
     * and give it a chance to process any queued tasks it has.  It then sets the 'tileDataChanged' boolean flag
     * to true, which is read by the rendering thread when querying if there is possibly new data to render.
     */
    class TileManagementThread extends Thread {

        private boolean running = false;

        private boolean dataChanged = true;

        public void setRunning(boolean running) {
            this.running = running;
        }

        private synchronized boolean getAndSetDataChanged(boolean newValue){

            boolean oldVal = dataChanged;
            dataChanged = newValue;
            return oldVal;
        }

        @Override
        public void run() {

            while (running) {

                if (tileProvider == null) {
                    continue;
                }

                boolean somethingGenerated = tileProvider.generateNextTile(state.visibleTileIdRange);
                if(somethingGenerated){
                    getAndSetDataChanged(true);

                    try{

                        /* back off a bit if there was nothing done. Perhaps this needs to be slightly less
                         * dumb, eg, sleep only after 5 successive empty calls. Or perhaps not needed at all.. */
                        Thread.sleep(20);
                    }
                    catch(InterruptedException ignored){}
                }

            }

        }

    }


    class TileGridDrawThread extends Thread {

        private final SurfaceHolder holder;
        boolean running = false;
        boolean rerenderRequested = false;

        private boolean visibleTileChange, offsetChanged;

        private Tile[][] visibleTiles;
        private int[][] visibleTilesHistory;

        public TileGridDrawThread(SurfaceHolder holder) {
            this.holder = holder;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        public void requestRerender(){
            this.rerenderRequested = true;
        }

        @Override
        public void run() {

            Canvas c;

            TileRange tileRange;
            int xCanvasOffset = 0, yCanvasOffset = 0;
            int xCanvasOffsetOld = 0, yCanvasOffsetOld = 0;

            while (running) {

                if (state.tileW <= 0) {
                    continue; // sanity check - surfaceChanged() work not called/finished yet
                }

                c = null;

                // the offsets may change _during these drawBlah() calls; Make a copy before
                // rendering, otherwise there'll be flickering/tearing of tiles!
                xCanvasOffset = state.xCanvasOffset;
                yCanvasOffset = state.yCanvasOffset;
                tileRange = state.visibleTileIdRange;


                // and detect any change in offset
                offsetChanged = xCanvasOffset != xCanvasOffsetOld || yCanvasOffset != yCanvasOffsetOld;

                // get changes in tile contents (if any) from the provider
                if((tileMgmtThread!=null && tileMgmtThread.getAndSetDataChanged(false)) || rerenderRequested || offsetChanged){
                    Log.d(Utils.LOG_TAG, "Range="+ tileRange);
                    visibleTileChange = getVisibleTileChanges(tileRange);
                }


                // update old values
                xCanvasOffsetOld = xCanvasOffset;
                yCanvasOffsetOld = yCanvasOffset;


                if(visibleTileChange || offsetChanged || rerenderRequested){

                    try {

                        c = holder.lockCanvas(null);
                        if (c == null) {
                            continue;
                        }

                        synchronized (holder) {
                            Log.w(Utils.LOG_TAG, String.format("visTileChng=%s, offsChng=%s, rendReq=%s",visibleTileChange, offsetChanged, rerenderRequested));
                            doDrawTileGrid(c, xCanvasOffset, yCanvasOffset, tileRange);
                        }
                    }
                    finally {
                        // do this in a finally so that if an exception is thrown
                        // during the above, we don't leave the Surface in an
                        // inconsistent state
                        if (c != null) {
                            holder.unlockCanvasAndPost(c);
                        }
                    }

                    // turn off flags that might not be set each iteration
                    rerenderRequested = false;
                    visibleTileChange = false;

                }



                // NB: do not sleep while the canvas is locked
//                try{
//                    Thread.sleep(5);
//                }
//                catch(InterruptedException ignored){}


            }

        }

        // return value indicates if there was a change in tile content requiring a redraw
        private boolean getVisibleTileChanges(TileRange tileRange) {

            if (tileRange == null) {
                return false;
            }

            // only create the visibleTiles array when necessary
            if(visibleTiles == null ||
                    visibleTiles.length != state.tilesH || visibleTiles[0].length != state.tilesW){
                visibleTiles = new Tile[state.tilesH][state.tilesW];

                visibleTilesHistory = new int[state.tilesH][state.tilesW];

            }


            boolean renderRequired = false;
            Tile tile_new;

            int top = tileRange.top;
            int left = tileRange.left;

            int xId, yId;

            for (int y = 0; y < state.tilesH; y++) {

                for (int x = 0; x < state.tilesW; x++) {

                    yId = y + top;
                    xId = x + left;

                    tile_new = tileProvider.getTile(xId, yId);

                    if (tile_new == null) {

                        if(visibleTiles[y][x] != null){
                            renderRequired = true; // wasn't null, now is!
                        }

                        tile_new = new MissingTile(xId, yId); // always return a grid of non-null tiles
                    }
                    else{ // tile_new != null

                        if(visibleTiles[y][x] == null) {
                            renderRequired = true; // was null, now isn't!
                        }
                        else{
                            // old and new tiles were non-null
                            if(visibleTilesHistory[y][x] != tile_new.getBmpHashcode()){
                                renderRequired = true;
                            }
                        }

                    }

                    visibleTiles[y][x] = tile_new;
                }

            }



            return renderRequired;

        }

        public void doDrawTileGrid(Canvas canvas, int xCanvasOffset, int yCanvasOffset, TileRange tileRange) {

            canvas.save();

            // blank out previous contents of screen
            canvas.drawRect(0, 0, state.screenW, state.screenH, paint_bg);

            if(visibleTiles != null) { // null if other thread has generated anything yet

                Tile test = visibleTiles[0][0];

                // this is just a sanity log message to help me debug
                if(test.xId != tileRange.left){
                Log.e(Utils.LOG_TAG,String.format("*** Rendering grid starting at [%2d,%2d] at offs x=%5d, range=%s",
                        test.xId, test.yId, xCanvasOffset, tileRange));
                }


                // offset our canvas, so we can draw our whole tiles on with simple 0,0 origin co-ordinates
                canvas.translate(xCanvasOffset, yCanvasOffset);

                int curTileTop = 0;
                Tile t;

                for (int y=0; y< visibleTiles.length; y++) {

                    Tile[] tileRow = visibleTiles[y];

                    int curTileLeft = 0;

                    for (int x=0; x< tileRow.length; x++) {

                        t = tileRow[x];

                        Bitmap bmp = t.getBmpData();
                        if (bmp != null) {

                            canvas.drawBitmap(bmp, curTileLeft, curTileTop, null);

                            if (debugEnabled) {
                                canvas.drawRect(t.getRect(curTileLeft, curTileTop), paint_gridLine);
                            }

                        }
//                    else {
                        // TODO: null BMP - could allow provider to give us a 'no data' placeholder tile
//                    }

                        // remember what we drew in this cell
                        visibleTilesHistory[y][x] = t.getBmpHashcode();

                        if (debugEnabled) {

                            canvas.drawRect(t.getRect(curTileLeft, curTileTop), paint_gridLine);

                            String msg1 = String.format("[%d,%d]", t.xId, t.yId);
                            canvas.drawText(msg1, curTileLeft + (state.tileW / 2), curTileTop + (state.tileW / 2), paint_debugTileTxt);
                        }

                        curTileLeft += state.tileW; // move right one tile screenWidth
                    }

                    curTileTop += state.tileW; // move down one tile screenWidth
                }


                // we don't want the debug box scrolling with the tiles, undo the offset
                canvas.translate(-xCanvasOffset, -yCanvasOffset);

            }

            if(debugEnabled){
                // -------------------  debug box at bottom right ------------------------

                String fmt1 = "%dx%d, s=%1.3f";
                String fmt2 = "offx=%d,y=%d, xm=%d, ym=%d";
                String fmt3 = "tiles %s";
                String msgResAndScale = String.format(fmt1, state.screenW, state.screenH, state.scaleFactor);
                String msgOffset = String.format(fmt2, state.scrollOffsetX, state.scrollOffsetY, xCanvasOffset, yCanvasOffset);
                String msgVisibleIds = String.format(fmt3, tileRange);
                String msgProvider = tileProvider == null ? "" : tileProvider.getDebugSummary();
                //String msgMemory = Utils.getMemStatus();
                //Paint paintMem = Utils.isHeapAlmostFull() ? paint_errText : paint_debugBoxTxt;

                float boxWidth = 350, boxHeight = 145 - 35;

                float debug_x = state.screenW - boxWidth;
                float debug_y = state.screenH - boxHeight;

                canvas.drawRect(debug_x, debug_y, state.screenW, state.screenH, paint_debugBG);

                canvas.drawText(msgResAndScale, debug_x + boxWidth / 2, debug_y + 30, paint_debugBoxTxt);
                canvas.drawText(msgOffset, debug_x + boxWidth / 2, debug_y + 55, paint_debugBoxTxt);
                canvas.drawText(msgVisibleIds, debug_x + boxWidth / 2, debug_y + 80, paint_debugBoxTxt);
                canvas.drawText(msgProvider, debug_x + boxWidth / 2, debug_y + 105, paint_debugBoxTxt);
                //canvas.drawText(msgMemory, debug_x + boxWidth / 2, debug_y + 130, paintMem);
            }

            canvas.restore();

        }


    }


    //<editor-fold desc="secondary stuff">
    @Override // register GD
    public boolean onTouchEvent(MotionEvent me) {

        invalidate();

        gestureDetector.onTouchEvent(me);
        scaleDetector.onTouchEvent(me);

        return true;
    }


    static class ViewState {

        int screenW, screenH;

        int tileW;

        int tilesW, tilesH;

        // ScaleListener sets this from 0.1 to 5.0
        float scaleFactor = 1.0f;

        int scrollOffsetX = 0, scrollOffsetY = 0, xCanvasOffset = 0, yCanvasOffset = 0;


        TileRange visibleTileIdRange;


    }

    // http://android-developers.blogspot.com/2010/06/making-sense-of-multitouch.html
    class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {

            state.scaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            state.scaleFactor = Math.max(1.0f, Math.min(state.scaleFactor, 5.0f));

            // let the provider know something has changed
            tileProvider.notifyZoomFactorChange(state.scaleFactor);

            return true;
        }
    }


    class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public void onShowPress(MotionEvent motionEvent) {

            Log.d(Utils.LOG_TAG, "show press");

        }

        @Override
        public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float distanceX, float distanceY) {


            int newOffX = state.scrollOffsetX - (int) distanceX;
            int newOffY = state.scrollOffsetY - (int) distanceY;

            validateAndApplyOffset(newOffX, newOffY);

            return true;
        }


        @Override
        public void onLongPress(MotionEvent motionEvent) {

            Log.d(Utils.LOG_TAG, "long press");

        }

        @Override
        public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {

            Log.d(Utils.LOG_TAG, "fling");

            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {

            Log.d(Utils.LOG_TAG, "double tap");

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {

            Log.d(Utils.LOG_TAG, "single tap");

            return false;
        }

    }
    //</editor-fold>


}
