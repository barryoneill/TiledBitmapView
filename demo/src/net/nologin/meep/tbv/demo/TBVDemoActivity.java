package net.nologin.meep.tbv.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ToggleButton;

public class TBVDemoActivity extends Activity {

    SimpleTileView stv;
    Button btnBackToOrigin;
    ToggleButton btnToggleDebug;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        stv = (SimpleTileView)findViewById(R.id.simpleTileView);

        btnBackToOrigin = (Button)findViewById(R.id.btn_backToOrigin);
        btnToggleDebug = (ToggleButton)findViewById(R.id.btn_debug_toggle);


        btnBackToOrigin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stv.jumpToOriginTile();
            }
        });


        btnToggleDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stv.toggleDebugEnabled();
            }
        });

        // default to on, TODO: impl view state saving in SimpleTileView
        btnToggleDebug.setChecked(true);
        stv.setDebugEnabled(true);

    }


}
