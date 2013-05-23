package net.nologin.meep.tbv;

import android.graphics.Bitmap;
import android.graphics.Rect;import java.lang.String;

public class Tile {

    public final int size;
    public final int xId;
    public final int yId;
    public final long cacheKey;
    public Bitmap bmpData; // not final, can be cleared

    public Tile(int xId, int yId, int size) {

        this.xId = xId;
        this.yId = yId;
        this.size = size;
        this.cacheKey = getCacheKey(xId,yId);
    }

    public static long getCacheKey(int x, int y){
        return (long)x << 32 | y & 0xFFFFFFFFL; // pack into high and low words in a long
    }

    public Rect getRect(int offsetX, int offsetY){
        return new Rect(offsetX, offsetY, offsetX + size, offsetY + size);
    }

    public String toString() {

        return String.format("Tile[(%d,%d),%dpx]",xId,yId,size);

    }

}