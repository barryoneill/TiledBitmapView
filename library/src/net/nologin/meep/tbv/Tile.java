package net.nologin.meep.tbv;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.lang.String;

/**
 * The surface of a {@link TiledBitmapView} is rendered using a collection of these Tile objects, each holding a bitmap
 * suitable for rendering a square area of the surface.  The view calculates which tiles are required, and fetches them
 * from the {@link TileProvider}.  Each tile is then rendered at the correct location on the surface.
 * <br/><br/>
 * Tiles are IDed by their coordinates in a 2D plane. The co-ordinate convention is similar to that of the android canvas.
 * Negative, zero and positive values are permitted, the x values increase rightward, and the y values increase downward.
 * Eg, tile <code>(0,1)</code> is located directly below <code>(0,0)</code>, and to the left of <code>(1,1)</code>.
 * <br/><br/>
 * All tiles contain a mutable bitmap, allowing {@link TileProvider} implementations to generate the renderable
 * content when ready, and also to clear that content should the tile go off-screen (to save heap memory) without
 * the allocation overhead of creating both Tile and Bitmap objects.
 */
public class Tile {

    /**
     * Default tile size, 256 pixels.  The actual content of the tile can be scaled by the provider.  This
     * size is all about how the SurfaceView is rendered.  Too large, and if the Provider is slow, the user
     * will feel like the screen is taking forever to display.  Too small, and the number of various method
     * calls to the provider will be excessive.  256px seems to be a good balance.
     */
    public static final int DEFAULT_TILE_SIZE = 256;

    /**
     * Size of the side of the square (pixels)
     */
    public final int size;

    /**
     * X-Coordinate of the tile in the tile grid
     */
    public final int xId;

    /**
     * Y-Coordinate of the tile in the tile grid
     */
    public final int yId;

    /**
     * Coordinate-based key that is useful for caching.  This is an alternative to having an
     * ambiguous hashCode, (two tiles may have the same x,y coordinates, but not the same bitmap data)
     */
    public final long cacheKey;

    /* The only mutable fields in this class, the bitmap data is set by the provider, and can
     * be cleared as well (usually when the provider detects that the tile has gone out of
     * range to prevent the heap memory from being gobbled up) */
    private Bitmap bmpData;

    /**
     * Create a tile with side length {@link #DEFAULT_TILE_SIZE}, IDed by the x and y
     * coordinates of that tile in the grid.
     *
     * @param xId The x coordinate
     * @param yId The y coordinate
     */
    public Tile(int xId, int yId) {
        this(xId, yId, DEFAULT_TILE_SIZE);
    }

    /**
     * Create a tile of the specified size length, IDed by the x and y coordinates of that tile in the grid.
     *
     * @param xId  The x coordinate
     * @param yId  The y coordinate
     * @param size The length of the side of the square tile
     */
    public Tile(int xId, int yId, int size) {

        this.xId = xId;
        this.yId = yId;
        this.size = size;
        this.cacheKey = createCacheKey(xId, yId);
    }

    /**
     * Generate a unique ID based on the x and y coordinates could be achieved with a Pair object, but
     * can also be achieved by packing them into the high and low words of a long.
     * See: http://stackoverflow.com/q/919612/276183
     *
     * @param x The x coordinate
     * @param y The y coordinate
     * @return The cache key
     */
    public static long createCacheKey(int x, int y) {

        return (long) x << 32 | y & 0xFFFFFFFFL;
    }

    /**
     * Convenience method to create a Rect of this tile's size at the specified coordinates
     *
     * @param left The 'left' coordinate
     * @param top  The 'top' coordinate
     * @return The Rect
     */
    public Rect getRect(int left, int top) {
        return new Rect(left, top, left + size, top + size);
    }

    /**
     * @return The bitmap, may be <code>null</code>.
     */
    public Bitmap getBmpData() {
        return bmpData;
    }

    /**
     * Set the bitmap
     *
     * @param bmpData The bitmap data, may be <code>null</code>.
     */
    public void setBmpData(Bitmap bmpData) {
        this.bmpData = bmpData;
    }

    /**
     * Convenience method to set to contained bitmap to <code>null</code>
     */
    public void clearBmpData() {
        setBmpData(null);
    }

    /**
     * This method is used by the rendering thread to detect a change in bitmap data.  Bitmap hashcodes might
     * be sufficient here, while a bit-by-bit comparison might be expensive.  Regardless of implementation,
     * if two tiles return the same value, they'll be assumed to contain the same bitmap data.
     *
     * @return An identifying hashcode for the contained bitmap data.  Return <code>0</code> to indicate that
     * there is no bitmap data contained in this tile.
     */
    public int getBitmapContentHash() {
        // if we switch to something a bit more intensive than hashCode, consider caching the value on setBmpData
        return bmpData == null ? 0 : bmpData.hashCode();
    }


    public String toString() {
        return String.format("Tile[(%d,%d),%dpx]", xId, yId, size);
    }

}