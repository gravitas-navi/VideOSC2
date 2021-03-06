package net.videosc.fragments.settings;

import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.videosc.R;
import net.videosc.VideOSCApplication;
import net.videosc.activities.VideOSCMainActivity;
import net.videosc.fragments.VideOSCBaseFragment;
import net.videosc.fragments.VideOSCCameraFragment;
import net.videosc.utilities.VideOSCUIHelpers;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class VideOSCSettingsListFragment extends VideOSCBaseFragment {
	private final static String TAG = "VideOSCSettingsList";
	private VideOSCApplication mApp;

	/**
	 * @param savedInstanceState
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	/**
	 * @param inflater
	 * @param container
	 * @param savedInstanceState
	 */
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final FragmentManager fragmentManager = getFragmentManager();
		final VideOSCNetworkSettingsFragment networkSettingsFragment = new VideOSCNetworkSettingsFragment();
		final VideOSCResolutionSettingsFragment resolutionSettingsFragment = new VideOSCResolutionSettingsFragment();
		final VideOSCSensorSettingsFragment sensorSettingsFragment = new VideOSCSensorSettingsFragment();
		final VideOSCDebugSettingsFragment debugSettingsFragment = new VideOSCDebugSettingsFragment();
		final VideOSCAboutFragment aboutFragment = new VideOSCAboutFragment();
		assert fragmentManager != null;
		final VideOSCCameraFragment cameraView = (VideOSCCameraFragment) fragmentManager.findFragmentByTag("CamPreview");
		assert cameraView != null;
		final Camera.Parameters params = cameraView.mCamera.getParameters();
		final View view = inflater.inflate(R.layout.settings_container, container, false);
		final View settingsView = view.findViewById(R.id.settings_container);
		final ListView settingsListView = view.findViewById(R.id.settings_list);
		final VideOSCMainActivity activity = (VideOSCMainActivity) getActivity();
		assert activity != null;
		mApp = (VideOSCApplication) activity.getApplicationContext();
		mApp.setSettingsContainerID(this.getId());

		// get the setting items for the main selection list and parse them into the layout
		final String[] items = getResources().getStringArray(R.array.settings_select_items);
		final ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(activity, R.layout.settings_selection_item, items);
		settingsListView.setAdapter(itemsAdapter);
		// does the fade-in animation really work?...
		VideOSCUIHelpers.setTransitionAnimation(container);
		view.setVisibility(View.VISIBLE);

		settingsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				if (!mApp.getIsTablet())
					settingsListView.setVisibility(View.INVISIBLE);
				settingsView.setBackgroundResource(R.color.colorDarkTransparentBackground);
				final FragmentTransaction ft = fragmentManager.beginTransaction();
				switch (i) {
					case 0:
						ft.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
								.replace(R.id.settings_container, networkSettingsFragment)
								.commit();
						mApp.setNetworkSettingsID(networkSettingsFragment.getId());
						break;
					case 1:
						ft.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
								.replace(R.id.settings_container, resolutionSettingsFragment)
								.commit();
						mApp.setResolutionSettingsID(resolutionSettingsFragment.getId());
						break;
					case 2:
						ft.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
								.replace(R.id.settings_container, sensorSettingsFragment)
								.commit();
						mApp.setSensorSettingsID(sensorSettingsFragment.getId());
						break;
					case 3:
						ft.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
								.replace(R.id.settings_container, debugSettingsFragment)
								.commit();
						mApp.setDebugSettingsID(debugSettingsFragment.getId());
						break;
					case 4:
						ft.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
								.replace(R.id.settings_container, aboutFragment)
								.commit();
						mApp.setAboutSettingsID(aboutFragment.getId());
						break;
					default:
				}
			}
		});

		return view;
	}

	@Override
	public void onPause() {
		Log.d(TAG, "'onPause()' called");
		super.onPause();
	}

	/**
	 * Called when the Fragment is no longer started.  This is generally
	 * tied to 'onStop()' of the containing
	 * Activity's lifecycle.
	 */
	@Override
	public void onStop() {
		Log.d(TAG, "'onStop()' called");
		super.onStop();
	}

	/**
	 * Called when the view previously created by {@link #onCreateView} has
	 * been detached from the fragment.  The next time the fragment needs
	 * to be displayed, a new view will be created.  This is called
	 * after {@link #onStop()} and before {@link #onDestroy()}.  It is called
	 * <em>regardless</em> of whether {@link #onCreateView} returned a
	 * non-null view.  Internally it is called after the view's state has
	 * been saved but before it has been removed from its parent.
	 */
	@Override
	public void onDestroyView() {
		Log.d(TAG, "'onDestroyView()' called");
		mApp.setSettingsContainerID(-1);
		super.onDestroyView();
	}

	/**
	 * Called when the fragment is no longer in use.  This is called
	 * after {@link #onStop()} and before {@link #onDetach()}.
	 */
	@Override
	public void onDestroy() {
		Log.d(TAG, "'onDestroy()' called");
		super.onDestroy();
	}

	/**
	 * Called when the fragment is no longer attached to its activity.  This
	 * is called after {@link #onDestroy()}.
	 */
	@Override
	public void onDetach() {
		Log.d(TAG, "'onDetach()' called");
		super.onDetach();
	}

	static class Address {
		private long mRowId;
		private String mIp;
		private int mPort;
		private int mReceivePort;
		private String mProtocol;

		Address() { }

		void setRowId(long id) {
			this.mRowId = id;
		}

		void setIP(String ip) {
			this.mIp = ip;
		}

		void setPort(int port) {
			this.mPort = port;
		}

		void setReceivePort(int port) {
			this.mReceivePort = port;
		}

		void setProtocol(String protocol) {
			this.mProtocol = protocol;
		}

		long getRowId() {
			return this.mRowId;
		}

		String getIP() {
			return this.mIp;
		}

		int getPort() {
			return this.mPort;
		}

		int getReceivePort() {
			return this.mReceivePort;
		}

		String getProtocol() {
			return this.mProtocol;
		}
	}

	static class Settings {
		private long mRowId;
		private short mResolutionHorizontal;
		private short mResolutionVertical;
		private short mFramerateRange;
		private boolean mNormalized;
		private boolean mRememberPixelStates;
		private short mCalculationPeriod;
		private String mRootCmd;
		private int mUdpReceivePort;
		private int mTcpReceivePort;

		Settings() { }

		void setRowId(long id) {
			this.mRowId = id;
		}

		void setResolutionHorizontal(short resolutionH) {
			this.mResolutionHorizontal = resolutionH;
		}

		void setResolutionVertical(short resolutionV) {
			this.mResolutionVertical = resolutionV;
		}

		void setFramerateRange(short index) {
			this.mFramerateRange = index;
		}

		void setNormalized(short boolVal) {
			this.mNormalized = boolVal > 0;
		}

		void setRememberPixelStates(short boolVal) {
			this.mRememberPixelStates = boolVal > 0;
		}

		void setCalculationPeriod(short calcPeriod) {
			this.mCalculationPeriod = calcPeriod;
		}

		void setRootCmd(String cmdName) {
			this.mRootCmd = cmdName;
		}

		void setUdpReceivePort(int port) {
			this.mUdpReceivePort = port;
		}

		void setTcpReceivePort(int port) {
			this.mTcpReceivePort = port;
		}

		long getRowId() {
			return this.mRowId;
		}

		short getResolutionHorizontal() {
			return this.mResolutionHorizontal;
		}

		short getResolutionVertical() {
			return this.mResolutionVertical;
		}

		short getFramerateRange() {
			return this.mFramerateRange;
		}

		boolean getNormalized() {
			return this.mNormalized;
		}

		boolean getRememberPixelStates() {
			return this.mRememberPixelStates;
		}

		short getCalculationPeriod() {
			return this.mCalculationPeriod;
		}

		String getRootCmd() {
			return this.mRootCmd;
		}

		int getUdpReceivePort() {
			return this.mUdpReceivePort;
		}

		int getTcpReceivePort() {
			return this.mTcpReceivePort;
		}
	}

	static class Sensors {
		private long mRowId;
		private boolean mOrientationSensorActivated;
		private boolean mAccelerationSensorActivated;
		private boolean mLinAccelerationSensorActivated;
		private boolean mMagneticSensorActivated;
		private boolean mGravitySensorActivated;
		private boolean mProximitySensorActivated;
		private boolean mLightSensorActivated;
		private boolean mPressureSensorActivated;
		private boolean mTemperatureSensorActivated;
		private boolean mHumiditySensorActivated;
		private boolean mLocationSensorActivated;

		Sensors() { }

		void setRowId(long rowId) {
			this.mRowId = rowId;
		}

		void setOrientationSensorActivated(short boolVal) {
			this.mOrientationSensorActivated = boolVal > 0;
		}

		void setAccelerationSensorActivated(short boolVal) {
			this.mAccelerationSensorActivated = boolVal > 0;
		}

		void setLinAccelerationSensorActivated(short boolVal) {
			this.mLinAccelerationSensorActivated = boolVal > 0;
		}

		void setMagneticSensorActivated(short boolVal) {
			this.mMagneticSensorActivated = boolVal > 0;
		}

		void setGravitySensorActivated(short boolVal) {
			this.mGravitySensorActivated = boolVal > 0;
		}

		void setProximitySensorActivated(short boolVal) {
			this.mProximitySensorActivated = boolVal > 0;
		}

		void setLightSensorActivated(short boolVal) {
			this.mLightSensorActivated = boolVal > 0;
		}

		void setPressureSensorActivated(short boolVal) {
			this.mPressureSensorActivated = boolVal > 0;
		}

		void setTemperatureSensorActivated(short boolVal) {
			this.mTemperatureSensorActivated = boolVal > 0;
		}

		void setHumiditySensorActivated(short boolVal) {
			this.mHumiditySensorActivated = boolVal > 0;
		}

		void setLocationSensorActivated(short boolVal) {
			this.mLocationSensorActivated = boolVal > 0;
		}

		long getRowId() {
			return this.mRowId;
		}

		boolean getOrientationSensorActivated() {
			return this.mOrientationSensorActivated;
		}

		boolean getAccelerationSensorActivated() {
			return this.mAccelerationSensorActivated;
		}

		boolean getLinAccelerationSensorActivated() {
			return this.mLinAccelerationSensorActivated;
		}

		boolean getMagneticSensorActivated() {
			return this.mMagneticSensorActivated;
		}

		boolean getGravitySensorActivated() {
			return this.mGravitySensorActivated;
		}

		boolean getProximitySensorActivated() {
			return this.mProximitySensorActivated;
		}

		boolean getLightSensorActivated() {
			return this.mLightSensorActivated;
		}

		boolean getPressureSensorActivated() {
			return this.mPressureSensorActivated;
		}

		boolean getTemperatureSensorActivated() {
			return this.mTemperatureSensorActivated;
		}

		boolean getHumiditySensorActivated() {
			return this.mHumiditySensorActivated;
		}

		boolean getLocationSensorActivated() {
			return this.mLocationSensorActivated;
		}
	}
}
