package net.nologin.meep.tbv;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.lang.String;

public class Tile {

    public static final int DEFAULT_TILE_SIZE = 256;

    public final int size;
    public final int xId;
    public final int yId;
    public final long cacheKey;

    // TODO: doc
    private Bitmap bmpData;

    public Tile(int xId, int yId) {
        this(xId, yId, DEFAULT_TILE_SIZE);
    }

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

    public Bitmap getBmpData(){
        return bmpData;
    }

    // TODO: doc
    public int getBmpHashcode(){
        return bmpData == null ? 0 : bmpData.hashCode();
    }

    public void setBmpData(Bitmap bmpData){
        this.bmpData = bmpData;
    }

    public void clearBmpData(){
        this.bmpData = null;
    }


    public String toString() {

        return String.format("Tile[(%d,%d),%dpx]",xId,yId,size);

    }

}