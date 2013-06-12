package net.nologin.meep.tbv;

import android.content.Context;
import android.graphics.*;

/**
 * This class is the default TileProvider implementation that TiledBitmapView uses, and can be used as
 * a base for other providers.  It simply generates tiles that all carry the same yellow circle on black
 * background with the 'No Provider Registered' text.
 */
public class GenericTileProvider implements TileProvider {

    // debug meaningless for this simple provider, static string is fine
    private static final String GENERIC_DEBUG_MSG = "GenericProv[¯\\(°_o)/¯]";

    private Context ctx;

    Paint paintTileBG;
    Paint paintTileCircle;
    Paint paintTileTxt;

    Bitmap sharedTileBmp = null; // same bitmap for all tiles, cache it

    public GenericTileProvider(Context ctx) {

        this.ctx = ctx;

        paintTileBG = new Paint();
        paintTileBG.setColor(Color.DKGRAY);

        paintTileCircle = new Paint();
        paintTileCircle.setColor(Color.YELLOW);
        paintTileCircle.setTextSize(4);
        paintTileCircle.setAntiAlias(true);

        paintTileTxt = new Paint();
        paintTileTxt.setColor(Color.BLACK);
        paintTileTxt.setTextSize(20);
        paintTileTxt.setAntiAlias(true);
        paintTileTxt.setTextAlign(Paint.Align.CENTER);

        genSharedBitmap();

    }

    private void genSharedBitmap(){

        int w = getTileWidthPixels();

        sharedTileBmp = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sharedTileBmp);

        // gray background
        c.drawRect(0, 0, w, w, paintTileBG);

        // yellow circle
        c.drawCircle(w / 2, w / 2, w / 2 - 20, paintTileCircle);

        // 'provider not registered' text
        float txtY = w / 2;
        String line1 = ctx.getResources().getString(R.string.genericprovider_tilemsg_line1);
        String line2 = ctx.getResources().getString(R.string.genericprovider_tilemsg_line2);

        c.drawText(line1, w / 2, txtY, paintTileTxt);
        txtY += 20;
        c.drawText(line2, w / 2, txtY, paintTileTxt);

    }

    protected Context getContext(){
        return ctx;
    }

    @Override
    public int getTileWidthPixels() {
        return Tile.DEFAULT_TILE_SIZE;
    }

    @Override
    public Tile getTile(int x, int y) {

        Tile t = new Tile(x, y);

        // all tiles get the same bitmap
        if (sharedTileBmp != null) {
            t.setBmpData(sharedTileBmp);
        }

        return t;
    }

    @Override
    public GridAnchor getGridAnchor() {
        // put (0,0) in the middle of the screen
        return GridAnchor.Center;
    }

    @Override
    public boolean hasFreshData() {


        /* The processing done here only happens once and could be done in the constructor, but I'm
           putting it here - That way, any problems with the seperate thread that calls this will
           result in the problem being noticed sooner.
         */

        // signal that there's no more processing to be done
        return false;
    }


    @Override
    public Integer[] getTileIndexBounds() {

        return null; // unlimited scrolling

    }

    @Override
    public void onTileIDRangeChange(TileRange newRange) {

        // this provider doesn't need to know about this

    }

    @Override
    public void onZoomFactorChange(float newZoom) {

        // no zooming in this provider
    }

    @Override
    public String getDebugSummary() {

        return GENERIC_DEBUG_MSG;
    }

    @Override
    public void onSurfaceDestroyed() {
        // nop
    }
}
