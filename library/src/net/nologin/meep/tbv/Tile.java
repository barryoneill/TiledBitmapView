package net.nologin.meep.tbv;

import android.graphics.Bitmap;
import android.graphics.Rect;import java.lang.String;

public abstract class Tile {

    public int size;
    public int xId;
    public int yId;

    public Tile(int xId, int yId, int size) {

        this.xId = xId;
        this.yId = yId;
        this.size = size;
    }

    public Rect getRect(int offsetX, int offsetY){
        return new Rect(offsetX, offsetY, offsetX + size, offsetY + size);
    }

    public String toString() {

        return String.format("Tile[(%d,%d),%dpx]",xId,yId,size);

    }

    public abstract Bitmap getBmpData();

}