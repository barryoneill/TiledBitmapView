package net.nologin.meep.tbv.demo;

import android.content.Context;
import android.graphics.*;
import android.util.Log;
import net.nologin.meep.tbv.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DemoTileProvider extends GenericTileProvider {

    private static final String DEBUG_SUMMARY_FMT = "StonesProv[cache=%d]";

    private ExecutorService executorService;
    private Future lastSubmittedTask;

    // keep a cache of tiles we've already seen or are currently in the process of rendering
    private final Map<Long,Tile> tileCache;

    private final Map<String,Bitmap> resCache;



    private AtomicBoolean hasFreshData = new AtomicBoolean(false);

    public DemoTileProvider(Context ctx){

        super(ctx);



        tileCache = new ConcurrentHashMap<Long,Tile>();

        resCache = new HashMap<String, Bitmap>();

    }


    @Override
    public int getGridBufferSize() {
        return 1;
    }

    @Override
    public Tile getTile(int x, int y) {

        /* don't render the tile here, that could hold up the UI.  The call to onTileIDRangeChange() will
         * let us add the required tiles to a queue that we can render in the bg in processQueue(). */
        return tileCache.get(Tile.getCacheKey(x,y));

    }

    // Wa-hey.
    public boolean hasFreshData(){

        boolean res = hasFreshData.getAndSet(false);

        if(res){
            Log.e(Utils.LOG_TAG," ----------------- has fresh data!");
        }

        return res;
    }



    @Override
    public void onTileIDRangeChange(TileRange newRange) {



        // clear out the bitmaps of any off-screen cached tiles, so we don't gobble ram
        Collection<Tile> entries = tileCache.values();
        for(Tile t : entries){
            if(t.getBmpData() != null && !newRange.contains(t)){ // TODO: investigate buffer removal
               t.clearBmpData();
            }

        }


        // find out what visible tiles are missing bmp data, we'll add that to the queue
        List<Tile> renderQueue = new LinkedList<Tile>();
        Tile t;

        for(int y = newRange.top; y <= newRange.bottom; y++) {
            for(int x = newRange.left; x <= newRange.right; x++) {

                t = getTile(x, y);
                if(t == null || t.getBmpData() == null){
                    renderQueue.add(new Tile(x,y));
                }

            }
        }

        // init the executor
        if(executorService == null || executorService.isShutdown()){
            executorService = Executors.newSingleThreadExecutor();
        }

        // kill any previous queue processing
        if(lastSubmittedTask != null){
            lastSubmittedTask.cancel(true);
        }

        // Fire off our jobby.
        lastSubmittedTask = executorService.submit(new QueueProcessTask(renderQueue));

    }

    class QueueProcessTask implements Runnable {

        private List<Tile> renderQueue;

        public QueueProcessTask(List<Tile> renderQueue) {

            this.renderQueue = renderQueue;

        }

        @Override
        public void run() {

            Log.e(Utils.LOG_TAG, " **** STARTING QUEUEPROCESSTASK!");

            Context ctx = getContext();

            // THREAD_PRIORITY_MORE_FAVORABLE

            while(!renderQueue.isEmpty()){

                if(Thread.currentThread().isInterrupted()){
                    Log.e(Utils.LOG_TAG, " ####################### argh I'm being killed");
                    return;
                }


                Tile t = renderQueue.remove(0);

                // anything to render?
                if(t == null || t.getBmpData() != null){
                    continue;
                }

                Log.d(Utils.LOG_TAG, "Processing tile " + t);

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

                hasFreshData.set(true);
            }

            Log.e(Utils.LOG_TAG, " ####################### I finished normally!");
        }
    }


    @Override
    public void onSurfaceDestroyed() {

        // ensure we don't leave any hanging threads
        if(executorService != null) {
            executorService.shutdownNow();
        }

    }

    @Override
    public String getDebugSummary(){

        int cache = tileCache == null ? 0 : tileCache.size();
        return String.format(DEBUG_SUMMARY_FMT, cache);
    }
}
