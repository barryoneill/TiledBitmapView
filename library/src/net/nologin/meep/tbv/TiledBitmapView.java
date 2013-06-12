package net.nologin.meep.tbv;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.os.Process;
import android.view.*;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TiledBitmapView is an SurfaceView implementation, in which the screen is rendered as a grid of tiles.
 * The content of those tiles is fetched from a 'TileProvider' implementation, specified by the user of
 * the view.  Each tile has an ID, made up of its x and y coordinates (similar to android canvas
 * co-ordinates, the x and y values are increasing rightwards and downwards respectively).  Only the
 * required number of tiles to fill the screen are accessed at any given time, and there are provider
 * lifecycle methods to allow efficient release of resources as tiles are scolled out of view.
 */

public class TiledBitmapView extends SurfaceView implements SurfaceHolder.Callback {

    // onSaveInstanceState/onRestoreInstanceState keys
    private static final String STATEKEY_SUPERCLASS = "net.nologin.meep.tbv.super";
    private static final String STATEKEY_DEBUG_ENABLED = "net.nologin.meep.tbv.debugenabled";

    final GestureDetector gestureDetector;
    final ScaleGestureDetector scaleDetector;

    final Paint paint_bg, paint_debugTileTxt, paint_debugGridLine,
            paint_debugBoxBG, paint_debugBoxTxt, paint_debugBoxErrTxt;

    TileGridDrawThread tileDrawThread;
    TileGenThread tileGenThread;

    ViewState state;

    TileProvider tileProvider;

    private boolean debugEnabled;

    public TiledBitmapView(Context context, AttributeSet attrs) {

        super(context, attrs);

        getHolder().addCallback(this);

        // attach listeners for scroll and zoom
        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());

        /* Just in case the user doesn't register their own provider, let's have a
         * dummy one that generates something a bit more instructive than a blank screen */
        tileProvider = new GenericTileProvider(context);

        // ------- cosmetic init --------

        Resources res = getResources();

        // background behind tiles (visible if tile content unavailable)
        paint_bg = new Paint();
        paint_bg.setColor(res.getColor(R.color.main_background_color));
        paint_bg.setStyle(Paint.Style.FILL);

        // common text
        Paint centerAlignedTxt = new Paint();
        centerAlignedTxt.setAntiAlias(true);
        centerAlignedTxt.setTypeface(Typeface.MONOSPACE);
        centerAlignedTxt.setTextAlign(Paint.Align.CENTER);

        // per-tile debug text
        paint_debugTileTxt = new Paint(centerAlignedTxt);
        paint_debugTileTxt.setColor(res.getColor(R.color.debug_tile_text));
        paint_debugTileTxt.setTextSize(32);
        paint_debugTileTxt.setShadowLayer(5, 2, 2, Color.BLACK);

        // border lines around tiles
        paint_debugGridLine = new Paint();
        paint_debugGridLine.setColor(res.getColor(R.color.debug_tile_border));
        paint_debugGridLine.setStyle(Paint.Style.STROKE);
        paint_debugGridLine.setStrokeWidth(1);

        // background of bottom right debug info box
        paint_debugBoxBG = new Paint();
        paint_debugBoxBG.setColor(res.getColor(R.color.debug_box_bg));
        paint_debugBoxBG.setStyle(Paint.Style.FILL);
        paint_debugBoxBG.setAlpha(140);

        // text for debug info box
        paint_debugBoxTxt = new Paint(centerAlignedTxt);
        paint_debugBoxTxt.setColor(res.getColor(R.color.debug_box_text));
        paint_debugBoxTxt.setTextSize(14);

        // same as paint_debugBoxTxt, except for errors
        paint_debugBoxErrTxt = new Paint(paint_debugBoxTxt);
        paint_debugBoxErrTxt.setColor(res.getColor(R.color.debug_box_text_error));

    }

    /**
     * Register your tile provider implementation.  The view will reset to the origin, and start using
     * this provider to render the tile grid's contents.
     *
     * @param tileProvider The {@link TileProvider implementation}.  If <code>null</code>, this
     *                     will default to an instance of {@link GenericTileProvider}.
     */
    public void registerProvider(TileProvider tileProvider) {
        if (tileProvider == null) {
            Log.e(Utils.LOG_TAG, "Null supplied to registerProvider, defaulting to generic provider.");
            tileProvider = new GenericTileProvider(getContext());
        }
        this.tileProvider = tileProvider;

        notifyThreads();
    }

    /**
     * Registers the default tile provider. The view will reset to the origin.
     */
    public void registerDefaultProvider() {
        registerProvider(new GenericTileProvider(getContext()));
    }

    /**
     * @return The currently registered tile provider
     */
    public TileProvider getProvider() {
        return tileProvider;
    }

    /**
     * @return True if this view is rendering debug information
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Toggle whether this view should render debug information
     *
     * @return if debug information is being rendered after toggling
     */
    public boolean toggleDebugEnabled() {
        debugEnabled = !debugEnabled;

        notifyThreads();

        return debugEnabled;
    }

    /**
     * Set whether debug information should be rendered on the view.
     *
     * @param debugEnabled true if debug should be rendered, false otherwise.
     */
    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        notifyThreads();
    }

    @Override
    public Parcelable onSaveInstanceState() {

        // the only value (apart from superclass state) we care about is whether debug is on
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

        // start both the tile generation and rendering threads if not running

        if (tileGenThread == null || !tileGenThread.isAlive()) {
            tileGenThread = new TileGenThread();
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

        // Stop both background threads (Based on surfaceDestroyed() in the 'LunarLander' sample app)

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

        // create a state object that will maintain changes within this surface configuration
        state = new ViewState(width, height, tileProvider.getTileWidthPixels(),
                tileProvider.getTileIndexBounds(), tileProvider.getGridBufferSize());

        // start off at the origin tile.
        moveToTile(0, 0, true);
    }

    /**
     * Convenience method for moveToTile(0,0,false).  See {@link #moveToTile(int, int, boolean)}
     */
    public void moveToOriginTile() {
        moveToTile(0, 0, false);
    }

    /**
     * Convenience method for moveToTile(0,0,boolean).  See {@link #moveToTile(int, int, boolean)}
     */
    public void moveToOriginTile(boolean alwaysNotifyProvider) {
        moveToTile(0, 0, alwaysNotifyProvider);
    }


    /**
     * Adjust the scroll offset so that the view is 'focused' at the specified tile ID.  'Focused' means
     * whatever {@link GridAnchor} has been specified by your TileProvider's
     * {@link net.nologin.meep.tbv.TileProvider#getGridAnchor()} implementation.
     *
     * @param tileX The x tile coordinate
     * @param tileY The y tile coordinate
     * @param alwaysNotifyProvider If <code>false</code>, and the move does not result in a change in the tile IDs
     *                             required to render the view, then the provider won't get an update via
     *                             {@link TileProvider#notifyTileIDRangeChange(TileRange)}.  If <code>true</code>,
     *                             the provider will always get notified, even if the range is unchanged.
     */
    public void moveToTile(int tileX, int tileY, boolean alwaysNotifyProvider) {

        if (tileProvider == null) {
            Log.d(Utils.LOG_TAG, "Provider not ready yet, cannot go to tile (" + tileX + "," + tileY + ")");
            return;
        }

        if (state == null || state.screenW == 0) {
            Log.d(Utils.LOG_TAG, "Surface not ready yet, cannot go to tile (" + tileX + "," + tileY + ")");
            return;
        }

        // get left/top position (relative to the visible surface) where the anchor tile would be rendered
        GridAnchor anchor = tileProvider.getGridAnchor();
        Pair<Integer, Integer> anchorCoords = anchor.getPosition(state.screenW, state.screenH, state.tileWidth);

        // now calculate how many pixels we'd need to 'scroll' from there to get to the desired tile
        int newX = anchorCoords.first - (state.tileWidth * tileX);
        int newY = anchorCoords.second - (state.tileWidth * tileY);

        // update the state for those coordinates,
        TileRange updatedRange = state.setSurfaceOffset(newX, newY, alwaysNotifyProvider);
        if (updatedRange != null) {
            tileProvider.notifyTileIDRangeChange(updatedRange);
        }

        notifyThreads();

    }

    // TODO: review this method's role.
    private void notifyThreads() {

        if (tileDrawThread != null) {
            tileDrawThread.requestRerender();
        }

    }


    /**
     * The TileGenThread simply loops in the background, and repeatedly gives the provider a chance to
     * render any pending tiles it may have determined is necessary.
     */
    class TileGenThread extends Thread {

        private boolean running = false;

        // atomic as it'll be monitored and unset by the rendering thread
        private AtomicBoolean dataChanged = new AtomicBoolean(true);

        TileRange range = null;

        public TileGenThread() {


        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public void run() {


            while (running) {

                if (tileProvider == null || state == null) {
                    continue;
                }

                range = state.getVisibleTileIdRange();
                if (range == null) {
                    continue;
                }


                boolean somethingGenerated = tileProvider.processQueue(range);
                if (somethingGenerated) {

                    // only set the flag to true, let the rendering thread set it to false
                    dataChanged.set(true);

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
        private AtomicBoolean rerenderRequested = new AtomicBoolean(false);

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
            rerenderRequested.set(true);
            Log.d(Utils.LOG_TAG, "setting rerender to true");
        }

        @Override
        public void run() {

            /* Make sure this thread has a slightly higher prio than the tile generation thread, so that
               even if tiles queue up, the grid rendering is smooth. */
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);

            Canvas c;

            int xCanvasOffsetOld = 0, yCanvasOffsetOld = 0;

            boolean providerDataChangeCpy, rerenderRequestedCpy;

            while (running) {

                if (state == null) {
                    continue;
                }

                snapshot = state.createSnapshot();

                if (state.tileWidth <= 0 || snapshot.visibleTileIdRange == null) {
                    continue; // sanity check - surfaceChanged() work not called/finished yet
                }

                c = null;

                // and detect any change in offset
                offsetChanged = snapshot.canvasOffsetX != xCanvasOffsetOld || snapshot.canvasOffsetY != yCanvasOffsetOld;

                // update old values
                xCanvasOffsetOld = snapshot.canvasOffsetX;
                yCanvasOffsetOld = snapshot.canvasOffsetY;

                // get and set both to false
                providerDataChangeCpy = tileGenThread.dataChanged.getAndSet(false);
                rerenderRequestedCpy = rerenderRequested.getAndSet(false);

                // get changes in tile contents (if any) from the provider
                if ((tileGenThread != null && providerDataChangeCpy) || rerenderRequestedCpy || offsetChanged) {
                    visibleTileChange = getVisibleTileChanges(snapshot.visibleTileIdRange);
                }

                if (visibleTileChange || offsetChanged || rerenderRequestedCpy) {

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

                        tile_new = new EmptyTile(xId, yId); // always return a grid of non-null tiles
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

                // offset our canvas, so we can draw our whole tiles on with simple 0,0 origin co-ordinates
                canvas.translate(snapshot.canvasOffsetX, snapshot.canvasOffsetY);

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
                                canvas.drawRect(t.getRect(curTileLeft, curTileTop), paint_debugGridLine);
                            }

                        }
                        // else {
                        //     could possibly let providers give us a 'no data' tile in the future.  For now, ignore.
                        // }

                        // remember what we drew in this cell
                        visibleTilesHistory[y][x] = t.getBmpHashcode();

                        if (debugEnabled) {

                            canvas.drawRect(t.getRect(curTileLeft, curTileTop), paint_debugGridLine);

                            String msg1 = String.format("[%d,%d]", t.xId, t.yId);
                            canvas.drawText(msg1, curTileLeft + (state.tileWidth / 2),
                                    curTileTop + (state.tileWidth / 2), paint_debugTileTxt);
                        }

                        curTileLeft += state.tileWidth; // move right one tile screenWidth
                    }

                    curTileTop += state.tileWidth; // move down one tile screenWidth
                }


                // we don't want the debug box scrolling with the tiles, undo the offset
                canvas.translate(-snapshot.canvasOffsetX, -snapshot.canvasOffsetY);

            }

            if (debugEnabled) {
                // -------------------  debug box at bottom right ------------------------

                String fmt1 = "%dx%d, zf=%1.3f";
                String fmt2 = "x=%5d,y=%5d, cx=%4d,cy=%4d";
                String msgResAndScale = String.format(fmt1, state.screenW, state.screenH, snapshot.zoomFactor);
                String msgOffset = String.format(fmt2, snapshot.surfaceOffsetX, snapshot.surfaceOffsetY, snapshot.canvasOffsetX, snapshot.canvasOffsetY);
                String msgVisibleIds = snapshot.visibleTileIdRange.toString();
                String msgProvider = tileProvider == null ? "" : tileProvider.getDebugSummary();
                String msgMemory = Utils.getMemStatus();
                Paint paintMem = Utils.isHeapAlmostFull() ? paint_debugBoxErrTxt : paint_debugBoxTxt;

                float boxWidth = 350, boxHeight = 110;

                float boxLeft = state.screenW - boxWidth;
                float boxTop = state.screenH - boxHeight;
                float boxMid = boxLeft + boxWidth / 2;

                canvas.drawRect(boxLeft, boxTop, state.screenW, state.screenH, paint_debugBoxBG);

                canvas.drawText(msgResAndScale, boxMid, boxTop + 20, paint_debugBoxTxt);
                canvas.drawText(msgOffset, boxMid, boxTop + 40, paint_debugBoxTxt);
                canvas.drawText(msgVisibleIds, boxMid, boxTop + 60, paint_debugBoxTxt);
                canvas.drawText(msgProvider, boxMid, boxTop + 80, paint_debugBoxTxt);
                canvas.drawText(msgMemory, boxMid, boxTop + 100, paintMem);
            }

            canvas.restore();

        }

        // used by the thread as a placeholder object when the provider isn't ready to give us a desired tile.
        private class EmptyTile extends Tile {
            public EmptyTile(int x, int y) {
                super(x, y, state.tileWidth);
            }
        }


    }


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

            notifyThreads();

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


            int newOffX = -(int) distanceX;
            int newOffY = -(int) distanceY;

            TileRange updatedRange = state.setSurfaceOffsetRelative(newOffX, newOffY, false);
            if (updatedRange != null) {
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


}
