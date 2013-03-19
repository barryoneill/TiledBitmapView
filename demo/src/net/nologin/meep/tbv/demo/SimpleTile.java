package net.nologin.meep.tbv.demo;

import android.graphics.Bitmap;
import net.nologin.meep.tbv.Tile;


public class SimpleTile extends Tile {

    public static final int TILE_SIZE = 256;

    private Bitmap bmpData;

    public SimpleTile(int xId, int yId) {
        super(xId, yId, TILE_SIZE);
    }

    public void setBmpData(Bitmap bmpData){
       this.bmpData = bmpData;
    }

    @Override
    public Bitmap getBmpData() {
        return bmpData;
    }
}
