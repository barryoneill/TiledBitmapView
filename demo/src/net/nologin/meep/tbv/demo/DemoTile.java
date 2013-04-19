package net.nologin.meep.tbv.demo;

import android.graphics.Bitmap;
import android.util.Log;
import net.nologin.meep.tbv.Tile;
import net.nologin.meep.tbv.Utils;


public class DemoTile extends Tile {

    public static final int TILE_SIZE = 256;

    private Bitmap bmpData;

    public DemoTile(int xId, int yId) {
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
