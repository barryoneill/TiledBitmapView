package net.nologin.meep.tbv;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;

import java.util.ArrayList;
import java.util.List;

public abstract class TiledBitmapView extends SurfaceView implements SurfaceHolder.Callback {

    GestureDetector gestureDetector;
    ScaleGestureDetector scaleDetector;

    Paint paint_bg;
    Paint paint_msgText;
    Paint paint_errText;
    Paint paint_gridLine;
    Paint paint_debugBG;

    TileGenerationThread tgThread;

    ScreenState state;

    TileProvider tileProvider;

    protected boolean debugEnabled;

    public TiledBitmapView(Context context, AttributeSet attrs) {

        super(context, attrs);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        state = new ScreenState();
        tgThread = new TileGenerationThread(holder, this);

        // background paint
        paint_bg = new Paint();
        paint_bg.setColor(Color.DKGRAY); // LTGRAY
        paint_bg.setStyle(Paint.Style.FILL);

        // background status text paint (needed?)
        paint_msgText = new Paint();
        paint_msgText.setColor(Color.WHITE);
        paint_msgText.setTextSize(20);
        paint_msgText.setAntiAlias(true);
        paint_msgText.setTextAlign(Paint.Align.CENTER);

        paint_errText = new Paint(paint_msgText);
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

    public boolean isDebugEnabled(){
        return debugEnabled;
    }

    public boolean toggleDebugEnabled(){
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
        // we have to tell tgThread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        boolean retry = true;
        tgThread.setRunning(false);
        while (retry) {
            try {
                tgThread.join();
                retry = false;
            } catch (InterruptedException e) {
                // loop until we've
            }
        }
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        state.width = width;
        state.height = height;

        int tileSize = tileProvider.getTileWidthPixels();

        // use int division to floor, then add 1 to cover both any deficit from rounding, + scrolling
        state.numVisibleTiles_w = (state.width / tileSize)+1;
        state.numVisibleTiles_h = (state.height / tileSize)+1;

        jumpToOriginTile();
    }

    public void jumpToOriginTile() {

        if (tileProvider == null) {
            return;
        }

        // todo: copy this into state?
        int tileSize = tileProvider.getTileWidthPixels();

        // in the case of an odd number of horizontal tiles, we need an offset to move the origin to the middle
        state.canvasOffsetX = state.numVisibleTiles_w % 2 != 0 ? -tileSize / 2 : 0;
        state.canvasOffsetY = 0;

        handleOffsetChange();


    }

    private void handleOffsetChange() {

        //Log.w(Utils.LOG_TAG, "--------------------- offset change -----------------------------");

        /* get the id of the leftmost tile which puts half the tiles on the left of the y-axis
         * Integer division rounding will put the larger half (in the case of odd) on the right */
        int leftTileID = -(state.numVisibleTiles_w - (state.numVisibleTiles_w / 2));

        // decrease the left tile id for the number of tiles that fit in the x-offset (int division)
        leftTileID -= state.canvasOffsetX / tileProvider.getTileWidthPixels();


        int topTileId = -state.canvasOffsetY / tileProvider.getTileWidthPixels();


        state.visibleTileIdRange = new TileRange(leftTileID, topTileId, leftTileID + state.numVisibleTiles_w, topTileId + state.numVisibleTiles_h);

        tileProvider.notifyTileIDRangeChange(state.visibleTileIdRange);

    }


    private class MissingTile extends Tile {
        public MissingTile(int x, int y) {
            super(x,y,tileProvider.getTileWidthPixels());
        }
        public Bitmap getBmpData() {return null;}
    }

    private List<List<Tile>> getVisibleTileGrid(TileRange tileIdRange) {

        List<List<Tile>> result = new ArrayList<List<Tile>>();

        Tile t;

        for (int y = tileIdRange.top; y <= tileIdRange.bottom; y++) {

            List<Tile> curRow = new ArrayList<Tile>();
            for (int x = tileIdRange.left; x <= tileIdRange.right; x++) {
                t = tileProvider.getTile(x,y);
                if(t == null){
                    t = new MissingTile(x,y); // always return a grid of non-null tiles
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

                        tileProvider.generateNextTile(state.visibleTileIdRange);

                    }
                    Thread.sleep(5); // so we can interact in a reasonable time

                } catch (InterruptedException e) {
                    // nop
                } finally {
                    // do this in a finally so that if an exception is thrown
                    // during the above, we don't leave the Surface in an
                    // inconsistent bmpData
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

        // the offsets may change during doDraw; Use a copy, otherwise there'll be flickering/tearing of tiles!
        int moffX = state.canvasOffsetX;
        int moffY = state.canvasOffsetY;

        // draw BG
        canvas.drawRect(new Rect(0, 0, state.width, state.height), paint_bg);

        if (tileProvider != null) {

            List<List<Tile>> visibleGrid = getVisibleTileGrid(state.visibleTileIdRange);

            int size = tileProvider.getTileWidthPixels();

            int yMargin = moffY % size;
            int xMargin = moffX % size;

            int y = yMargin - size; // start one above

            for (List<Tile> tileRow : visibleGrid) {

                int x = xMargin - size; // start with one left

                for (Tile t : tileRow) {

                    Bitmap bmp = t.getBmpData();
                    if (bmp != null) {

                        //bmpData.setPixels(t.bmpData, 0, tileSize, xOff, yOff, tileSize, tileSize);
                        canvas.drawBitmap(bmp, x, y, null);

                        if (debugEnabled) {
                            canvas.drawRect(t.getRect(x, y), paint_gridLine);
                        }

                    }
                    else {
                        // TODO: could allow provider to give us a 'no data' placeholder tile
                    }

                    if (debugEnabled) {

                        canvas.drawRect(t.getRect(x, y), paint_gridLine);

                        String fmt1 = "Tile(%d,%d)\\n@(%dpx,%dpx)";
                        String msg1 = String.format(fmt1, t.xId, t.yId, x, y);
                        canvas.drawText(msg1, x + (size / 2), y + (size / 2), paint_msgText);
                    }

                    x += size; // move right one tile width
                }

                y += size; // move down one tile width
            }

            if (debugEnabled) {
                drawDebugBox(canvas,xMargin,yMargin);
            }
        }

        canvas.restore();

    }

    private void drawDebugBox(Canvas canvas, int xm, int ym) {

        // draw a bunch of debug stuff
        String fmt1 = "%dx%d[%dx%d], s=%1.3f";
        String fmt2 = "offx=%d,y=%d, xm=%d, ym=%d";
        String fmt3 = "tiles %s";
        String msgResAndScale = String.format(fmt1, state.width, state.height,
                        state.numVisibleTiles_w, state.numVisibleTiles_h,state.scaleFactor);
        String msgOffset = String.format(fmt2, state.canvasOffsetX, state.canvasOffsetY, xm, ym);
        String msgVisibleIds = String.format(fmt3, state.visibleTileIdRange);
        String msgProvider = tileProvider.getDebugSummary();
        String msgMemory = Utils.getMemStatus();
        Paint paintMem = Utils.isHeapAlmostFull() ? paint_errText : paint_msgText;

        float boxWidth = 330, boxHeight = 145;

        float debug_x = state.width - boxWidth;
        float debug_y = state.height - boxHeight;

        canvas.drawRect(debug_x, debug_y, state.width, state.height, paint_debugBG);

        canvas.drawText(msgResAndScale, debug_x + boxWidth / 2, debug_y + 30, paint_msgText);
        canvas.drawText(msgOffset, debug_x + boxWidth / 2, debug_y + 55, paint_msgText);
        canvas.drawText(msgVisibleIds, debug_x + boxWidth / 2, debug_y + 80, paint_msgText);
        canvas.drawText(msgProvider, debug_x + boxWidth / 2, debug_y + 105, paint_msgText);
        canvas.drawText(msgMemory, debug_x + boxWidth / 2, debug_y + 130, paintMem);


    }


    @Override // register GD
    public boolean onTouchEvent(MotionEvent me) {

        invalidate();

        gestureDetector.onTouchEvent(me);
        scaleDetector.onTouchEvent(me);

        return true;
    }


    class ScreenState {

        int width, height;

        int numVisibleTiles_w, numVisibleTiles_h;

        // ScaleListener sets this from 0.1 to 5.0
        float scaleFactor = 1.0f;

        int canvasOffsetX = 0, canvasOffsetY = 0;

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

            //TileRange bounds = tileProvider.getTileIndexBounds();

            //log("scroll x=" + distanceX + ", y=" + distanceY);
            state.canvasOffsetX -= (int) distanceX;
            state.canvasOffsetY -= (int) distanceY;

            // TODO: reset!
            // state.canvasOffsetY = newOffY > bounds.top ? bounds.top : newOffY;
            // Log.d(Utils.LOG_TAG, msg);

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
