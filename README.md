# TiledBitmapView #

## What Is TiledBitmapView? ##
This Android library provides a custom 
[SurfaceView](http://developer.android.com/reference/android/view/SurfaceView.html) called **TiledBitmapView** whose contents are loaded tilewise from a provider - similar to the way 
map applications such as Google Maps load their content dynamically as the user scrolls around 
(although this library has absolutely nothing to do with mapping).  

It supports devices running Android **2.3.3** (*API Level: 10, GINGERBREAD_MR1*).

The goal of this library is to be as lightweight and simple as possible, and has no dependencies of
its own.  It's not particularly feature heavy, it simply supports a scrollable view with dynamic 
content.   

### Examples ###

- Demo App ([Play Store](https://play.google.com/store/apps/details?id=net.nologin.meep.tbv.demo) 
	| [GitHub](https://github.com/barryoneill/TiledBitmapView/tree/master/demo))

	The `demo` subdirectory of this repository contains a demo android app, which uses a data 
	source with 25 tiles making up a repeating larger image of an area of stones.  The screenshot 
	below shows the demo with debug enabled, so you can see the tiles (click for bigger):

	[![click for bigger](http://barryoneill.github.io/TiledBitmapView/ghpages_static/screenshots/demoprov_withdebug_400.png)](http://barryoneill.github.io/TiledBitmapView/ghpages_static/screenshots/demoprov_withdebug_1280.png)
	
		
- Wolfram CA ([Play Store](https://play.google.com/store/apps/details?id=net.nologin.meep.ca) 
	| [GitHub](https://github.com/barryoneill/WolframCA) )  
	
	This app is used for viewing the successive generations of a 
	[1-dimensional CA](http://mathworld.wolfram.com/ElementaryCellularAutomaton.html). As the 
	user scrolls down, more tiles with successive generations are generated on the fly and rendered.
	The screenshot below also has debug enabled (click for bigger):

	[![click for bigger](http://barryoneill.github.io/TiledBitmapView/ghpages_static/screenshots/wolframca_withdebug_400.png)](http://barryoneill.github.io/TiledBitmapView/ghpages_static/screenshots/wolframca_withdebug_1280.png)

## Using TiledBitmapView ##

### Adding the library as a project dependency ###

For [Android Studio](http://developer.android.com/sdk/installing/studio.html) (which is based on 
[Intellij IDEA](http://www.jetbrains.com/idea/)) this consists of the following steps (at the
time of writing anyway):

* Go to *File* -> *Project Structure...* and select category *Modules* on the left. 
* Above the module list (in which your android project is currently present), click the green **+** 
	and select *Import Module*
* Browse to and select the `library` subdirectory of this repository.  
* In the wizard that starts, choose *Create Module From Existing Sources*.  Step through the 
	wizard, acceping the defaults to setup the module.
* Back in the *Project Structure* -> *Modules* view, the additional library module will now be 
	present.  Select your android app's module (not the library) and click the *Dependencies* tab.  
* Click the small green **+** to the right, and select *Module Dependency...*.  In the dialog 
	that appears, select the library module.  Done!  

If you're using Gradle, check out the relevant 
[plugin docs](http://tools.android.com/tech-docs/new-build-system/user-guide).  If you're using 
Eclipse, do yourself a favour and install 
[Android Studio](http://developer.android.com/sdk/installing/studio.html). :trollface:

### Adding the view to your application ###

Simply add the following XML to the target layout file (with an ID and layout parameters that 
suit your design):

```xml    
    <net.nologin.meep.tbv.TiledBitmapView
        android:id="@+id/MyTiledView"
        android:layout_width="fill_parent"
        android:layout_height="fill_parent"/>
```

By default, the TiledBitmapView will produce dummy content until you're ready to implement your 
own provider.  To check that the library is working, simply deploy your application to your 
device/emulator and the view should render content similar to the following (click for bigger):   

[![click for bigger](http://barryoneill.github.io/TiledBitmapView/ghpages_static/screenshots/genericprov_nodebug_400.png)](http://barryoneill.github.io/TiledBitmapView/ghpages_static/screenshots/genericprov_nodebug_1280.png)

## Providing your own tiles ##

### Implementing TileProvider ###

The TiledBitmapView fetches the tiles to render from an implementation of the `TileProvider` 
interface. By default, a very crude implementation (`GenericTileProvider`) is used, which generates 
tiles with that hideous yellow circle on a gray background, that you see above.

Before writing your own implementation of this interface: 

* Carefully read the [TileProvider JavaDoc](http://barryoneill.github.io/TiledBitmapView/ghpages_static/javadoc/index.html?net/nologin/meep/tbv/TileProvider.html) 
	for information on the lifecycle of a provider and the role of each of the interface's methods.   
* Look at the [source for the DemoTileProvider](https://github.com/barryoneill/TiledBitmapView/blob/master/demo/src/net/nologin/meep/tbv/demo/DemoTileProvider.java) 
	to see an example of a provider that generates the desired tiles asynchronously, resulting
	in a more responsive application (Tiles appear as they become ready) 

### Using your TileProvider ###

Now the `<TiledBitmapView/>` has been added to the layout as described above, we use `findViewById(..)` 
in the Activity to get a reference to it, and call  `registerProvider(..)`  to register our custom 
provider:

```java

@Override
protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    setContentView(R.layout.your_layout);

	...

    TiledBitmapView tbv = (TiledBitmapView)findViewById(R.id.MyTiledView);
    tbv.registerProvider(new YourProvider(this));

	...

}

```

You can call `setDebugEnabled(true)` on the view to render the tile grid, the IDs of the tiles 
and a small info box to give you invaluable help as you write your provider.  It can impact 
performance, so perhaps don't make the feature available to your end user. 


