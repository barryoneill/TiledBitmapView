package net.nologin.meep.tbv;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.*;

import java.util.ArrayList;
import java.util.List;


public abstract class TiledBitmapView extends SurfaceView implements SurfaceHolder.Callback {

    GestureDetector gestureDetector;
    ScaleGestureDetector scaleDetector;

    Paint paint_bg;
    Paint paint_debugTileTxt;
    Paint paint_debugBoxTxt;
    Paint paint_errText;
    Paint paint_gridLine;
    Paint paint_debugBG;

    TileGenerationThread tgThread;

    ViewState state;

    TileProvider tileProvider;

    protected boolean debugEnabled;

    public TiledBitmapView(Context context, AttributeSet attrs) {

        super(context, attrs);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // TODO: persist/restore during suitable lifecycle events
        state = new ViewState();
        tgThread = new TileGenerationThread(holder, this);

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
        paint_debugTileTxt.setShadowLayer(5,2,2,Color.BLACK);

        // TODO:

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
        if (!tgThread.isAlive()) {
            tgThread = new TileGenerationThread(holder, this);
            tgThread.setRunning(true);
            tgThread.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        // Based on android example 'LunarLander' app
        boolean retry = true;
        tgThread.setRunning(false);
        while (retry) {
            try {
                tgThread.join();
                retry = false;
            } catch (InterruptedException ignored) {
            }
        }
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        state.screenWidth = width;
        state.screenHeight = height;

        state.tileWidth = tileProvider.getTileWidthPixels();

        state.tilesW = maxTilesNeeded(width, state.tileWidth);
        state.tilesH = maxTilesNeeded(height, state.tileWidth);

        jumpToOriginTile();
    }

    /**
     * @return The largest possible number of tiles needed to render a row/column
     * for the given tile size.  (eg, if two tiles fit perfectly, we'll still need
     * 3 for then the user scrolls slightly off to one side).
     */
    private int maxTilesNeeded(int availableSize, int tileWidth) {

        /* The + 1 ist to cover scrolling (eg, scroll left a bit, and part of a
         * new tile will appear on the right, but we still need the left tile */
        int num = (availableSize / tileWidth) + 1;

        /* An additional tile if the int division above floored */
        return num + (availableSize % tileWidth == 0 ? 0 : 1);
    }

    public void jumpToOriginTile() {

        jumpToTileId(0,0);

    }

    public void jumpToTileId(int x, int y) {

        if (tileProvider == null) {
            return;
        }

        /* Imagine the (0,0) tile placed on the y axis, half on each side.  Our x-scroll offset is what's left over
         * when we fill up the remainder of the left area, until there's no room left in the view for more full tiles.
         */

        state.scrollOffsetX = (state.screenWidth - state.tileWidth)/2;

        state.scrollOffsetY = (state.screenHeight - state.tileWidth) /2;


        // values are valid for (0,0) -> now offset for the desired tile ID
        state.scrollOffsetX -= (state.tileWidth * x);
        state.scrollOffsetY -= (state.tileWidth * y);

        handleOffsetChange();


    }

    private void handleOffsetChange() {

        Pair<Integer, Integer> hInfo = getTileRangeForOffset(state.scrollOffsetX, state.tilesW, state.tileWidth);
        Pair<Integer, Integer> vInfo = getTileRangeForOffset(state.scrollOffsetY, state.tilesH, state.tileWidth);

        TileRange newRange = new TileRange(hInfo.first, vInfo.first, hInfo.second, vInfo.second);

        // avoid unnecessary provider notifications
        if (state.visibleTileIdRange == null || !newRange.equals(state.visibleTileIdRange)) {
            state.visibleTileIdRange = newRange;
            tileProvider.notifyTileIDRangeChange(state.visibleTileIdRange);
        }

    }

    private Pair<Integer, Integer> getTileRangeForOffset(int offset, int numTiles, int tileWidth) {

        int startTileId = - (offset / tileWidth);
        //int startTileId = (-numTiles / 2) - (offset / tileWidth);

        // positive offset means one tile before (negative handled by numTiles)
        if (offset % tileWidth > 0) {
                startTileId--;
        }

        return new Pair<Integer, Integer>(startTileId, startTileId + numTiles - 1);
    }


    private class MissingTile extends Tile {
        public MissingTile(int x, int y) {
            super(x, y, state.tileWidth);
        }

        public Bitmap getBmpData() {
            return null;
        }
    }

    private List<List<Tile>> getVisibleTileGrid(TileRange tileIdRange) {

        List<List<Tile>> result = new ArrayList<List<Tile>>();

        if (tileIdRange == null) {
            return result;
        }

        Tile t;

        for (int y = tileIdRange.top; y <= tileIdRange.bottom; y++) {

            List<Tile> curRow = new ArrayList<Tile>();
            for (int x = tileIdRange.left; x <= tileIdRange.right; x++) {
                t = tileProvider.getTile(x, y);
                if (t == null) {
                    t = new MissingTile(x, y); // always return a grid of non-null tiles
                }
                curRow.add(t);
            }
            result.add(curRow);
        }

        return result;
    }


    class TileGenerationThread extends Thread {

        private final SurfaceHolder holder;
        private TiledBitmapView view;
        private boolean running = false;


        public TileGenerationThread(SurfaceHolder holder, TiledBitmapView view) {
            this.holder = holder;
            this.view = view;
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

                        view.doDraw(c);

                        /* TODO: in the time it takes doDraw to run, the provider
                         * might have been able to generate several tiles. Perhaps
                         * use another thread to better timeslice the CPU between
                         * these two tasks, rather than alternate them */

                        tileProvider.generateNextTile(state.visibleTileIdRange);

                    }
                    Thread.sleep(5); // so we can interact in a reasonable time

                } catch (InterruptedException e) {
                    // nop
                } finally {
                    // do this in a finally so that if an exception is thrown
                    // during the above, we don't leave the Surface in an
                    // inconsistent state
                    if (c != null) {
                        holder.unlockCanvasAndPost(c);
                    }
                }
            }

        }

    }




    public void doDraw(Canvas canvas) {

        super.onDraw(canvas);

        canvas.save();

        // the offsets may change _during this doDraw_ call; Make a copy before
        // rendering, otherwise there'll be flickering/tearing of tiles!
        int xMargin = state.scrollOffsetX % state.tileWidth;
        int yMargin = state.scrollOffsetY % state.tileWidth;

        // in the case we're offset to the right, we need to start rendering 'back' a tile (the longer tile range
        // handles the case of left offset)
        if (xMargin > 0) {
            xMargin -= state.tileWidth;
        }
        if (yMargin > 0) {
            yMargin -= state.tileWidth;
        }
//
        // todo: remove (won't be necessary when tile gen is correct)
        canvas.drawRect(new Rect(0, 0, state.screenWidth, state.screenHeight), paint_bg);

        // offset our canvas, so we can draw our whole tiles on with simple 0,0 origin co-ordinates
        canvas.translate(xMargin, yMargin);

        // draw BG
        canvas.drawRect(new Rect(0, 0, state.screenWidth, state.screenHeight), paint_bg);

        if (tileProvider != null) {

            List<List<Tile>> visibleGrid = getVisibleTileGrid(state.visibleTileIdRange);

            int y = 0;

            for (List<Tile> tileRow : visibleGrid) {

                int x = 0;

                for (Tile t : tileRow) {

                    Bitmap bmp = t.getBmpData();
                    if (bmp != null) {

                        //bmpData.setPixels(t.bmpData, 0, tileSize, xOff, yOff, tileSize, tileSize);
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
                        canvas.drawText(msg1, x + (state.tileWidth / 2), y + (state.tileWidth / 2), paint_debugTileTxt);
                    }

                    x += state.tileWidth; // move right one tile screenWidth
                }

                y += state.tileWidth; // move down one tile screenWidth
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
        String fmt1 = "%dx%d, s=%1.3f";
        String fmt2 = "offx=%d,y=%d, xm=%d, ym=%d";
        String fmt3 = "tiles %s";
        String msgResAndScale = String.format(fmt1, state.screenWidth, state.screenHeight, state.scaleFactor);
        String msgOffset = String.format(fmt2, state.scrollOffsetX, state.scrollOffsetY, xm, ym);
        String msgVisibleIds = String.format(fmt3, state.visibleTileIdRange);
        String msgProvider = tileProvider == null ? "" : tileProvider.getDebugSummary();
        String msgMemory = Utils.getMemStatus();
        Paint paintMem = Utils.isHeapAlmostFull() ? paint_errText : paint_debugBoxTxt;

        float boxWidth = 330, boxHeight = 145;

        float debug_x = state.screenWidth - boxWidth;
        float debug_y = state.screenHeight - boxHeight;

        canvas.drawRect(debug_x, debug_y, state.screenWidth, state.screenHeight, paint_debugBG);

        canvas.drawText(msgResAndScale, debug_x + boxWidth / 2, debug_y + 30, paint_debugBoxTxt);
        canvas.drawText(msgOffset, debug_x + boxWidth / 2, debug_y + 55, paint_debugBoxTxt);
        canvas.drawText(msgVisibleIds, debug_x + boxWidth / 2, debug_y + 80, paint_debugBoxTxt);
        canvas.drawText(msgProvider, debug_x + boxWidth / 2, debug_y + 105, paint_debugBoxTxt);
        canvas.drawText(msgMemory, debug_x + boxWidth / 2, debug_y + 130, paintMem);


    }


    @Override // register GD
    public boolean onTouchEvent(MotionEvent me) {

        invalidate();

        gestureDetector.onTouchEvent(me);
        scaleDetector.onTouchEvent(me);

        return true;
    }


    static class ViewState {

        int screenWidth, screenHeight;

        int tileWidth;

        int tilesW, tilesH;

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
            state.scaleFactor = Math.max(0.1f, Math.min(state.scaleFactor, 5.0f));

            tileProvider.notifyZoomFactorChangeTEMP(state.scaleFactor);

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

            //log("scroll x=" + distanceX + ", y=" + distanceY);
            state.scrollOffsetX -= (int) distanceX;
            state.scrollOffsetY -= (int) distanceY;

            // TODO: implement scroll boundaries if specified

            handleOffsetChange();

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
