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

/**
 * Provider of {@link Tile} data for the {@link TiledBitmapView} (TBV from here on).
 * <br/><br/>
 * <b>Provider Overview:</b>
 * <br/><br/>
 * The TBV is a scrollable SurfaceView which requires a <code>TileProvider</code> to deliver the contained selection
 * of bitmap data as the user manipulates the view.  The data is referenced as a conceptual 2D plane of square tiles,
 * with (x,y) coordinates as IDs (eg, <code>(0,0)(0,1)(0,2)</code> for three horizontally sequential tiles).
 * As the user manipulates the TBV, it informs the provider of the current 'visible' range of tile IDs, and fetches
 * the respective {@link Tile} objects from the provider as it renders the surface.
 * <br/><br/>
 * <b>Lifecycle Summary:</b>
 * <ul>
 * <li>There are several <code>getConfig*()</code> methods that are used to setup the TBV.</li>
 * <li>Based on screen state (ie screen dimensions, zoom level, scroll offset), the TBV calculates what range of
 * Tile IDs are required to render the surface, and informs the provider via {@link #onTileIDRangeChange(TileRange)}.
 * This will be called any time that a TBV manipulation results in a change in the range of required IDs
 * (eg, user scrolls, screen rotation).</li>
 * <li>When refreshing the surface, the TBV's rendering thread will call {@link #getTile(int, int)} for each
 * {@link Tile} in that range. Tiles without bitmap data at this time are rendered as empty squares.  This allows for
 * implementations where the tiles gradually appear as the provider makes the bitmaps available.</li>
 * </ul>
 * <br/><br/>
 * <b>Performance</b><br/>
 * Simple (ie 'fast') solutions can just ignore the {@link #onTileIDRangeChange(TileRange)} calls and populate the bitmap
 * data on the fly for each {@link #getTile(int, int)} call.  This approach can have several issues:
 * <ol>
 * <li><b>Heap Space</b>: Depending on the app, the user may scroll around the same area repeatedly.  It can improve
 * performance to maintain a bitmap cache in your provider, rather than generate/fetch each time.  However, as the
 * user scrolls around, this can exhaust heap memory. (Turning on {@link TiledBitmapView#setDebugEnabled(boolean)}
 * can help you see any jumps in heap use as you scroll). Use {@link #onTileIDRangeChange(TileRange)} to see which
 * tiles in your cache are well out of view, and remove them.</li>
 * <li><b>Responsiveness</b>: No calls to any of the interface methods should result in long-running or blocking
 * activity. At best, tiles will be slow to appear, at worst the app will become unresponsive or cause an
 * <a href="http://developer.android.com/training/articles/perf-anr.html">ANR</a>.
 * Instead, when {@link #onTileIDRangeChange(TileRange)} is called, trigger a background task to process the queue of
 * any tiles that need populating. In the meantime, calls to {@link #getTile(int, int)} for unprocessed tiles should
 * tiles with null bitmap data. The TBV constantly polls the {@link #hasFreshData()} method, and should it return <code>true</code>,
 * the TBV will trigger a fresh surface render, and therefore a fresh set of calls to {@link #getTile(int, int)}.
 * The {@link #onSurfaceDestroyed()} method can be used to stop any outstanding background tasks when the view is closed.</li>
 * <li><b>User Complaints</b>: Maybe your solution isn't fast, it just seems it on your quad core 2ghz plaything.</li>
 * </ol>
 * <br/><br/>
 * <b>Background Rendering & Threads</b>
 * <p/>
 * The {@link TiledBitmapView} is a {@link android.view.SurfaceView} subclass that uses a separate thread to
 * render the actual contents of the surface.  Most methods - those related to setup, and view/range change
 * notification are invoked on the main UI thread as the user interacts with the TBV.  However, since the
 * surface is rendered by a seperate thread, methods such as {@link #getTile(int, int)} and {@link #hasFreshData()}
 * will be invoked on that seperate thread.
 * <br/><br/>
 * <b>It is up to the implementation to ensure that variables accessed by the two threads are handled in a safe
 * manner. Methods are documented to confirm whether they are invoked by the UI or the rendering thread.</b>
 * <br/><br/>
 *
 * @see GenericTileProvider GenericTileProvider: A simple implementation (that can also be extended).
 */
public interface TileProvider {

    /**
     * Get the side length (in px) of the square {@link Tile} objects generated by this provider.
     * <br/><br/>
     * <b>Thread: Main UI - See {@link TileProvider class javadoc}</b>
     *
     * @return The side length (in pixels) of the square tiles generated by this provider.
     */
    public int getConfigTileSize();

    /**
     * If specified, scrolling past these tile ID limits in the view will be prohibited.
     * <br/>
     * Useful to prevent scrolling past excessive ranges, or also in conjuction with {@link #getConfigGridAnchor()}
     * when your data has a logical boundary.
     *
     * Unspecified behaviour occurs if the bounds are too restrictive (ie, less than the initial range displayed on
     * the screen).
     * <br/><br/>
     * <b>Thread: Main UI - See {@link TileProvider class javadoc}</b>
     *
     *
     * @return <code>null</code> if there are no restrictions on scrolling.  Otherwise, a 4-element Integer
     *         array should be specified, specifying the left, top, right, and bottom ID limits respectively.  Each
     *         individual element can be <code>null</code> to selectively allow unlimited scrolling.  Eg, to enforce
     *         that scrolling up can't go beyond x=5, then specify <code>{null,null,5,null}</code>.
     *         Invalid arrays will be ignored (same as null).
     */
    public Integer[] getConfigTileIDLimits();

    /**
     * When one of the <code>moveTo*</code> methods in {@link TiledBitmapView} is called, the desired Tile will
     * be moved into view.  This {@link GridAnchor} value defines where exactly on the screen that Tile will end
     * up.  In map-style views (or if unsure), generally {@link GridAnchor#Center} is sufficent.
     *
     * <br/><br/>
     * <b>Thread: Main UI - See {@link TileProvider class javadoc}</b>
     *
     * @return The relative position on the screen that target tiles will be rendered.
     */
    public GridAnchor getConfigGridAnchor();


    /**
     * Get the specified tile.
     * <br/><br/>
     * <b>Thread: Surface Renderer (Not UI) - See {@link TileProvider class javadoc}</b>
     *
     * @param x The tile's x-coordinate
     * @param y The tile's y-coordinate
     * @return The tile.  Don't return null: If the bitmap isn't ready, return a tile without the bitmap data set.
     */
    public Tile getTile(int x, int y);

    /**
     * Useful when this provider performs background queue processing, this method is polled by the view to
     * check if there are fresh bitmaps to include in the next rendering of the surface.  The implementation
     * of this method should probably toggle the flag back to false in order to prevent unecessary repeat renderings.
     *
     * <br/><br/>
     * <b>Thread: Surface Renderer (Not UI) - See {@link TileProvider class javadoc}</b>
     *
     * @return <code>true</code> if this provider has generated new bitmap data that should be included since the
     * last call.
     */
    public boolean hasFreshData();

    /**
     * Called by the view when it calculates a change in the visble tile range.  This gives the provider a
     * chance to clear out older cached data, or to trigger background tasks to generate/fetch new tile
     * bitmap data.  Return true from {@link #hasFreshData()} to let the view know when any background
     * task has produced new data it can use.
     * <br/><br/>
     * <b>Thread: Main UI - See {@link TileProvider class javadoc}</b>
     *
     * @param newRange The boundaries of the tile IDs fpsTimeNow on display.
     */
    public void onTileIDRangeChange(TileRange newRange);

    /**
     * Called by the view when the user uses pinch-zoom
     * <br/><br/>
     * <b>Thread: Main UI - See {@link TileProvider class javadoc}</b>
     *
     * @param newZoom a float value from 1.0 to 5.0 (initial is 1.0)
     */
    public void onZoomFactorChange(float newZoom);

    /**
     * Called when the view's surface is being destroyed, giving the provider a chance to shutdown
     * any background tasks.
     * <br/><br/>
     * <b>Thread: Main UI - See {@link TileProvider class javadoc}</b>
     */
    public void onSurfaceDestroyed();

    /**
     * Generate a short summary string with the state of this provider. When
     * {@link TiledBitmapView#setDebugEnabled(boolean)} has been set true, this string will be displayed
     * as part of the debug information at the bottom right. Although only called during debug mode,
     * depending on your provider, it may be called a lot, so keep it as resource efficient as possible.
     * <br/><br/>
     * <b>Thread: Surface Renderer (Not UI) - See {@link TileProvider class javadoc}</b>
     *
     * @return Short descriptive summary of this provider's state, eg "MyProv[cache=100,blah=123]".
     */
    public String getDebugSummary();


}