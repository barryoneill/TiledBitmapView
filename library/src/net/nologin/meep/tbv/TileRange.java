package net.nologin.meep.tbv;


import java.lang.Math;import java.lang.String;public class TileRange {

    public final int left,top,right,bottom;

    public TileRange(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public boolean contains(Tile t){

        return t != null && contains(t.xId,t.yId);

    }

    public boolean contains(int x, int y){

        return
            // check for empty first
            left < right && top < bottom
            // then containment (inclusive of all boundaries, unlike android.graphics.Rect)
            && x >= left && x <= right && y >= top && y <= bottom;
    }

    public int numTilesHorizontal() {
        if(left > right){
            return 0;
        }
        return Math.abs(right-left+1);
    }

    public int numTilesVertical() {
        if(top > bottom){
            return 0;
        }
        return Math.abs(bottom-top+1);
    }

    public int numTiles(){
        return numTilesHorizontal() * numTilesVertical();
    }

    public String toString(){
        return String.format("TileRange[%d,%d,%d,%d,n=%d]", left, top, right, bottom, numTiles());
    }

}
