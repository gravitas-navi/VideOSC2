/*
 * Copyright (c) 2014 Rex St. John on behalf of AirPair.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYEND HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.videosc2.activities;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;

import net.videosc2.R;
import net.videosc2.adapters.ToolsMenuAdapter;
import net.videosc2.fragments.VideOSCBaseFragment;
import net.videosc2.fragments.VideOSCCameraFragment;
import net.videosc2.utilities.VideOSCUIHelpers;
import net.videosc2.utilities.enums.GestureModes;
import net.videosc2.utilities.enums.InteractionModes;
import net.videosc2.utilities.enums.RGBModes;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stefan Nussbaumer on 2017-03-15.
 */
public class VideOSCMainActivity extends AppCompatActivity
		implements VideOSCBaseFragment.OnFragmentInteractionListener {

	static final String TAG = "VideOSCMainActivity";

	View camView;
	public static Point dimensions;
	private DrawerLayout toolsDrawerLayout;
	private FrameLayout mainFrame;
	private ActionBarDrawerToggle drawerToggle;
	protected ArrayList<View> uiElements = new ArrayList<>();

	// is device currently sending OSC?
	public boolean isPlaying = false;
	// is flashlight on?
	public boolean isTorchOn = false;
	// don't create more than one color mode panel
	private boolean isColorModePanelOpen = false;

	public Fragment cameraPreview;
	Camera camera;

	private View indicatorPanel;

	// the current color mode
	public Enum colorMode = RGBModes.RGB;
	// the current interaction mode
	public Enum interactionMode = InteractionModes.BASIC;
	// the current gesture mode
	public Enum gestureMode = GestureModes.SWAP;

	/**
	 * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");

		final boolean hasTorch = VideOSCUIHelpers.hasTorch();
		final LayoutInflater inflater = getLayoutInflater();

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);

		final FragmentManager fragmentManager = getFragmentManager();
		if (findViewById(R.id.camera_preview) != null) {
			camView = findViewById(R.id.camera_preview);

			if (savedInstanceState != null) return;
			cameraPreview = new VideOSCCameraFragment();

			fragmentManager.beginTransaction()
					.replace(R.id.camera_preview, cameraPreview, "CamPreview")
					.commit();
		}

		indicatorPanel = inflater.inflate(R.layout.indicator_panel, (FrameLayout) camView, true);

		// does the device have an inbuilt flash light?
		int drawer_icons_id = hasTorch ? R.array.drawer_icons : R.array.drawer_icons_no_torch;

		TypedArray tools = getResources().obtainTypedArray(drawer_icons_id);
//		Log.d(TAG, "tools: " + tools.toString());
		toolsDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		toolsDrawerLayout.setScrimColor(Color.TRANSPARENT);

		// FIXME: touches seem to get swallowed by the DrawerLayout first
		final ListView toolsDrawerList = (ListView) findViewById(R.id.drawer);

		List<BitmapDrawable> toolsList = new ArrayList<>();
		for (int i = 0; i < tools.length(); i++) {
			toolsList.add((BitmapDrawable) tools.getDrawable(i));
		}

		toolsDrawerList.setAdapter(new ToolsMenuAdapter(this, R.layout.drawer_item, R.id.tool, toolsList));
		tools.recycle();

		toolsDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				BitmapDrawable img;
				final ImageView indicatorView;
				final ImageView imgView = (ImageView) view.findViewById(R.id.tool);
				Context context = getApplicationContext();
				// we can not use 'cameraPreview' to retrieve the 'mCamera' object
				VideOSCCameraFragment camPreview = (VideOSCCameraFragment) fragmentManager.findFragmentByTag("CamPreview");
				camera = camPreview.mCamera;

				if (i == 0) {
					indicatorView = (ImageView) findViewById(R.id.indicator_osc);
					isPlaying = !isPlaying;
					if (isPlaying) {
						// TODO: stop sending OSC
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.stop);
						indicatorView.setImageResource(R.drawable.osc_playing);
					} else {
						// TODO: start sending OSC
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.start);
						indicatorView.setImageResource(R.drawable.osc_paused);
					}
					imgView.setImageDrawable(img);
				} else if (i == 1 && hasTorch) {
					if (camera != null) {
						Camera.Parameters cParameters = camera.getParameters();
						String flashMode = cParameters.getFlashMode();
						indicatorView = (ImageView) findViewById(R.id.torch_status_indicator);
						isTorchOn = !isTorchOn;
						if (!flashMode.equals("torch")) {
							cParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.light_on);
							indicatorView.setImageResource(R.drawable.light_on_indicator);
						} else {
							cParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.light);
							indicatorView.setImageResource(R.drawable.light_off_indicator);
						}
						camera.setParameters(cParameters);
						imgView.setImageDrawable(img);
					}
				} else if ((i == 2 && hasTorch) || i == 1) {
					if (!isColorModePanelOpen) {
						int y = (int) view.getY();

						AnimatorSet in_animator = (AnimatorSet) AnimatorInflater.loadAnimator(getApplicationContext(), R.animator.fade_in);
						final View modePanel = inflater.inflate(R.layout.color_mode_panel, (FrameLayout) camView, true);
						in_animator.setTarget(modePanel);
						in_animator.start();

						indicatorView = (ImageView) findViewById(R.id.indicator_color);
						isColorModePanelOpen = true;
						final View modePanelInner = modePanel.findViewById(R.id.color_mode_panel);
						VideOSCUIHelpers.setMargins(modePanelInner, 0, y, 60, 0);

						for (int k = 0; k < ((ViewGroup) modePanelInner).getChildCount(); k++) {
							final Context iContext = context;

							View button = ((ViewGroup) modePanelInner).getChildAt(k);
							button.setFocusableInTouchMode(true);
							button.setOnTouchListener(new View.OnTouchListener() {
								@Override
								public boolean onTouch(View view, MotionEvent motionEvent) {
									if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
										switch (view.getId()) {
											case R.id.mode_rgb:
												Log.d(TAG, "rgb");
												imgView.setImageDrawable(ContextCompat.getDrawable(iContext, R.drawable.rgb));
												indicatorView.setImageResource(R.drawable.rgb_indicator);
												break;
											case R.id.mode_rgb_inv:
												Log.d(TAG, "rgb inverted");
												imgView.setImageDrawable(ContextCompat.getDrawable(iContext, R.drawable.rgb_inv));
												indicatorView.setImageResource(R.drawable.rgb_inv_indicator);
												break;
											case R.id.mode_r:
												Log.d(TAG, "red");
												imgView.setImageDrawable(ContextCompat.getDrawable(iContext, R.drawable.r));
												break;
											case R.id.mode_g:
												Log.d(TAG, "green");
												imgView.setImageDrawable(ContextCompat.getDrawable(iContext, R.drawable.g));
												break;
											case R.id.mode_b:
												Log.d(TAG, "blue");
												imgView.setImageDrawable(ContextCompat.getDrawable(iContext, R.drawable.b));
												break;
											default:
												imgView.setImageDrawable(ContextCompat.getDrawable(iContext, R.drawable.rgb));
										}
//										toolsDrawerLayout.openDrawer(Gravity.END);
										((ViewGroup) modePanelInner.getParent()).removeView(modePanelInner);
										isColorModePanelOpen = false;
									}
									return false;
								}
							});
						}
					}
				} else if ((i == 3 && hasTorch) || i == 2) {
					Log.d(TAG, "set interaction mode");
					indicatorView = (ImageView) findViewById(R.id.indicator_interaction);
					if (interactionMode.equals(InteractionModes.BASIC)) {
						interactionMode = InteractionModes.SINGLE_PIXEL;
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.interactionplus);
						indicatorView.setImageResource(R.drawable.interaction_plus_indicator);
					} else if (interactionMode.equals(InteractionModes.SINGLE_PIXEL)) {
						interactionMode = InteractionModes.BASIC;
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.interaction);
						indicatorView.setImageResource(R.drawable.interaction_none_indicator);
					} else {
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.interaction);
					}
					imgView.setImageDrawable(img);
				} else if ((i == 4 && hasTorch) || i == 3) {
					Log.d(TAG, "framerate, calculation period info");
					// TODO: implement panel displaying the current framerate, calculation period
				} else if ((i == 5 && hasTorch) || i == 4) {
					Log.d(TAG, "settings");
				}
				toolsDrawerLayout.closeDrawer(Gravity.END);
			}
		});
		toolsDrawerLayout.openDrawer(Gravity.END);

//		drawerToggle = setupDrawerToggle();
//		mDrawer.addDrawerListener(drawerToggle);

		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		// width/height of the screen
		dimensions = new Point(dm.widthPixels, dm.heightPixels);
	}

/*
	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		return !(isColorModePanelOpen && event.getAction() == MotionEvent.ACTION_UP) && super.dispatchTouchEvent(event);
	}
*/

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		Log.d(TAG, "onContentChanged");
	}

	// There are 2 signatures and only `onPostCreate(Bundle state)` shows the hamburger icon.
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		ImageButton menuButton = (ImageButton) findViewById(R.id.show_menu);
		menuButton.bringToFront();
		menuButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (toolsDrawerLayout.isDrawerOpen(Gravity.END)) {
					toolsDrawerLayout.closeDrawer(Gravity.END);
				} else if (!toolsDrawerLayout.isDrawerOpen(Gravity.END)) {
					toolsDrawerLayout.openDrawer(Gravity.END);
				}
			}
		});

		View indicatorPanelInner = indicatorPanel.findViewById(R.id.indicator_panel);
		indicatorPanelInner.bringToFront();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//			Log.d(TAG, "Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT");
			camView.setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
							| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN
							| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		} else {
//			Log.d(TAG, "else branch");
			camView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
		}
	}

/*
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.d(TAG, "menu item: " + item.getItemId());
		switch(item.getItemId()) {
			case android.R.id.home:
				mDrawer.openDrawer(GravityCompat.END);
				return true;
		}

		return drawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
	}
*/

/*
	private ActionBarDrawerToggle setupDrawerToggle() {
		// NOTE: Make sure you pass in a valid toolbar reference.
		// ActionBarDrawToggle() does not require it
		// and will not render the hamburger icon without it.
		return new ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
	}
*/

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Pass any configuration change to the drawer toggles
//		drawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public void onFragmentInteraction(Uri uri) {

	}

	@Override
	public void onFragmentInteraction(String id) {

	}

	@Override
	public void onFragmentInteraction(int actionId) {

	}
}
