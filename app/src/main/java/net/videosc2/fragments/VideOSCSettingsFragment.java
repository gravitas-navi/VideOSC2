package net.videosc2.fragments;

import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import net.videosc2.R;
import net.videosc2.VideOSCApplication;
import net.videosc2.db.SettingsContract;
import net.videosc2.utilities.VideOSCUIHelpers;

//import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ketai.net.KetaiNet;

/**
 * Created by stefan on 12.03.17.
 */

public class VideOSCSettingsFragment extends VideOSCBaseFragment {
	private final static String TAG = "VideOSCSettingsFragment";
	public VideOSCSettingsFragment() {}

/*
	public static VideOSCSettingsFragment newInstance() {
		VideOSCSettingsFragment s = new VideOSCSettingsFragment();
		Bundle args = new Bundle();
		s.setArguments(args);
		return s;
	}
*/

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
	                         Bundle savedInstanceState) {
		// the background scrollview - dark transparent, no content
		final ScrollView bg = (ScrollView) inflater.inflate(R.layout.settings_background_scroll, container, false);
		// the view holding the main selection of settings
		View view = inflater.inflate(R.layout.settings_selection, bg, false);
		// the listview finally holding the links to different settings: network, resolution, sensors, about
		final ListView settingsListView = (ListView) view.findViewById(R.id.settings_selection_list);
		// the network settings form
		final View networkSettingsView = inflater.inflate(R.layout.network_settings, bg, false);
		// the resolution settings form
		final View resolutionSettingsView = inflater.inflate(R.layout.resolution_settings, bg, false);
		// the sensor settings form
		final View sensorSettingsView = inflater.inflate(R.layout.sensor_settings, bg, false);
		// about
		final View aboutView = inflater.inflate(R.layout.about, bg, false);
		final WebView webView = (WebView) aboutView.findViewById(R.id.html_about);
//		final SQLiteDatabase db = VideOSCMainActivity.mDbHelper.getReadableDatabase();
		final FragmentManager fragmentManager = getFragmentManager();

		// get application methods and avoid reflection
		final VideOSCApplication app = (VideOSCApplication) getActivity().getApplicationContext();
		// the database
		final SQLiteDatabase db = app.getSettingsHelper().getReadableDatabase();

		// get the setting items for the main selection list and parse them into the layout
		String[] items = getResources().getStringArray(R.array.settings_select_items);
		ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(this.getActivity(), R.layout.settings_selection_item, items);
		settingsListView.setAdapter(itemsAdapter);
		// does the fade-in animation really work?...
		VideOSCUIHelpers.setTransitionAnimation(bg);
		// add the scroll view background to the container (camView)
		container.addView(bg);

		settingsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				String[] settingsFields;
				String[] addrFields;
				final List<Address> addresses = new ArrayList<>();
				final List<Settings> settings = new ArrayList<>();
				final ContentValues values = new ContentValues();
				final VideOSCCameraFragment cameraView = (VideOSCCameraFragment) fragmentManager.findFragmentByTag("CamPreview");

				app.setSettingsLevel(2);

				switch (i) {
					case 0:
						// network settings
						VideOSCUIHelpers.addView(networkSettingsView, bg);

						// table vosc_client_addresses
						addrFields = new String[]{
								SettingsContract.AddressSettingsEntry._ID,
								SettingsContract.AddressSettingsEntry.IP_ADDRESS,
								SettingsContract.AddressSettingsEntry.PORT
						};
						String sortOrder =
								SettingsContract.AddressSettingsEntry.IP_ADDRESS + " DESC";

						// receive port and root cmd are store stored within regular settings
						// table vosc_settings
						settingsFields = new String[]{
								SettingsContract.SettingsEntries._ID,
								SettingsContract.SettingsEntries.UDP_RECEIVE_PORT,
								SettingsContract.SettingsEntries.ROOT_CMD
						};

						Cursor cursor = db.query(
								SettingsContract.AddressSettingsEntry.TABLE_NAME,
								addrFields,
								null,
								null,
								null,
								null,
								sortOrder
						);

/*
						String[] columns = cursor.getColumnNames();
						for (String colName : columns) {
							Log.d(TAG, "column: " + colName);
						}
*/
						// clear list of addresses before adding new content
						addresses.clear();

						while (cursor.moveToNext()) {
							Address address = new Address();
							long rowId =
									cursor.getLong(cursor.getColumnIndexOrThrow(SettingsContract.AddressSettingsEntry._ID));
							String ip =
									cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.AddressSettingsEntry.IP_ADDRESS));
							int port =
									cursor.getInt(cursor.getColumnIndexOrThrow(SettingsContract.AddressSettingsEntry.PORT));
							address.setRowId(rowId);
							address.setIP(ip);
							address.setPort(port);
							addresses.add(address);
						}

						cursor.close();

						final EditText remoteIPField = (EditText) networkSettingsView.findViewById(R.id.remote_ip_field);
						remoteIPField.setText(addresses.get(0).getIP(), TextView.BufferType.EDITABLE);
						final EditText remotePortField = (EditText) networkSettingsView.findViewById(R.id.remote_port_field);
						remotePortField.setText(
								String.format(Locale.getDefault(), "%d", addresses.get(0).getPort()),
								TextView.BufferType.EDITABLE
						);

						cursor = db.query(
								SettingsContract.SettingsEntries.TABLE_NAME,
								settingsFields,
								null,
								null,
								null,
								null,
								null
						);

						// clear list of settings before adding new content
						settings.clear();

						while (cursor.moveToNext()) {
							Settings setting = new Settings();
							long rowId = cursor.getLong(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries._ID));
							int udpReceivePort =
									cursor.getInt(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.UDP_RECEIVE_PORT));
							String cmd =
									cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.ROOT_CMD));
							setting.setRowId(rowId);
							setting.setUdpReceivePort(udpReceivePort);
							setting.setRootCmd(cmd);
							settings.add(setting);
						}

						cursor.close();

						final EditText udpReceivePortField = (EditText) networkSettingsView.findViewById(R.id.device_port_field);
						udpReceivePortField.setText(
								String.format(Locale.getDefault(), "%d", settings.get(0).getUdpReceivePort()),
								TextView.BufferType.EDITABLE
						);
						final EditText rootCmdField = (EditText) networkSettingsView.findViewById(R.id.root_cmd_name_field);
						rootCmdField.setText(settings.get(0).getRootCmd(), TextView.BufferType.EDITABLE);
						final TextView deviceIP = (TextView) networkSettingsView.findViewById(R.id.device_ip_address);
						deviceIP.setText(KetaiNet.getIP());

						remoteIPField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
							@Override
							public void onFocusChange(View v, boolean hasFocus) {
								if (!hasFocus && !remoteIPField.getText().toString().equals(addresses.get(0).getIP())) {
									values.put(
											SettingsContract.AddressSettingsEntry.IP_ADDRESS,
											remoteIPField.getText().toString()
									);
									db.update(
											SettingsContract.AddressSettingsEntry.TABLE_NAME,
											values,
											SettingsContract.AddressSettingsEntry._ID + " = " + addresses.get(0).getRowId(),
											null
									);
									values.clear();
								}
							}
						});
						remotePortField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
							@Override
							public void onFocusChange(View v, boolean hasFocus) {
								if (!hasFocus && !remotePortField.getText().toString().equals(String.format(Locale.getDefault(), "%d", addresses.get(0).getPort()))) {
									values.put(
											SettingsContract.AddressSettingsEntry.PORT,
											remotePortField.getText().toString()
									);
									db.update(
											SettingsContract.AddressSettingsEntry.TABLE_NAME,
											values,
											SettingsContract.AddressSettingsEntry._ID + " = " + addresses.get(0).getRowId(),
											null
									);
									values.clear();
								}
							}
						});
						udpReceivePortField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
							@Override
							public void onFocusChange(View v, boolean hasFocus) {
								if (!hasFocus && !udpReceivePortField.getText().toString().equals(
										String.format(Locale.getDefault(), "%d", settings.get(0).getUdpReceivePort()))) {
									values.put(
											SettingsContract.SettingsEntries.UDP_RECEIVE_PORT,
											udpReceivePortField.getText().toString()
									);
									db.update(
											SettingsContract.SettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SettingsEntries._ID + " = " + settings.get(0).getRowId(),
											null
									);
									values.clear();
								}
							}
						});
						rootCmdField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
							@Override
							public void onFocusChange(View v, boolean hasFocus) {
								if (!hasFocus && !rootCmdField.getText().toString().equals(settings.get(0).getRootCmd())) {
									values.put(
											SettingsContract.SettingsEntries.ROOT_CMD,
											rootCmdField.getText().toString()
									);
									db.update(
											SettingsContract.SettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SettingsEntries._ID + " = " + settings.get(0).getRowId(),
											null
									);
									values.clear();
								}

							}
						});
						break;
					case 1:
						// resolution settings
						VideOSCUIHelpers.addView(resolutionSettingsView, bg);

						// table vosc_settings
						settingsFields = new String[]{
								SettingsContract.SettingsEntries._ID,
								SettingsContract.SettingsEntries.RES_H,
								SettingsContract.SettingsEntries.RES_V,
								SettingsContract.SettingsEntries.CALC_PERIOD,
								SettingsContract.SettingsEntries.FRAMERATE_FIXED,
								SettingsContract.SettingsEntries.NORMALIZE,
								SettingsContract.SettingsEntries.REMEMBER_PIXEL_STATES
						};

						cursor = db.query(
								SettingsContract.SettingsEntries.TABLE_NAME,
								settingsFields,
								null,
								null,
								null,
								null,
								null
						);

						// clear list of settings before adding new content
						settings.clear();

						while (cursor.moveToNext()) {
							Settings setting = new Settings();
							long rowId = cursor.getLong(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries._ID));
							short resH = cursor.getShort(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.RES_H));
							short resV = cursor.getShort(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.RES_V));
							short calcPeriod =
									cursor.getShort(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.CALC_PERIOD));
							short framerateFixed =
									cursor.getShort(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.FRAMERATE_FIXED));
							short normalized =
									cursor.getShort(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.NORMALIZE));
							short rememberPixelStates =
									cursor.getShort(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.REMEMBER_PIXEL_STATES));

							setting.setRowId(rowId);
							setting.setResolutionHorizontal(resH);
							setting.setResolutionVertical(resV);
							setting.setCalculationPeriod(calcPeriod);
							setting.setFramerateFixed(framerateFixed);
							setting.setNormalized(normalized);
							setting.setRememberPixelStates(rememberPixelStates);
							settings.add(setting);
						}

						cursor.close();

						final EditText resHField =
								(EditText) resolutionSettingsView.findViewById(R.id.resolution_horizontal_field);
						resHField.setText(
								String.format(Locale.getDefault(), "%d", settings.get(0).getResolutionHorizontal()),
								TextView.BufferType.EDITABLE
						);
						final EditText resVField =
								(EditText) resolutionSettingsView.findViewById(R.id.resolution_vertical_field);
						resVField.setText(
								String.format(Locale.getDefault(), "%d", settings.get(0).getResolutionVertical()),
								TextView.BufferType.EDITABLE
						);
						final EditText calcPeriodField =
								(EditText) resolutionSettingsView.findViewById(R.id.calulation_period_field);
						calcPeriodField.setText(
								String.format(Locale.getDefault(), "%d", settings.get(0).getCalculationPeriod()),
								TextView.BufferType.EDITABLE
						);

						final Button selectFramerate =
								(Button) resolutionSettingsView.findViewById(R.id.framerate_selection);
						selectFramerate.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								Camera.Parameters params = cameraView.mCamera.getParameters();
								// FIXME
//								ListView framerateSelection = (ListView) resolutionSettingsView.findViewById()
								List<int[]> supportedPreviewFpsRange = params.getSupportedPreviewFpsRange();
								String[] items = new String[supportedPreviewFpsRange.size()];
								for (int j = 0; j < supportedPreviewFpsRange.size(); j++) {
									int[] item = supportedPreviewFpsRange.get(j);
									items[j] = item[0] + " / " + item[1];
								}
								ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(getActivity(), R.layout.framerate_selection_item, items);
							}
						});

						final CheckBox normalizedCB =
								(CheckBox) resolutionSettingsView.findViewById(R.id.normalize_output_checkbox);
						normalizedCB.setChecked(settings.get(0).getNormalized());
						final CheckBox rememberPixelStatesCB =
								(CheckBox) resolutionSettingsView.findViewById(R.id.remember_activated_checkbox);
						rememberPixelStatesCB.setChecked(settings.get(0).getRememberPixelStates());

						resHField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
							@Override
							public void onFocusChange(View v, boolean hasFocus) {
								if (!hasFocus && !resHField.getText().toString().equals(
										String.format(Locale.getDefault(), "%d", settings.get(0).getResolutionHorizontal()))) {
									values.put(
											SettingsContract.SettingsEntries.RES_H,
											resHField.getText().toString()
									);
									db.update(
											SettingsContract.SettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SettingsEntries._ID + " = " + settings.get(0).getRowId(),
											null
									);
									values.clear();
									// update camera preview immediately
									cameraView.setResolution(
											Integer.parseInt(resHField.getText().toString()),
											cameraView.getResolution().y
									);
								}
							}
						});
						resVField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
							@Override
							public void onFocusChange(View v, boolean hasFocus) {
								if (!hasFocus && !resVField.getText().toString().equals(
										String.format(Locale.getDefault(), "%d", settings.get(0).getResolutionVertical()))) {
									values.put(
											SettingsContract.SettingsEntries.RES_V,
											resVField.getText().toString()
									);
									db.update(
											SettingsContract.SettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SettingsEntries._ID + " = " + settings.get(0).getRowId(),
											null
									);
									values.clear();
									// update camera preview immediately
									cameraView.setResolution(
											cameraView.getResolution().x,
											Integer.parseInt(resVField.getText().toString())
									);
								}
							}
						});
						calcPeriodField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
							@Override
							public void onFocusChange(View v, boolean hasFocus) {
								if (!hasFocus && !calcPeriodField.getText().toString().equals(
										String.format(Locale.getDefault(), "%d", settings.get(0).getCalculationPeriod()))) {
									values.put(
											SettingsContract.SettingsEntries.CALC_PERIOD,
											calcPeriodField.getText().toString()
									);
									db.update(
											SettingsContract.SettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SettingsEntries._ID + " = " + settings.get(0).getRowId(),
											null
									);
									values.clear();
								}
							}
						});
/*
						fixFramerateCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (fixFramerateCB.isChecked() != settings.get(0).getFramerateFixed()) {
									values.put(
											SettingsContract.SettingsEntries.FRAMERATE_FIXED,
											fixFramerateCB.isChecked()
									);
									db.update(
											SettingsContract.SettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SettingsEntries._ID + " = " + settings.get(0).getRowId(),
											null
									);
									values.clear();
									// update camera preview immediately
									cameraView.setFramerateFixed(fixFramerateCB.isChecked() ? 1 : 0);
								}
							}
						});
*/
						normalizedCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (normalizedCB.isChecked() != settings.get(0).getNormalized()) {
									values.put(
											SettingsContract.SettingsEntries.NORMALIZE,
											normalizedCB.isChecked()
									);
									db.update(
											SettingsContract.SettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SettingsEntries._ID + " = " + settings.get(0).getRowId(),
											null
									);
									values.clear();
								}
							}
						});
						rememberPixelStatesCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (rememberPixelStatesCB.isChecked() != settings.get(0).getRememberPixelStates()) {
									values.put(
											SettingsContract.SettingsEntries.REMEMBER_PIXEL_STATES,
											rememberPixelStatesCB.isChecked()
									);
									db.update(
											SettingsContract.SettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SettingsEntries._ID + " = " + settings.get(0).getRowId(),
											null
									);
									values.clear();
								}
							}
						});
						break;
					case 2:
						// sensor settings
						VideOSCUIHelpers.addView(sensorSettingsView, bg);
						setPlaceholder(bg);

						final Sensors sensors = new Sensors();

						cursor = db.rawQuery("SELECT * FROM " + SettingsContract.SensorSettingsEntries.TABLE_NAME, null);

						while (cursor.moveToNext()) {
							switch (cursor.getString(cursor.getColumnIndex(SettingsContract.SensorSettingsEntries.SENSOR))) {
								case "ori":
									sensors.setOrientationSensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "acc":
									sensors.setAccelerationSensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "lin_acc":
									sensors.setLinAccelerationSensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "mag":
									sensors.setMagneticSensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "grav":
									sensors.setGravitySensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "prox":
									sensors.setProximitySensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "light":
									sensors.setLightSensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "press":
									sensors.setPressureSensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "temp":
									sensors.setTemperatureSensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "hum":
									sensors.setHumiditySensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								case "loc":
									sensors.setLocationSensorActivated(Short.parseShort(
											cursor.getString(
													cursor.getColumnIndexOrThrow(SettingsContract.SensorSettingsEntries.VALUE)
											)
									));
									break;
								default:
							}
						}

						final CheckBox oriCB = (CheckBox) sensorSettingsView.findViewById(R.id.orientation_sensor);
						final CheckBox accCB = (CheckBox) sensorSettingsView.findViewById(R.id.accelerometer);
						final CheckBox linAccCB = (CheckBox) sensorSettingsView.findViewById(R.id.linear_acceleration);
						final CheckBox magCB = (CheckBox) sensorSettingsView.findViewById(R.id.magnetic_field);
						final CheckBox gravCB = (CheckBox) sensorSettingsView.findViewById(R.id.gravity_sensor);
						final CheckBox proxCB = (CheckBox) sensorSettingsView.findViewById(R.id.proximity_sensor);
						final CheckBox lightCB = (CheckBox) sensorSettingsView.findViewById(R.id.light_sensor);
						final CheckBox pressCB = (CheckBox) sensorSettingsView.findViewById(R.id.air_pressure_sensor);
						final CheckBox tempCB = (CheckBox) sensorSettingsView.findViewById(R.id.temperature_sensor);
						final CheckBox humCB = (CheckBox) sensorSettingsView.findViewById(R.id.humidity_sensor);
						final CheckBox locCB = (CheckBox) sensorSettingsView.findViewById(R.id.geo_loc_sensor);

						oriCB.setChecked(sensors.getOrientationSensorActivated());
						accCB.setChecked(sensors.getAccelerationSensorActivated());
						linAccCB.setChecked(sensors.getLinAccelerationSensorActivated());
						magCB.setChecked(sensors.getMagneticSensorActivated());
						gravCB.setChecked(sensors.getGravitySensorActivated());
						proxCB.setChecked(sensors.getProximitySensorActivated());
						lightCB.setChecked(sensors.getLightSensorActivated());
						pressCB.setChecked(sensors.getPressureSensorActivated());
						tempCB.setChecked(sensors.getTemperatureSensorActivated());
						humCB.setChecked(sensors.getHumiditySensorActivated());
						locCB.setChecked(sensors.getLocationSensorActivated());

						oriCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (oriCB.isChecked() != sensors.getOrientationSensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, oriCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'ori'",
											null
									);
									values.clear();
								}
							}
						});
						accCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (accCB.isChecked() != sensors.getAccelerationSensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, accCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'acc'",
											null
									);
									values.clear();
								}
							}
						});
						linAccCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (linAccCB.isChecked() != sensors.getLinAccelerationSensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, linAccCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'lin_acc'",
											null
									);
									values.clear();
								}
							}
						});
						magCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (magCB.isChecked() != sensors.getMagneticSensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, magCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'mag'",
											null
									);
									values.clear();
								}
							}
						});
						gravCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (gravCB.isChecked() != sensors.getGravitySensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, gravCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'grav'",
											null
									);
									values.clear();
								}
							}
						});
						proxCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (proxCB.isChecked() != sensors.getProximitySensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, proxCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'prox'",
											null
									);
									values.clear();
								}
							}
						});
						lightCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (lightCB.isChecked() != sensors.getLightSensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, lightCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'light'",
											null
									);
									values.clear();
								}
							}
						});
						pressCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (pressCB.isChecked() != sensors.getPressureSensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, pressCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'press'",
											null
									);
									values.clear();
								}
							}
						});
						tempCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (tempCB.isChecked() != sensors.getTemperatureSensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, tempCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'temp'",
											null
									);
									values.clear();
								}
							}
						});
						humCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (humCB.isChecked() != sensors.getHumiditySensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, humCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'hum'",
											null
									);
									values.clear();
								}
							}
						});
						locCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								if (locCB.isChecked() != sensors.getLocationSensorActivated()) {
									values.put(SettingsContract.SensorSettingsEntries.VALUE, locCB.isChecked());
									db.update(
											SettingsContract.SensorSettingsEntries.TABLE_NAME,
											values,
											SettingsContract.SensorSettingsEntries.SENSOR + " = 'loc'",
											null
									);
									values.clear();
								}
							}
						});

						break;
					case 3:
						// about
						webView.loadUrl("http://pustota.basislager.org/coding/videosc/");
						VideOSCUIHelpers.addView(aboutView, bg);
					default:
				}
				settingsListView.setVisibility(View.INVISIBLE);
			}
		});

		return view;
	}

	// preliminary - replacement for placeholders should come from stored settings
	public void setPlaceholder(View container) {
		Resources res = getResources();
		SparseIntArray idsAndStrings = new SparseIntArray(11);
		String rootCmd = "vosc";
		String[] settingsFields = new String[]{
				SettingsContract.SettingsEntries.ROOT_CMD
		};
//		SQLiteDatabase db = VideOSCMainActivity.mDbHelper.getReadableDatabase();
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

		if (cursor.moveToFirst())
			rootCmd = cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.SettingsEntries.ROOT_CMD));

		cursor.close();

		idsAndStrings.append(R.id.orientation_sensor, R.string.orientation_sensor);
		idsAndStrings.append(R.id.accelerometer, R.string.accelerometer);
		idsAndStrings.append(R.id.linear_acceleration, R.string.linear_acceleration);
		idsAndStrings.append(R.id.magnetic_field, R.string.magnetic_field_sensor);
		idsAndStrings.append(R.id.gravity_sensor, R.string.gravity_sensor);
		idsAndStrings.append(R.id.proximity_sensor, R.string.proximity_sensor);
		idsAndStrings.append(R.id.light_sensor, R.string.light_sensor);
		idsAndStrings.append(R.id.air_pressure_sensor, R.string.air_pressure);
		idsAndStrings.append(R.id.temperature_sensor, R.string.temperature_sensor);
		idsAndStrings.append(R.id.humidity_sensor, R.string.humidity_sensor);
		idsAndStrings.append(R.id.geo_loc_sensor, R.string.geo_location_sensor);


		for (int i = 0; i < idsAndStrings.size(); i++) {
			TextView tv = (TextView) container.findViewById(idsAndStrings.keyAt(i));
			String text = String.format(res.getString(idsAndStrings.valueAt(i)), rootCmd);
			tv.setText(text);
		}
	}

	private class Address {
		private long rowId;
		private String ip;
		private int port;
		private String protocol;

		Address() {};

		void setRowId(long id) {
			this.rowId = id;
		}

		void setIP(String ip) {
			this.ip = ip;
		}

		void setPort(int port) {
			this.port = port;
		}

		void setProtocol(String protocol) {
			this.protocol = protocol;
		}

		long getRowId() {
			return this.rowId;
		}

		String getIP() {
			return this.ip;
		}

		int getPort() {
			return this.port;
		}

		String getProtocol() {
			return this.protocol;
		}
	}

	private class Settings {
		private long rowId;
		private short resolutionHorizontal;
		private short resolutionVertical;
		private boolean framerateFixed;
		private boolean normalized;
		private boolean rememberPixelStates;
		private short calculationPeriod;
		private String rootCmd;
		private int udpReceivePort;
		private int tcpReceivePort;

		Settings() {}

		void setRowId(long id) {
			this.rowId = id;
		}

		void setResolutionHorizontal(short resolutionH) {
			this.resolutionHorizontal = resolutionH;
		}

		void setResolutionVertical(short resolutionV) {
			this.resolutionVertical = resolutionV;
		}

		void setFramerateFixed(short boolVal) {
			this.framerateFixed = boolVal > 0;
		}

		void setNormalized(short boolVal) {
			this.normalized = boolVal > 0;
		}

		void setRememberPixelStates(short boolVal) {
			this.rememberPixelStates = boolVal > 0;
		}

		void setCalculationPeriod(short calcPeriod) {
			this.calculationPeriod = calcPeriod;
		}

		void setRootCmd(String cmdName) {
			this.rootCmd = cmdName;
		}

		void setUdpReceivePort(int port) {
			this.udpReceivePort = port;
		}

		void setTcpReceivePort(int port) {
			this.tcpReceivePort = port;
		}

		long getRowId() {
			return this.rowId;
		}

		short getResolutionHorizontal() {
			return this.resolutionHorizontal;
		}

		short getResolutionVertical() {
			return this.resolutionVertical;
		}

		boolean getFramerateFixed() {
			return this.framerateFixed;
		}

		boolean getNormalized() {
			return this.normalized;
		}

		boolean getRememberPixelStates() {
			return this.rememberPixelStates;
		}

		short getCalculationPeriod() {
			return this.calculationPeriod;
		}

		String getRootCmd() {
			return this.rootCmd;
		}

		int getUdpReceivePort() {
			return this.udpReceivePort;
		}

		int getTcpReceivePort() {
			return this.tcpReceivePort;
		}
	}

	private class Sensors {
		private long rowId;
		private boolean orientationSensorActivated;
		private boolean accelerationSensorActivated;
		private boolean linAccelerationSensorActivated;
		private boolean magneticSensorActivated;
		private boolean gravitySensorActivated;
		private boolean proximitySensorActivated;
		private boolean lightSensorActivated;
		private boolean pressureSensorActivated;
		private boolean temperatureSensorActivated;
		private boolean humiditySensorActivated;
		private boolean locationSensorActivated;

		Sensors() {}

		void setRowId(long rowId) {
			this.rowId = rowId;
		}

		void setOrientationSensorActivated(short boolVal) {
			this.orientationSensorActivated = boolVal > 0;
		}

		void setAccelerationSensorActivated(short boolVal) {
			this.accelerationSensorActivated = boolVal > 0;
		}

		void setLinAccelerationSensorActivated(short boolVal) {
			this.linAccelerationSensorActivated = boolVal > 0;
		}

		void setMagneticSensorActivated(short boolVal) {
			this.magneticSensorActivated = boolVal > 0;
		}

		void setGravitySensorActivated(short boolVal) {
			this.gravitySensorActivated = boolVal > 0;
		}

		void setProximitySensorActivated(short boolVal) {
			this.proximitySensorActivated = boolVal > 0;
		}

		void setLightSensorActivated(short boolVal) {
			this.lightSensorActivated = boolVal > 0;
		}

		void setPressureSensorActivated(short boolVal) {
			this.pressureSensorActivated = boolVal > 0;
		}

		void setTemperatureSensorActivated(short boolVal) {
			this.temperatureSensorActivated = boolVal > 0;
		}

		void setHumiditySensorActivated(short boolVal) {
			this.humiditySensorActivated = boolVal > 0;
		}

		void setLocationSensorActivated(short boolVal) {
			this.locationSensorActivated = boolVal > 0;
		}

		long getRowId() {
			return this.rowId;
		}

		boolean getOrientationSensorActivated() {
			return this.orientationSensorActivated;
		}

		boolean getAccelerationSensorActivated() {
			return this.accelerationSensorActivated;
		}

		boolean getLinAccelerationSensorActivated() {
			return this.linAccelerationSensorActivated;
		}

		boolean getMagneticSensorActivated() {
			return this.magneticSensorActivated;
		}

		boolean getGravitySensorActivated() {
			return this.gravitySensorActivated;
		}

		boolean getProximitySensorActivated() {
			return this.proximitySensorActivated;
		}

		boolean getLightSensorActivated() {
			return this.lightSensorActivated;
		}

		boolean getPressureSensorActivated() {
			return this.pressureSensorActivated;
		}

		boolean getTemperatureSensorActivated() {
			return this.temperatureSensorActivated;
		}

		boolean getHumiditySensorActivated() {
			return this.humiditySensorActivated;
		}

		boolean getLocationSensorActivated() {
			return this.locationSensorActivated;
		}
	}
}
