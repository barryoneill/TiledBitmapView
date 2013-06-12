package net.nologin.meep.tbv.demo;

import android.content.Context;
import android.graphics.*;
import net.nologin.meep.tbv.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DemoTileProvider extends GenericTileProvider {

    private Context ctx;
    private static final String DEBUG_SUMMARY_FMT = "STProv[cache=%d,queue=%d]";

    // keep a cache of tiles we've already seen or are currently in the process of rendering
     private final Map<Long,Tile> tileCache;

    private final Map<String,Bitmap> resCache;

    Paint tileTextPaint;

    // a queue of tiles to render in the background (in case of slow processing), must be multi-thread-friendly
    private final List<Tile> renderQueue;

    public DemoTileProvider(Context ctx){

        super(ctx);

        this.ctx = ctx;

        tileCache = new ConcurrentHashMap<Long,Tile>();
        renderQueue = Collections.synchronizedList(new LinkedList<Tile>());

        resCache = new HashMap<String, Bitmap>();

        tileTextPaint = new Paint();
        tileTextPaint.setColor(Color.BLUE);
        tileTextPaint.setTextSize(80);
        tileTextPaint.setAntiAlias(true);
        tileTextPaint.setTextAlign(Paint.Align.LEFT);

    }


    @Override
    public int getGridBufferSize() {
        return 1;
    }

    @Override
    public Tile getTile(int x, int y) {

        /* don't render the tile here, that could hold up the UI.  The call to notifyTileIDRangeChange() will
         * let us add the required tiles to a queue that we can render in the bg in processQueue(). */
        return tileCache.get(Tile.getCacheKey(x,y));

    }


    @Override
    public boolean processQueue(TileRange visible) {

        Tile t;

        // pop the next item to render off our queue
        synchronized (renderQueue){
            if(renderQueue.isEmpty()){
                return false;
            }
            t = renderQueue.remove(0);
        }

        // anything to render?
        if(t == null || t.getBmpData() != null){
            return false; // nothing to render
        }


        // okay, build the contents of the tile.  Doesn't matter if it's slow, we're in a bg thread
        // plus this is just a demo so I won't tweak for performance here - but you should :)

        // have tileable resources and rows and cols 1-5, loop our indexes to match
        // http://stackoverflow.com/a/4412200/276183 for handling negative mod in java
        int col = (t.xId % 5 + 5) % 5 + 1;
        int row = (t.yId % 5 + 5) % 5 + 1;
        String resName = "sr" + row + "c" + col;

        Bitmap bmp = resCache.get(resName);

        if(bmp == null){

            int resID = ctx.getResources().getIdentifier(resName,"drawable", ctx.getPackageName());

            bmp = BitmapFactory.decodeResource(ctx.getResources(),resID);

            resCache.put(resName,bmp);

        }


        t.setBmpData(bmp);

        // put it in the cache for the UI thread to find via getTile()
        tileCache.put(t.cacheKey, t);

        return true;

    }



    @Override
    public void notifyTileIDRangeChange(TileRange newRange) {

        // clear out the bitmaps of any off-screen cached tiles, so we don't gobble ram
        Collection<Tile> entries = tileCache.values();
        for(Tile t : entries){
            if(t.getBmpData() != null && !newRange.contains(t)){
               t.clearBmpData();
            }

        }

        // find out what visible tiles are missing bmp data, we'll add that to the queue
        synchronized (renderQueue){
            // wipe the render queue
            renderQueue.clear();

            Tile t;

            for(int y = newRange.top; y <= newRange.bottom; y++) {
                for(int x = newRange.left; x <= newRange.right; x++) {

                    t = getTile(x, y);
                    if(t == null || t.getBmpData() == null){
                        renderQueue.add(new Tile(x,y));
                    }

                }
            }
        }

    }

    @Override
    public String getDebugSummary(){
        int cache = tileCache == null ? 0 : tileCache.size();
        int queue = renderQueue == null ? 0 : renderQueue.size();

        return String.format(DEBUG_SUMMARY_FMT, cache, queue);
    }
}
