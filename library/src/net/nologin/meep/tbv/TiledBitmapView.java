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

    final GestureDetector gestureDetector;
    final ScaleGestureDetector scaleDetector;

    final Paint paint_bg;
    final Paint paint_debugTileTxt;
    final Paint paint_debugBoxTxt;
    final Paint paint_errText;
    final Paint paint_gridLine;
    final Paint paint_debugBG;

    TileGridDrawThread tileDrawThread;
    TileGenerationThread tileGenThread;

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
        paint_bg.setColor(Color.DKGRAY); // LTGRAY
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
        return debugEnabled;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (tileGenThread == null || !tileGenThread.isAlive()) {
            tileGenThread = new TileGenerationThread();
            tileGenThread.setRunning(true);
            tileGenThread.start();
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
        tileGenThread.setRunning(false);
        while (retry) {
            try {
                tileGenThread.join();
                retry = false;
            } catch (InterruptedException ignored) {
            }
        }
    }


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

        public Bitmap getBmpData() {
            return null;
        }
    }

    private void refreshVisibleTileGrid() {

        if (state.visibleTileIdRange == null) {
            return;
        }

        // only create the visibleTiles array when necessary
        if(state.visibleTiles == null
                || state.visibleTiles.length != state.tilesH
                || state.visibleTiles[0].length != state.tilesW){
            state.visibleTiles = new Tile[state.tilesH][state.tilesW];

        }

        Tile t;

        int top = state.visibleTileIdRange.top;
        int left = state.visibleTileIdRange.left;

        int xId, yId;

        try {

            for (int y = 0; y < state.tilesH; y++) {

                for (int x = 0; x < state.tilesW; x++) {

                    yId = y + top;
                    xId = x + left;

                    t = tileProvider.getTile(xId, yId);
                    if (t == null) {
                        t = new MissingTile(xId, yId); // always return a grid of non-null tiles
                    }

                    state.visibleTiles[y][x] = t;
                }

            }

        }
        catch(ArrayIndexOutOfBoundsException er) {
            throw new IllegalArgumentException("Caugh AIOBE with tileRange '" + state.visibleTileIdRange + "', +e=" + er.getMessage());
        }



    }

    class TileGenerationThread extends Thread {

        private boolean running = false;

        public void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public void run() {

            while (running) {

                if (tileProvider == null) {
                    continue;
                }

                boolean workPerformed = tileProvider.generateNextTile(state.visibleTileIdRange);
                try{
                    if(!workPerformed) {
                        Thread.sleep(20); // back off a bit if there was nothing done
                    }
                }
                catch(InterruptedException ignored){}

            }

        }

    }


    class TileGridDrawThread extends Thread {

        //Measure frames per second.
        long fpsTimeNow, fpsTimePrev;
        int fpsFrameCnt = 0, fpsAvg = 0;

        private final SurfaceHolder holder;
        private boolean running = false;


        public TileGridDrawThread(SurfaceHolder holder) {
            this.holder = holder;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }


        @Override
        public void run() {

            Canvas c;
            while (running) {

                c = null;

                try {


                    c = holder.lockCanvas(null);
                    if (c == null || tileProvider == null) {
                        continue; // is this right?
                    }
                    synchronized (holder) {
                        doDraw(c);
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

                // NB: do not sleep while the canvas is locked
//                try{
//                    Thread.sleep(5);
//                }
//                catch(InterruptedException ignored){}

                if (debugEnabled) {
                    // calculate draws per second
                    fpsTimeNow = System.currentTimeMillis();
                    fpsFrameCnt++;
                    if (fpsTimeNow - fpsTimePrev > 1000) {
                        fpsTimePrev = fpsTimeNow;
                        fpsAvg = fpsFrameCnt;
                        fpsFrameCnt = 0;
                    }
                }

            }

        }

    }


    public void doDraw(Canvas canvas) {

        super.onDraw(canvas);

        if (state.tileW <= 0) {
            return;
        }

        canvas.save();

        // the offsets may change _during this doDraw_ call; Make a copy before
        // rendering, otherwise there'll be flickering/tearing of tiles!
        int xMargin = state.scrollOffsetX % state.tileW;
        int yMargin = state.scrollOffsetY % state.tileW;

        // in the case we're offset to the right, we need to start rendering 'back' a tile (the longer tile range
        // handles the case of left offset)
        if (xMargin > 0) {
            xMargin -= state.tileW;
        }
        if (yMargin > 0) {
            yMargin -= state.tileW;
        }

        // blank out previous contents of screen
        canvas.drawRect(0, 0, state.screenW, state.screenH, paint_bg);

        // offset our canvas, so we can draw our whole tiles on with simple 0,0 origin co-ordinates
        canvas.translate(xMargin, yMargin);

        // draw BG
        canvas.drawRect(0, 0, state.screenW, state.screenH, paint_bg);

        if (tileProvider != null) {

            refreshVisibleTileGrid();

            int y = 0;

            for (Tile[] tileRow : state.visibleTiles) {

                int x = 0;

                for (Tile t : tileRow) {

                    Bitmap bmp = t.getBmpData();
                    if (bmp != null) {

                        canvas.drawBitmap(bmp, x, y, null);

                        if (debugEnabled) {
                            canvas.drawRect(t.getRect(x, y), paint_gridLine);
                        }


                    }
//                    else {
//                        // TODO: could allow provider to give us a 'no data' placeholder tile
//                    }

                    if (debugEnabled) {

                        canvas.drawRect(t.getRect(x, y), paint_gridLine);

                        String msg1 = String.format("[%d,%d]", t.xId, t.yId);
                        canvas.drawText(msg1, x + (state.tileW / 2), y + (state.tileW / 2), paint_debugTileTxt);
                    }

                    x += state.tileW; // move right one tile screenWidth
                }

                y += state.tileW; // move down one tile screenWidth
            }


        }

        // we don't want the debug box scrolling with the tiles, undo the offset
        canvas.translate(-xMargin, -yMargin);
        if (debugEnabled) {
            drawDebugBox(canvas, xMargin, yMargin);
        }

        canvas.restore();

    }

    private void drawDebugBox(Canvas canvas, int xm, int ym) {

        // draw a bunch of debug stuff
        String fmt1 = "%dx%d, s=%1.3f, %2dfps";
        String fmt2 = "offx=%d,y=%d, xm=%d, ym=%d";
        String fmt3 = "tiles %s";
        String msgResAndScale = String.format(fmt1, state.screenW, state.screenH, state.scaleFactor, tileDrawThread.fpsAvg);
        String msgOffset = String.format(fmt2, state.scrollOffsetX, state.scrollOffsetY, xm, ym);
        String msgVisibleIds = String.format(fmt3, state.visibleTileIdRange);
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
        Tile[][] visibleTiles;

        // ScaleListener sets this from 0.1 to 5.0
        float scaleFactor = 1.0f;

        int scrollOffsetX = 0, scrollOffsetY = 0;

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


}
