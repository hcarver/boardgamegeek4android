package com.boardgamegeek.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.boardgamegeek.R;
import com.boardgamegeek.model.Play;
import com.boardgamegeek.provider.BggContract.Buddies;
import com.boardgamegeek.provider.BggContract.PlayPlayers;
import com.boardgamegeek.provider.BggContract.Plays;
import com.boardgamegeek.service.SyncService;
import com.boardgamegeek.service.UpdateService;
import com.boardgamegeek.ui.model.Buddy;
import com.boardgamegeek.util.ActivityUtils;
import com.boardgamegeek.util.DetachableResultReceiver;
import com.boardgamegeek.util.HttpUtils;
import com.boardgamegeek.util.ResolverUtils;
import com.boardgamegeek.util.UIUtils;
import com.squareup.picasso.Picasso;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class BuddyFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
	private static final String KEY_REFRESHED = "REFRESHED";
	private static final int TOKEN = 0;

	private String mBuddyName;
	private boolean mRefreshed;
	private ViewGroup mRootView;
	@InjectView(R.id.full_name) TextView mFullName;
	@InjectView(R.id.username) TextView mName;
	@InjectView(R.id.avatar) ImageView mAvatar;
	@InjectView(R.id.nickname) TextView mNickname;
	@InjectView(R.id.updated) TextView mUpdated;
	private int mDefaultTextColor;
	private int mLightTextColor;

	public interface Callbacks {
		public DetachableResultReceiver getReceiver();
	}

	private static Callbacks sDummyCallbacks = new Callbacks() {
		@Override
		public DetachableResultReceiver getReceiver() {
			return null;
		}
	};

	private Callbacks mCallbacks = sDummyCallbacks;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			mRefreshed = savedInstanceState.getBoolean(KEY_REFRESHED);
		}

		final Intent intent = UIUtils.fragmentArgumentsToIntent(getArguments());
		mBuddyName = intent.getStringExtra(ActivityUtils.KEY_BUDDY_NAME);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(KEY_REFRESHED, mRefreshed);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mRootView = (ViewGroup) inflater.inflate(R.layout.fragment_buddy, container, false);

		ButterKnife.inject(this, mRootView);

		mDefaultTextColor = mNickname.getTextColors().getDefaultColor();
		mLightTextColor = getResources().getColor(R.color.light_text);

		getLoaderManager().restartLoader(TOKEN, null, this);

		return mRootView;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (!(activity instanceof Callbacks)) {
			throw new ClassCastException("Activity must implement fragment's callbacks.");
		}

		mCallbacks = (Callbacks) activity;
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mCallbacks = sDummyCallbacks;
	}

	@OnClick(R.id.nickname)
	public void onEditNicknameClick(View v) {
		showDialog(mNickname.getText().toString(), mBuddyName);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle data) {
		CursorLoader loader = null;
		if (id == TOKEN) {
			loader = new CursorLoader(getActivity(), Buddies.buildBuddyUri(mBuddyName), Buddy.PROJECTION, null,
				null, null);
		}
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		if (getActivity() == null) {
			return;
		}

		if (loader.getId() == TOKEN) {
			onBuddyQueryComplete(cursor);
		} else {
			cursor.close();
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
	}

	private void onBuddyQueryComplete(Cursor cursor) {
		if (cursor == null || !cursor.moveToFirst()) {
			requestRefresh();
			return;
		}

		Buddy buddy = Buddy.fromCursor(cursor);

		Picasso.with(getActivity())
			.load(HttpUtils.ensureScheme(buddy.getAvatarUrl()))
			.placeholder(R.drawable.person_image_empty)
			.error(R.drawable.person_image_empty)
			.fit().into(mAvatar);
		mFullName.setText(buddy.getFullName());
		mName.setText(mBuddyName);
		if (TextUtils.isEmpty(buddy.getNickName())) {
			mNickname.setTextColor(mLightTextColor);
			mNickname.setText(buddy.getFirstName());
		} else {
			mNickname.setTextColor(mDefaultTextColor);
			mNickname.setText(buddy.getNickName());
		}
		mUpdated.setText((buddy.getUpdated() == 0 ?
			getResources().getString(R.string.needs_updating) :
			getResources().getString(
				R.string.updated) + ": " + DateUtils.getRelativeTimeSpanString(buddy.getUpdated())));
	}

	public void requestRefresh() {
		if (!mRefreshed) {
			forceRefresh();
			mRefreshed = true;
		}
	}

	public void forceRefresh() {
		UpdateService.start(getActivity(), UpdateService.SYNC_TYPE_BUDDY, mBuddyName, mCallbacks.getReceiver());
	}

	public void showDialog(final String nickname, final String username) {
		final LayoutInflater inflater = (LayoutInflater) getActivity()
			.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.dialog_edit_nickname, mRootView, false);

		final EditText editText = (EditText) view.findViewById(R.id.edit_nickname);
		final CheckBox checkBox = (CheckBox) view.findViewById(R.id.change_plays);
		if (!TextUtils.isEmpty(nickname)) {
			editText.setText(nickname);
			editText.setSelection(0, nickname.length());
		}

		AlertDialog dialog = new AlertDialog.Builder(getActivity()).setView(view)
			.setTitle(R.string.title_edit_nickname).setNegativeButton(R.string.cancel, null)
			.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String newNickname = editText.getText().toString();
					new Task(username, checkBox.isChecked()).execute(newNickname);
				}
			}).create();
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		dialog.show();
	}

	private class Task extends AsyncTask<String, Void, String> {
		private static final String SELECTION = PlayPlayers.USER_NAME + "=? AND play_players." + PlayPlayers.NAME
			+ "!=?";
		Context mContext;
		Uri mUri;
		String mUsername;
		boolean mUpdatePlays;

		public Task(String username, boolean updatePlays) {
			mContext = getActivity();
			mUri = Buddies.buildBuddyUri(username);
			mUsername = username;
			mUpdatePlays = updatePlays;
		}

		@Override
		protected String doInBackground(String... params) {
			String newNickname = params[0];
			String result = null;
			updateNickname(mContext, mUri, newNickname);
			if (mUpdatePlays) {
				if (TextUtils.isEmpty(newNickname)) {
					result = getString(R.string.msg_missing_nickname);
				} else {
					int count = updatePlays(mContext, mUsername, newNickname);
					if (count > 0) {
						updatePlayers(mContext, mUsername, newNickname);
						SyncService.sync(mContext, SyncService.FLAG_SYNC_PLAYS_UPLOAD);
					}
					result = getResources().getQuantityString(R.plurals.msg_updated_plays, count, count);
				}
			}
			return result;
		}

		@Override
		protected void onPostExecute(String result) {
			if (!TextUtils.isEmpty(result)) {
				Toast.makeText(mContext, result, Toast.LENGTH_LONG).show();
			}
		}

		private void updateNickname(final Context context, final Uri uri, String nickname) {
			ContentValues values = new ContentValues(1);
			values.put(Buddies.PLAY_NICKNAME, nickname);
			context.getContentResolver().update(uri, values, null, null);
		}

		private int updatePlays(final Context context, final String username, final String newNickname) {
			ContentValues values = new ContentValues(1);
			values.put(Plays.SYNC_STATUS, Play.SYNC_STATUS_PENDING_UPDATE);
			List<Integer> playIds = ResolverUtils.queryInts(context.getContentResolver(), Plays.buildPlayersUri(),
				Plays.PLAY_ID, SELECTION, new String[] { username, newNickname });
			for (Integer playId : playIds) {
				context.getContentResolver().update(Plays.buildPlayUri(playId), values, null, null);
			}
			return playIds.size();
		}

		private int updatePlayers(final Context context, final String username, String newNickname) {
			ContentValues values = new ContentValues(1);
			values.put(PlayPlayers.NAME, newNickname);
			return context.getContentResolver().update(Plays.buildPlayersUri(), values, SELECTION,
				new String[] { username, newNickname });
		}
	}
}
