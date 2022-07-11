/*
 *  Copyright (C) 2022 Team Gateship-One
 *  (Hendrik Borghorst & Frederik Luetkes)
 *
 *  The AUTHORS.md file contains a detailed contributors list:
 *  <https://gitlab.com/gateship-one/malp/blob/master/AUTHORS.md>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.mopidy.mplay.application.views;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mopidy.mplay.R;
import org.mopidy.mplay.application.activities.FanartActivity;
import org.mopidy.mplay.application.artwork.ArtworkManager;
import org.mopidy.mplay.application.background.BackgroundService;
import org.mopidy.mplay.application.background.BackgroundServiceConnection;
import org.mopidy.mplay.application.callbacks.OnSaveDialogListener;
import org.mopidy.mplay.application.fragments.ErrorDialog;
import org.mopidy.mplay.application.fragments.TextDialog;
import org.mopidy.mplay.application.fragments.serverfragments.ChoosePlaylistDialog;
import org.mopidy.mplay.application.utils.CoverBitmapLoader;
import org.mopidy.mplay.application.utils.FormatHelper;
import org.mopidy.mplay.application.utils.OutputResponseMenuHandler;
import org.mopidy.mplay.application.utils.ThemeUtils;
import org.mopidy.mplay.application.utils.VolumeButtonLongClickListener;
import org.mopidy.mplay.mpdservice.ConnectionManager;
import org.mopidy.mplay.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.mopidy.mplay.mpdservice.handlers.MPDStatusChangeHandler;
import org.mopidy.mplay.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.mopidy.mplay.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.mopidy.mplay.mpdservice.handlers.serverhandler.MPDStateMonitoringHandler;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDTrack;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDProfileChangeHandler;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDProfileManager;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDServerProfile;
import org.mopidy.mplay.mpdservice.websocket.WSInterface;

import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.Locale;

public class NowPlayingView extends RelativeLayout implements PopupMenu.OnMenuItemClickListener, ArtworkManager.onNewAlbumImageListener, ArtworkManager.onNewArtistImageListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = NowPlayingView.class.getSimpleName();

    private final ViewDragHelper mDragHelper;

    private final ServerStatusListener mStateListener;

    private final ProfileListener mProfileListener;

    private final ServerConnectionListener mConnectionStateListener;

    /**
     * Upper view part which is dragged up & down
     */
    private View mHeaderView;

    /**
     * Main view of draggable part
     */
    private View mMainView;

    private LinearLayout mDraggedUpButtons;
    private LinearLayout mDraggedDownButtons;

    /**
     * Absolute pixel position of upper layout bound
     */
    private int mTopPosition;

    /**
     * relative dragposition
     */
    private float mDragOffset;

    /**
     * Height of non-draggable part.
     * (Layout height - draggable part)
     */
    private int mDragRange;

    /**
     * Flag whether the nowplaying hint should be shown. This should only be true if the app was used the first time.
     */
    private boolean mShowNPVHint = false;

    /**
     * Flag whether the views switches between album cover and artist image
     */
    private boolean mShowArtistImage = false;

    private BackgroundService.STREAMING_STATUS mStreamingStatus;

    /**
     * Main cover imageview
     */
    private AlbumArtistView mCoverImage;

    /**
     * Small cover image, part of the draggable header
     */
    private ImageView mTopCoverImage;

    /**
     * View that contains the playlist ListVIew
     */
    private CurrentPlaylistView mPlaylistView;

    /**
     * ViewSwitcher used for switching between the main cover image and the playlist
     */
    private ViewSwitcher mViewSwitcher;

    /**
     * Asynchronous loader for coverimages for TrackItems.
     */
    private CoverBitmapLoader mCoverLoader = null;

    /**
     * Observer for information about the state of the draggable part of this view.
     * This is probably the Activity of which this view is part of.
     * (Used for smooth statusbar transition and state resuming)
     */
    private NowPlayingDragStatusReceiver mDragStatusReceiver = null;

    private StreamingStatusReceiver mStreamingStatusReceiver;

    private BackgroundServiceConnection mBackgroundServiceConnection;

    /**
     * Top buttons in the draggable header part.
     */
    private ImageButton mTopLikeButton;
    private ImageButton mTopPlayPauseButton;
    private ImageButton mTopPlaylistButton;
    private ImageButton mTopMenuButton;

    /**
     * Buttons in the bottom part of the view
     */
    private ImageButton mBottomLikeButton;
    private ImageButton mBottomRepeatButton;
    private ImageButton mBottomPreviousButton;
    private ImageButton mBottomPlayPauseButton;
    private ImageButton mBottomStopButton;
    private ImageButton mBottomNextButton;
    private ImageButton mBottomRandomButton;

    /**
     * Seekbar used for seeking and informing the user of the current playback position.
     */
    private SeekBar mPositionSeekbar;

    /**
     * Seekbar used for volume control of host
     */
    private SeekBar mVolumeSeekbar;
    private ImageView mVolumeIcon;
    private ImageView mVolumeIconButtons;

    private TextView mVolumeText;

    private ImageButton mVolumeMinus;
    private ImageButton mVolumePlus;

    private VolumeButtonLongClickListener mPlusListener;
    private VolumeButtonLongClickListener mMinusListener;

    private LinearLayout mHeaderTextLayout;

    private LinearLayout mVolumeSeekbarLayout;
    private LinearLayout mVolumeButtonLayout;

    private int mVolumeStepSize;

    /**
     * Various textviews for track information
     */
    private TextView mTrackName;
    private TextView mTrackAdditionalInfo;
    private TextView mElapsedTime;
    private TextView mDuration;

    private TextView mTrackNo;
    private TextView mPlaylistNo;
    private TextView mBitrate;
    private TextView mAudioProperties;
    private TextView mTrackURI;
    private TextView mCurrentProfile;


    private MPDCurrentStatus mLastStatus;
    private MPDTrack mLastTrack;

    private boolean mUseEnglishWikipedia;

    public NowPlayingView(Context context) {
        this(context, null, 0);
    }

    public NowPlayingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NowPlayingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDragHelper = ViewDragHelper.create(this, 1f, new BottomDragCallbackHelper());
        mStateListener = new ServerStatusListener(this);
        mProfileListener = new ProfileListener(this);
        mConnectionStateListener = new ServerConnectionListener(this, getContext().getMainLooper());
        mLastStatus = new MPDCurrentStatus();
        mLastTrack = new MPDTrack("");
    }

    /**
     * Maximizes this view with an animation.
     */
    public void maximize() {
        smoothSlideTo(0f);
    }

    /**
     * Minimizes the view with an animation.
     */
    public void minimize() {
        smoothSlideTo(1f);
    }

    /**
     * Slides the view to the given position.
     *
     * @param slideOffset 0.0 - 1.0 (0.0 is dragged down, 1.0 is dragged up)
     * @return If the move was successful
     */
    boolean smoothSlideTo(float slideOffset) {
        final int topBound = getPaddingTop();
        int y = (int) (topBound + slideOffset * mDragRange);

        if (mDragHelper.smoothSlideViewTo(mHeaderView, mHeaderView.getLeft(), y)) {
            ViewCompat.postInvalidateOnAnimation(this);
            return true;
        }
        return false;
    }


    /**
     * Set the position of the draggable view to the given offset. This is done without an animation.
     * Can be used to resume a certain state of the view (e.g. on resuming an activity)
     *
     * @param offset Offset to position the view to from 0.0 - 1.0 (0.0 dragged up, 1.0 dragged down)
     */
    public void setDragOffset(float offset) {
        if (offset > 1.0f || offset < 0.0f) {
            mDragOffset = 1.0f;
        } else {
            mDragOffset = offset;
        }

        invalidate();
        requestLayout();


        // Set inverse alpha values for smooth layout transition.
        // Visibility still needs to be set otherwise parts of the buttons
        // are not clickable.
        mDraggedDownButtons.setAlpha(mDragOffset);
        mDraggedUpButtons.setAlpha(1.0f - mDragOffset);

        // Calculate the margin to smoothly resize text field
        LayoutParams layoutParams = (LayoutParams) mHeaderTextLayout.getLayoutParams();
        layoutParams.setMarginEnd((int) (mTopPlaylistButton.getWidth() * (1.0 - mDragOffset)));
        mHeaderTextLayout.setLayoutParams(layoutParams);

        // Notify the observers about the change
        if (mDragStatusReceiver != null) {
            mDragStatusReceiver.onDragPositionChanged(offset);
        }

        if (mDragOffset == 0.0f) {
            // top
            mDraggedDownButtons.setVisibility(INVISIBLE);
            mDraggedUpButtons.setVisibility(VISIBLE);
            mCoverImage.setVisibility(VISIBLE);
            if (mDragStatusReceiver != null) {
                mDragStatusReceiver.onStatusChanged(NowPlayingDragStatusReceiver.DRAG_STATUS.DRAGGED_UP);
            }
        } else {
            // bottom
            mDraggedDownButtons.setVisibility(VISIBLE);
            mDraggedUpButtons.setVisibility(INVISIBLE);
            mCoverImage.setVisibility(INVISIBLE);
            if (mDragStatusReceiver != null) {
                mDragStatusReceiver.onStatusChanged(NowPlayingDragStatusReceiver.DRAG_STATUS.DRAGGED_DOWN);
            }
        }
    }

    /**
     * Menu click listener. This method gets called when the user selects an item of the popup menu (right top corner).
     *
     * @param item MenuItem that was clicked.
     * @return Returns true if the item was handled by this method. False otherwise.
     */
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.action_clear_playlist) {
            final MaterialAlertDialogBuilder removeListBuilder = new MaterialAlertDialogBuilder(getContext());
            removeListBuilder.setTitle(getContext().getString(R.string.action_delete_playlist));
            removeListBuilder.setMessage(getContext().getString(R.string.dialog_message_delete_current_playlist));
            removeListBuilder.setPositiveButton(R.string.dialog_action_yes, (dialog, which) -> MPDQueryHandler.clearPlaylist());
            removeListBuilder.setNegativeButton(R.string.dialog_action_no, (dialog, which) -> {

            });
            removeListBuilder.create().show();
        } else if (itemId == R.id.action_shuffle_playlist) {
            final MaterialAlertDialogBuilder shuffleListBuilder = new MaterialAlertDialogBuilder(getContext());
            shuffleListBuilder.setTitle(getContext().getString(R.string.action_shuffle_playlist));
            shuffleListBuilder.setMessage(getContext().getString(R.string.dialog_message_shuffle_current_playlist));
            shuffleListBuilder.setPositiveButton(R.string.dialog_action_yes, (dialog, which) -> MPDQueryHandler.shufflePlaylist());
            shuffleListBuilder.setNegativeButton(R.string.dialog_action_no, (dialog, which) -> {
            });
            shuffleListBuilder.create().show();
        } else if (itemId == R.id.action_save_playlist) {
            OnSaveDialogListener plDialogCallback = new OnSaveDialogListener() {
                @Override
                public void onSaveObject(final String title) {
                    MaterialAlertDialogBuilder overWriteBuilder = new MaterialAlertDialogBuilder(getContext());
                    overWriteBuilder.setTitle(getContext().getString(R.string.action_overwrite_playlist));
                    overWriteBuilder.setMessage(getContext().getString(R.string.dialog_message_overwrite_playlist) + ' ' + title + '?');
                    overWriteBuilder.setPositiveButton(R.string.dialog_action_yes, (dialog, which) -> {
                        MPDQueryHandler.removePlaylist(title);
                        MPDQueryHandler.savePlaylist(title);
                    });
                    overWriteBuilder.setNegativeButton(R.string.dialog_action_no, (dialog, which) -> {

                    });
                    overWriteBuilder.create().show();

                }

                @Override
                public void onCreateNewObject() {
                    // open dialog in order to save the current playlist as a playlist in the mediastore
                    TextDialog textDialog = TextDialog.newInstance(getResources().getString(R.string.dialog_save_playlist),
                            getResources().getString(R.string.default_playlist_title));

                    textDialog.setCallback(MPDQueryHandler::savePlaylist);
                    textDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "SavePLTextDialog");
                }
            };

            // open dialog in order to save the current playlist as a playlist in the mediastore
            ChoosePlaylistDialog choosePlaylistDialog = ChoosePlaylistDialog.newInstance(true);

            choosePlaylistDialog.setCallback(plDialogCallback);
            choosePlaylistDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "ChoosePlaylistDialog");
        } else if (itemId == R.id.action_add_url) {
            TextDialog addURLDialog = TextDialog.newInstance(getResources().getString(R.string.action_add_url), "http://...");

            addURLDialog.setCallback(MPDQueryHandler::addPath);
            addURLDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "AddURLDialog");
        } else if (itemId == R.id.action_add_url_playlist) {
            TextDialog addURLDialog = TextDialog.newInstance(getResources().getString(R.string.action_add_url), "http://...");

            addURLDialog.setCallback(MPDQueryHandler::loadPlaylist);
            addURLDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "AddURLDialog");
        } else if (itemId == R.id.action_jump_to_current) {
            mPlaylistView.jumpToCurrentSong();
        } else if (itemId == R.id.action_toggle_single_mode) {
            if (null != mLastStatus) {
                if (mLastStatus.getSinglePlayback() == 0) {
                    MPDCommandHandler.setSingle(true);
                } else {
                    MPDCommandHandler.setSingle(false);
                }
            }
        } else if (itemId == R.id.action_toggle_consume_mode) {
            if (null != mLastStatus) {
                if (mLastStatus.getConsume() == 0) {
                    MPDCommandHandler.setConsume(true);
                } else {
                    MPDCommandHandler.setConsume(false);
                }
            }
        } else if (itemId == R.id.action_open_fanart) {
            Intent intent = new Intent(getContext(), FanartActivity.class);
            getContext().startActivity(intent);
            return true;
        } else if (itemId == R.id.action_wikipedia_album) {
            Intent albumIntent = new Intent(Intent.ACTION_VIEW);
            //albumIntent.setData(Uri.parse("https://" + Locale.getDefault().getLanguage() + ".wikipedia.org/wiki/index.php?search=" + mLastTrack.getTrackAlbum() + "&title=Special:Search&go=Go"));
            if (mUseEnglishWikipedia) {
                albumIntent.setData(Uri.parse("https://en.wikipedia.org/wiki/" + mLastTrack.getStringTag(MPDTrack.StringTagTypes.ALBUM)));
            } else {
                albumIntent.setData(Uri.parse("https://" + Locale.getDefault().getLanguage() + ".wikipedia.org/wiki/" + mLastTrack.getStringTag(MPDTrack.StringTagTypes.ALBUM)));
            }

            try {
                getContext().startActivity(albumIntent);
            } catch (ActivityNotFoundException e) {
                final ErrorDialog noBrowserFoundDlg = ErrorDialog.newInstance(R.string.dialog_no_browser_found_title, R.string.dialog_no_browser_found_message);
                noBrowserFoundDlg.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "BrowserNotFoundDlg");
            }

            return true;
        } else if (itemId == R.id.action_wikipedia_artist) {
            Intent artistIntent = new Intent(Intent.ACTION_VIEW);
            //artistIntent.setData(Uri.parse("https://" + Locale.getDefault().getLanguage() + ".wikipedia.org/wiki/index.php?search=" + mLastTrack.getTrackAlbumArtist() + "&title=Special:Search&go=Go"));
            if (mUseEnglishWikipedia) {
                artistIntent.setData(Uri.parse("https://en.wikipedia.org/wiki/" + mLastTrack.getStringTag(MPDTrack.StringTagTypes.ARTIST)));
            } else {
                artistIntent.setData(Uri.parse("https://" + Locale.getDefault().getLanguage() + ".wikipedia.org/wiki/" + mLastTrack.getStringTag(MPDTrack.StringTagTypes.ARTIST)));
            }

            try {
                getContext().startActivity(artistIntent);
            } catch (ActivityNotFoundException e) {
                final ErrorDialog noBrowserFoundDlg = ErrorDialog.newInstance(R.string.dialog_no_browser_found_title, R.string.dialog_no_browser_found_message);
                noBrowserFoundDlg.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "BrowserNotFoundDlg");
            }

            return true;
        } else if (itemId == R.id.action_start_streaming) {
            if (mStreamingStatus == BackgroundService.STREAMING_STATUS.PLAYING || mStreamingStatus == BackgroundService.STREAMING_STATUS.BUFFERING) {
                try {
                    mBackgroundServiceConnection.getService().stopStreamingPlayback();
                } catch (RemoteException ignored) {

                }
            } else {
                try {
                    mBackgroundServiceConnection.getService().startStreamingPlayback();
                } catch (RemoteException ignored) {

                }
            }
            return true;
        } else if (itemId == R.id.action_share_current_song) {
            shareCurrentTrack();
            return true;
        }

        return false;
    }


    @Override
    public void newAlbumImage(MPDAlbum album) {
        if (mLastTrack.getStringTag(MPDTrack.StringTagTypes.ALBUM).equals(album.getName())) {
            mCoverLoader.getImage(mLastTrack, true, mCoverImage.getWidth(), mCoverImage.getHeight());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getContext().getString(R.string.pref_volume_controls_key))) {
            setVolumeControlSetting();
        } else if (key.equals(getContext().getString(R.string.pref_use_english_wikipedia_key))) {
            mUseEnglishWikipedia = sharedPreferences.getBoolean(key, getContext().getResources().getBoolean(R.bool.pref_use_english_wikipedia_default));
        } else if (key.equals(getContext().getString(R.string.pref_show_npv_artist_image_key))) {
            mShowArtistImage = sharedPreferences.getBoolean(key, getContext().getResources().getBoolean(R.bool.pref_show_npv_artist_image_default));

            // Show artist image if artwork is requested
            if (mShowArtistImage) {
                mCoverLoader.getArtistImage(mLastTrack, true, mCoverImage.getWidth(), mCoverImage.getHeight());
            } else {
                // Hide artist image
                mCoverImage.clearArtistImage();
            }
        } else if (key.equals(getContext().getString(R.string.pref_volume_steps_key))) {
            setVolumeControlSetting();
        }
    }


    private void setVolumeControlSetting() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        String volumeControlView = sharedPref.getString(getContext().getString(R.string.pref_volume_controls_key), getContext().getString(R.string.pref_volume_control_view_default));

        LinearLayout volLayout = findViewById(R.id.volume_control_layout);

        if (volumeControlView.equals(getContext().getString(R.string.pref_volume_control_view_off_key))) {
            if (volLayout != null) {
                volLayout.setVisibility(GONE);
            }
            mVolumeSeekbarLayout.setVisibility(GONE);
            mVolumeButtonLayout.setVisibility(GONE);
        } else if (volumeControlView.equals(getContext().getString(R.string.pref_volume_control_view_seekbar_key))) {
            if (volLayout != null) {
                volLayout.setVisibility(VISIBLE);
            }
            mVolumeSeekbarLayout.setVisibility(VISIBLE);
            mVolumeButtonLayout.setVisibility(GONE);
        } else if (volumeControlView.equals(getContext().getString(R.string.pref_volume_control_view_buttons_key))) {
            if (volLayout != null) {
                volLayout.setVisibility(VISIBLE);
            }
            mVolumeSeekbarLayout.setVisibility(GONE);
            mVolumeButtonLayout.setVisibility(VISIBLE);
        }

        mVolumeStepSize = sharedPref.getInt(getContext().getString(R.string.pref_volume_steps_key), getResources().getInteger(R.integer.pref_volume_steps_default));
        mPlusListener.setVolumeStepSize(mVolumeStepSize);
        mMinusListener.setVolumeStepSize(mVolumeStepSize);
    }

    @Override
    public void newArtistImage(MPDArtist artist) {
        if (mShowArtistImage && mLastTrack.getStringTag(MPDTrack.StringTagTypes.ARTIST).equals(artist.getArtistName())) {
            mCoverLoader.getArtistImage(artist, false, mCoverImage.getWidth(), mCoverImage.getHeight());
        }
    }

    /**
     * Observer class for changes of the drag status.
     */
    private class BottomDragCallbackHelper extends ViewDragHelper.Callback {

        /**
         * Checks if a given child view should act as part of the drag. This is only true for the header
         * element of this View-class.
         *
         * @param child     Child that was touched by the user
         * @param pointerId Id of the pointer used for touching the view.
         * @return True if the view should be allowed to be used as dragging part, false otheriwse.
         */
        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            return child == mHeaderView;
        }

        /**
         * Called if the position of the draggable view is changed. This rerequests the layout of the view.
         *
         * @param changedView The view that was changed.
         * @param left        Left position of the view (should stay constant in this case)
         * @param top         Top position of the view
         * @param dx          Dimension of the width
         * @param dy          Dimension of the height
         */
        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
            // Save the heighest top position of this view.
            mTopPosition = top;

            // Calculate the new drag offset
            mDragOffset = (float) top / mDragRange;

            // Relayout this view
            requestLayout();

            // Set inverse alpha values for smooth layout transition.
            // Visibility still needs to be set otherwise parts of the buttons
            // are not clickable.
            mDraggedDownButtons.setAlpha(mDragOffset);
            mDraggedUpButtons.setAlpha(1.0f - mDragOffset);

            // Calculate the margin to smoothly resize text field
            LayoutParams layoutParams = (LayoutParams) mHeaderTextLayout.getLayoutParams();
            layoutParams.setMarginEnd((int) (mTopPlaylistButton.getWidth() * (1.0 - mDragOffset)));
            mHeaderTextLayout.setLayoutParams(layoutParams);

            if (mDragStatusReceiver != null) {
                mDragStatusReceiver.onDragPositionChanged(mDragOffset);
            }

        }

        /**
         * Called if the user lifts the finger(release the view) with a velocity
         *
         * @param releasedChild View that was released
         * @param xvel          x position of the view
         * @param yvel          y position of the view
         */
        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            int top = getPaddingTop();
            if (yvel > 0 || (yvel == 0 && mDragOffset > 0.3f)) {
                top += mDragRange;
            }
            // Snap the view to top/bottom position
            mDragHelper.settleCapturedViewAt(releasedChild.getLeft(), top);
            invalidate();
        }

        /**
         * Returns the range within a view is allowed to be dragged.
         *
         * @param child Child to get the dragrange for
         * @return Dragging range
         */
        @Override
        public int getViewVerticalDragRange(@NonNull View child) {
            return mDragRange;
        }


        /**
         * Clamps (limits) the view during dragging to the top or bottom(plus header height)
         *
         * @param child Child that is being dragged
         * @param top   Top position of the dragged view
         * @param dy    Delta value of the height
         * @return The limited height value (or valid position inside the clamped range).
         */
        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            final int topBound = getPaddingTop();
            int bottomBound = getHeight() - mHeaderView.getHeight() - mHeaderView.getPaddingBottom();

            final int newTop = Math.min(Math.max(top, topBound), bottomBound);

            return newTop;
        }

        /**
         * Called when the drag state changed. Informs observers that it is either dragged up or down.
         * Also sets the visibility of button groups in the header
         *
         * @param state New drag state
         */
        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);

            // Check if the new state is the idle state. If then notify the observer (if one is registered)
            if (state == ViewDragHelper.STATE_IDLE) {
                // Enable scrolling of the text views
                mTrackName.setSelected(true);
                mTrackAdditionalInfo.setSelected(true);

                if (mDragOffset == 0.0f) {
                    // Called when dragged up
                    mDraggedDownButtons.setVisibility(INVISIBLE);
                    mDraggedUpButtons.setVisibility(VISIBLE);
                    if (mDragStatusReceiver != null) {
                        mDragStatusReceiver.onStatusChanged(NowPlayingDragStatusReceiver.DRAG_STATUS.DRAGGED_UP);
                    }
                } else {
                    // Called when dragged down
                    mDraggedDownButtons.setVisibility(VISIBLE);
                    mDraggedUpButtons.setVisibility(INVISIBLE);
                    mCoverImage.setVisibility(INVISIBLE);
                    if (mDragStatusReceiver != null) {
                        mDragStatusReceiver.onStatusChanged(NowPlayingDragStatusReceiver.DRAG_STATUS.DRAGGED_DOWN);
                    }

                }
            } else if (state == ViewDragHelper.STATE_DRAGGING) {
                /*
                 * Show both layouts to enable a smooth transition via
                 * alpha values of the layouts.
                 */
                mDraggedDownButtons.setVisibility(VISIBLE);
                mDraggedUpButtons.setVisibility(VISIBLE);
                mCoverImage.setVisibility(VISIBLE);
                // report the change of the view
                if (mDragStatusReceiver != null) {
                    // Disable scrolling of the text views
                    mTrackName.setSelected(false);
                    mTrackAdditionalInfo.setSelected(false);

                    mDragStatusReceiver.onStartDrag();

                    if (mViewSwitcher.getCurrentView() == mPlaylistView && mDragOffset == 1.0f) {
                        mPlaylistView.jumpToCurrentSong();
                    }
                }

            }
        }
    }

    /**
     * Informs the dragHelper about a scroll movement.
     */
    @Override
    public void computeScroll() {
        // Continues the movement of the View Drag Helper and sets the invalidation for this View
        // if the animation is not finished and needs continuation
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Handles touch inputs to some views, to make sure, the ViewDragHelper is called.
     *
     * @param ev Touch input event
     * @return True if handled by this view or false otherwise
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Call the drag helper
        mDragHelper.processTouchEvent(ev);

        // Get the position of the new touch event
        final float x = ev.getX();
        final float y = ev.getY();

        // Check if the position lies in the bounding box of the header view (which is draggable)
        boolean isHeaderViewUnder = mDragHelper.isViewUnder(mHeaderView, (int) x, (int) y);

        // Check if drag is handled by the helper, or the header or mainview. If not notify the system that input is not yet handled.
        return isHeaderViewUnder && isViewHit(mHeaderView, (int) x, (int) y) || isViewHit(mMainView, (int) x, (int) y);
    }


    /**
     * Checks if an input to coordinates lay within a View
     *
     * @param view View to check with
     * @param x    x value of the input
     * @param y    y value of the input
     * @return
     */
    private boolean isViewHit(View view, int x, int y) {
        int[] viewLocation = new int[2];
        view.getLocationOnScreen(viewLocation);
        int[] parentLocation = new int[2];
        this.getLocationOnScreen(parentLocation);
        int screenX = parentLocation[0] + x;
        int screenY = parentLocation[1] + y;
        return screenX >= viewLocation[0] && screenX < viewLocation[0] + view.getWidth() &&
                screenY >= viewLocation[1] && screenY < viewLocation[1] + view.getHeight();
    }

    /**
     * Asks the ViewGroup about the size of all its children and paddings around.
     *
     * @param widthMeasureSpec  The width requirements for this view
     * @param heightMeasureSpec The height requirements for this view
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // FIXME check why super.onMeasure(widthMeasureSpec, heightMeasureSpec); causes
        // problems with scrolling header view.
        measureChildren(widthMeasureSpec, heightMeasureSpec);

        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxHeight = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, 0),
                resolveSizeAndState(maxHeight, heightMeasureSpec, 0));

        ViewGroup.LayoutParams imageParams = mCoverImage.getLayoutParams();
        imageParams.height = mViewSwitcher.getHeight();
        mCoverImage.setLayoutParams(imageParams);
        mCoverImage.requestLayout();


        // Calculate the margin to smoothly resize text field
        LayoutParams layoutParams = (LayoutParams) mHeaderTextLayout.getLayoutParams();
        layoutParams.setMarginEnd((int) (mTopPlaylistButton.getMeasuredHeight() * (1.0 - mDragOffset)));
        mHeaderTextLayout.setLayoutParams(layoutParams);
    }


    /**
     * Called after the layout inflater is finished.
     * Sets all global view variables to the ones inflated.
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get both main views (header and bottom part)
        mHeaderView = findViewById(R.id.now_playing_headerLayout);
        mMainView = findViewById(R.id.now_playing_bodyLayout);

        // header buttons
        mTopPlayPauseButton = findViewById(R.id.now_playing_topPlayPauseButton);
        mTopPlaylistButton = findViewById(R.id.now_playing_topPlaylistButton);
        mTopMenuButton = findViewById(R.id.now_playing_topMenuButton);
        mTopLikeButton = findViewById(R.id.now_playing_topLikeButton);

        // bottom buttons
        mBottomRepeatButton = findViewById(R.id.now_playing_bottomRepeatButton);
        mBottomPreviousButton = findViewById(R.id.now_playing_bottomPreviousButton);
        mBottomPlayPauseButton = findViewById(R.id.now_playing_bottomPlayPauseButton);
        mBottomStopButton = findViewById(R.id.now_playing_bottomStopButton);
        mBottomNextButton = findViewById(R.id.now_playing_bottomNextButton);
        mBottomRandomButton = findViewById(R.id.now_playing_bottomRandomButton);
        mBottomLikeButton = findViewById(R.id.now_playing_likeButton);

        // Main cover image
        mCoverImage = findViewById(R.id.now_playing_cover);
        // Small header cover image
        mTopCoverImage = findViewById(R.id.now_playing_topCover);

        // View with the ListView of the playlist
        mPlaylistView = findViewById(R.id.now_playing_playlist);

        // view switcher for cover and playlist view
        mViewSwitcher = findViewById(R.id.now_playing_view_switcher);

        // Button container for the buttons shown if dragged up
        mDraggedUpButtons = findViewById(R.id.now_playing_layout_dragged_up);
        // Button container for the buttons shown if dragged down
        mDraggedDownButtons = findViewById(R.id.now_playing_layout_dragged_down);

        // textviews
        mTrackName = findViewById(R.id.now_playing_trackName);
        mCurrentProfile = findViewById(R.id.current_profile_name);
        // For marquee scrolling the TextView need selected == true
        mTrackName.setSelected(true);
        mTrackAdditionalInfo = findViewById(R.id.now_playing_track_additional_info);
        // For marquee scrolling the TextView need selected == true
        mTrackAdditionalInfo.setSelected(true);

        mTrackNo = findViewById(R.id.now_playing_text_track_no);
        mPlaylistNo = findViewById(R.id.now_playing_text_playlist_no);
        mBitrate = findViewById(R.id.now_playing_text_bitrate);
        mAudioProperties = findViewById(R.id.now_playing_text_audio_properties);
        mTrackURI = findViewById(R.id.now_playing_text_track_uri);

        // Textviews directly under the seekbar
        mElapsedTime = findViewById(R.id.now_playing_elapsedTime);
        mDuration = findViewById(R.id.now_playing_duration);

        mHeaderTextLayout = findViewById(R.id.now_playing_header_textLayout);

        // seekbar (position)
        mPositionSeekbar = findViewById(R.id.now_playing_seekBar);
        mPositionSeekbar.setOnSeekBarChangeListener(new PositionSeekbarListener());

        mVolumeSeekbar = findViewById(R.id.volume_seekbar);
        mVolumeIcon = findViewById(R.id.volume_icon);
        mVolumeIcon.setOnClickListener(view -> MPDCommandHandler.setVolume(0));

        mVolumeIcon.setOnLongClickListener(view -> {

            MPDQueryHandler.getOutputs(new OutputResponseMenuHandler(getContext(), view));

            return true;
        });

        mVolumeSeekbar.setMax(100);
        mVolumeSeekbar.setOnSeekBarChangeListener(new VolumeSeekBarListener());


        /* Volume control buttons */
        mVolumeIconButtons = findViewById(R.id.volume_icon_buttons);
        mVolumeIconButtons.setOnClickListener(view -> MPDCommandHandler.setVolume(0));

        mVolumeIconButtons.setOnLongClickListener(view -> {

            MPDQueryHandler.getOutputs(new OutputResponseMenuHandler(getContext(), view));

            return true;
        });

        mVolumeText = findViewById(R.id.volume_button_text);

        mVolumeMinus = findViewById(R.id.volume_button_minus);

        mVolumeMinus.setOnClickListener(v -> MPDCommandHandler.decreaseVolume(mVolumeStepSize));

        mVolumePlus = findViewById(R.id.volume_button_plus);
        mVolumePlus.setOnClickListener(v -> MPDCommandHandler.increaseVolume(mVolumeStepSize));

        /* Create two listeners that start a repeating timer task to repeat the volume plus/minus action */
        mPlusListener = new VolumeButtonLongClickListener(VolumeButtonLongClickListener.LISTENER_ACTION.VOLUME_UP, mVolumeStepSize);
        mMinusListener = new VolumeButtonLongClickListener(VolumeButtonLongClickListener.LISTENER_ACTION.VOLUME_DOWN, mVolumeStepSize);

        /* Set the listener to the plus/minus button */
        mVolumeMinus.setOnLongClickListener(mMinusListener);
        mVolumeMinus.setOnTouchListener(mMinusListener);

        mVolumePlus.setOnLongClickListener(mPlusListener);
        mVolumePlus.setOnTouchListener(mPlusListener);

        mVolumeSeekbarLayout = findViewById(R.id.volume_seekbar_layout);
        mVolumeButtonLayout = findViewById(R.id.volume_button_layout);

        // set dragging part default to bottom
        mDragOffset = 1.0f;
        mDraggedUpButtons.setVisibility(INVISIBLE);
        mDraggedDownButtons.setVisibility(VISIBLE);
        mDraggedUpButtons.setAlpha(0.0f);

        // add listener to top playpause button
        mTopPlayPauseButton.setOnClickListener(arg0 -> MPDCommandHandler.togglePause());

        mBottomLikeButton.setOnClickListener(v -> this.toggleLike());
        mTopLikeButton.setOnClickListener(v -> this.toggleLike());


        // Add listeners to top playlist button
        mTopPlaylistButton.setOnClickListener(v -> {

            if (mViewSwitcher.getCurrentView() != mPlaylistView) {
                setViewSwitcherStatus(NowPlayingDragStatusReceiver.VIEW_SWITCHER_STATUS.PLAYLIST_VIEW);
            } else {
                setViewSwitcherStatus(NowPlayingDragStatusReceiver.VIEW_SWITCHER_STATUS.COVER_VIEW);
            }

            // report the change of the view
            if (mDragStatusReceiver != null) {
                // set view status
                if (mViewSwitcher.getDisplayedChild() == 0) {
                    // cover image is shown
                    mDragStatusReceiver.onSwitchedViews(NowPlayingDragStatusReceiver.VIEW_SWITCHER_STATUS.COVER_VIEW);
                } else {
                    // playlist view is shown
                    mDragStatusReceiver.onSwitchedViews(NowPlayingDragStatusReceiver.VIEW_SWITCHER_STATUS.PLAYLIST_VIEW);
                    mPlaylistView.jumpToCurrentSong();
                }
            }
        });

        TooltipCompat.setTooltipText(mTopPlaylistButton, getResources().getString(R.string.action_npv_show_playlist));

        // Add listener to top menu button
        mTopMenuButton.setOnClickListener(this::showAdditionalOptionsMenu);
        TooltipCompat.setTooltipText(mTopMenuButton, getResources().getString(R.string.action_npv_more_options));

        // Add listener to bottom repeat button
        mBottomRepeatButton.setOnClickListener(arg0 -> {
            if (null != mLastStatus) {
                if (mLastStatus.getRepeat() == 0) {
                    MPDCommandHandler.setRepeat(true);
                } else {
                    MPDCommandHandler.setRepeat(false);
                }
            }
        });

        // Add listener to bottom previous button
        mBottomPreviousButton.setOnClickListener(arg0 -> MPDCommandHandler.previousSong());

        // Add listener to bottom playpause button
        mBottomPlayPauseButton.setOnClickListener(arg0 -> MPDCommandHandler.togglePause());

        mBottomStopButton.setOnClickListener(view -> MPDCommandHandler.stop());

        // Add listener to bottom next button
        mBottomNextButton.setOnClickListener(arg0 -> MPDCommandHandler.nextSong());

        // Add listener to bottom random button
        mBottomRandomButton.setOnClickListener(arg0 -> {
            if (null != mLastStatus) {
                if (mLastStatus.getRandom() == 0) {
                    MPDCommandHandler.setRandom(true);
                } else {
                    MPDCommandHandler.setRandom(false);
                }
            }
        });

        mCoverImage.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), FanartActivity.class);
            getContext().startActivity(intent);
        });
        mCoverImage.setVisibility(INVISIBLE);

        mCoverLoader = new CoverBitmapLoader(getContext(), new CoverReceiverClass());
    }

    private void toggleLike() {
        if (mLastTrack != null) {
            MPDCommandHandler.toggleLike(mLastTrack.getPath(), !mLastTrack.hasLike());
            mLastTrack.setLike(!mLastTrack.hasLike());
            if (mLastTrack.hasLike()) {
                mBottomLikeButton.setImageResource(R.drawable.ic_heart_filled_48dp);
                mTopLikeButton.setImageResource(R.drawable.ic_heart_filled_24dp);
            } else {
                mBottomLikeButton.setImageResource(R.drawable.ic_heart_48dp);
                mTopLikeButton.setImageResource(R.drawable.ic_heart_24dp);
            }
        }
    }

    /**
     * Called to open the popup menu on the top right corner.
     *
     * @param v
     */
    private void showAdditionalOptionsMenu(View v) {
        PopupMenu popupMenu = new PopupMenu(getContext(), v);
        // Inflate the menu from a menu xml file
        popupMenu.inflate(R.menu.popup_menu_nowplaying);
        // Set the main NowPlayingView as a listener (directly implements callback)
        popupMenu.setOnMenuItemClickListener(this);
        // Real menu
        Menu menu = popupMenu.getMenu();

        // Set the checked menu item state if a MPDCurrentStatus is available
        if (null != mLastStatus) {
            MenuItem singlePlaybackItem = menu.findItem(R.id.action_toggle_single_mode);
            singlePlaybackItem.setChecked(mLastStatus.getSinglePlayback() == 1);

            MenuItem consumeItem = menu.findItem(R.id.action_toggle_consume_mode);
            consumeItem.setChecked(mLastStatus.getConsume() == 1);
        }

        // Check if the current view is the cover or the playlist. If it is the playlist hide its actions.
        // If the viewswitcher only has one child the dual pane layout is used
        if (mViewSwitcher.getDisplayedChild() == 0 && (mViewSwitcher.getChildCount() > 1)) {
            menu.setGroupEnabled(R.id.group_playlist_actions, false);
            menu.setGroupVisible(R.id.group_playlist_actions, false);
        }

        // Check if streaming is configured for the current server
        boolean streamingEnabled = ConnectionManager.getInstance(getContext().getApplicationContext()).getStreamingEnabled();
        MenuItem streamingStartStopItem = menu.findItem(R.id.action_start_streaming);

        if (!streamingEnabled) {
            streamingStartStopItem.setVisible(false);
        } else {
            if (mStreamingStatus == BackgroundService.STREAMING_STATUS.PLAYING || mStreamingStatus == BackgroundService.STREAMING_STATUS.BUFFERING) {
                streamingStartStopItem.setTitle(getResources().getString(R.string.action_stop_streaming));
            } else {
                streamingStartStopItem.setTitle(getResources().getString(R.string.action_start_streaming));
            }
        }

        // Open the menu itself
        popupMenu.show();
    }


    /**
     * Called when a layout is requested from the graphics system.
     *
     * @param changed If the layout is changed (size, ...)
     * @param l       Left position
     * @param t       Top position
     * @param r       Right position
     * @param b       Bottom position
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Calculate the maximal range that the view is allowed to be dragged
        mDragRange = (getMeasuredHeight() - mHeaderView.getMeasuredHeight());

        // New temporary top position, to fix the view at top or bottom later if state is idle.
        int newTop = mTopPosition;

        // fix height at top or bottom if state idle
        if (mDragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
            newTop = (int) (mDragRange * mDragOffset);
        }

        // Request the upper part of the NowPlayingView (header)
        mHeaderView.layout(
                0,
                newTop,
                r,
                newTop + mHeaderView.getMeasuredHeight());

        // Request the lower part of the NowPlayingView (main part)
        mMainView.layout(
                0,
                newTop + mHeaderView.getMeasuredHeight(),
                r,
                newTop + b);
    }

    /**
     * Stop the refresh timer when the view is not visible to the user anymore.
     * Unregister the receiver for NowPlayingInformation intends, not needed anylonger.
     */
    public void onPause() {
        // Unregister listener
        MPDStateMonitoringHandler.getHandler().unregisterStatusListener(mStateListener);
        MPDProfileManager.getInstance(this.getContext()).unregisterProfileListener(mProfileListener);
        WSInterface.getGenericInstance().removeMPDConnectionStateChangeListener(mConnectionStateListener);
        mPlaylistView.onPause();

        if (null != mBackgroundServiceConnection) {
            mBackgroundServiceConnection.closeConnection();
            mBackgroundServiceConnection = null;
        }

        getContext().getApplicationContext().unregisterReceiver(mStreamingStatusReceiver);

        ArtworkManager.getInstance(getContext().getApplicationContext()).unregisterOnNewAlbumImageListener(this);
        ArtworkManager.getInstance(getContext().getApplicationContext()).unregisterOnNewArtistImageListener(this);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Resumes refreshing operation because the view is visible to the user again.
     * Also registers to the NowPlayingInformation intends again.
     */
    public void onResume() {

        // get the playbackservice, when the connection is successfully established the timer gets restarted

        // Reenable scrolling views after resuming
        if (mTrackName != null) {
            mTrackName.setSelected(true);
        }

        if (mTrackAdditionalInfo != null) {
            mTrackAdditionalInfo.setSelected(true);
        }

        if (mStreamingStatusReceiver == null) {
            mStreamingStatusReceiver = new StreamingStatusReceiver();
        }

        if (null == mBackgroundServiceConnection) {
            mBackgroundServiceConnection = new BackgroundServiceConnection(getContext().getApplicationContext(), new BackgroundServiceConnectionListener());
        }
        mBackgroundServiceConnection.openConnection();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BackgroundService.ACTION_STREAMING_STATUS_CHANGED);
        getContext().getApplicationContext().registerReceiver(mStreamingStatusReceiver, filter);

        // Register with MPDStateMonitoring system
        MPDStateMonitoringHandler.getHandler().registerStatusListener(mStateListener);
        MPDProfileManager.getInstance(this.getContext()).registerProfileListener(mProfileListener);
        WSInterface.getGenericInstance().addMPDConnectionStateChangeListener(mConnectionStateListener);

        mPlaylistView.onResume();
        ArtworkManager.getInstance(getContext().getApplicationContext()).registerOnNewAlbumImageListener(this);
        ArtworkManager.getInstance(getContext().getApplicationContext()).registerOnNewArtistImageListener(this);


        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());

        mShowNPVHint = sharedPref.getBoolean(getContext().getResources().getString(R.string.pref_show_npv_hint), true);

        sharedPref.registerOnSharedPreferenceChangeListener(this);

        setVolumeControlSetting();

        mUseEnglishWikipedia = sharedPref.getBoolean(getContext().getString(R.string.pref_use_english_wikipedia_key), getContext().getResources().getBoolean(R.bool.pref_use_english_wikipedia_default));

        mShowArtistImage = sharedPref.getBoolean(getContext().getString(R.string.pref_show_npv_artist_image_key), getContext().getResources().getBoolean(R.bool.pref_show_npv_artist_image_default));
    }

    private void updateVolume(int volume) {
        if (!mVolumeSeekbar.isPressed()) {
            mVolumeSeekbar.setProgress(volume);
        }
    }

    private void updateProfile(String profileName) {

        mCurrentProfile.setText("@ "+profileName);
    }


    private void updateMPDStatus(MPDCurrentStatus status) {
        MPDCurrentStatus.MPD_PLAYBACK_STATE state = status.getPlaybackState();

        // update play buttons
        switch (state) {
            case MPD_PLAYING:
                mTopPlayPauseButton.setImageResource(R.drawable.ic_pause_48dp);
                mBottomPlayPauseButton.setImageResource(R.drawable.ic_pause_circle_fill_48dp);

                // show the hint if necessary
                if (mShowNPVHint) {
                    showHint();
                }

                break;
            case MPD_PAUSING:
            case MPD_STOPPED:
                mTopPlayPauseButton.setImageResource(R.drawable.ic_play_arrow_48dp);
                mBottomPlayPauseButton.setImageResource(R.drawable.ic_play_circle_fill_48dp);


                break;
        }

        // update repeat button
        // FIXME with single playback
        switch (status.getRepeat()) {
            case 0:
                mBottomRepeatButton.setImageResource(R.drawable.ic_repeat_24dp);
                mBottomRepeatButton.setImageTintList(ColorStateList.valueOf(ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent)));
                break;
            case 1:
                mBottomRepeatButton.setImageResource(R.drawable.ic_repeat_24dp);
                mBottomRepeatButton.setImageTintList(ColorStateList.valueOf(ThemeUtils.getThemeColor(getContext(), android.R.attr.colorAccent)));
                break;
        }

        // update random button
        switch (status.getRandom()) {
            case 0:
                mBottomRandomButton.setImageTintList(ColorStateList.valueOf(ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent)));
                break;
            case 1:
                mBottomRandomButton.setImageTintList(ColorStateList.valueOf(ThemeUtils.getThemeColor(getContext(), android.R.attr.colorAccent)));
                break;
        }

        int elapsed = Math.round(status.getElapsedTime());
        int length = Math.round(status.getTrackLength());
        // Update position seekbar & textviews
        mPositionSeekbar.setMax(length);
        if (!mPositionSeekbar.isPressed()) {
            mPositionSeekbar.setProgress(elapsed);
        }

        mElapsedTime.setText(FormatHelper.formatTracktimeFromS(elapsed));
        mDuration.setText(FormatHelper.formatTracktimeFromS(length));

        // Update volume seekbar
        int volume = status.getVolume();
        if (!mVolumeSeekbar.isPressed()) {
            mVolumeSeekbar.setProgress(volume);
        }

        if (volume >= 70) {
            mVolumeIcon.setImageResource(R.drawable.ic_volume_high_black_48dp);
            mVolumeIconButtons.setImageResource(R.drawable.ic_volume_high_black_48dp);
        } else if (volume >= 30) {
            mVolumeIcon.setImageResource(R.drawable.ic_volume_medium_black_48dp);
            mVolumeIconButtons.setImageResource(R.drawable.ic_volume_medium_black_48dp);
        } else if (volume > 0) {
            mVolumeIcon.setImageResource(R.drawable.ic_volume_low_black_48dp);
            mVolumeIconButtons.setImageResource(R.drawable.ic_volume_low_black_48dp);
        } else {
            mVolumeIcon.setImageResource(R.drawable.ic_volume_mute_black_48dp);
            mVolumeIconButtons.setImageResource(R.drawable.ic_volume_mute_black_48dp);
        }
        mVolumeIcon.setImageTintList(ColorStateList.valueOf(ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent)));
        mVolumeIconButtons.setImageTintList(ColorStateList.valueOf(ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent)));

        mVolumeText.setText(getResources().getString(R.string.volume_level_template, volume));

        mPlaylistNo.setText(getResources().getString(R.string.track_number_template, status.getCurrentSongIndex() + 1, status.getPlaylistLength()));

        mLastStatus = status;

        mBitrate.setText(getResources().getString(R.string.bitrate_unit_kilo_bits_template, status.getBitrate()));

        // Set audio properties string
        final StringBuilder propertiesBuilder = new StringBuilder();

        propertiesBuilder.append(getResources().getString(R.string.samplerate_unit_hertz_template, status.getSamplerate()));
        propertiesBuilder.append(' ');

        // Check for fancy new formats here (dsd, float = f)
        String sampleFormat = status.getBitDepth();

        // 16bit is the most probable sample format
        switch (sampleFormat) {
            case "16":
            case "24":
            case "8":
            case "32":
                propertiesBuilder.append(getResources().getString(R.string.sampleformat_unit_bits_template, sampleFormat));
                break;
            case "f":
                propertiesBuilder.append("float");
                break;
            default:
                propertiesBuilder.append(sampleFormat);
                break;
        }
        propertiesBuilder.append(' ');

        propertiesBuilder.append(getResources().getString(R.string.channels_template, status.getChannelCount()));

        mAudioProperties.setText(propertiesBuilder.toString());
    }
    public static class SetCoverTransform extends BitmapTransformation {
        AlbumArtistView mView = null;
        public SetCoverTransform(AlbumArtistView view) {
            super();
            mView = view;
        }

        @Override
        public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {

        }

        @Override
        protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
            //return toTransform;
            return toTransform;
        }

    }


    public static class DummyTransform extends BitmapTransformation {
        public DummyTransform(Context context) {
            super();
        }

        @Override
        public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {

        }

        @Override protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
            return toTransform;
        }


    }

    private void updateMPDCurrentTrack(MPDTrack track) {
        // Check if track title is set, otherwise use track name, otherwise path

        String title = track.getVisibleTitle();
        mTrackName.setText(title);


        mTrackAdditionalInfo.setText(track.getSubLine(getContext()));

        if (null == mLastTrack ||
                !track.equalsStringTag(MPDTrack.StringTagTypes.ALBUM, mLastTrack) ||
                !track.equalsStringTag(MPDTrack.StringTagTypes.ALBUM_MBID, mLastTrack) ||
                !track.hasLike() == mLastTrack.hasLike() ||
                !track.getArtwork("200").equals(mLastTrack.getArtwork("200")) ) {
            // Show the placeholder image until the cover fetch process finishes
            mCoverImage.clearAlbumImage();

            // The same for the small header image
            int tintColor = ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent);
            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.cover_placeholder_128dp, null);

            if (drawable != null) {
                drawable = DrawableCompat.wrap(drawable);
                DrawableCompat.setTint(drawable, tintColor);
            }

            if (track.hasLike()) {
                mBottomLikeButton.setImageResource(R.drawable.ic_heart_filled_48dp);
                mTopLikeButton.setImageResource(R.drawable.ic_heart_filled_24dp);
            }
            else {
                mBottomLikeButton.setImageResource(R.drawable.ic_heart_48dp);
                mTopLikeButton.setImageResource(R.drawable.ic_heart_24dp);
            }

            mTopCoverImage.setImageDrawable(drawable);
            // Start the cover loader
            if(track.hasArtwork()) {
                String artwork = track.getArtwork("200");
                Glide.with(getContext()).load(artwork).into(mTopCoverImage);
                Glide.with(mCoverImage.getAlbumImageView().getContext()).load(artwork).into(mCoverImage.getAlbumImageView());
            } else
            if (!track.getPath().isEmpty()) {
                Glide.with(getContext()).load(track.getPath()).into(mTopCoverImage);
                Glide.with(mCoverImage.getAlbumImageView().getContext()).load(track.getPath()).into(mCoverImage.getAlbumImageView());
            }
        }
/*
        if (mShowArtistImage && (null == mLastTrack || !track.equalsStringTag(MPDTrack.StringTagTypes.ARTIST, mLastTrack) || !track.equalsStringTag(MPDTrack.StringTagTypes.ARTIST_MBID, mLastTrack))) {
            mCoverImage.clearArtistImage();
            mCoverLoader.getArtistImage(track, true, mCoverImage.getWidth(), mCoverImage.getHeight());
        }
*/
        // Calculate the margin to avoid cut off textviews
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mHeaderTextLayout.getLayoutParams();
        layoutParams.setMarginEnd((int) (mTopPlaylistButton.getWidth() * (1.0 - mDragOffset)));
        mHeaderTextLayout.setLayoutParams(layoutParams);

        mTrackURI.setText(track.getPath());
        if (track.getAlbumTrackCount() != 0) {
            mTrackNo.setText(getResources().getString(R.string.track_number_template, track.getTrackNumber(), track.getAlbumTrackCount()));
        } else {
            mTrackNo.setText(String.valueOf(track.getTrackNumber()));
        }
        mLastTrack = track;

    }


    /**
     * Can be used to register an observer to this view, that is notified when a change of the dragstatus,offset happens.
     *
     * @param receiver Observer to register, only one observer at a time is possible.
     */
    public void registerDragStatusReceiver(NowPlayingDragStatusReceiver receiver) {
        mDragStatusReceiver = receiver;
        // Initial status notification
        if (mDragStatusReceiver != null) {

            // set drag status
            if (mDragOffset == 0.0f) {
                // top
                mDragStatusReceiver.onStatusChanged(NowPlayingDragStatusReceiver.DRAG_STATUS.DRAGGED_UP);
            } else {
                // bottom
                mDragStatusReceiver.onStatusChanged(NowPlayingDragStatusReceiver.DRAG_STATUS.DRAGGED_DOWN);
            }

            // set view status
            if (mViewSwitcher.getDisplayedChild() == 0) {
                // cover image is shown
                mDragStatusReceiver.onSwitchedViews(NowPlayingDragStatusReceiver.VIEW_SWITCHER_STATUS.COVER_VIEW);
            } else {
                // playlist view is shown
                mDragStatusReceiver.onSwitchedViews(NowPlayingDragStatusReceiver.VIEW_SWITCHER_STATUS.PLAYLIST_VIEW);
            }
        }
    }


    /**
     * Set the viewswitcher of cover/playlist view to the requested state.
     *
     * @param view the view which should be displayed.
     */
    public void setViewSwitcherStatus(NowPlayingDragStatusReceiver.VIEW_SWITCHER_STATUS view) {
        int color = 0;

        switch (view) {
            case COVER_VIEW:
                // change the view only if the requested view is not displayed
                if (mViewSwitcher.getCurrentView() != mCoverImage) {
                    mViewSwitcher.showNext();
                }
                color = ThemeUtils.getThemeColor(getContext(), android.R.attr.textColor);
                TooltipCompat.setTooltipText(mTopPlaylistButton, getResources().getString(R.string.action_npv_show_playlist));
                break;
            case PLAYLIST_VIEW:
                // change the view only if the requested view is not displayed
                if (mViewSwitcher.getCurrentView() != mPlaylistView) {
                    mViewSwitcher.showNext();
                }
                color = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
                TooltipCompat.setTooltipText(mTopPlaylistButton, getResources().getString(R.string.action_npv_show_cover));
                break;
        }

        // tint the button according to the requested view
        mTopPlaylistButton.setImageTintList(ColorStateList.valueOf(color));
    }

    /**
     * This method will drag up the NPV a little for 1 second.
     * <p>
     * This should be called only the first time music is played with Odyssey.
     */
    private void showHint() {
        mShowNPVHint = false;

        SharedPreferences.Editor sharedPrefEditor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        sharedPrefEditor.putBoolean(getContext().getString(R.string.pref_show_npv_hint), false);
        sharedPrefEditor.apply();

        if (mDragOffset == 1.0f) {
            // show hint only if the npv ist not already dragged up
            smoothSlideTo(0.75f);

            Handler handler = new Handler();
            handler.postDelayed(() -> {
                if (mDragOffset > 0.0f) {
                    smoothSlideTo(1.0f);
                }
            }, 3000);
        }
    }

    /**
     * Simple sharing for the current track.
     * <p>
     * This will only work if the track can be found in the mediastore.
     */
    private void shareCurrentTrack() {
        if (null == mLastTrack) {
            return;
        }
        String sharingText = getContext().getString(R.string.sharing_song_details, mLastTrack.getStringTag(MPDTrack.StringTagTypes.TITLE), mLastTrack.getStringTag(MPDTrack.StringTagTypes.ARTIST), mLastTrack.getStringTag(MPDTrack.StringTagTypes.ALBUM));

        // set up intent for sharing
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, sharingText);
        shareIntent.setType("text/plain");

        // start sharing
        getContext().startActivity(Intent.createChooser(shareIntent, getContext().getString(R.string.dialog_share_song_details)));
    }

    /**
     * Public interface used by observers to be notified about a change in drag state or drag position.
     */
    public interface NowPlayingDragStatusReceiver {
        // Possible values for DRAG_STATUS (up,down)
        enum DRAG_STATUS {
            DRAGGED_UP, DRAGGED_DOWN
        }

        // Possible values for the view in the viewswitcher (cover, playlist)
        enum VIEW_SWITCHER_STATUS {
            COVER_VIEW, PLAYLIST_VIEW
        }

        // Called when the whole view is either completely dragged up or down
        void onStatusChanged(DRAG_STATUS status);

        // Called continuously during dragging.
        void onDragPositionChanged(float pos);

        // Called when the view switcher switches between cover and playlist view
        void onSwitchedViews(VIEW_SWITCHER_STATUS view);

        // Called when the user starts the drag
        void onStartDrag();
    }

    private static class ProfileListener extends MPDProfileChangeHandler {

        private final WeakReference<NowPlayingView> mNowPlayingView;

        ProfileListener(final NowPlayingView nowPlayingView) {
            mNowPlayingView = new WeakReference<>(nowPlayingView);
        }

        protected void onProfileChanged(MPDServerProfile profile) {
            final NowPlayingView nowPlayingView = mNowPlayingView.get();
            if (nowPlayingView != null) {
                nowPlayingView.updateProfile(profile.getProfileName());
            }
        }
    }

    private static class ServerStatusListener extends MPDStatusChangeHandler {

        private final WeakReference<NowPlayingView> mNowPlayingView;

        ServerStatusListener(final NowPlayingView nowPlayingView) {
            mNowPlayingView = new WeakReference<>(nowPlayingView);
        }

        protected void onNewVolume(Volume volume) {
            final NowPlayingView nowPlayingView = mNowPlayingView.get();
            if (nowPlayingView != null) {
                nowPlayingView.updateVolume(volume.getVolume());
            }
        }
        @Override
        protected void onNewStatusReady(MPDCurrentStatus status) {
            final NowPlayingView nowPlayingView = mNowPlayingView.get();

            if (nowPlayingView != null) {
                nowPlayingView.updateMPDStatus(status);
            }
        }

        @Override
        protected void onNewTrackReady(MPDTrack track) {
            final NowPlayingView nowPlayingView = mNowPlayingView.get();

            if (nowPlayingView != null) {
                nowPlayingView.updateMPDCurrentTrack(track);
            }
        }


    }

    private static class ServerConnectionListener extends MPDConnectionStateChangeHandler {

        private final WeakReference<NowPlayingView> mNPV;

        ServerConnectionListener(NowPlayingView npv, Looper looper) {
            super(looper);
            mNPV = new WeakReference<>(npv);
        }

        @Override
        public void onConnected() {
            mNPV.get().updateMPDStatus(MPDStateMonitoringHandler.getHandler().getLastStatus());
        }

        @Override
        public void onDisconnected() {
            mNPV.get().updateMPDStatus(new MPDCurrentStatus());
            mNPV.get().updateMPDCurrentTrack(new MPDTrack(""));
        }
    }

    private static class PositionSeekbarListener implements SeekBar.OnSeekBarChangeListener {
        /**
         * Called if the user drags the seekbar to a new position or the seekbar is altered from
         * outside. Just do some seeking, if the action is done by the user.
         *
         * @param seekBar  Seekbar of which the progress was changed.
         * @param progress The new position of the seekbar.
         * @param fromUser If the action was initiated by the user.
         */
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                // FIXME Check if it is better to just update if user releases the seekbar
                // (network stress)
                MPDCommandHandler.seekSeconds(progress);
            }
        }

        /**
         * Called if the user starts moving the seekbar. We do not handle this for now.
         *
         * @param seekBar SeekBar that is used for dragging.
         */
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub
        }

        /**
         * Called if the user ends moving the seekbar. We do not handle this for now.
         *
         * @param seekBar SeekBar that is used for dragging.
         */
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub
        }
    }

    private class VolumeSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        /**
         * Called if the user drags the seekbar to a new position or the seekbar is altered from
         * outside. Just do some seeking, if the action is done by the user.
         *
         * @param seekBar  Seekbar of which the progress was changed.
         * @param progress The new position of the seekbar.
         * @param fromUser If the action was initiated by the user.
         */
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                MPDCommandHandler.setVolume(progress);

                if (progress >= 70) {
                    mVolumeIcon.setImageResource(R.drawable.ic_volume_high_black_48dp);
                } else if (progress >= 30) {
                    mVolumeIcon.setImageResource(R.drawable.ic_volume_medium_black_48dp);
                } else if (progress > 0) {
                    mVolumeIcon.setImageResource(R.drawable.ic_volume_low_black_48dp);
                } else {
                    mVolumeIcon.setImageResource(R.drawable.ic_volume_mute_black_48dp);
                }
            }
        }

        /**
         * Called if the user starts moving the seekbar. We do not handle this for now.
         *
         * @param seekBar SeekBar that is used for dragging.
         */
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub
        }

        /**
         * Called if the user ends moving the seekbar. We do not handle this for now.
         *
         * @param seekBar SeekBar that is used for dragging.
         */
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub
        }
    }

    /**
     * Private class that handles when the CoverGenerator finishes its fetching of cover images.
     */
    private class CoverReceiverClass implements CoverBitmapLoader.CoverBitmapListener {

        /**
         * Called when a bitmap is created
         *
         * @param bm Bitmap ready for use in the UI
         */
        @Override
        public void receiveBitmap(final Bitmap bm, final CoverBitmapLoader.IMAGE_TYPE type) {
            if (bm != null) {
                Activity activity = (Activity) getContext();
                if (activity != null) {
                    // Run on the UI thread of the activity because we are modifying gui elements.
                    activity.runOnUiThread(() -> {
                        if (type == CoverBitmapLoader.IMAGE_TYPE.ALBUM_IMAGE) {
                            // Set the main cover image
                            mCoverImage.setAlbumImage(bm);
                            // Set the small header image
                            mTopCoverImage.setImageBitmap(bm);
                        } else if (type == CoverBitmapLoader.IMAGE_TYPE.ARTIST_IMAGE) {
                            mCoverImage.setArtistImage(bm);
                        }
                    });
                }
            }
        }
    }

    /**
     * Receives stream playback status updates. When stream playback is started the status
     * is necessary to show the right menu item.
     */
    private class StreamingStatusReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BackgroundService.ACTION_STREAMING_STATUS_CHANGED.equals(intent.getAction())) {
                mStreamingStatus = BackgroundService.STREAMING_STATUS.values()[intent.getIntExtra(BackgroundService.INTENT_EXTRA_STREAMING_STATUS, 0)];
            }
        }
    }

    /**
     * Private class to handle when a {@link android.content.ServiceConnection} to the {@link BackgroundService}
     * is established. When the connection is established, the stream playback status is retrieved.
     */
    private class BackgroundServiceConnectionListener implements BackgroundServiceConnection.OnConnectionStatusChangedListener {

        @Override
        public void onConnected() {
            try {
                mStreamingStatus = BackgroundService.STREAMING_STATUS.values()[mBackgroundServiceConnection.getService().getStreamingStatus()];
            } catch (RemoteException ignored) {

            }
        }

        @Override
        public void onDisconnected() {

        }
    }


}
