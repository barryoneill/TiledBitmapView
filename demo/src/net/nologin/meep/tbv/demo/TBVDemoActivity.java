package net.nologin.meep.tbv.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;
import net.nologin.meep.tbv.TiledBitmapView;

/**
 * A simple demo activity to demonstrate how to interact with the TiledBitmapView, including registering a provider.
 */
public class TBVDemoActivity extends Activity {

    TiledBitmapView tiledBitmapView;
    Button btnBackToOrigin;
    ToggleButton btnToggleDebug, btnToggleProvider;
    DemoTileProvider demoProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        tiledBitmapView = (TiledBitmapView)findViewById(R.id.simpleTileView);
        btnBackToOrigin = (Button)findViewById(R.id.btn_backToOrigin);
        btnToggleDebug = (ToggleButton)findViewById(R.id.btn_debug_toggle);
        btnToggleProvider = (ToggleButton)findViewById(R.id.btn_provider_toggle);

        // attach our 'Jump to Origin' button to the TBV method that centers the grid around tile 0,0
        btnBackToOrigin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tiledBitmapView.moveToOriginTile();
            }
        });

        // Setup the toggle button that enables and disables the render of debug information on the TBV
        btnToggleDebug.setChecked(tiledBitmapView.isDebugEnabled());
        btnToggleDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tiledBitmapView.toggleDebugEnabled();
                if(tiledBitmapView.isDebugEnabled()){
                    // debug incurs a performance hit, perhaps better not to expose it to end users in your app
                    Toast.makeText(TBVDemoActivity.this, R.string.debug_warning ,Toast.LENGTH_SHORT).show();
                }
            }
        });

        // register our 'demo' tile provider
        demoProvider = new DemoTileProvider(this);
        tiledBitmapView.registerProvider(demoProvider);
        btnToggleProvider.setChecked(true);

        // and allow toggling between it and the dummy provider that comes with the TBV by default
        btnToggleProvider.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(btnToggleProvider.isChecked()){
                    tiledBitmapView.registerProvider(demoProvider);
                }
                else{
                    tiledBitmapView.registerDefaultProvider();
                }
            }
        });


    }


}
