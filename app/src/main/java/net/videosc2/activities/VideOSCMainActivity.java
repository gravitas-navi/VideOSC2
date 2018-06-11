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

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v13.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
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
import android.widget.ListView;
import android.widget.Switch;

import net.videosc2.R;
import net.videosc2.VideOSCApplication;
import net.videosc2.adapters.ToolsMenuAdapter;
import net.videosc2.db.SettingsContract;
import net.videosc2.db.SettingsDBHelper;
import net.videosc2.fragments.VideOSCBaseFragment;
import net.videosc2.fragments.VideOSCCameraFragment;
import net.videosc2.fragments.VideOSCMultiSliderFragment;
import net.videosc2.fragments.VideOSCSettingsFragment;
import net.videosc2.utilities.VideOSCDialogHelper;
import net.videosc2.utilities.VideOSCUIHelpers;
import net.videosc2.utilities.enums.GestureModes;
import net.videosc2.utilities.enums.InteractionModes;
import net.videosc2.utilities.enums.RGBModes;
import net.videosc2.utilities.enums.RGBToolbarStatus;
import net.videosc2.views.SliderBar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Stefan Nussbaumer on 2017-03-15.
 */
public class VideOSCMainActivity extends AppCompatActivity
		implements VideOSCBaseFragment.OnFragmentInteractionListener/*, VideOSCCameraFragment.OnCompleteCameraFragmentListener*/ {

	static final String TAG = "VideOSCMainActivity";

	private View mCamView;
	private Point mDimensions;
	private DrawerLayout mToolsDrawerLayout;

	// is the multisliderview currently visible?
	private boolean isMultiSliderVisible = false;

	private Fragment mCameraPreview;
	private Fragment mMultiSliderView;
	// ID of currently opened camera
	public static int backsideCameraId;
	public static int frontsideCameraId;
	public static int currentCameraID;

	private View mIndicatorPanel;

	// the global application, used to exchange various temporary data
	private VideOSCApplication mApp;

	// the current color mode
	// RGB or RGB inverted?
	// set to true when isRGBPositive changes
	private boolean rgbHasChanged = false;
	// the current gesture mode
	public Enum gestureMode = GestureModes.SWAP;

	// ListView for the tools drawer
	private List<BitmapDrawable> mToolsList = new ArrayList<>();
	private ListView mToolsDrawerList;
	private HashMap<Integer, Integer> mToolsDrawerListState = new HashMap<>();
	// toolbar status
	public Enum mColorModeToolsDrawer = RGBToolbarStatus.RGB;

	// pop-out menu for setting color mode
	private ViewGroup mModePanel;
	// panel for displaying frame rate, calculation period
	private ViewGroup mFrameRateCalculationPanel;
	// the settings list
	private ViewGroup settingsList;
	// multislider view
//	private ViewGroup mMultiSliderView;

	// drawer menu
	private int START_STOP, TORCH, COLOR_MODE, INTERACTION, SELECT_CAM, INFO, SETTINGS, QUIT;

	// settings, retrieved from sqlite db
	public SettingsDBHelper mDbHelper;

	Intent starterIntent;
	private static final int CODE_WRITE_SETTINGS_PERMISSION = 111;
	private static final int PERMISSION_REQUEST_CAMERA = 1;

	private View mDecorView;
	private Resources mRes;

	/**
	 * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mRes = getResources();
		// immersive fullscreen
		mDecorView = getWindow().getDecorView();

		VideOSCUIHelpers.resetSystemUIState(mDecorView);

		starterIntent = getIntent();
//		requestSettingsPermission();

		mApp = (VideOSCApplication) getApplicationContext();
		backsideCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
		if (VideOSCUIHelpers.hasFrontsideCamera()) {
			frontsideCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
		}
		mApp.setCurrentCameraId(backsideCameraId);

		final float scale = getResources().getDisplayMetrics().density;
		mApp.setScreenDensity(scale);

		// keep db access open through the app's lifetime
		mDbHelper = new SettingsDBHelper(this);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final String[] settingsFields = new String[]{
				SettingsContract.AddressSettingsEntry.IP_ADDRESS,
				SettingsContract.AddressSettingsEntry.PORT
		};

		Cursor cursor = db.query(
				SettingsContract.AddressSettingsEntry.TABLE_NAME,
				settingsFields,
				null,
				null,
				null,
				null,
				null
		);

		// for now we only have one address stored in the addresses table
		// protocol will be UDP
		if (cursor.moveToFirst()) {
			mApp.mOscHelper.setBroadcastAddr(
					cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.AddressSettingsEntry.IP_ADDRESS)),
					cursor.getInt(cursor.getColumnIndexOrThrow(SettingsContract.AddressSettingsEntry.PORT))
			);
		}

		cursor.close();

		final LayoutInflater inflater = getLayoutInflater();

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		View mainLayout = inflater.inflate(R.layout.activity_main, null);
		setContentView(mainLayout);

		mCamView = mainLayout.findViewById(R.id.camera_preview);

		// maybe needed on devices other than Google Nexus?
		// startActivity(new Intent(this, RefreshScreen.class));

		final FragmentManager fragmentManager = getFragmentManager();

		if (savedInstanceState != null) return;

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			checkCamera();
			mCameraPreview = new VideOSCCameraFragment();
			fragmentManager.beginTransaction()
					.replace(R.id.camera_preview, mCameraPreview, "CamPreview")
					.commit();
			buildUI();
		} else {
			requestCameraPermission();
		}
	}

	private void buildUI() {
		final LayoutInflater inflater = getLayoutInflater();
		final FragmentManager fragmentManager = getFragmentManager();
		final Activity activity = this;
		final Context context = getApplicationContext();

		// does the device have an inbuilt flashlight? frontside camera? flashlight but no frontside camera
		// frontside camer but no flashlight?...
		int drawerIconsIds = mApp.getHasTorch() ?
				VideOSCUIHelpers.hasFrontsideCamera() ? R.array.drawer_icons : R.array.drawer_icons_no_frontside_cam :
				VideOSCUIHelpers.hasFrontsideCamera() ? R.array.drawer_icons_no_torch : R.array.drawer_icons_no_torch_no_frontside_cam;

		TypedArray tools = getResources().obtainTypedArray(drawerIconsIds);
//		Log.d(TAG, "tools: " + tools.getClass());
		mToolsDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mToolsDrawerLayout.setScrimColor(Color.TRANSPARENT);

		mToolsDrawerList = (ListView) findViewById(R.id.drawer);

		for (int i = 0; i < tools.length(); i++) {
			mToolsList.add((BitmapDrawable) tools.getDrawable(i));
		}

		mToolsDrawerList.setAdapter(new ToolsMenuAdapter(this, R.layout.drawer_item, R.id.tool, mToolsList));
		tools.recycle();

		mModePanel = (ViewGroup) inflater.inflate(R.layout.color_mode_panel, (FrameLayout) mCamView, false);
		Drawable shape = mRes.getDrawable(R.drawable.black_rounded_rect);
		mModePanel.setBackground(shape);
//		mMultiSliderView = (ViewGroup) inflater.inflate(R.layout.multislider_view, (FrameLayout) mCamView, false);
		mFrameRateCalculationPanel = (ViewGroup) inflater.inflate(R.layout.framerate_calculation_indicator, (FrameLayout) mCamView, false);
		mFrameRateCalculationPanel.setBackground(shape);

		// get keys for toolsDrawer
		HashMap<String, Integer> toolsDrawerKeys = toolsDrawerKeys();
		START_STOP = toolsDrawerKeys.get("startStop");
		if (toolsDrawerKeys.containsKey("torch"))
			TORCH = toolsDrawerKeys.get("torch");
		COLOR_MODE = toolsDrawerKeys.get("modeSelect");
		INTERACTION = toolsDrawerKeys.get("mInteractionMode");
		if (toolsDrawerKeys.containsKey("camSelect"))
			SELECT_CAM = toolsDrawerKeys.get("camSelect");
		INFO = toolsDrawerKeys.get("info");
		SETTINGS = toolsDrawerKeys.get("prefs");
		QUIT = toolsDrawerKeys.get("quit");

		mToolsDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, final View view, int i, long l) {
				BitmapDrawable img;
				final ImageView oscIndicatorView = (ImageView) findViewById(R.id.indicator_osc);
				final ImageView rgbModeIndicator = (ImageView) findViewById(R.id.indicator_color);
				final ImageView interactionModeIndicator = (ImageView) findViewById(R.id.indicator_interaction);
				final ImageView cameraIndicator = (ImageView) findViewById(R.id.indicator_camera);
				final ImageView torchIndicatorView = (ImageView) findViewById(R.id.torch_status_indicator);
				final ImageView imgView = (ImageView) view.findViewById(R.id.tool);
				// cameraFragment provides all instance methods of the camera preview
				// no reflections needed
				final VideOSCCameraFragment cameraFragment = (VideOSCCameraFragment) fragmentManager.findFragmentByTag("CamPreview");
				Camera camera = cameraFragment.mCamera;
				Camera.Parameters cameraParameters;


				if (i == START_STOP) {
					closeColorModePanel();
					if (!mApp.getCameraOSCisPlaying()) {
						mApp.setCameraOSCisPlaying(true);
						mToolsDrawerListState.put(START_STOP, R.drawable.stop);
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.stop);
						oscIndicatorView.setImageResource(R.drawable.osc_playing);
					} else {
						mApp.setCameraOSCisPlaying(false);
						mToolsDrawerListState.put(START_STOP, R.drawable.start);
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.start);
						oscIndicatorView.setImageResource(R.drawable.osc_paused);
					}
					imgView.setImageDrawable(img);
				} else if (i == TORCH) {
					closeColorModePanel();
					cameraParameters = camera.getParameters();
					if (mApp.getCurrentCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK) {
						String flashMode = cameraParameters.getFlashMode();
						mApp.setIsTorchOn(!mApp.getIsTorchOn());
						if (!flashMode.equals("torch")) {
							cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
							mToolsDrawerListState.put(TORCH, R.drawable.light_on);
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.light_on);
							torchIndicatorView.setImageResource(R.drawable.light_on_indicator);
						} else {
							cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
							mToolsDrawerListState.put(TORCH, R.drawable.light);
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.light);
							torchIndicatorView.setImageResource(R.drawable.light_off_indicator);
						}
						camera.setParameters(cameraParameters);
						imgView.setImageDrawable(img);
					}

				} else if (i == COLOR_MODE) {
					if (!mApp.getIsColorModePanelOpen()) {
						int y = (int) view.getY();

						VideOSCUIHelpers.setTransitionAnimation(mModePanel);
						mApp.setIsColorModePanelOpen(VideOSCUIHelpers.addView(mModePanel, (FrameLayout) mCamView));

						if (rgbHasChanged) {
							ImageView red = (ImageView) findViewById(R.id.mode_r);
							ImageView green = (ImageView) findViewById(R.id.mode_g);
							ImageView blue = (ImageView) findViewById(R.id.mode_b);
							int redRes = mApp.getIsRGBPositive() ? R.drawable.r : R.drawable.r_inv;
							int greenRes = mApp.getIsRGBPositive() ? R.drawable.g : R.drawable.g_inv;
							int blueRes = mApp.getIsRGBPositive() ? R.drawable.b : R.drawable.b_inv;
							red.setImageResource(redRes);
							green.setImageResource(greenRes);
							blue.setImageResource(blueRes);
						}

						final View modePanelInner = mModePanel.findViewById(R.id.color_mode_panel);

						VideOSCUIHelpers.setMargins(modePanelInner, 0, y, 60, 0);

						for (int k = 0; k < ((ViewGroup) modePanelInner).getChildCount(); k++) {
							View button = ((ViewGroup) modePanelInner).getChildAt(k);
							button.setFocusableInTouchMode(true);
							button.setOnTouchListener(new View.OnTouchListener() {
								@Override
								public boolean onTouch(View view, MotionEvent motionEvent) {
									view.performClick();
									if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
										switch (view.getId()) {
											case R.id.mode_rgb:
												mApp.setColorMode(RGBModes.RGB);
												if (!mApp.getIsRGBPositive()) {
													mApp.setIsRGBPositive(true);
													rgbHasChanged = true;
												}
												mToolsDrawerListState.put(COLOR_MODE, R.drawable.rgb);
												imgView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.rgb));
												rgbModeIndicator.setImageResource(R.drawable.rgb_indicator);
												mColorModeToolsDrawer = RGBToolbarStatus.RGB;
												break;
											case R.id.mode_rgb_inv:
												mApp.setColorMode(RGBModes.RGB);
												if (mApp.getIsRGBPositive()) {
													mApp.setIsRGBPositive(false);
													rgbHasChanged = true;
												}
												mToolsDrawerListState.put(COLOR_MODE, R.drawable.rgb_inv);
												imgView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.rgb_inv));
												rgbModeIndicator.setImageResource(R.drawable.rgb_inv_indicator);
												mColorModeToolsDrawer = RGBToolbarStatus.RGB_INV;
												break;
											case R.id.mode_r:
												mApp.setColorMode(RGBModes.R);
												if (mApp.getIsRGBPositive()) {
													mToolsDrawerListState.put(COLOR_MODE, R.drawable.r);
													imgView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.r));
													mColorModeToolsDrawer = RGBToolbarStatus.R;
												} else {
													mToolsDrawerListState.put(COLOR_MODE, R.drawable.r_inv);
													imgView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.r_inv));
													mColorModeToolsDrawer = RGBToolbarStatus.R_INV;
												}
												break;
											case R.id.mode_g:
//												Log.d(TAG, "green");
												mApp.setColorMode(RGBModes.G);
												if (mApp.getIsRGBPositive()) {
													mToolsDrawerListState.put(COLOR_MODE, R.drawable.g);
													imgView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.g));
													mColorModeToolsDrawer = RGBToolbarStatus.G;
												} else {
													mToolsDrawerListState.put(COLOR_MODE, R.drawable.g_inv);
													imgView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.g_inv));
													mColorModeToolsDrawer = RGBToolbarStatus.G_INV;
												}
												break;
											case R.id.mode_b:
//												Log.d(TAG, "blue");
												mApp.setColorMode(RGBModes.B);
												if (mApp.getIsRGBPositive()) {
													mToolsDrawerListState.put(COLOR_MODE, R.drawable.b);
													imgView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.b));
													mColorModeToolsDrawer = RGBToolbarStatus.B;
												} else {
													mToolsDrawerListState.put(COLOR_MODE, R.drawable.b_inv);
													imgView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.b_inv));
													mColorModeToolsDrawer = RGBToolbarStatus.B_INV;
												}
												break;
											default:
												mApp.setColorMode(RGBModes.RGB);
												mToolsDrawerListState.put(COLOR_MODE, R.drawable.rgb);
												imgView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.rgb));
												mColorModeToolsDrawer = RGBToolbarStatus.RGB;
										}
										view.clearFocus();
										mApp.setIsColorModePanelOpen(VideOSCUIHelpers.removeView(mModePanel, (FrameLayout) mCamView));
									}
									return false;
								}
							});
						}
					}
				} else if (i == INTERACTION) {
					closeColorModePanel();
					if (mApp.getInteractionMode().equals(InteractionModes.BASIC)) {
						mApp.setInteractionMode(InteractionModes.SINGLE_PIXEL);
						mToolsDrawerListState.put(INTERACTION, R.drawable.interactionplus);
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.interactionplus);
						interactionModeIndicator.setImageResource(R.drawable.interaction_plus_indicator);
						/*mMultiSliderView = new VideOSCMultiSliderFragment();
						fragmentManager.beginTransaction()
								.add(R.id.camera_preview, mMultiSliderView, "MultiSliderView")
								.commit();
						VideOSCMultiSliderFragment multiSliderView = (VideOSCMultiSliderFragment) fragmentManager.findFragmentByTag("MultiSliderView");
						Bundle msArgsBundle = new Bundle();
						ArrayList<Integer> testList = new ArrayList<>(Arrays.asList(2, 4, 5, 7, 9, 12, 14, 15, 17, 19, 21, 22, 23, 30, 34, 37, 34));
						msArgsBundle.putIntegerArrayList("nums", testList);
						mMultiSliderView.setArguments(msArgsBundle);*/
					} else if (mApp.getInteractionMode().equals(InteractionModes.SINGLE_PIXEL)) {
						mApp.setInteractionMode(InteractionModes.BASIC);
						mToolsDrawerListState.put(INTERACTION, R.drawable.interaction);
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.interaction);
						interactionModeIndicator.setImageResource(R.drawable.interaction_none_indicator);
						if (mMultiSliderView != null)
							fragmentManager.beginTransaction().remove(mMultiSliderView).commit();
//						isMultiSliderVisible = VideOSCUIHelpers.removeView(mMultiSliderView, (FrameLayout) mCamView);
					} else {
						mToolsDrawerListState.put(INTERACTION, R.drawable.interaction);
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.interaction);
						if (mMultiSliderView != null)
							fragmentManager.beginTransaction().remove(mMultiSliderView).commit();
//						isMultiSliderVisible = VideOSCUIHelpers.removeView(mMultiSliderView, (FrameLayout) mCamView);
					}
					imgView.setImageDrawable(img);
				} else if (i == SELECT_CAM) {
					closeColorModePanel();
					cameraParameters = camera.getParameters();
					Log.d(TAG, "current camera id: " + mApp.getCurrentCameraId() + ", backside camera: " + backsideCameraId + ", frontside camera: " + frontsideCameraId);
					if (mApp.getCurrentCameraId() == backsideCameraId) {
						mApp.setCurrentCameraId(frontsideCameraId);
						mApp.setIsTorchOn(false);
						mToolsDrawerListState.put(SELECT_CAM, R.drawable.front_camera);
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.front_camera);
						if (mApp.getHasTorch() && cameraParameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_TORCH)) {
							cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
							BitmapDrawable torchImg = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.light);
							torchIndicatorView.setImageResource(R.drawable.light_off_indicator);
							ImageView torchSwitch = (ImageView) adapterView.getChildAt(TORCH);
							torchSwitch.setImageDrawable(torchImg);
						}
						cameraIndicator.setImageResource(R.drawable.indicator_camera_front);
					} else {
						mApp.setCurrentCameraId(backsideCameraId);
						mToolsDrawerListState.put(SELECT_CAM, R.drawable.back_camera);
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.back_camera);
						cameraIndicator.setImageResource(R.drawable.indicator_camera_back);
					}
					imgView.setImageDrawable(img);
					// invoke setting of new camera
					// camera ID should already have been set in currentCameraID
					cameraFragment.safeCameraOpenInView(mCamView);
				} else if (i == INFO) {
					closeColorModePanel();
					if (mApp.getIsFPSCalcPanelOpen())
						mApp.setIsFPSCalcPanelOpen(VideOSCUIHelpers.removeView(mFrameRateCalculationPanel, (FrameLayout) mCamView));
					else {
						VideOSCUIHelpers.setTransitionAnimation(mFrameRateCalculationPanel);
						mApp.setIsFPSCalcPanelOpen(VideOSCUIHelpers.addView(mFrameRateCalculationPanel, (FrameLayout) mCamView));
					}
				} else if (i == SETTINGS) {
//					Log.d(TAG, "settings");
					closeColorModePanel();
					mApp.setSettingsLevel(1);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
						mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
					}
					VideOSCSettingsFragment settings = new VideOSCSettingsFragment();
					fragmentManager.beginTransaction().add(R.id.camera_preview, settings, "settings selection").commit();
				} else if (i == QUIT) {
					VideOSCDialogHelper.showQuitDialog(activity);
				}
				mToolsDrawerLayout.closeDrawer(Gravity.END);
				// reset menu item background immediatly
				view.setBackgroundColor(0x00000000);
			}
		});
		if (mApp.getSettingsLevel() < 1)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				Log.d(TAG, "KitKat or higher");
				mCamView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
			} else
				mCamView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
		mToolsDrawerLayout.openDrawer(Gravity.END);

		mDimensions = getAbsoluteScreenSize();
		mApp.setDimensions(mDimensions);

		ImageButton menuButton = (ImageButton) findViewById(R.id.show_menu);
		menuButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!mToolsDrawerLayout.isDrawerOpen(Gravity.END))
					mToolsDrawerLayout.openDrawer(Gravity.END);
				closeColorModePanel();
			}
		});

		int indicatorXMLiD = mApp.getHasTorch() ? R.layout.indicator_panel : R.layout.indicator_panel_no_torch;
		mIndicatorPanel = inflater.inflate(indicatorXMLiD, (FrameLayout) mCamView, true);
//		ViewGroup drawOverlay = (ViewGroup) inflater.inflate(R.layout.tile_overlay_view, (FrameLayout) mCamView, true);
//		final ImageView rgbModeIndicator = (ImageView) findViewById(R.id.indicator_color);
	}

	private void closeColorModePanel() {
		if (mApp.getIsColorModePanelOpen())
			mApp.setIsColorModePanelOpen(VideOSCUIHelpers.removeView(mModePanel, (FrameLayout) mCamView));
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
	}

	// There are 2 signatures and only `onPostCreate(Bundle state)` shows the hamburger icon.
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		VideOSCUIHelpers.resetSystemUIState(mDecorView);
	}

	@Override
	public void onBackPressed() {
		View bg = findViewById(R.id.settings_background);
		short settingsLevel = mApp.getSettingsLevel();
		View networkSettingsDialog = findViewById(R.id.network_settings);
		View resolutionSettingsDialog = findViewById(R.id.resolution_settings);
		View sensorSettingsDialog = findViewById(R.id.sensor_settings);
		View debugSettingsDialog = findViewById(R.id.debug_settings);
		View about = findViewById(R.id.about);

		switch (settingsLevel) {
			case 1:
				FragmentManager manager = getFragmentManager();
				Fragment fragment = manager.findFragmentByTag("settings selection");
				VideOSCUIHelpers.removeView(findViewById(R.id.settings_selection), (FrameLayout) mCamView);
				VideOSCUIHelpers.removeView(bg, (FrameLayout) mCamView);
				VideOSCUIHelpers.resetSystemUIState(mDecorView);
				if (fragment != null)
					manager.beginTransaction().remove(fragment).commit();
				mToolsDrawerLayout.closeDrawer(Gravity.END);
				mApp.setSettingsLevel(0);
				break;
			case 2:
				findViewById(R.id.settings_selection_list).setVisibility(View.VISIBLE);
				if (networkSettingsDialog != null)
					VideOSCUIHelpers.removeView(networkSettingsDialog, (ViewGroup) bg);
				if (resolutionSettingsDialog != null)
					VideOSCUIHelpers.removeView(resolutionSettingsDialog, (ViewGroup) bg);
				if (sensorSettingsDialog != null)
					VideOSCUIHelpers.removeView(sensorSettingsDialog, (ViewGroup) bg);
				if (debugSettingsDialog != null)
					VideOSCUIHelpers.removeView(debugSettingsDialog, (ViewGroup) bg);
				if (about != null)
					VideOSCUIHelpers.removeView(about, (ViewGroup) bg);
				bg.setVisibility(View.VISIBLE);
				mApp.setSettingsLevel(1);
				break;
			case 3:
				View exposureSetters = findViewById(R.id.fix_exposure_button_layout);
				Switch exposureSwitch = (Switch) findViewById(R.id.fix_exposure_checkbox);
				// temporarily disable checked-change listener
				mApp.setBackPressed(true);
				exposureSwitch.setChecked(mApp.getExposureIsFixed());
				mApp.setBackPressed(false);
				if (exposureSetters != null)
					VideOSCUIHelpers.removeView(exposureSetters, (FrameLayout) mCamView);
				bg.setVisibility(View.VISIBLE);
				resolutionSettingsDialog.setVisibility(View.VISIBLE);
				mApp.setExposureIsFixed(false);
				mApp.setSettingsLevel(2);
				break;
			default:
				VideOSCDialogHelper.showQuitDialog(this);
		}
	}

	private HashMap<String, Integer> toolsDrawerKeys() {
		HashMap<String, Integer> toolsDrawerKeys = new HashMap<>();
		int index = 0;

		toolsDrawerKeys.put("startStop", index);
		if (mApp.getHasTorch())
			toolsDrawerKeys.put("torch", ++index);
		toolsDrawerKeys.put("modeSelect", ++index);
		toolsDrawerKeys.put("mInteractionMode", ++index);
		Log.d(TAG, "has frontside camera: " + VideOSCUIHelpers.hasFrontsideCamera());
		if (VideOSCUIHelpers.hasFrontsideCamera())
			toolsDrawerKeys.put("camSelect", ++index);
		toolsDrawerKeys.put("info", ++index);
		toolsDrawerKeys.put("prefs", ++index);
		toolsDrawerKeys.put("quit", ++index);

		return toolsDrawerKeys;
	}

	@Override
	public void onDestroy() {
		// stop sending OSC (probably not necessary)
		mApp.setCameraOSCisPlaying(false);
		// reset inverted colors
		mApp.setIsRGBPositive(true);
		// reset debug settings
		mApp.setPixelImageHidden(false);
		VideOSCApplication.setDebugPixelOsc(false);
		mApp.setHasExposureSettingBeenCancelled(false);
		mApp.setExposureIsFixed(false);
		mApp.setInteractionMode(InteractionModes.BASIC);
		mApp.setColorMode(RGBModes.RGB);
		// close db
		mDbHelper.close();
		super.onDestroy();
	}

	public Enum getColorModeToolsDrawer() {
		return this.mColorModeToolsDrawer;
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "main activity on pause");
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume called: " + mToolsDrawerList);
		// update tools drawer if some item's state has changed
		if (mToolsDrawerList != null) {
			for (Integer key : mToolsDrawerListState.keySet()) {
				mToolsList.set(key, (BitmapDrawable) ContextCompat.getDrawable(getApplicationContext(), mToolsDrawerListState.get(key)));
			}
			mToolsDrawerList.setAdapter(new ToolsMenuAdapter(this, R.layout.drawer_item, R.id.tool, mToolsList));
		}
	}

	/* private void requestSettingsPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (!Settings.System.canWrite(this)) {
				Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
				intent.setData(Uri.parse("package:" + getPackageName()));
				startActivityForResult(intent, VideOSCMainActivity.CODE_WRITE_SETTINGS_PERMISSION);
			}
		}
	} */

	private void requestCameraPermission() {
		if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
			Snackbar.make(mCamView, R.string.camera_permission_required,
					Snackbar.LENGTH_INDEFINITE).setAction(R.string.grant, new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					// Request the permission
					ActivityCompat.requestPermissions(VideOSCMainActivity.this,
							new String[]{Manifest.permission.CAMERA},
							PERMISSION_REQUEST_CAMERA);
				}
			}).show();
		} else {
			Snackbar.make(mCamView,
					R.string.camera_permission_not_available,
					Snackbar.LENGTH_SHORT).show();
			// Request the permission. The result will be received in onRequestPermissionResult().
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
					PERMISSION_REQUEST_CAMERA);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		Camera camera = null;

		if(requestCode == PERMISSION_REQUEST_CAMERA) {
			if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Log.d(TAG, "onRequestPermissionsResult()");
				Snackbar.make(
						mCamView,
						R.string.camera_permissions_granted,
						Snackbar.LENGTH_SHORT
				).show();

				// make sure permissions are granted before we touch the camera
				checkCamera();

				// the camera fragment overlays all other screen elements
				// hence, we get gui elements to front in surfaceCreated() within CameraPreview (VideOSCCameraFragment)
				mCameraPreview = new VideOSCCameraFragment();
				FragmentManager fragmentManager = getFragmentManager();
				fragmentManager.beginTransaction()
						.replace(R.id.camera_preview, mCameraPreview, "CamPreview")
						.commit();
				buildUI();
			} else {
				Snackbar.make(
						mCamView,
						R.string.camera_permissions_denied,
						Snackbar.LENGTH_SHORT
				).show();
			}
		}
	}

	// check if device has inbuilt torch
	// if camera failes to open show warning
	private void checkCamera() {
		Camera camera = null;

		try {
			camera = Camera.open();
		} catch(Exception e) {
			e.printStackTrace();
		}

		if (camera != null) {
			mApp.setHasTorch(VideOSCUIHelpers.hasTorch(camera));
			camera.release();
		} else {
			VideOSCDialogHelper.showDialog(
					this,
					android.R.style.Theme_Holo_Light_Dialog,
					getString(R.string.msg_on_camera_open_fail),
					getString(R.string.OK),
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}

					}, null, null
			);
		}
	}

	private Point getAbsoluteScreenSize() {
		Point dimensions = new Point();

		final Display display = getWindowManager().getDefaultDisplay();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
			display.getRealSize(dimensions);
		else display.getSize(dimensions);

		return dimensions;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
				&& requestCode == CODE_WRITE_SETTINGS_PERMISSION
				&& Settings.System.canWrite(this)) {
			Log.d(TAG, "CODE_WRITE_SETTINGS_PERMISSION success");
			finish();
			startActivity(starterIntent);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Pass any configuration change to the drawer toggles
	}

	@Override
	public void onFragmentInteraction(Uri uri) {
		Log.d(TAG, "onFragmentInteraction, uri: " + uri);
	}

	@Override
	public void onFragmentInteraction(String id) {
		Log.d(TAG, "onFragmentInteraction, id: " + id);
	}

	@Override
	public void onFragmentInteraction(int actionId) {
		Log.d(TAG, "onFragmentInteraction, actionId: " + actionId);
	}

	/*@Override
	public void onCompleteCameraFragment() {
		Log.d(TAG, "onCompleteCameraFragment");
		final FragmentManager fragmentManager = getFragmentManager();
		final VideOSCCameraFragment cameraFragment = (VideOSCCameraFragment) fragmentManager.findFragmentByTag("CamPreview");
		Log.d(TAG, "camera fragment: " + cameraFragment.mPreview);
	}*/
}
