/*
 *    TiledBitmapView - A library providing a view component rendered dynamically with tile data
 *    Copyright 2013 Barry O'Neill (http://meep.nologin.net/)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package net.nologin.meep.tbv;

import android.content.Context;
import android.graphics.*;

/**
 * This class is a very basic/test provider implementation and is the default provider, should the developer
 * forget to register their own with the {@link TiledBitmapView}.  It provides some sensible defaults for the less
 * important methods, so can also be extended instead of having to implement your own {@link TileProvider} from scratch.
 * <br/><br/>
 * It creates placeholder bitmap on construction (A yellow circle on a grey background with the text <i>'no provider
 * registered'</i>), and every tile served by this provider reuses it.  It serves only as a minimalistic base/default
 * class and <b>should not be used as an example for your own provider</b> especially any that does anything more
 * complicated than this one does.
 * <br/><br/>
 * To write a <b>proper</b> provider, take a look at the detailed javadoc of the {@link TileProvider} interface,
 * and also checkout the <code>DemoTileProvider</code> implementation in the </code><b>TiledBitmapViewDemo</b>
 * project for a proper example on how to asynchronously generate bitmaps.
 *
 * @see TiledBitmapView
 * @see TileProvider
 */
public class GenericTileProvider implements TileProvider {

    private Context ctx;

    private Bitmap sharedTileBmp = null;

    public GenericTileProvider(Context ctx) {

        this.ctx = ctx;

        // create on startup once
        sharedTileBmp = generatedSharedBmp(ctx, getConfigTileSize());

    }


    private static Bitmap generatedSharedBmp(Context ctx, int w) {

        Paint paintTileBG = new Paint();
        paintTileBG.setColor(ctx.getResources().getColor(R.color.genericprovider_tile_bg));

        Paint paintTileCircle = new Paint();
        paintTileCircle.setColor(ctx.getResources().getColor(R.color.genericprovider_tile_circle));
        paintTileCircle.setTextSize(4);
        paintTileCircle.setAntiAlias(true);

        Paint paintTileTxt = new Paint();
        paintTileTxt.setColor(Color.BLACK);
        paintTileTxt.setTextSize(20);
        paintTileTxt.setAntiAlias(true);
        paintTileTxt.setTextAlign(Paint.Align.CENTER);

        Bitmap bmp = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        // background
        c.drawRect(0, 0, w, w, paintTileBG);

        // circle
        int radius = w / 2 - 15;
        c.drawCircle(w / 2, w / 2, radius, paintTileCircle);

        // 'provider not registered' text
        float txtY = w / 2;
        String line1 = ctx.getResources().getString(R.string.genericprovider_tilemsg_line1);
        String line2 = ctx.getResources().getString(R.string.genericprovider_tilemsg_line2);

        c.drawText(line1, w / 2, txtY, paintTileTxt);
        txtY += 20;
        c.drawText(line2, w / 2, txtY, paintTileTxt);

        return bmp;
    }

    /**
     * @return The context provided to the constructor
     */
    protected Context getContext(){
        return ctx;
    }

    @Override
    public int getConfigTileSize() {
        return Tile.DEFAULT_TILE_SIZE;
    }

    @Override
    public Tile getTile(int x, int y) {

        /* this is very fast, since every tile is the same.  Do not do this in a real provider, as this will result
         * in a very sluggish tile surface. See class javadoc! */
        Tile t = new Tile(x, y);
        t.setBmpData(sharedTileBmp);
        return t;
    }

    @Override
    public GridAnchor getConfigGridAnchor() {
        // put (0,0) in the middle of the screen
        return GridAnchor.Center;
    }

    @Override
    public boolean hasFreshData() {
        // we're doing doing any asynchronous processing (you should!) so there's never any new data
        return false;
    }

    @Override
    public Integer[] getConfigTileIDLimits() {
        // unbounded scrolling in every direction
        return null;
    }

    @Override
    public void onTileIDRangeChange(TileRange newRange) {
        // no caching or asynchronous processing (you should!), so nothing to do here.
    }


    @Override
    public void onZoomFactorChange(float newZoom) {
        // no zooming in this provider
    }

    @Override
    public String getDebugSummary() {
        // debug meaningless for this simple provider, static string is fine
        return "GenericProv[¯\\(°_o)/¯]";
    }

    @Override
    public void onSurfaceDestroyed() {
        // if we were performing async processing, we'd
    }


}
