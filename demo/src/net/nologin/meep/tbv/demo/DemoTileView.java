package net.nologin.meep.tbv.demo;

import android.content.Context;
import android.util.AttributeSet;
import net.nologin.meep.tbv.TiledBitmapView;


public class DemoTileView extends TiledBitmapView {

    public DemoTileView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // TODO: enable registration without extending
        DemoTileProvider sp = (DemoTileProvider)getTileProvider();
        if(sp == null){
            sp = new DemoTileProvider(getContext());
            setTileProvider(sp);
        }

    }



}
