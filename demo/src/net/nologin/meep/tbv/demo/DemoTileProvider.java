package net.nologin.meep.tbv.demo;

import android.content.Context;
import android.graphics.*;
import android.util.Log;
import net.nologin.meep.tbv.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This provider (a subclass of {@link GenericTileProvider}) provides an example of writing a provider
 * which asynchronously generates tiles.  For this demo I took 'Stone texture' from the following page:
 * <p/>
 * http://seamless-pixels.blogspot.com/p/free-seamless-ground-textures.html
 * (http://3.bp.blogspot.com/-Ooh1GWkBwVU/UHU9EpVeurI/AAAAAAAADgQ/HcWIllWCHB4/s1600/Seamless+stones+00.jpg)
 * <p/>
 * This in itself is a tileable image, but I'm going to use the default size of 256px tiles, rather than just
 * deliver the same massive 1280px tile over and over :)  The images are broken up into 5 rows of five, and
 * stored in 'res/drawable-nodpi' with filenames such as sr1c2.png (for row1, column 2) etc.
 * <p/>
 * As per the instructions in {@link TileProvider}, on each call of
 * {@link #onTileIDRangeChange(net.nologin.meep.tbv.TileRange)} I create a queue of the tiles needed to fill
 * the surface.  In a background task I populate each of these with the correct one of the aforementioned
 * bitmaps.  (The bitmaps themselves I cache, but it's just to give an idea).
 *
 * @see TileProvider
 * @see TiledBitmapView
 */
public class DemoTileProvider extends GenericTileProvider {

    private static final String DEBUG_SUMMARY_FMT = "StonesProv[cache=%d]";

    // used for the starting and stopping of background tasks
    private ExecutorService executorService;
    private Future lastSubmittedTask;

    // keep a cache of tiles we've already seen or are currently in the process of rendering
    private final Map<Long, Tile> tileCache;

    // cache the 25 bitmaps as we load them from resources (25 256x256 bitmaps won't kill the heap)
    private final Map<String, Bitmap> resCache;

    // used to let the hasFreshData call know when we've got something for it to render
    private AtomicBoolean hasFreshData = new AtomicBoolean(false);

    public DemoTileProvider(Context ctx) {

        super(ctx);

        // getTile and onTileIDRangeChange are invoked on different threads, but both access this (see TileProv doc)
        tileCache = new ConcurrentHashMap<Long, Tile>();

        // only our QueueProcessTask accesses this, no need for synchronization
        resCache = new HashMap<String, Bitmap>();

    }


    @Override
    public Tile getTile(int x, int y) {

        /* as the TileProvider docs say, return empty tiles here if necessary, we can use 'hasFreshData'
         * to get the provider to do another pull of tiles later. */
        return tileCache.get(Tile.createCacheKey(x, y));

    }

    @Override
    public boolean hasFreshData() {

        // set this back to false if we've just told the provider data is ready, otherwise future calls
        // will result in possibly unnecessary renders
        return hasFreshData.getAndSet(false);

    }


    @Override
    public void onTileIDRangeChange(TileRange newRange) {

        /* clear out the bitmaps of any off-screen cached tiles, so we don't gobble up heap.  We're not using
         * that much memory for the tiles, so we add a buffer of 1 to our criteria so when we scroll back to
         * already seen tiles, they'll still have bitmap data.
         */
        Collection<Tile> entries = tileCache.values();
        for (Tile t : entries) {
            if (t.getBmpData() != null && !newRange.contains(t, 1)) {
                t.clearBmpData();
            }

        }

        // create a list of all the requested tiles that have no bitmap data
        List<Tile> renderQueue = new LinkedList<Tile>();
        Tile t;

        for (int y = newRange.top; y <= newRange.bottom; y++) {
            for (int x = newRange.left; x <= newRange.right; x++) {

                t = getTile(x, y);
                if (t == null || t.getBmpData() == null) {
                    renderQueue.add(new Tile(x, y));
                }

            }
        }

        // init the executor
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }

        // kill any previous queue processing
        if (lastSubmittedTask != null) {
            lastSubmittedTask.cancel(true);
        }

        // fire off our background task
        lastSubmittedTask = executorService.submit(new QueueProcessTask(renderQueue));

    }

    /**
     * Populate the bitmaps of a provided list of tiles asynchronously.
     */
    class QueueProcessTask implements Runnable {

        private List<Tile> renderQueue;

        public QueueProcessTask(List<Tile> renderQueue) {
            this.renderQueue = renderQueue;

        }

        @Override
        public void run() {

            Context ctx = getContext();

            while (!renderQueue.isEmpty()) {

                // always keep checking to see if the view has requested that we stop
                if (Thread.currentThread().isInterrupted()) {
                    Log.d(Utils.LOG_TAG, "Current queue processing being interruped");
                    return;
                }

                Tile t = renderQueue.remove(0);

                if (t == null || t.getBmpData() != null) {
                    continue; // nothing to do
                }

                Log.d(Utils.LOG_TAG, "Processing tile " + t);

                // have tileable resources and rows and cols 1-5, loop our indexes to match
                // http://stackoverflow.com/a/4412200/276183 for handling negative mod in java
                int col = (t.xId % 5 + 5) % 5 + 1;
                int row = (t.yId % 5 + 5) % 5 + 1;
                String resName = "sr" + row + "c" + col;

                Bitmap bmp = resCache.get(resName);

                if (bmp == null) {

                    int resID = ctx.getResources().getIdentifier(resName, "drawable", ctx.getPackageName());
                    bmp = BitmapFactory.decodeResource(ctx.getResources(), resID);
                    resCache.put(resName, bmp);

                }


                t.setBmpData(bmp);

                // cache the tile so the next getTile() for this tile will offer something the view can render
                tileCache.put(t.cacheKey, t);

                // next time the provider polls hasFreshData(), it'll trigger a fresh set of getTiles()
                hasFreshData.set(true);
            }

            Log.d(Utils.LOG_TAG,"Queue processing finished normally");
        }
    }


    @Override
    public void onSurfaceDestroyed() {

        // ensure we don't leave any hanging threads
        if (executorService != null) {
            executorService.shutdownNow();
        }

    }

    @Override
    public String getDebugSummary() {

        int cache = tileCache == null ? 0 : tileCache.size();
        return String.format(DEBUG_SUMMARY_FMT, cache);
    }
}
