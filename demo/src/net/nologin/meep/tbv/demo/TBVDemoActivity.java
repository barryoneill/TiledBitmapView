/*
 *    TiledBitmapViewDemo - An Android application demonstrating the abilities of the TiledBitmapView library
 *    Copyright 2013 Barry O'Neill (http://meep.nologin.net/)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package net.nologin.meep.tbv.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import net.nologin.meep.tbv.TiledBitmapView;

/**
 * A simple demo activity to demonstrate how to interact with the TiledBitmapView, including registering a provider.
 */
public class TBVDemoActivity extends Activity {

    TiledBitmapView tiledBitmapView;
    Button btnBackToOrigin, btnAbout;
    ToggleButton btnToggleDebug, btnToggleProvider;
    DemoTileProvider demoProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        tiledBitmapView = (TiledBitmapView) findViewById(R.id.simpleTileView);
        btnBackToOrigin = (Button) findViewById(R.id.btn_backToOrigin);
        btnToggleDebug = (ToggleButton) findViewById(R.id.btn_debug_toggle);
        btnToggleProvider = (ToggleButton) findViewById(R.id.btn_provider_toggle);
        btnAbout = (Button) findViewById(R.id.btn_about);

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
                if (tiledBitmapView.isDebugEnabled()) {
                    // debug incurs a performance hit, perhaps better not to expose it to end users in your app
                    Toast.makeText(TBVDemoActivity.this, R.string.debug_warning, Toast.LENGTH_SHORT).show();
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
                if (btnToggleProvider.isChecked()) {
                    tiledBitmapView.registerProvider(demoProvider);
                } else {
                    tiledBitmapView.registerDefaultProvider();
                }
            }
        });

        // attach about dialog
        btnAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context ctx = TBVDemoActivity.this;

                final TextView message = new TextView(ctx);

                final SpannableString s =
                        new SpannableString(ctx.getText(R.string.dialog_about_text));
                Linkify.addLinks(s, Linkify.WEB_URLS);
                message.setText(s);
                message.setMovementMethod(LinkMovementMethod.getInstance());

                new AlertDialog.Builder(ctx)
                        .setTitle(R.string.dialog_about_title)
                        .setCancelable(true)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setPositiveButton(android.R.string.ok, null)
                        .setView(message)
                        .create().show();
            }
        });

    }

}


