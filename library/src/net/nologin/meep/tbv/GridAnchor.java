package net.nologin.meep.tbv;

import android.util.Pair;

/**
 * Used to specify a position on the screen where the view can scroll to in oder to
 * display a desired tile ID.  Eg, for some apps, it makes sense, when going to the
 * Origin (0,0) tile, to scroll so that tile is in the center of the view.  For other
 * apps, it may make sense to have the origin elsewhere.
 */
public enum GridAnchor {

    NW, N, NE,
    W, CENTER, E,
    SW, S, SE;


    final Pair<Integer,Integer> getOriginOffset(int width, int height, int tilew) {

        // default to 0,0 (covers left hand side and across top)
        int offX=0, offY=0;

        // anchors vertical through the middle
        if(N == this || CENTER == this || S == this){
            offX = (width - tilew)/2;
        }

        // anchors down the right hand side
        if(NE == this || E == this || SE == this){
            offX = width - tilew;
        }

        // anchors horizontal through the middle
        if(W == this || CENTER == this || E == this){
            offY = (height - tilew)/2;
        }

        // anchors along the bottom
        if(SW == this || S == this || SE == this) {
            offY = height - tilew;
        }

        // Aside: debate on '==' vs equals for enums at the following page:
        // http://stackoverflow.com/questions/1750435/comparing-java-enum-members-or-equals

        return new Pair<Integer, Integer>(offX,offY);
    }
}
