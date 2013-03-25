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
    Paint paint_msgText;
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

        state = new ViewState();
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

        state.screenWidth = width;
        state.screenHeight = height;

        state.tileWidth = tileProvider.getTileWidthPixels();

        jumpToOriginTile();
    }

    public void jumpToOriginTile() {

        if (tileProvider == null) {
            return;
        }

        /* Imagine the (0,0) tile placed on the y axis, half on each side.  Our x-scroll offset is what's left over
         * when we fill up the remainder of the left area, until there's no room left in the view for more full tiles.
         */
        int halfScreen = (state.screenWidth + state.tileWidth) / 2;
        state.scrollOffsetX = halfScreen % state.tileWidth;


        // in the case of an odd number of horizontal tiles, we need an offset to move the origin to the middle
        //state.scrollOffsetX = fitCompletelyOnScreen % 2 == state. ? -tileSize / 2 : 0;
        //Toast.makeText(this.getContext(),"WOAH it's " + state.screenWidth + "/256=" + (state.screenWidth/state.tileWidth) % 2, Toast.LENGTH_SHORT).show();
        state.scrollOffsetY = 0;

        handleOffsetChange();


    }

    private void handleOffsetChange() {

        Pair<Integer,Integer> hInfo = calculate1DTileRange(state.scrollOffsetX, state.screenWidth, state.tileWidth);
        Pair<Integer,Integer> vInfo = calculate1DTileRange(state.scrollOffsetY, state.screenHeight, state.tileWidth);

        state.visibleTileIdRange = new TileRange(hInfo.first, vInfo.first, hInfo.second, vInfo.second);

        tileProvider.notifyTileIDRangeChange(state.visibleTileIdRange);

    }

    String lastMsg1 = "";

    private Pair<Integer,Integer> calculate1DTileRange(int offset, int availableLength, int tileWidth){

        int numTiles = availableLength / tileWidth; // int division floors

        int startTileId = (-numTiles/2) - (offset / tileWidth);

        int remainingOffset = offset % tileWidth;

        String offsetApplied = "";
        if(remainingOffset!=0){
            // we have blank space either side, add another tile and apply appropriate offset to startId
            numTiles++;
            // positive offset means one tile before, a negative one means after (handled by increase in numtiles)
            if(remainingOffset > 0){
                startTileId--;
            }

        }

        String msg = String.format("numTiles=%d, startTileId=%d, (offapp=%s)", numTiles, startTileId, offsetApplied);
        if(!msg.equals(lastMsg1)){
            Log.e(Utils.LOG_TAG,String.format("MOVE - offset=%d, numTiles=%d, startTileId=%d, offapp=%s, remainingOffset=%d", offset, numTiles,startTileId,offsetApplied, remainingOffset));
            lastMsg1 = msg;
        }



        return new Pair<Integer, Integer>(startTileId,startTileId+numTiles-1);

    }




    private class MissingTile extends Tile {
        public MissingTile(int x, int y) {
            super(x,y,state.tileWidth);
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


    int i =0;

    public void doDraw(Canvas canvas) {

        super.onDraw(canvas);

        canvas.save();

        // the offsets may change _during this doDraw_ call; Make a copy before
        // rendering, otherwise there'll be flickering/tearing of tiles!
        int xMargin = state.scrollOffsetX % state.tileWidth;
        int yMargin = state.scrollOffsetY % state.tileWidth;

        // in the case we're offset to the right, we need to start rendering 'back' a tile (the longer tile range
        // handles the case of left offset)
        if(xMargin > 0){
            xMargin -= state.tileWidth;
        }
        if(yMargin > 0){
            yMargin -= state.tileWidth;
        }
//
        // todo: remove (won't be necessary when tile gen is correct)
        canvas.drawRect(new Rect(0, 0, state.screenWidth, state.screenHeight), paint_bg);

        // offset our canvas, so we can draw our whole tiles on with simple 0,0 origin co-ordinates
        canvas.translate(xMargin,yMargin);

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

                        if(i == 30){
                            //Log.e(Utils.LOG_TAG,String.format("%20s - drawing at x=%4d,y=%4d",t,x,y));
                        }


                    }
                    else {
                        // TODO: could allow provider to give us a 'no data' placeholder tile
                    }

                    if (debugEnabled) {

                        canvas.drawRect(t.getRect(x, y), paint_gridLine);

                        String fmt1 = "Tile(%d,%d)\\n@(%dpx,%dpx)";
                        String msg1 = String.format(fmt1, t.xId, t.yId, x, y);
                        canvas.drawText(msg1, x + (state.tileWidth / 2), y + (state.tileWidth / 2), paint_msgText);
                    }

                    x += state.tileWidth; // move right one tile screenWidth
                }

                y += state.tileWidth; // move down one tile screenWidth
            }

            if(i == 30){
                //Log.e(Utils.LOG_TAG,String.format("Offset is x=%d(m:%d) and y=%d(m:%d)",moffX, xMargin, moffY, yMargin));
            }


        }

        // we don't want the debug box scrolling with the tiles, undo the offset
        canvas.translate(-xMargin,-yMargin);
        if (debugEnabled) {
            drawDebugBox(canvas,xMargin,yMargin);
        }

        if(i<=30){
            i++;
        }


        canvas.restore();

    }

    private void drawDebugBox(Canvas canvas, int xm, int ym) {

        // draw a bunch of debug stuff
        String fmt1 = "%dx%d, s=%1.3f";
        String fmt2 = "offx=%d,y=%d, xm=%d, ym=%d";
        String fmt3 = "tiles %s";
        String msgResAndScale = String.format(fmt1, state.screenWidth, state.screenHeight,state.scaleFactor);
        String msgOffset = String.format(fmt2, state.scrollOffsetX, state.scrollOffsetY, xm, ym);
        String msgVisibleIds = String.format(fmt3, state.visibleTileIdRange);
        String msgProvider = tileProvider == null ? "" : tileProvider.getDebugSummary();
        String msgMemory = Utils.getMemStatus();
        Paint paintMem = Utils.isHeapAlmostFull() ? paint_errText : paint_msgText;

        float boxWidth = 330, boxHeight = 145;

        float debug_x = state.screenWidth - boxWidth;
        float debug_y = state.screenHeight - boxHeight;

        canvas.drawRect(debug_x, debug_y, state.screenWidth, state.screenHeight, paint_debugBG);

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


    // TODO: persist/restor during suitable lifecycle events
    class ViewState {

        int screenWidth, screenHeight;

        int tileWidth;

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

            //TileRange bounds = tileProvider.getTileIndexBounds();

            //log("scroll x=" + distanceX + ", y=" + distanceY);
            state.scrollOffsetX -= (int) distanceX;
            state.scrollOffsetY -= (int) distanceY;

            // TODO: reset!
            // state.scrollOffsetY = newOffY > bounds.top ? bounds.top : newOffY;
            // Log.d(Utils.LOG_TAG, msg);

            handleOffsetChange();

            i = 0;

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
