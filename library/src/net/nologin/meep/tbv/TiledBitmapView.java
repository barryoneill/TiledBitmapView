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
 * The TileBitmapView is an attempt to offer a relatively simple SurfaceView whose bitmap contents come tilewise
 * from a provider (similar to many mapping libraries, but this has nothing to do with mapping).  To use this
 * TileBitmapView, the user simply plugs the appropriate view XML tag into their layout.  They then get a reference
 * to that view and call {@link #registerProvider(TileProvider)} with their own implementation of a {@link TileProvider}
 * (which has extensive javadoc about its responsibilites).
 * <br/><br/>
 * <b>Tiles</b><br/>
 * The surface is rendered as a grid of tiles.  {@link Tile} objects are identified by their x and y co-ordinates
 * (not pixel co-ordinates, see {@link Tile}). As the user manipulates the surface (e.g. scroll), we calculate which
 * range of tile IDs is required, inform the {@link TileProvider} of that range, and then fetch the tiles (with contain
 * the bitmap data for rendering) from the provider.  The provider has the opportunity to provide the data immediately,
 * or as it becomes become availabe. Full javadoc of this interaction is found in {@link TileProvider}.
 *
 * @see TileProvider
 * @see Tile
 */

public class TiledBitmapView extends SurfaceView implements SurfaceHolder.Callback {

    /* Rough implementation summary:
     *
     * - On surface creation, calculate the size of the minumum grid of tiles needed to fill the surfaceview (taking
     *   into account tiles may be partially scrolled off-screen).  This number of 'visible tiles' won't change for
     *   surface's lifetime (rotation etc = new surface).  A 'ViewState' object is created with this number and other
     *   non-changing surface information stored in immutable variables.
     *
     * - Every change in the offset (eg, user scrolling) results in a recalcuation of which tile IDs (TileRange)
     *   should be used to populate the grid of visible tiles, and at what offset (mod tilewidth) that grid needs to be
     *   rendered to maintain the illusion of infinite scrolling.  These changes are recorded in mutable variables
     *   in the ViewState object.  Any change in this calculated TileRange will result in a notification to the
     *   provider, so it has an opportunity to start processing the newly required tiles asynchronously.
     *
     * - On surface creation, we create a rendering thread (similar to the Lunar Lander app) which runs for the life
     *   of the surface.  On every iteration, it takes a thread-safe snapshot of the ViewState, and polls the provider
     *   for new tile data if required/requested.  If it detects that there is a change in bitmap content for the
     *   visible grid, a new render is performed.
     *
     * - There's a debug flag, which if set will cause the render thread to draw tile borders, tile coordinate info
     *   and a box at the bottom right with useful information.  It hits performance a bit, but is a massive help
     *   when something goes wrong.
     */

    // onSaveInstanceState/onRestoreInstanceState keys
    private static final String STATEKEY_SUPERCLASS = "net.nologin.meep.tbv.super";
    private static final String STATEKEY_DEBUG_ENABLED = "net.nologin.meep.tbv.debugenabled";

    final GestureDetector gestureDetector;
    final ScaleGestureDetector scaleDetector;

    final Paint paint_bg, paint_debugTileTxt, paint_debugGridLine,
            paint_debugBoxBG, paint_debugBoxTxt, paint_debugBoxErrTxt;

    TileSurfaceDrawThread surfaceDrawThread;
    ViewState state;
    TileProvider tileProvider;
    private boolean debugEnabled;

    public TiledBitmapView(Context context, AttributeSet attrs) {

        super(context, attrs);

        getHolder().addCallback(this);

        // attach listeners for scroll and zoom
        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());

        // the user _should_ set their own, but this is more helpful when they forget/don't
        tileProvider = new GenericTileProvider(context);

        // ------- end view setup, configure paint objects --------

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

        requestSurfaceRefresh(true);
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

        requestSurfaceRefresh(false);

        return debugEnabled;
    }

    /**
     * Set whether debug information should be rendered on the view.
     *
     * @param debugEnabled true if debug should be rendered, false otherwise.
     */
    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        requestSurfaceRefresh(false);
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

        // again, apart from the superclass state, we only restore debug state
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

        // runs for the lifetime of the surface (killed in surfaceDestroyed)
        if (surfaceDrawThread == null || !surfaceDrawThread.isAlive()) {
            surfaceDrawThread = new TileSurfaceDrawThread(holder);
            surfaceDrawThread.setRunning(true);
            surfaceDrawThread.start();
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        // the provider may have it's own async stuff, give it a chance to clean up
        if (tileProvider != null) {
            tileProvider.onSurfaceDestroyed();
        }

        // stop rendering thread (started in surfaceCreated), retry stuff based on LunarLander source
        boolean retry = true;
        surfaceDrawThread.setRunning(false);
        while (retry) {
            try {
                surfaceDrawThread.join();
                retry = false;
            } catch (InterruptedException ignored) {
            }
        }

    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        // init view state with surface config info
        state = new ViewState(width, height,
                tileProvider.getConfigTileSize(),
                tileProvider.getConfigTileIDLimits());

        // start off at the origin tile.
        moveToTile(0, 0, true);
    }

    /**
     * Request that the rendering thread redraw the surface
     */
    public void requestSurfaceRefresh(boolean notifyProvider) {

        if (tileProvider == null) {
            Log.w(Utils.LOG_TAG, "Cannot request surface refresh, provider is null");
            return;
        }
        if (state == null) {
            Log.w(Utils.LOG_TAG, "Cannot request surface refresh, screen state not ready");
            return;
        }

        if (notifyProvider) {
            tileProvider.onTileIDRangeChange(state.getVisibleTileRange());
        }

        if (surfaceDrawThread != null) {
            surfaceDrawThread.requestRerender();
        }

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
     * whatever {@link GridAnchor} has been specified by the tile provider.
     * {@link net.nologin.meep.tbv.TileProvider#getConfigGridAnchor()} implementation.
     *
     * @param tileX                The x tile coordinate
     * @param tileY                The y tile coordinate
     * @param alwaysNotifyProvider If <code>false</code>, and the move does not result in a change in the tile IDs
     *                             required to render the view, then the provider won't get an update via
     *                             {@link TileProvider#onTileIDRangeChange(TileRange)}.  If <code>true</code>,
     *                             the provider will always get notified, even if the range is unchanged.
     */
    public void moveToTile(int tileX, int tileY, boolean alwaysNotifyProvider) {

        if (tileProvider == null) {
            Log.d(Utils.LOG_TAG, "Provider not ready yet, cannot go to tile (" + tileX + "," + tileY + ")");
            return;
        }

        if (state == null || state.surfaceW == 0) {
            Log.d(Utils.LOG_TAG, "Surface not ready yet, cannot go to tile (" + tileX + "," + tileY + ")");
            return;
        }

        // get left/top canvas position (px) where the anchor tile would be rendered
        GridAnchor anchor = tileProvider.getConfigGridAnchor();
        Pair<Integer, Integer> anchorCoords = anchor.getPosition(state.surfaceW, state.surfaceH, state.tileWidth);

        // now calculate how many pixels we'd need to 'scroll' from there to get to the desired tile
        int newX = anchorCoords.first - (state.tileWidth * tileX);
        int newY = anchorCoords.second - (state.tileWidth * tileY);

        // update state for these values
        boolean rangeChange = state.applySurfaceOffset(newX, newY);

        // refresh, notifying the provider should the tile IDs have changed as a result
        requestSurfaceRefresh(rangeChange || alwaysNotifyProvider);

    }

    /**
     * This thread runs for the lifecycle of the surface, and is responsible for redrawing the surface's contents.
     * It goes to some effort only to redraw the surface when absolutely necessary (it improves responsiveness, but
     * is also important because on older devices, the provider will be sharing CPU time
     */
    class TileSurfaceDrawThread extends Thread {

        private final SurfaceHolder holder;
        boolean running = false;

        // used to allow the view to manually request a re-render
        private AtomicBoolean rerenderRequested = new AtomicBoolean(false);

        // a snapshot of the state to avoid concurrency issues (users scrolls mid-render, etc)
        private ViewState.Snapshot snapshot;

        // used to detect changes since the last render
        private boolean hasOffsetChanged, wasRenderRequested;

        // the actual tile references that will get drawn to the surface
        private Tile[][] visibleTiles;

        // the hashcodes of those tiles will be recorded and compared the next time round
        private int[][] oldTileHashcodes;

        public TileSurfaceDrawThread(SurfaceHolder holder) {
            this.holder = holder;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        /**
         * Request that the thread performs a render next time round, regardless of whether the bitmap
         * content has changed or not.
         */
        public void requestRerender() {
            rerenderRequested.set(true);
        }

        @Override
        public void run() {

            /* Responsible for rendering surface content, we want to make sure that this thread doesn't end up fighting
             * with provider-generated threads for CPU time on older devices  (Will result in a very laggy scroll) */
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);

            setName(this.getClass().getSimpleName() + "(" + getName() + ")");

            Canvas c;

            int xCanvasOffsetOld = 0, yCanvasOffsetOld = 0; // detect offset changes

            while (running) {

                // sanity checks - surfaceChanged() setup not called/finished yet
                if (state == null || state.tileWidth <= 0) {
                    continue;
                }

                // grab a snapshot of all UI-managed state data we need in order to render (synchronized)
                snapshot = state.getUpdatedSnapshot();

                // another sanity check
                if (snapshot.visibleTileIdRange == null) {
                    continue;
                }

                // did the UI thread request that we do a render?
                wasRenderRequested = rerenderRequested.getAndSet(false);

                c = null;

                hasOffsetChanged = snapshot.canvasOffsetX != xCanvasOffsetOld || snapshot.canvasOffsetY != yCanvasOffsetOld;
                xCanvasOffsetOld = snapshot.canvasOffsetX;
                yCanvasOffsetOld = snapshot.canvasOffsetY;


                // only compare the tiles' bitmap data when we know it may have changed (as this can be relatively slow)
                boolean haveTileBmpsChanged = false;
                if (tileProvider.hasFreshData() || wasRenderRequested || hasOffsetChanged) {
                    haveTileBmpsChanged = refreshTileBitmapsAndCompare(snapshot.visibleTileIdRange);
                }

                // content change, offset change or request for refresh results in a draw
                if (haveTileBmpsChanged || wasRenderRequested || hasOffsetChanged) {

                    try {

                        c = holder.lockCanvas(null);
                        if (c == null) {
                            continue;
                        }

                        synchronized (holder) {
                            doDrawVisibleTiles(c, snapshot);
                        }

                    } finally {

                        if (c != null) {
                            holder.unlockCanvasAndPost(c);
                        }
                    }


                }


            }

        }


        /* Updates the 'visibleTiles' references that we're going to render, returns true if some bmpdata has changed */
        private boolean refreshTileBitmapsAndCompare(TileRange visibleRange) {

            // reuse the existing arrays as long as possible (otherwise we'll allocate objects like crazy)
            if (visibleTiles == null ||
                    visibleTiles.length != state.tileWidth || visibleTiles[0].length != state.tileWidth) {
                visibleTiles = new Tile[state.tilesVert][state.tilesHoriz];
                oldTileHashcodes = new int[state.tilesVert][state.tilesHoriz];
            }

            boolean bmpChangeDetected = false;
            int newTileHash;

            int xId, yId;

            for (int y = 0; y < state.tilesVert; y++) {

                for (int x = 0; x < state.tilesHoriz; x++) {

                    yId = y + visibleRange.top;
                    xId = x + visibleRange.left;

                    // refresh the tile from the provider
                    visibleTiles[y][x] = tileProvider.getTile(xId, yId);
                    if (visibleTiles[y][x] == null) {
                        visibleTiles[y][x] = new EmptyTile(xId, yId);
                    }

                    // generate hashcode, compare to that from last time around
                    newTileHash = visibleTiles[y][x].getBitmapContentHash();
                    if (newTileHash != oldTileHashcodes[y][x]) {
                        bmpChangeDetected = true; // don't break, all tiles need refreshing
                    }
                    oldTileHashcodes[y][x] = newTileHash;

                }
            }

            return bmpChangeDetected;

        }

        /* actually renders the surface */
        public void doDrawVisibleTiles(Canvas canvas, ViewState.Snapshot snapshot) {

            canvas.save();

            // blank out entire surface so empty tiles show up blank
            canvas.drawRect(0, 0, state.surfaceW, state.surfaceH, paint_bg);

            // offset our canvas, so we can draw our whole tiles on with simple 0,0 origin co-ordinates
            canvas.translate(snapshot.canvasOffsetX, snapshot.canvasOffsetY);

            int curTileTop = 0;

            for (Tile[] tileRow : visibleTiles) {

                int curTileLeft = 0;

                for (Tile t : tileRow) {

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


                    // if debug, draw a border round each tile (results in a 'grid'), and write tile IDs
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


            // we don't want the debug box offset with the tiles, undo the translate
            canvas.translate(-snapshot.canvasOffsetX, -snapshot.canvasOffsetY);

            // -------------------  debug box at bottom right ------------------------
            if (debugEnabled) {

                String fmt1 = "%dx%d, zf=%1.3f";
                String fmt2 = "x=%5d,y=%5d, cx=%4d,cy=%4d";
                String msgResAndScale = String.format(fmt1, state.surfaceW, state.surfaceH, snapshot.zoomFactor);
                String msgOffset = String.format(fmt2, snapshot.surfaceOffsetX, snapshot.surfaceOffsetY, snapshot.canvasOffsetX, snapshot.canvasOffsetY);
                String msgVisibleIds = snapshot.visibleTileIdRange.toString();
                String msgProvider = tileProvider == null ? "" : tileProvider.getDebugSummary();
                String msgMemory = Utils.getMemDebugString();
                Paint paintMem = Utils.isHeapAlmostFull() ? paint_debugBoxErrTxt : paint_debugBoxTxt;

                float boxWidth = 350, boxHeight = 110;

                float boxLeft = state.surfaceW - boxWidth;
                float boxTop = state.surfaceH - boxHeight;
                float boxMid = boxLeft + boxWidth / 2;

                canvas.drawRect(boxLeft, boxTop, state.surfaceW, state.surfaceH, paint_debugBoxBG);

                canvas.drawText(msgResAndScale, boxMid, boxTop + 20, paint_debugBoxTxt);
                canvas.drawText(msgOffset, boxMid, boxTop + 40, paint_debugBoxTxt);
                canvas.drawText(msgVisibleIds, boxMid, boxTop + 60, paint_debugBoxTxt);
                canvas.drawText(msgProvider, boxMid, boxTop + 80, paint_debugBoxTxt);
                canvas.drawText(msgMemory, boxMid, boxTop + 100, paintMem);
            }
            // -----------------  end debug box ------------------------------------------

            canvas.restore();

        }

        // Sometimes the provider screws up and sends back null instead of a tile with null content.  Can't
        // work with null, have an tile ID to work with - this'll do to cover those blips.
        private class EmptyTile extends Tile {
            public EmptyTile(int x, int y) {
                super(x, y, state.tileWidth);
            }
        }


    }


    @Override
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

            // notify provider of zoom change
            tileProvider.onZoomFactorChange(newZoomFactor);

            requestSurfaceRefresh(false); // or true? Will result in second call to the provider

            return true;
        }
    }


    class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public void onShowPress(MotionEvent motionEvent) {

            Log.d(Utils.LOG_TAG, "show press");
            // nop
        }

        @Override
        public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float distanceX, float distanceY) {

            int newOffX = -(int) distanceX;
            int newOffY = -(int) distanceY;

            boolean rangeChange = state.applySurfaceOffsetRelative(newOffX, newOffY);
            requestSurfaceRefresh(rangeChange);

            return true;
        }


        @Override
        public void onLongPress(MotionEvent motionEvent) {

            Log.d(Utils.LOG_TAG, "long press");
            // nop

        }

        @Override
        public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {

            Log.d(Utils.LOG_TAG, "fling");
            // nop

            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {

            Log.d(Utils.LOG_TAG, "double tap");
            // nop

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {

            Log.d(Utils.LOG_TAG, "single tap");
            // nop

            return true;
        }

    }


}
