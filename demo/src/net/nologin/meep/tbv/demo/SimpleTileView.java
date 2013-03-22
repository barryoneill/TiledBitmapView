package net.nologin.meep.tbv.demo;

import android.content.Context;
import android.util.AttributeSet;
import net.nologin.meep.tbv.TiledBitmapView;


public class SimpleTileView extends TiledBitmapView {

    public SimpleTileView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // TODO: enable registration without extending
        SimpleTileProvider sp = (SimpleTileProvider)getTileProvider();
        if(sp == null){
            sp = new SimpleTileProvider(getContext());
            setTileProvider(sp);
        }

    }



}
