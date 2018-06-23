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
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.videosc2.fragments;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import net.videosc2.R;
import net.videosc2.VideOSCApplication;
import net.videosc2.db.SettingsContract;
import net.videosc2.utilities.VideOSCDialogHelper;
import net.videosc2.utilities.VideOSCOscHandler;
import net.videosc2.utilities.VideOSCUIHelpers;
import net.videosc2.utilities.enums.InteractionModes;
import net.videosc2.utilities.enums.RGBModes;
import net.videosc2.views.TileOverlayView;
import net.videosc2.views.VideOSCMultiSliderView;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import jp.co.cyberagent.android.gpuimage.GPUImageNativeLibrary;
import oscP5.OscMessage;
import oscP5.OscP5;

/**
 * Display the down-scaled preview, calculated
 * from the smallest possible preview size
 * Created by Stefan Nussbaumer
 * after a piece of code by
 * Rex St. John (on behalf of AirPair.com) on 3/4/14.
 */
public class VideOSCCameraFragment extends VideOSCBaseFragment {
	private final static String TAG = "VideOSCCameraFragment";

	private long mNow, mPrev = 0;
	private float mFrameRate;

	// Native camera.
	public Camera mCamera;
	private Camera.Parameters mCameraParams;
	private List<int[]> mSupportedPreviewFpsRanges;


	// View to display the camera output.
	public CameraPreview mPreview;
	// preview container
	private ViewGroup mPreviewContainer;

	// Reference to the ImageView containing the downscaled video frame
	private ImageView mImage;

	/**
	 * Default empty constructor.
	 */
	public VideOSCCameraFragment() {
		super();
	}

	private float mCamZoom = 1f;

	private Point mResolution;

	private int[] mFrameRateRange;

	private String mRed, mGreen, mBlue;

	private VideOSCApplication mApp;
	private LayoutInflater mInflater;
	private static OscP5 mOscP5;

	// pixels set by multislider
	// these arrays shouldn't get get reinitialized
	// when switching the camera
	private Double[] mRedValues;
	private Double[] mGreenValues;
	private Double[] mBlueValues;

	private Double[] mRedMixValues;
	private Double[] mGreenMixValues;
	private Double[] mBlueMixValues;

	// must be owned by the fragment - no idea why
	private Bitmap mBmp;

	/**
	 * OnCreateView fragment override
	 *
	 * @param inflater           the layout inflater inflating the layout for the view
	 * @param container          the layout's container
	 * @param savedInstanceState a Bundle instance
	 * @return a View
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		mApp = (VideOSCApplication) getActivity().getApplication();
		mInflater = inflater;
		mOscP5 = mApp.mOscHelper.getOscP5();
//		Log.d(TAG, "send OSC to: " + mApp.mOscHelper.getBroadcastIP());
		View view = inflater.inflate(R.layout.fragment_native_camera, container, false);
		// store the container for later re-use
		mPreviewContainer = container;
		mImage = (ImageView) view.findViewById(R.id.camera_downscaled);

		// Create our Preview view and set it as the content of our activity.
		safeCameraOpenInView(view);

		return view;
	}

	/**
	 * Recommended "safe" way to open the camera.
	 *
	 * @param view the view on which the camera is going to be displayed to the user
	 */
	public void safeCameraOpenInView(View view) {
		FrameLayout preview;
		// cache current zoom
		int zoom = mPreview != null ? mPreview.getCurrentZoom() : 0;
		releaseCameraAndPreview();
		mCamera = getCameraInstance(mApp);
		if (mCamera != null) {
			mCameraParams = mCamera.getParameters();
			mSupportedPreviewFpsRanges = mCameraParams.getSupportedPreviewFpsRange();

			if (mPreview == null) {
				mPreview = new CameraPreview(getActivity().getApplicationContext(), mCamera);
				if (view.findViewById(R.id.camera_preview) != null) {
					preview = (FrameLayout) view.findViewById(R.id.camera_preview);
					preview.addView(mPreview);
					Log.d(TAG, "view found and set");
				} else Log.d(TAG, "FrameLayout is null");
			} else {
				mPreview.switchCamera(mCamera);
				// set camera zoom to the zoom value of the old camera
				mPreview.setZoom(zoom);
				Log.d(TAG, "switch camera");
			}
		} else {
			final Activity activity = getActivity();
			VideOSCDialogHelper.showDialog(
					activity,
					android.R.style.Theme_Holo_Light_Dialog,
					getString(R.string.msg_on_camera_open_fail),
					getString(R.string.OK),
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							activity.finish();
						}

					}, null, null
			);
		}
	}

	/**
	 * Safe method for getting a camera instance.
	 *
	 * @return Camera instance
	 */
	private static Camera getCameraInstance(VideOSCApplication app) {
		Camera c = null;

		try {
			c = Camera.open(app.getCurrentCameraId()); // attempt to get a Camera instance
		} catch (Exception e) {
			e.printStackTrace();
		}
		return c; // returns null if camera is unavailable
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy called");
		releaseCameraAndPreview();
	}

	/**
	 * Clear any existing preview / camera.
	 */
	private void releaseCameraAndPreview() {
		if (mCamera != null) {
			mCamera.stopPreview();
			// hack: set a null callback as the already set callback
			// otherwise prevails even after camera.release() and
			// causes a crash on quit
			mCamera.setPreviewCallback(null);
			Log.d(TAG, "camera.release now");
			mCamera.release();
			mCamera = null;
		}
		if (mPreview != null) {
			mPreview.destroyDrawingCache();
			mPreview.mViewCamera = null;
		}
	}

	public void setColorOscCmds(String cmd) {
		mRed = String.format("/%1$s/red", cmd);
		mGreen = String.format("/%1$s/green", cmd);
		mBlue = String.format("/%1$s/blue", cmd);
	}

	public String[] getColorOscCmds() {
		return new String[]{mRed, mGreen, mBlue};
	}

	private void setFramerateRange(int index) {
		mFrameRateRange = mSupportedPreviewFpsRanges.get(index);
	}

	private int[] getFramerateRange() {
		return mFrameRateRange;
	}

	/**
	 * Surface on which the camera projects it's capture results. This is derived both from Google's docs and the
	 * excellent StackOverflow answer provided below.
	 * <p>
	 * Reference / Credit: http://stackoverflow.com/questions/7942378/android-camera-will-not-work-startpreview-fails
	 */
	class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

		// SurfaceHolder
		private SurfaceHolder mHolder;

		// Our Camera.
		private Camera mViewCamera;

		// Camera Sizing (For rotation, orientation changes)
		private Camera.Size mPreviewSize;

		// List of supported preview sizes
		private List<Camera.Size> mSupportedPreviewSizes;

		// Flash modes supported by this camera
		private List<String> mSupportedFlashModes;

		private double mOldFingerDistance = 0.0;
		private final Point mPixelSize = new Point();

		// lock the state of a pixel after changing its state, otherwise pixels would constantly
		// change their state as long as they're hoevered
		private final ArrayList<Boolean[]> lockList = new ArrayList<>();
		// store the states of all pixels
		private final ArrayList<Boolean[]> offPxls = new ArrayList<>();
		final private Boolean[] falses = {false, false, false};

		private RedOscRunnable mRedOscRunnable;
		private GreenOscRunnable mGreenOscRunnable;
		private BlueOscRunnable mBlueOscRunnable;
		private Thread mRedOscSender;
		private Thread mGreenOscSender;
		private Thread mBlueOscSender;

		private ViewGroup mOverlay;
		private TileOverlayView mOverlayView;
		private ViewGroup mOkCancelSetPixel;
		private final ArrayList<Rect> mSelectedPixels = new ArrayList<>();
		private final List<Integer> mPixelIds = new ArrayList<>();
		private final FragmentManager mManager;

		private volatile OscMessage oscR, oscG, oscB;

		// debugging
		private long mCountR = 0, mCountG = 0, mCountB = 0;

		/**
		 * @param context the context of the application
		 * @param camera  an instance of Camera, to be used throughout CameraPreview
		 */
		CameraPreview(Context context, Camera camera) {
			super(context);

			Log.d(TAG, "CameraPreview(): " + camera);
			// Capture the context
			setCamera(camera);
			// explicitely trigger drawing (onDraw)
			setWillNotDraw(false);

			// Install a SurfaceHolder.Callback so we get notified when the
			// underlying surface is created and destroyed.
			mHolder = getHolder();
			mHolder.addCallback(this);
			mHolder.setKeepScreenOn(true);
			// deprecated setting, but required on Android versions prior to 3.0
//			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

			if (mRedOscSender == null) {
				mRedOscRunnable = new RedOscRunnable();
				RedOscRunnable.setOscHelper(mApp.mOscHelper);
				mRedOscSender = new Thread(mRedOscRunnable);
				mRedOscSender.start();
			}

			if (mGreenOscSender == null) {
				mGreenOscRunnable = new GreenOscRunnable();
				GreenOscRunnable.setOscHelper(mApp.mOscHelper);
				mGreenOscSender = new Thread(mGreenOscRunnable);
				mGreenOscSender.start();
			}

			if (mBlueOscSender == null) {
				mBlueOscRunnable = new BlueOscRunnable();
				BlueOscRunnable.setOscHelper(mApp.mOscHelper);
				mBlueOscSender = new Thread(mBlueOscRunnable);
				mBlueOscSender.start();
			}

			// get initial settings from database
			String[] settingsFields = new String[]{
					SettingsContract.SettingsEntries.RES_H,
					SettingsContract.SettingsEntries.RES_V,
					SettingsContract.SettingsEntries.FRAMERATE_RANGE,
					SettingsContract.SettingsEntries.ROOT_CMD
			};

			final SQLiteDatabase db = ((VideOSCApplication) getActivity().getApplicationContext()).getSettingsHelper().getReadableDatabase();

			Cursor cursor = db.query(
					SettingsContract.SettingsEntries.TABLE_NAME,
					settingsFields,
					null,
					null,
					null,
					null,
					null
			);

			if (cursor.moveToFirst()) {
				mApp.setResolution(
						new Point(
								cursor.getInt(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.RES_H)),
								cursor.getInt(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.RES_V))
						)
				);
				setFramerateRange(
						cursor.getInt(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.FRAMERATE_RANGE))
				);
				setColorOscCmds(
						cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.ROOT_CMD))
				);
			}

			cursor.close();

			// triplets of booleans, to be added to lockList, offPxls;
			Point res = mApp.getResolution();
			for (int i = 0; i < res.x * res.y; i++) {
				lockList.add(falses.clone());
				offPxls.add(falses.clone());
			}

			if (mRedValues == null)
				mRedValues = new Double[res.x * res.y];
			if (mGreenValues == null)
				mGreenValues = new Double[res.x * res.y];
			if (mBlueValues == null)
				mBlueValues = new Double[res.x * res.y];

			if (mRedMixValues == null)
				mRedMixValues = new Double[res.x * res.y];
			if (mGreenMixValues == null)
				mGreenMixValues = new Double[res.x * res.y];
			if (mBlueMixValues == null)
				mBlueMixValues = new Double[res.x * res.y];

			mManager = getFragmentManager();
		}

		/**
		 * switch backside to frontside camera and vice versa
		 * called within safeCameraOpenInView(View view)
		 *
		 * @param camera an instance of Camera
		 */
		public void switchCamera(Camera camera) {
			Log.d(TAG, "switch camera, mViewCamera: " + camera);
			mViewCamera = camera;
			mHolder.removeCallback(this);
			ViewGroup parent = (ViewGroup) mPreview.getParent();
			// cache new preview locally and remove old preview later
			// removing old preview immediately caused surfaceDestroyed to be called
			// and switching wasn't finished but ONLY when switching back from front- to backside
			// camera...
			CameraPreview preview = new CameraPreview(getContext(), camera);
			SurfaceHolder holder = preview.getHolder();
			holder.addCallback(this);
			parent.addView(preview);
			parent.removeView(mPreview);
			mPreview = preview;
			mHolder = holder;
		}

		/**
		 * Extract supported preview and flash modes from the camera.
		 *
		 * @param camera an instance of Camera
		 */
		private void setCamera(Camera camera) {
			mViewCamera = camera;
			mCameraParams = camera.getParameters();
			// Source: http://stackoverflow.com/questions/7942378/android-camera-will-not-work-startpreview-fails
			mSupportedPreviewSizes = mCameraParams.getSupportedPreviewSizes();
			mPreviewSize = getSmallestPreviewSize(mSupportedPreviewSizes);
			mCameraParams.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
			mSupportedFlashModes = mCameraParams.getSupportedFlashModes();

			// Set the camera to Auto Flash mode.
			if (mSupportedFlashModes != null && mSupportedFlashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
				mCameraParams.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
			}
			camera.setParameters(mCameraParams);

			requestLayout();
		}

		/**
		 * The Surface has been created, now tell the camera where to draw the preview.
		 *
		 * @param holder the surface holder
		 */
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			Log.d(TAG, "surfaceCreated: " + mViewCamera);
			try {
				mViewCamera.setPreviewDisplay(holder);
				mViewCamera.startPreview();
//				mCanvas = holder.lockCanvas();
//				Log.d(TAG, "canvas initialized in surfaceCreated: " + mCanvas);
				View menuButton = mPreviewContainer.findViewById(R.id.show_menu);
				menuButton.bringToFront();
				View indicatorPanel = mPreviewContainer.findViewById(R.id.indicator_panel);
				if (indicatorPanel != null)
					indicatorPanel.bringToFront();
//				Log.d(TAG, "holder: " + holder.lockCanvas());
			} catch (IOException e) {
				e.printStackTrace();
			}

			mOverlay = (ViewGroup) mInflater.inflate(R.layout.tile_overlay_view, mPreviewContainer, false);
			mOverlayView = (TileOverlayView) mOverlay.findViewById(R.id.tile_draw_view);
			VideOSCUIHelpers.addView(mOverlayView, mPreviewContainer);
			mOkCancelSetPixel = (ViewGroup) mInflater.inflate(R.layout.cancel_ok_buttons, mPreviewContainer, false);
		}

		/**
		 * Dispose of the camera preview.
		 *
		 * @param holder the surface holder
		 */
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.d(TAG, "surfaceDestroyed");
			// prevent errors resulting from camera being used after Camera.release() has been
			// called. Seems to work...
			if (mViewCamera != null) try {
				mViewCamera.stopPreview();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * React to surface changed events
		 *
		 * @param holder the surface holder
		 * @param format the pixel format of the surface
		 * @param w      the surface width
		 * @param h      the surface height
		 */
		@Override
		public void surfaceChanged(final SurfaceHolder holder, int format, final int w, final int h) {
			if (mHolder.getSurface() == null) {
				// preview surface does not exist
				return;
			}

			final ViewGroup modePanel = (ViewGroup) mPreviewContainer.findViewById(R.id.color_mode_panel);
			final ViewGroup fpsRateCalcPanel = (ViewGroup) mPreviewContainer.findViewById(R.id.fps_calc_period_indicator);
			final ViewGroup indicators = (ViewGroup) mPreviewContainer.findViewById(R.id.indicator_panel);

			mResolution = mApp.getResolution();

			// memorize current pixel size
			setPixelSize(holder);

			if (mApp.getIsMultiSliderActive()) {
				if (mApp.getIsFPSCalcPanelOpen())
					VideOSCUIHelpers.removeView(fpsRateCalcPanel, mPreviewContainer);
				if (mApp.getIsColorModePanelOpen())
					VideOSCUIHelpers.removeView(modePanel, mPreviewContainer);
				if (mApp.getIsIndicatorPanelOpen()) {
					mApp.setIsIndicatorPanelOpen(VideOSCUIHelpers.removeView(indicators, mPreviewContainer));
				}
			}

			// stop preview before making changes
			try {
				final Camera.Parameters parameters = mViewCamera.getParameters();
				int[] frameRates = getFramerateRange();
				parameters.setPreviewFpsRange(frameRates[0], frameRates[1]);

				mViewCamera.setParameters(parameters);
				Log.d(TAG, "camera parameters set");
				mViewCamera.setPreviewCallback(new Camera.PreviewCallback() {
					@Override
					public void onPreviewFrame(byte[] data, Camera camera) {
						mNow = System.currentTimeMillis();
						mFrameRate = Math.round(1000.0f / (mNow - mPrev) * 10.0f) / 10.0f;
						mPrev = mNow;
						TextView frameRateText = (TextView) mPreviewContainer.findViewById(R.id.fps);
						if (frameRateText != null)
							frameRateText.setText(String.format(Locale.getDefault(), "%.1f", mFrameRate));
						TextView zoomText = (TextView) mPreviewContainer.findViewById(R.id.zoom);
						if (zoomText != null)
							zoomText.setText(String.format(Locale.getDefault(), "%.1f", mCamZoom));
						Point resolution = mApp.getResolution();
						int outWidth = resolution.x;
						int outHeight = resolution.y;
						int previewSize = outWidth * outHeight;
						int diff = previewSize - offPxls.size();
						if (diff != 0) pad(diff);
						Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
						int[] out = new int[mPreviewSize.width * mPreviewSize.height];
						GPUImageNativeLibrary.YUVtoRBGA(data, mPreviewSize.width, mPreviewSize.height, out);
						Bitmap bmp = Bitmap.createBitmap(mPreviewSize.width, mPreviewSize.height, inPreferredConfig);
						bmp.copyPixelsFromBuffer(IntBuffer.wrap(out));
						mBmp = drawFrame(Bitmap.createScaledBitmap(bmp, outWidth, outHeight, true), outWidth, outHeight);
//						Log.d(TAG, "scaled bitmap: " + mBmp);
						BitmapDrawable bmpDraw = new BitmapDrawable(getResources(), mBmp);
						bmpDraw.setAntiAlias(false);
						bmpDraw.setDither(false);
						bmpDraw.setFilterBitmap(false);
						mImage.bringToFront();
						mImage.setImageDrawable(bmpDraw);
						Point dimensions = mApp.getDimensions();
						// provide neccessary information for overlay
						mOverlayView.setInterActionMode(mApp.getInteractionMode());
						mOverlayView.setParentResolution(resolution);
						mOverlayView.setColorMode(mApp.getColorMode());
						mOverlayView.setPixelSize(mPixelSize);
						mOverlayView.setRedMixValues(mRedMixValues);
						mOverlayView.setGreenMixValues(mGreenMixValues);
						mOverlayView.setBlueMixValues(mBlueMixValues);
						mOverlayView.layout(0, 0, dimensions.x, dimensions.y);
						mOverlayView.invalidate();
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * @param sizes the ArrayList of possible preview sizes
		 * @return the smallest possible preview size
		 */
		private Camera.Size getSmallestPreviewSize(List<Camera.Size> sizes) {
			Camera.Size optimalSize;
			ArrayList<Integer> productList = new ArrayList<>();

			for (Camera.Size size : sizes) {
				productList.add(size.width * size.height);
			}

			int minIndex = productList.indexOf(Collections.min(productList));
			optimalSize = sizes.get(minIndex);

			return optimalSize;
		}

		@Override
		public boolean onTouchEvent(MotionEvent motionEvent) {
			performClick();
			final Context context = getContext();
			final Camera.Parameters params = mViewCamera.getParameters();
			final ViewGroup modePanel = (ViewGroup) mPreviewContainer.findViewById(R.id.color_mode_panel);
			ViewGroup fpsRateCalcPanel = (ViewGroup) mPreviewContainer.findViewById(R.id.fps_calc_period_indicator);
			ViewGroup indicators = (ViewGroup) mPreviewContainer.findViewById(R.id.indicator_panel);
			boolean hasTorch = mApp.getHasTorch();
			BitmapDrawable img;

//			Log.d(TAG, "motion event: " + motionEvent.getActionMasked() + ", x: " + motionEvent.getX() + ", y: " + motionEvent.getY() + ", pressure: " + motionEvent.getPressure());

			if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
				VideOSCUIHelpers.removeView(modePanel, mPreviewContainer);
				if (mApp.getInteractionMode().equals(InteractionModes.SINGLE_PIXEL)) {
					if (fpsRateCalcPanel != null)
						fpsRateCalcPanel.setVisibility(View.INVISIBLE);
					indicators.setVisibility(View.INVISIBLE);
					if (mPixelIds.size() > 0)
						mPixelIds.clear();
				}
			}

			if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
//				VideOSCUIHelpers.removeView(mOverlay, mPreviewContainer);
				// clear selected pixels on up
				mSelectedPixels.clear();
				mOverlayView.setSelectedRects(mSelectedPixels);
				mOverlayView.invalidate();

				// TODO: if multisliders shall be shown on ACTION_UP this must be considered in the show-hide logic separately
				if (mApp.getInteractionMode().equals(InteractionModes.SINGLE_PIXEL)) {
					// mPixelIds holds the indices of the selected pixels (resp. index + 1, as we display pixel at index 0 as "1")
					// colors keeps the integer color values of the pixels denoted in mPixelIds
					if (mPixelIds.size() > 0) {
						short numSelectedPixels = (short) mPixelIds.size();
						int[] colors = new int[numSelectedPixels];
						double[] redVals = new double[numSelectedPixels];
						double[] redMixVals = new double[numSelectedPixels];
						double[] greenVals = new double[numSelectedPixels];
						double[] greenMixVals = new double[numSelectedPixels];
						double[] blueVals = new double[numSelectedPixels];
						double[] blueMixVals = new double[numSelectedPixels];
						Point res = mApp.getResolution();
						for (int i = 0; i < numSelectedPixels; i++) {
							int id = mPixelIds.get(i) - 1;
//							Log.d(TAG, "selected id: " + id);
							colors[i] = mBmp.getPixel(id % res.x, id / res.x);
							// once a value has been set manually the value should not get reset
							// when editing the same pixel again
							if (mApp.getColorMode().equals(RGBModes.RGB) || mApp.getColorMode().equals(RGBModes.R)) {
								if (mRedValues[id] == null)
									mRedValues[id] = ((colors[i] >> 16) & 0xFF) / 255.0;
								redVals[i] = mRedValues[id];
								redMixVals[i] = mRedMixValues[id] == null ? 1.0 : mRedMixValues[id];
							}
							if (mApp.getColorMode().equals(RGBModes.RGB) || mApp.getColorMode().equals(RGBModes.G)) {
								if (mGreenValues[id] == null)
									mGreenValues[id] = ((colors[i] >> 8) & 0xFF) / 255.0;
								greenVals[i] = mGreenValues[id];
								greenMixVals[i] = mGreenMixValues[id] == null ? 1.0 : mGreenMixValues[id];
							}
							if (mApp.getColorMode().equals(RGBModes.RGB) || mApp.getColorMode().equals(RGBModes.B)) {
								if (mBlueValues[id] == null)
									mBlueValues[id] = (colors[i] & 0xFF) / 255.0;
								blueVals[i] = mBlueValues[id];
								blueMixVals[i] = mBlueMixValues[id] == null ? 1.0 : mBlueMixValues[id];
							}
						}
						Bundle msArgsBundle = new Bundle();
						msArgsBundle.putIntegerArrayList("nums", (ArrayList<Integer>) mPixelIds);
						if (mApp.getColorMode().equals(RGBModes.RGB) || mApp.getColorMode().equals(RGBModes.R)) {
							msArgsBundle.putDoubleArray("redVals", redVals);
							msArgsBundle.putDoubleArray("redMixVals", redMixVals);
						}
						if (mApp.getColorMode().equals(RGBModes.RGB) || mApp.getColorMode().equals(RGBModes.G)) {
							msArgsBundle.putDoubleArray("greenVals", greenVals);
							msArgsBundle.putDoubleArray("greenMixVals", greenMixVals);
						}
						if (mApp.getColorMode().equals(RGBModes.RGB) || mApp.getColorMode().equals(RGBModes.B)) {
							msArgsBundle.putDoubleArray("blueVals", blueVals);
							msArgsBundle.putDoubleArray("blueMixVals", blueMixVals);
						}
						if (mManager.findFragmentByTag("MultiSliderView") == null) {
							if (!mApp.getColorMode().equals(RGBModes.RGB)) {
								VideOSCMultiSliderFragment multiSliderFragment = new VideOSCMultiSliderFragment();
								mManager.beginTransaction()
										.add(R.id.camera_preview, multiSliderFragment, "MultiSliderView")
										.commit();
								multiSliderFragment.setArguments(msArgsBundle);
								multiSliderFragment.setParentContainer(mPreviewContainer);
							} else {
								VideOSCMultiSliderFragmentRGB multiSliderFragment = new VideOSCMultiSliderFragmentRGB();
								mManager.beginTransaction()
										.add(R.id.camera_preview, multiSliderFragment, "MultiSliderView")
										.commit();
								multiSliderFragment.setArguments(msArgsBundle);
								multiSliderFragment.setParentContainer(mPreviewContainer);
							}

							mApp.setIsMultiSliderActive(true);
							indicators.setVisibility(View.INVISIBLE);
							if (fpsRateCalcPanel != null)
								fpsRateCalcPanel.setVisibility(View.INVISIBLE);
							mApp.setIsColorModePanelOpen(VideOSCUIHelpers.removeView(modePanel, mPreviewContainer));
						}
					}

					if (!mApp.getIsMultiSliderActive()) {
						fpsRateCalcPanel = (ViewGroup) mInflater.inflate(R.layout.framerate_calculation_indicator, mPreviewContainer, false);
						if (mApp.getIsFPSCalcPanelOpen())
							VideOSCUIHelpers.addView(fpsRateCalcPanel, mPreviewContainer);
						indicators = mApp.getHasTorch() ?
								(ViewGroup) mInflater.inflate(R.layout.indicator_panel, mPreviewContainer, false) :
								(ViewGroup) mInflater.inflate(R.layout.indicator_panel_no_torch, mPreviewContainer, false);
						VideOSCUIHelpers.addView(indicators, mPreviewContainer);
						// play indicator
						final ImageView playIndicator = (ImageView) indicators.findViewById(R.id.indicator_osc);
						if (mApp.getCameraOSCisPlaying()) {
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.osc_playing);
							playIndicator.setImageResource(R.drawable.osc_playing);
						} else {
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.osc_paused);
							playIndicator.setImageResource(R.drawable.osc_paused);
						}
						playIndicator.setImageDrawable(img);

						// rgb status
						final ImageView rgbIndicator = (ImageView) indicators.findViewById(R.id.indicator_color);
						if (mApp.getIsRGBPositive()) {
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.rgb_indicator);
							rgbIndicator.setImageResource(R.drawable.rgb_indicator);
						} else {
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.rgb_inv_indicator);
							rgbIndicator.setImageResource(R.drawable.rgb_inv_indicator);
						}
						rgbIndicator.setImageDrawable(img);

						// camera indicator
						final ImageView cameraIndicator = (ImageView) indicators.findViewById(R.id.indicator_camera);
						if (mApp.getCurrentCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK) {
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.indicator_camera_back);
							cameraIndicator.setImageResource(R.drawable.indicator_camera_back);
						} else {
							img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.indicator_camera_front);
							cameraIndicator.setImageResource(R.drawable.indicator_camera_front);
						}
						cameraIndicator.setImageDrawable(img);

						// torch status indicator
						final ImageView torchIndicator = hasTorch ? (ImageView) indicators.findViewById(R.id.torch_status_indicator) : null;
						if (torchIndicator != null) {
							if (mApp.getIsTorchOn()) {
								img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.light_on_indicator);
								torchIndicator.setImageResource(R.drawable.light_on_indicator);
							} else {
								img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.light_off_indicator);
								torchIndicator.setImageResource(R.drawable.light_off_indicator);
							}
							torchIndicator.setImageDrawable(img);
						}

						// interaction indicator - always in plus mode
						final ImageView interactionModeIndicator = (ImageView) indicators.findViewById(R.id.indicator_interaction);
						img = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.interaction_plus_indicator);
						interactionModeIndicator.setImageResource(R.drawable.interaction_plus_indicator);
						interactionModeIndicator.setImageDrawable(img);
					}
				}
			}

			if (motionEvent.getPointerCount() > 1 && params.isZoomSupported()) {
				int zoom = params.getZoom();

				if (mOldFingerDistance == 0.0)
					mOldFingerDistance = getFingerSpacing(motionEvent);
				double currFingerDistance = getFingerSpacing(motionEvent);

				if (mOldFingerDistance != currFingerDistance) {
					if (mOldFingerDistance < currFingerDistance) {
						if (zoom + 1 <= params.getMaxZoom()) zoom++;
					} else {
						if (zoom - 1 >= 0) zoom--;
					}
					params.setZoom(zoom);
					mOldFingerDistance = currFingerDistance;
					mViewCamera.setParameters(params);
//					Log.d(TAG, "zoom: " + params.getZoom() + ", ratio: " + params.getZoomRatios().get(params.getZoom()));
					mCamZoom = (float) (params.getZoomRatios().get(params.getZoom()) / 100.0);
				}
			}

			if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
				if (mApp.getInteractionMode().equals(InteractionModes.SINGLE_PIXEL)) {
					int currPixel = getHoverPixel(motionEvent.getX(), motionEvent.getY());
//					Log.d(TAG, "current pixel color: " + mBmp.getPixel(currPixel % mApp.getResolution().x, currPixel / mApp.getResolution().x));
					if (!mPixelIds.contains(currPixel + 1)) {
						mPixelIds.add(currPixel + 1);
						Collections.sort(mPixelIds);
//						Log.d(TAG, "selected pixels: " + mPixelIds);
					}
					Rect currRect = getCurrentPixelRect(currPixel);
					if (!containsRect(mSelectedPixels, currRect)) {
						mSelectedPixels.add(currRect);
					}
					mOverlayView.setSelectedRects(mSelectedPixels);
					mOverlayView.measure(getMeasuredWidth(), getMeasuredHeight());
					mOverlayView.invalidate();
				}
			}

			return true;
		}

		private boolean containsRect(ArrayList<Rect> rectList, Rect rect) {
			for (Rect testRect : rectList) {
				if (testRect.equals(rect)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean performClick() {
			super.performClick();
			return false;
		}

		/**
		 * Determine the space between the first two fingers
		 */
		private double getFingerSpacing(MotionEvent event) {
			float x = event.getX(0) - event.getX(1);
			float y = event.getY(0) - event.getY(1);

			return Math.sqrt(x * x + y * y);
		}

		/**
		 * get current zoom
		 */
		private int getCurrentZoom() {
			Camera.Parameters params = mViewCamera.getParameters();
			return params.getZoom();
		}

		/**
		 * set zoom
		 */
		private void setZoom(int zoom) {
			Camera.Parameters params = mViewCamera.getParameters();
			params.setZoom(zoom);
			mViewCamera.setParameters(params);
		}

		private void setPixelSize(SurfaceHolder holder) {
			Rect surfaceFrame = holder.getSurfaceFrame();
			Point resolution = mApp.getResolution();
//			Log.d(TAG, "surfaceFrame: " + surfaceFrame.width() + " x " + surfaceFrame.height() + ", resolution: " + resolution);
			mPixelSize.x = surfaceFrame.width() / resolution.x;
			mPixelSize.y = surfaceFrame.height() / resolution.y;
		}

		private Point getPixelSize() {
			return mPixelSize;
		}

		private int getHoverPixel(float x, float y) {
			int hIndex = (int) x / mPixelSize.x;
			int vIndex = (int) y / mPixelSize.y;

			return vIndex * mApp.getResolution().x + hIndex;
		}

		private Rect getCurrentPixelRect(int pixelId) {
			Point resolution = mApp.getResolution();
			int left = pixelId % resolution.x * mPixelSize.x;
			int top = pixelId / resolution.x * mPixelSize.y;
			int right = left + mPixelSize.x;
			int bottom = top + mPixelSize.y;
			return new Rect(left, top, right, bottom);
		}

		// set the preview fps range and update framerate immediately
		// format, w, h must be passed in explicitely as they're demanded by surfaceChanged()
		private void setPreviewFpsRange(Camera.Parameters params, int[] range, int format, int w, int h) {
			params.setPreviewFpsRange(range[0], range[1]);
			surfaceChanged(mHolder, format, w, h);
		}

		private void pad(int diff) {
			if (diff > 0) {
				for (int i = 0; i < diff; i++) {
					offPxls.add(falses.clone());
					lockList.add(falses.clone());
				}
			} /*else if (diff < 0) {
				Log.d(TAG, "diff: " + diff + ", offPxls size: " + offPxls.size());
				offPxls = (ArrayList<Boolean[]>) offPxls.subList(0, offPxls.size() - 1 + diff);
				Log.d(TAG, "offPxls size: " + offPxls.size());
				lockList = (ArrayList<Boolean[]>) lockList.subList(0, lockList.size() - 1 + diff);
			}*/
		}

		private Bitmap drawFrame(Bitmap bmp, int width, int height) {
			double rValue, gValue, bValue;
			Double redSliderVal, greenSliderVal, blueSliderVal;
			Point resolution = mApp.getResolution();
			int dimensions = resolution.x * resolution.y;
			int[] pixels = new int[width * height];

			bmp.getPixels(pixels, 0, width, 0, 0, width, height);

			// color mode RGB (or RGB inverted)
			VideOSCMultiSliderView msRedLeft = (VideOSCMultiSliderView) mPreviewContainer.findViewById(R.id.multislider_view_r_left);
			VideOSCMultiSliderView msRedRight = (VideOSCMultiSliderView) mPreviewContainer.findViewById(R.id.multislider_view_r_right);
			VideOSCMultiSliderView msGreenLeft = (VideOSCMultiSliderView) mPreviewContainer.findViewById(R.id.multislider_view_g_left);
			VideOSCMultiSliderView msGreenRight = (VideOSCMultiSliderView) mPreviewContainer.findViewById(R.id.multislider_view_g_right);
			VideOSCMultiSliderView msBlueLeft = (VideOSCMultiSliderView) mPreviewContainer.findViewById(R.id.multislider_view_b_left);
			VideOSCMultiSliderView msBlueRight = (VideOSCMultiSliderView) mPreviewContainer.findViewById(R.id.multislider_view_b_right);
			// color mode R, G or B
			VideOSCMultiSliderView msLeft = (VideOSCMultiSliderView) mPreviewContainer.findViewById(R.id.multislider_view_left);
			VideOSCMultiSliderView msRight = (VideOSCMultiSliderView) mPreviewContainer.findViewById(R.id.multislider_view_right);

			for (int i = 0; i < dimensions; i++) {
				Double mixVal;
				double assignedMixVal;

				// only the downsampled image gets inverted as inverting the original would slow
				// down the application considerably
				int rPixVal = (!mApp.getIsRGBPositive()) ? 0xFF - ((pixels[i] >> 16) & 0xFF)
						: (pixels[i] >> 16) & 0xFF;
				int gPixVal = (!mApp.getIsRGBPositive()) ? 0xFF - ((pixels[i] >> 8) & 0xFF)
						: (pixels[i] >> 8) & 0xFF;
				int bPixVal = (!mApp.getIsRGBPositive()) ? 0xFF - (pixels[i] & 0xFF)
						: pixels[i] & 0xFF;

//				if (i == 0)
//					Log.d(TAG, "0, before, red value: " + mRedValues[i] + ", green value: " + mGreenValues[i] + ", blue value: " + mBlueValues[i]);

				if (mApp.getColorMode().equals(RGBModes.RGB)
						&& msRedLeft != null
						&& msRedRight != null
						&& msGreenLeft != null
						&& msGreenRight != null
						&& msBlueLeft != null
						&& msBlueRight != null) {

					// color values
					redSliderVal = msRedLeft.getSliderValueAt(i);
					if (redSliderVal != null) {
						mRedValues[i] = redSliderVal;
						// mix values: once a mix value has been set it should be remembered until it's set
						// to a new value (by moving the slider. Next time the regarding pixel is edited
						// the slider should be set to the value that has been stored on the last edit
						// TODO: default value should maybe be settable in preferences to 1.0 or 0.0
						mixVal = msRedRight.getSliderValueAt(i);
						mRedMixValues[i] = mixVal == null ? 1.0 : mixVal;
					}
					greenSliderVal = msGreenLeft.getSliderValueAt(i);
					if (greenSliderVal != null) {
						mGreenValues[i] = greenSliderVal;
						mixVal = msGreenRight.getSliderValueAt(i);
						mGreenMixValues[i] = mixVal == null ? 1.0 : mixVal;
					}
					blueSliderVal = msBlueLeft.getSliderValueAt(i);
					if (blueSliderVal != null) {
						mBlueValues[i] = blueSliderVal;
						mixVal = msBlueRight.getSliderValueAt(i);
						mBlueMixValues[i] = mixVal == null ? 1.0 : mixVal;
					}
				} else if (!mApp.getColorMode().equals(RGBModes.RGB)
						&& msLeft != null
						&& msRight != null) {
					switch (mApp.getColorMode()) {
						case R:
							redSliderVal = msLeft.getSliderValueAt(i);
							if (redSliderVal != null) {
								mRedValues[i] = redSliderVal;
								mixVal = msRight.getSliderValueAt(i);
								mRedMixValues[i] = mixVal == null ? 1.0 : mixVal;
							}
							break;
						case G:
							greenSliderVal = msLeft.getSliderValueAt(i);
							if (greenSliderVal != null) {
								mGreenValues[i] = greenSliderVal;
								mixVal = msRight.getSliderValueAt(i);
								mGreenMixValues[i] = mixVal == null ? 1.0 : mixVal;
							}
							break;
						case B:
							blueSliderVal = msLeft.getSliderValueAt(i);
							if (blueSliderVal != null) {
								mBlueValues[i] = blueSliderVal;
								mixVal = msRight.getSliderValueAt(i);
								mBlueMixValues[i] = mixVal == null ? 1.0 : mixVal;
							}
							break;
					}
				}

				// set values considering values coming from the 'mix' multislider
				// should allow a non-linear, exponential crossfade

				// default values before being set through sliders
				rValue = rPixVal / 255.0;
				gValue = gPixVal / 255.0;
				bValue = bPixVal / 255.0;

				if (mApp.getColorMode().equals(RGBModes.RGB) || mApp.getColorMode().equals(RGBModes.R)) {
					if (mRedValues[i] != null) {
						if (mRedMixValues[i] != null && mRedMixValues[i] < 1.0) {
							double mixCubed = Math.pow(mRedMixValues[i], 3);
							double mixReciprCubed = Math.pow(1.0 - mRedMixValues[i], 3);
							double mult = 1.0 / (mixCubed + mixReciprCubed);
							rValue = (rPixVal / 255.0 * mixReciprCubed + mRedValues[i] * mixCubed) * mult;
						} else rValue = mRedValues[i];
					};
				}

				if (mApp.getColorMode().equals(RGBModes.RGB) || mApp.getColorMode().equals(RGBModes.G)) {
					if (mGreenValues[i] != null) {
						if (mGreenMixValues[i] != null && mGreenMixValues[i] < 1.0) {
							double mixCubed = Math.pow(mGreenMixValues[i], 3);
							double mixReciprCubed = Math.pow(1.0 - mGreenMixValues[i], 3);
							double mult = 1.0 / (mixCubed + mixReciprCubed);
							gValue = (gPixVal / 255.0 * mixReciprCubed + mGreenValues[i] * mixCubed) * mult;
						} else gValue = mGreenValues[i];
					};
				}

				if (mApp.getColorMode().equals(RGBModes.RGB) || mApp.getColorMode().equals(RGBModes.B)) {
					if (mBlueValues[i] != null) {
						if (mBlueMixValues[i] != null && mBlueMixValues[i] < 1.0) {
							double mixCubed = Math.pow(mBlueMixValues[i], 3);
							double mixReciprCubed = Math.pow(1.0 - mBlueMixValues[i], 3);
							double mult = 1.0 / (mixCubed + mixReciprCubed);
							bValue = (bPixVal / 255.0 * mixReciprCubed + mBlueValues[i] * mixCubed) * mult;
						} else bValue = mBlueValues[i];
					};
				}

//				if (i == 0)
//					Log.d(TAG, "0, after, red value: " + mRedValues[i] + ", green value: " + mGreenValues[i] + ", blue value: " + mBlueValues[i]);

				// pixels can only be set to ints in a range from 0-255
				if (mRedValues[i] != null) rPixVal = (int) Math.round(rValue * 255);
				if (mGreenValues[i] != null) gPixVal = (int) Math.round(gValue * 255);
				if (mBlueValues[i] != null) bPixVal = (int) Math.round(bValue * 255);
				/*if (i == 0)
					Log.d(TAG, "0, rPixVal: " + rPixVal + ", gPixVal: " + gPixVal + ". bPixVal: " + bPixVal);*/

				// set pixels
				if (!mApp.getPixelImageHidden()) {
					if (mApp.getColorMode().equals(RGBModes.RGB)) {
						pixels[i] = Color.argb(255, rPixVal, gPixVal, bPixVal);
					} else if (mApp.getColorMode().equals(RGBModes.R)) {
						pixels[i] = Color.argb(255, rPixVal, 0, 0);
					} else if (mApp.getColorMode().equals(RGBModes.G)) {
						pixels[i] = Color.argb(255, 0, gPixVal, 0);
					} else if (mApp.getColorMode().equals(RGBModes.B)) {
						pixels[i] = Color.argb(255, 0, 0, bPixVal);
					}
				} else {
					// all pixels fully transparent
					pixels[i] = Color.argb(0, 0, 0, 0);
				}

				// compose basic OSC message for slot
				if (mApp.getCameraOSCisPlaying()) {
//					if (calcsPerPeriod == 1) {
					if (mApp.getNormalized()) {
						rValue = (float) rPixVal / 255;
						gValue = (float) gPixVal / 255;
						bValue = (float) bPixVal / 255;
					} else {
						rValue = rPixVal;
						gValue = gPixVal;
						bValue = bPixVal;
					}

					// all OSC messaging (message construction sending) must happen synchronized
					// otherwise messages easily get overwritten during processing

					synchronized (mRedOscRunnable.mOscLock) {
						if (!offPxls.get(i)[0]) {
							oscR = mApp.mOscHelper.makeMessage(oscR, mRed + (i + 1));
							oscR.add(rValue);
							if (VideOSCApplication.getDebugPixelOsc()) {
								RedOscRunnable.setDebugPixelOsc(true);
								oscR.add(++mCountR);
							} else {
								RedOscRunnable.setDebugPixelOsc(false);
							}
							mRedOscRunnable.mMsg = oscR;
							mRedOscRunnable.mOscLock.notify();
						}
					}

					synchronized (mGreenOscRunnable.mOscLock) {
						if (!offPxls.get(i)[1]) {
							oscG = mApp.mOscHelper.makeMessage(oscG, mGreen + (i + 1));
							oscG.add(gValue);
							if (VideOSCApplication.getDebugPixelOsc()) {
								GreenOscRunnable.setDebugPixelOsc(true);
								oscG.add(++mCountG);
							} else {
								GreenOscRunnable.setDebugPixelOsc(false);
							}
							mGreenOscRunnable.mMsg = oscG;
							mGreenOscRunnable.mOscLock.notify();
						}
					}

					synchronized (mBlueOscRunnable.mOscLock) {
						if (!offPxls.get(i)[2]) {
							oscB = mApp.mOscHelper.makeMessage(oscB, mBlue + (i + 1));
							oscB.add(bValue);
							if (VideOSCApplication.getDebugPixelOsc()) {
								BlueOscRunnable.setDebugPixelOsc(true);
								oscB.add(++mCountB);
							} else {
								BlueOscRunnable.setDebugPixelOsc(false);
							}
							mBlueOscRunnable.mMsg = oscB;
							mBlueOscRunnable.mOscLock.notify();
						}
					}
				}
			}

			bmp.setPixels(pixels, 0, width, 0, 0, width, height);
			return bmp;
		}
	}

	// prevent memory leaks by declaring Runnable static
	// see also https://stackoverflow.com/questions/29694222/is-this-runnable-safe-from-memory-leak
	// or http://www.androiddesignpatterns.com/2013/04/activitys-threads-memory-leaks.html
	private static class RedOscRunnable implements Runnable {
		private volatile OscMessage mMsg;
		private final Object mOscLock = new Object();
		private long mCountSentR = 0;
		private static boolean mDebugPixel = false;
		private static VideOSCOscHandler mOscHelper;

		private static void setDebugPixelOsc(boolean debugPixel) {
			mDebugPixel = debugPixel;
		}

		private static void setOscHelper(VideOSCOscHandler oscHelper) {
			mOscHelper = oscHelper;
		}

		/**
		 * When an object implementing interface <code>Runnable</code> is used
		 * to create a thread, starting the thread causes the object's
		 * <code>run</code> method to be called in that separately executing
		 * thread.
		 * <p>
		 * The general contract of the method <code>run</code> is that it may
		 * take any action whatsoever.
		 *
		 * @see Thread#run()
		 */
		@Override
		@SuppressWarnings("InfiniteLoopStatement")
		public void run() {
			while (true) {
				synchronized (mOscLock) {
					try {
						if (mMsg != null && mMsg.addrPattern().length() > 0 && mMsg.arguments().length > 0) {
							if (mDebugPixel)
								mMsg.add(++mCountSentR);
							mOscP5.send(mMsg, mOscHelper.getBroadcastAddr());
						}
						mOscLock.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	private static class GreenOscRunnable implements Runnable {
		private volatile OscMessage mMsg;
		private final Object mOscLock = new Object();
		private long mCountSentG = 0;
		private static boolean mDebugPixel = false;
		private static VideOSCOscHandler mOscHelper;

		private static void setDebugPixelOsc(boolean debugPixel) {
			mDebugPixel = debugPixel;
		}

		private static void setOscHelper(VideOSCOscHandler oscHelper) {
			mOscHelper = oscHelper;
		}

		/**
		 * When an object implementing interface <code>Runnable</code> is used
		 * to create a thread, starting the thread causes the object's
		 * <code>run</code> method to be called in that separately executing
		 * thread.
		 * <p>
		 * The general contract of the method <code>run</code> is that it may
		 * take any action whatsoever.
		 *
		 * @see Thread#run()
		 */
		@Override
		@SuppressWarnings("InfiniteLoopStatement")
		public void run() {
			while (true) {
				synchronized (mOscLock) {
					try {
						if (mMsg != null && mMsg.addrPattern().length() > 0 && mMsg.arguments().length > 0) {
							if (mDebugPixel)
								mMsg.add(++mCountSentG);
							mOscP5.send(mMsg, mOscHelper.getBroadcastAddr());
						}
						mOscLock.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	private static class BlueOscRunnable implements Runnable {
		private volatile OscMessage mMsg;
		private final Object mOscLock = new Object();
		private long mCountSentB = 0;
		private static boolean mDebugPixel = false;
		private static VideOSCOscHandler mOscHelper;

		private static void setDebugPixelOsc(boolean debugPixel) {
			mDebugPixel = debugPixel;
		}

		private static void setOscHelper(VideOSCOscHandler oscHelper) {
			mOscHelper = oscHelper;
		}

		/**
		 * When an object implementing interface <code>Runnable</code> is used
		 * to create a thread, starting the thread causes the object's
		 * <code>run</code> method to be called in that separately executing
		 * thread.
		 * <p>
		 * The general contract of the method <code>run</code> is that it may
		 * take any action whatsoever.
		 *
		 * @see Thread#run()
		 */
		@Override
		@SuppressWarnings("InfiniteLoopStatement")
		public void run() {
			while (true) {
				synchronized (mOscLock) {
					try {
						if (mMsg != null && mMsg.addrPattern().length() > 0 && mMsg.arguments().length > 0) {
							if (mDebugPixel)
								mMsg.add(++mCountSentB);
							mOscP5.send(mMsg, mOscHelper.getBroadcastAddr());
						}
						mOscLock.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

}
