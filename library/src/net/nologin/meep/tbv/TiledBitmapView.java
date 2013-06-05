package net.nologin.meep.tbv;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.os.Process;
import android.view.*;

@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
public abstract class TiledBitmapView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String STATEKEY_SUPERCLASS = "net.nologin.meep.tbv.super";
    private static final String STATEKEY_DEBUG_ENABLED = "net.nologin.meep.tbv.debugenabled";

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

    ViewState state;

    TileProvider tileProvider;

    private boolean debugEnabled;

    public TiledBitmapView(Context context, AttributeSet attrs) {

        super(context, attrs);

        // noinspection ConstantConditions
        getHolder().addCallback(this); // register as SurfaceHolder handler

        // background paint
        paint_bg = new Paint();
        paint_bg.setColor(Color.BLACK); // LTGRAY
        paint_bg.setStyle(Paint.Style.FILL);

        // background status text paint (needed?)
        paint_debugBoxTxt = new Paint();
        paint_debugBoxTxt.setColor(Color.WHITE);
        paint_debugBoxTxt.setTextSize(14);
        paint_debugBoxTxt.setAntiAlias(true);
        paint_debugBoxTxt.setTypeface(Typeface.MONOSPACE);
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

    }

    protected void setTileProvider(TileProvider tileProvider) {
        this.tileProvider = tileProvider;
    }

    protected TileProvider getTileProvider() {
        return this.tileProvider;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean toggleDebugEnabled() {
        debugEnabled = !debugEnabled;

        if (tileDrawThread != null) {
            tileDrawThread.requestRerender();
        }

        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        if(tileDrawThread != null) {
            tileDrawThread.requestRerender();
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {

        Bundle bundle = new Bundle();
        bundle.putParcelable(STATEKEY_SUPERCLASS, super.onSaveInstanceState());
        bundle.putBoolean(STATEKEY_DEBUG_ENABLED, debugEnabled);

        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {

        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            this.debugEnabled = bundle.getBoolean(STATEKEY_DEBUG_ENABLED);
            super.onRestoreInstanceState(bundle.getParcelable(STATEKEY_SUPERCLASS));
            return;
        }

        super.onRestoreInstanceState(state);
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

        state = new ViewState(width,height,tileProvider.getTileWidthPixels(),
                tileProvider.getTileIndexBounds(), tileProvider.getGridBufferSize());

        jumpToOriginTile();
    }


    public void jumpToOriginTile() {

        jumpToTile(0, 0);

        if (tileDrawThread != null) {
            tileDrawThread.requestRerender();
        }

    }

    public void jumpToTile(int x, int y) {

        if (tileProvider == null) {
            Log.d(Utils.LOG_TAG, "Provider not ready yet, cannot go to tile");
            return;
        }

        if (state == null || state.screenW == 0) {
            Log.d(Utils.LOG_TAG, "Surface not ready yet, cannot go to tile");
            return;
        }

        GridAnchor anchor = tileProvider.getGridAnchor();

        Pair<Integer, Integer> originCoords = anchor.getOriginCoords(state.screenW, state.screenH, state.tileWidth);

        int newX = originCoords.first - (state.tileWidth * x);
        int newY = originCoords.second - (state.tileWidth * y);

        TileRange updatedRange = state.goToCoordinates(newX, newY);
        if(updatedRange != null){
            tileProvider.notifyTileIDRangeChange(updatedRange);
        }

    }



    private class MissingTile extends Tile {
        public MissingTile(int x, int y) {
            super(x, y, state.tileWidth);
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

        TileRange range = null;

        public TileManagementThread(){


        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        private synchronized boolean getAndSetDataChanged(boolean newValue) {

            boolean oldVal = dataChanged;
            dataChanged = newValue;
            return oldVal;
        }

        @Override
        public void run() {



            while (running) {

                if (tileProvider == null || state == null) {
                    continue;
                }

                range = state.getVisibleTileIdRange();
                if(range == null) {
                    continue;
                }


                boolean somethingGenerated = tileProvider.processQueue(range);
                if (somethingGenerated) {
                    getAndSetDataChanged(true);

                    try {

                        /* back off a bit if there was nothing done. Perhaps this needs to be slightly less
                         * dumb, eg, sleep only after 5 successive empty calls. Or perhaps not needed at all.. */
                        Thread.sleep(20);
                    } catch (InterruptedException ignored) {
                    }
                }

            }

        }

    }


    class TileGridDrawThread extends Thread {

        private final SurfaceHolder holder;
        boolean running = false;
        boolean rerenderRequested = false;

        private boolean visibleTileChange, offsetChanged;

        private ViewState.Snapshot snapshot;
        private Tile[][] visibleTiles;
        private int[][] visibleTilesHistory;


        public TileGridDrawThread(SurfaceHolder holder) {
            this.holder = holder;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        public void requestRerender() {
            this.rerenderRequested = true;
        }

        @Override
        public void run() {

            // this should have a ever so slightly better prio than the tile generation thread
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);

            Canvas c;

            int xCanvasOffsetOld = 0, yCanvasOffsetOld = 0;

            while (running) {

                if(state == null){
                    continue;
                }

                snapshot = state.updateRenderSnapshot();

                if (state.tileWidth <= 0 || snapshot.visibleTileIdRange == null) {
                    continue; // sanity check - surfaceChanged() work not called/finished yet
                }

                c = null;

                // and detect any change in offset
                offsetChanged = snapshot.xCanvasOffset != xCanvasOffsetOld || snapshot.yCanvasOffset != yCanvasOffsetOld;

                // update old values
                xCanvasOffsetOld = snapshot.xCanvasOffset;
                yCanvasOffsetOld = snapshot.yCanvasOffset;


                // get changes in tile contents (if any) from the provider
                if ((tileMgmtThread != null && tileMgmtThread.getAndSetDataChanged(false)) || rerenderRequested || offsetChanged) {
                    visibleTileChange = getVisibleTileChanges(snapshot.visibleTileIdRange);
                }

                if (visibleTileChange || offsetChanged || rerenderRequested) {

                    try {

                        c = holder.lockCanvas(null);
                        if (c == null) {
                            continue;
                        }

                        synchronized (holder) {
                            //Log.w(Utils.LOG_TAG, String.format("visTileChng=%s, offsChng=%s, rendReq=%s",visibleTileChange, offsetChanged, rerenderRequested));
                            doDrawTileGrid(c, snapshot);
                        }
                    } finally {
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
        private boolean getVisibleTileChanges(TileRange visibleRange) {

            // only create the visibleTiles array when necessary
            if (visibleTiles == null ||
                    visibleTiles.length != state.tileWidth || visibleTiles[0].length != state.tileWidth) {
                visibleTiles = new Tile[state.tilesVert][state.tilesHoriz];

                visibleTilesHistory = new int[state.tilesVert][state.tilesHoriz];

            }


            boolean renderRequired = false;
            Tile tile_new;

            int xId, yId;

            for (int y = 0; y < state.tilesVert; y++) {

                for (int x = 0; x < state.tilesHoriz; x++) {

                    yId = y + visibleRange.top;
                    xId = x + visibleRange.left;

                    tile_new = tileProvider.getTile(xId, yId);

                    if (tile_new == null) {

                        if (visibleTiles[y][x] != null) {
                            renderRequired = true; // wasn't null, now is!
                        }

                        tile_new = new MissingTile(xId, yId); // always return a grid of non-null tiles
                    } else { // tile_new != null

                        if (visibleTiles[y][x] == null) {
                            renderRequired = true; // was null, now isn't!
                        } else {
                            // old and new tiles were non-null
                            if (visibleTilesHistory[y][x] != tile_new.getBmpHashcode()) {
                                renderRequired = true;
                            }
                        }

                    }

                    visibleTiles[y][x] = tile_new;
                }

            }


            return renderRequired;

        }

        public void doDrawTileGrid(Canvas canvas, ViewState.Snapshot snapshot) {

            canvas.save();

            // blank out previous contents of screen
            canvas.drawRect(0, 0, state.screenW, state.screenH, paint_bg);

            if (visibleTiles != null) { // null if other thread has generated anything yet

                //Log.e(Utils.LOG_TAG, String.format("X=%6d,CX=%5d,CXS=%5d,R=%s", state.scrollOffsetX, xCanvasOffset, state.xCanvasOffset, tileRange));


                // offset our canvas, so we can draw our whole tiles on with simple 0,0 origin co-ordinates
                canvas.translate(snapshot.xCanvasOffset, snapshot.yCanvasOffset);

                int curTileTop = 0;
                Tile t;

                for (int y = 0; y < visibleTiles.length; y++) {

                    Tile[] tileRow = visibleTiles[y];

                    int curTileLeft = 0;

                    for (int x = 0; x < tileRow.length; x++) {

                        t = tileRow[x];

                        Bitmap bmp = t.getBmpData();
                        if (bmp != null) {

                            canvas.drawBitmap(bmp, curTileLeft, curTileTop, null);

                            if (debugEnabled) {
                                canvas.drawRect(t.getRect(curTileLeft, curTileTop), paint_gridLine);
                            }

                        }
                        // else {
                        //     could possibly let providers give us a 'no data' tile in the future.  For now, ignore.
                        // }

                        // remember what we drew in this cell
                        visibleTilesHistory[y][x] = t.getBmpHashcode();

                        if (debugEnabled) {

                            canvas.drawRect(t.getRect(curTileLeft, curTileTop), paint_gridLine);

                            String msg1 = String.format("[%d,%d]", t.xId, t.yId);
                            canvas.drawText(msg1, curTileLeft + (state.tileWidth / 2),
                                    curTileTop + (state.tileWidth / 2), paint_debugTileTxt);
                        }

                        curTileLeft += state.tileWidth; // move right one tile screenWidth
                    }

                    curTileTop += state.tileWidth; // move down one tile screenWidth
                }


                // we don't want the debug box scrolling with the tiles, undo the offset
                canvas.translate(-snapshot.xCanvasOffset, -snapshot.yCanvasOffset);

            }

            if (debugEnabled) {
                // -------------------  debug box at bottom right ------------------------

                String fmt1 = "%dx%d, s=%1.3f";
                String fmt2 = "x=%5d,y=%5d, cx=%4d,cy=%4d";
                String msgResAndScale = String.format(fmt1, state.screenW, state.screenH, snapshot.zoomFactor);
                String msgOffset = String.format(fmt2, snapshot.xCoordinate, snapshot.yCoordinate, snapshot.xCanvasOffset, snapshot.yCanvasOffset);
                String msgVisibleIds = snapshot.visibleTileIdRange.toString();
                String msgProvider = tileProvider == null ? "" : tileProvider.getDebugSummary();
                String msgMemory = Utils.getMemStatus();
                Paint paintMem = Utils.isHeapAlmostFull() ? paint_errText : paint_debugBoxTxt;

                float boxWidth = 350, boxHeight = 110;

                float boxLeft = state.screenW - boxWidth;
                float boxTop = state.screenH - boxHeight;
                float boxMid = boxLeft + boxWidth/2;

                canvas.drawRect(boxLeft, boxTop, state.screenW, state.screenH, paint_debugBG);

                canvas.drawText(msgResAndScale, boxMid, boxTop + 20, paint_debugBoxTxt);
                canvas.drawText(msgOffset, boxMid, boxTop + 40, paint_debugBoxTxt);
                canvas.drawText(msgVisibleIds, boxMid, boxTop + 60, paint_debugBoxTxt);
                canvas.drawText(msgProvider, boxMid, boxTop + 80, paint_debugBoxTxt);
                canvas.drawText(msgMemory, boxMid, boxTop + 100, paintMem);
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



    // http://android-developers.blogspot.com/2010/06/making-sense-of-multitouch.html
    class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {

            float newZoomFactor = state.updateZoomFactor(detector.getScaleFactor());

            // let the provider know something has changed
            tileProvider.notifyZoomFactorChange(newZoomFactor);

            if(tileDrawThread != null){
                tileDrawThread.requestRerender();
            }

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


            int newOffX = - (int) distanceX;
            int newOffY = - (int) distanceY;

            TileRange updatedRange = state.goToCoordinatesOffset(newOffX, newOffY);
            if(updatedRange != null){
                tileProvider.notifyTileIDRangeChange(updatedRange);
            }
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

            return true;
        }

    }
    //</editor-fold>


}
