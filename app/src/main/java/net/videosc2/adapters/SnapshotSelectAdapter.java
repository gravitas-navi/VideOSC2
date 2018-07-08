package net.videosc2.adapters;

import android.app.FragmentManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Point;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import net.videosc2.R;
import net.videosc2.VideOSCApplication;
import net.videosc2.activities.VideOSCMainActivity;
import net.videosc2.db.SettingsContract;
import net.videosc2.fragments.VideOSCCameraFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SnapshotSelectAdapter extends ResourceCursorAdapter {
	final private static String TAG = "SnapshotSelectAdapter";
	private int mLayout;

	private VideOSCApplication mApp;
	private VideOSCCameraFragment mCameraFragment;
	private FragmentManager mManager;
	private VideOSCMainActivity mActivity;

	/**
	 * Standard constructor.
	 *
	 * @param context The context where the ListView associated with this adapter is running
	 * @param layout  Resource identifier of a layout file that defines the views
	 *                for this list item.  Unless you override them later, this will
	 *                define both the item views and the drop down views.
	 * @param c       The cursor from which to get the data.
	 * @param flags   Flags used to determine the behavior of the adapter,
	 *                as per {@link CursorAdapter#CursorAdapter(Context, Cursor, int)}.
	 */
	public SnapshotSelectAdapter(Context context, int layout, Cursor c, int flags) {
		super(context, layout, c, flags);
		Log.d(TAG, "cursor: " + c.getCount());
		mActivity = (VideOSCMainActivity) context;
		mApp = (VideOSCApplication) mActivity.getApplication();
		mLayout = layout;
	}

	/**
	 * Makes a new view to hold the data pointed to by cursor.
	 *
	 * @param context Interface to application's global information
	 * @param cursor  The cursor from which to get the data. The cursor is already
	 *                moved to the correct position.
	 * @param parent  The parent to which the new view is attached to
	 * @return the newly created view.
	 */
	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		mManager = mActivity.getFragmentManager();
		mCameraFragment = (VideOSCCameraFragment) mManager.findFragmentByTag("CamPreview");
		return LayoutInflater.from(context).inflate(mLayout, parent, false);
	}

	/**
	 * Bind an existing view to the data pointed to by cursor
	 *
	 * @param view    Existing view, returned earlier by newView
	 * @param context Interface to application's global information
	 * @param cursor  The cursor from which to get the data. The cursor is already
	 */
	@Override
	public void bindView(View view, final Context context, Cursor cursor) {
		TextView row = (TextView) view.findViewById(R.id.snapshot_item);
		final int numPixels = cursor.getInt(cursor.getColumnIndexOrThrow(SettingsContract.PixelSnapshotEntries.SNAPSHOT_SIZE));
		String text = cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.PixelSnapshotEntries.SNAPSHOT_NAME));
		if (numPixels > 0)
			text = text.concat(" (" + String.valueOf(numPixels) + " pixels)");
		row.setText(text);

		final long rowId = cursor.getLong(cursor.getColumnIndexOrThrow(SettingsContract.PixelSnapshotEntries._ID));

		if (rowId >= 0) {
//			Log.d(TAG, "bindView called, ID: " + rowId + ", cursor position: " + cursor.getPosition());
			final String newRed = cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.PixelSnapshotEntries.SNAPSHOT_RED_VALUES));
			final String newRedMix = cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.PixelSnapshotEntries.SNAPSHOT_RED_MIX_VALUES));
			final String newGreen = cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.PixelSnapshotEntries.SNAPSHOT_GREEN_VALUES));
			final String newGreenMix = cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.PixelSnapshotEntries.SNAPSHOT_GREEN_MIX_VALUES));
			final String newBlue = cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.PixelSnapshotEntries.SNAPSHOT_BLUE_VALUES));
			final String newBlueMix = cursor.getString(cursor.getColumnIndexOrThrow(SettingsContract.PixelSnapshotEntries.SNAPSHOT_BLUE_MIX_VALUES));
			ArrayList<Double> redValues = new ArrayList<>();
			ArrayList<Double> redMixValues = new ArrayList<>();
			ArrayList<Double> greenValues = new ArrayList<>();
			ArrayList<Double> greenMixValues = new ArrayList<>();
			ArrayList<Double> blueValues = new ArrayList<>();
			ArrayList<Double> blueMixValues = new ArrayList<>();
			List<String> convertList;

			convertList = Arrays.asList(newRed.split(",", -1));
			if (redValues.size() == 0)
				redValues = convertStringValuesToDouble(convertList, redValues);
			convertList = Arrays.asList(newRedMix.split(",", -1));
			if (redMixValues.size() == 0)
				redMixValues = convertStringValuesToDouble(convertList, redMixValues);
			convertList = Arrays.asList(newGreen.split(",", -1));
			if (greenValues.size() == 0)
				greenValues = convertStringValuesToDouble(convertList, greenValues);
			convertList = Arrays.asList(newGreenMix.split(",", -1));
			if (greenMixValues.size() == 0)
				greenMixValues = convertStringValuesToDouble(convertList, greenMixValues);
			convertList = Arrays.asList(newBlue.split(",", -1));
			if (blueValues.size() == 0)
				blueValues = convertStringValuesToDouble(convertList, blueValues);
			convertList = Arrays.asList(newBlueMix.split(",", -1));
			if (blueMixValues.size() == 0)
				blueMixValues = convertStringValuesToDouble(convertList, blueMixValues);

//				Log.d(TAG, "ID: " + rowId + "\nred: " + redValues + "\nred mix: " + redMixValues + "\ngreen: " + greenValues + "\ngreen Mmix: " + greenMixValues + "\nblue: " + blueValues + "\nblue mix: " + blueMixValues);

			final ArrayList<Double> fRedValues = redValues;
			final ArrayList<Double> fRedMixValues = redMixValues;
			final ArrayList<Double> fGreenValues = blueValues;
			final ArrayList<Double> fGreenMixValues = greenMixValues;
			final ArrayList<Double> fBlueValues = blueValues;
			final ArrayList<Double> fBlueMixValues = blueMixValues;

			row.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
//					Log.d(TAG, "clicked: item with database id: " + rowId);
					Point res = mApp.getResolution();
					int size = res.x * res.y;
					mCameraFragment.setRedValues(fRedValues, size);
					mCameraFragment.setRedMixValues(fRedMixValues, size);
					mCameraFragment.setGreenValues(fGreenValues, size);
					mCameraFragment.setGreenMixValues(fGreenMixValues, size);
					mCameraFragment.setBlueValues(fBlueValues, size);
					mCameraFragment.setBlueMixValues(fBlueMixValues, size);
					mManager.beginTransaction()
							.remove(mManager.findFragmentByTag("snapshot select"))
							.commit();
					mActivity.setFullScreen();
					mApp.setSettingsLevel(0);
				}
			});

			row.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					// TODO: rename, delete snapshot
					return true;
				}
			});
		}
	}

	/**
	 * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
	 * closed.
	 *
	 * @param cursor The new cursor to be used
	 */
	@Override
	public void changeCursor(Cursor cursor) {
		super.changeCursor(cursor);
	}

	private ArrayList<Double> convertStringValuesToDouble(List<String> convertList, ArrayList<Double> resultList) {
		resultList.clear();
		for (String string : convertList) {
			if (string.length() == 0) resultList.add(null);
			else resultList.add(Double.valueOf(string));
		}

		return resultList;
	}
}
