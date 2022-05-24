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

package org.mopidy.mplay.application.background;




import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DrawFilter;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.VolumeProviderCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import org.mopidy.mplay.R;
import org.mopidy.mplay.application.activities.MainActivity;
import org.mopidy.mplay.application.artwork.ArtworkManager;
import org.mopidy.mplay.application.utils.CoverBitmapLoader;
import org.mopidy.mplay.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

public class NotificationManager implements CoverBitmapLoader.CoverBitmapListener, ArtworkManager.onNewAlbumImageListener {
    private static final String TAG = NotificationManager.class.getSimpleName();
    private static final int NOTIFICATION_ID = 1;

    private static final String NOTIFICATION_CHANNEL_ID = "Playback";

    private final BackgroundService mService;

    /**
     * Intent IDs used for controlling action.
     */
    private static final int INTENT_OPENGUI = 0;
    private static final int INTENT_PREVIOUS = 1;
    private static final int INTENT_PLAYPAUSE = 2;
    private static final int INTENT_STOP = 3;
    private static final int INTENT_NEXT = 4;
    private static final int INTENT_QUIT = 5;
    private static final int INTENT_LIKE = 6;

    private static final int PENDING_INTENT_UPDATE_CURRENT_FLAG =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;

    // Notification objects
    private final android.app.NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder = null;

    // Notification itself
    private Notification mNotification;

    // Save last track and last image
    private Bitmap mLastBitmap = null;

    /**
     * Last state of the MPD server
     */
    private MPDCurrentStatus mLastStatus;

    /**
     * Last played track of the MPD server. Used to check if track changed and a new cover is necessary.
     */
    private MPDTrack mLastTrack;

    /**
     * State of the notification and the media session.
     */
    private boolean mSessionActive;

    /**
     * Loader to asynchronously load cover images.
     */
    private final CoverBitmapLoader mCoverLoader;

    private boolean mDismissible;

    /**
     * Mediasession to set the lockscreen picture as well
     */
    private MediaSessionCompat mMediaSession;

    /**
     * {@link VolumeProviderCompat} to react to volume changes over the hardware keys by the user.
     * Only active as long as the notification is active.
     */
    private MALPVolumeControlProvider mVolumeControlProvider;

    public NotificationManager(BackgroundService service) {
        Log.e(TAG,"Service created");
        mService = service;
        mNotificationManager = (android.app.NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);
        mLastStatus = new MPDCurrentStatus();
        mLastTrack = new MPDTrack("");

        mDismissible = true;
        /*
         * Create loader to asynchronously load cover images. This class is the callback (s. receiveBitmap)
         */
        mCoverLoader = new CoverBitmapLoader(mService, this);

        ArtworkManager.getInstance(service).registerOnNewAlbumImageListener(this);

    }

    /**
     * Shows the notification
     */
    public synchronized void showNotification() {
        Log.e(TAG, "Show notification");
        openMediaSession();
        updateNotification(mLastTrack, mLastStatus);

        if (null != mNotification) {
            // Change to foreground service otherwise android will just kill it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mService.startForeground(NOTIFICATION_ID, mNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                mService.startForeground(NOTIFICATION_ID, mNotification);

            }
        }
    }

    private synchronized void openMediaSession() {
        if (mMediaSession == null) {
            mMediaSession = new MediaSessionCompat(mService, mService.getString(R.string.app_name));

            // Check if stream playback is enabled or not
            if (mDismissible) {
                mMediaSession.setCallback(new MALPMediaSessionCallback(mMediaSession));
                mVolumeControlProvider = new MALPVolumeControlProvider();
                mVolumeControlProvider.setCurrentVolume(mLastStatus.getVolume());
                mMediaSession.setPlaybackToRemote(mVolumeControlProvider);
            }
            mMediaSession.setRatingType(RatingCompat.RATING_HEART);
            mMediaSession.setActive(true);
            mSessionActive = true;
        }
    }

    /**
     * Creates the {@link NotificationChannel} for devices running Android O or higher
     */
    private void openChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, mService.getResources().getString(R.string.notification_channel_name_playback), android.app.NotificationManager.IMPORTANCE_LOW);
            // Disable lights & vibration
            channel.enableVibration(false);
            channel.enableLights(false);
            channel.setVibrationPattern(null);

            // Allow lockscreen playback control
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            // Register the channel
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Hides the notification (if shown) and resets state variables.
     */
    public synchronized void hideNotification() {
        // Stop foreground service as it is not necessary anymore
        mService.stopForeground(true);

        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }

        mNotificationManager.cancel(NOTIFICATION_ID);
        if (mNotification != null) {
            mService.stopForeground(true);
            mNotification = null;
            mNotificationBuilder = null;
        }
        mSessionActive = false;
    }

    /*
     * Creates a android system notification with two different remoteViews. One
     * for the normal layout and one for the big one. Sets the different
     * attributes of the remoteViews and starts a thread for Cover generation.
     */
    public synchronized void updateNotification(MPDTrack track, MPDCurrentStatus state) {
        if (track != null) {
            openChannel();
            mNotificationBuilder = new NotificationCompat.Builder(mService, NOTIFICATION_CHANNEL_ID);

            mNotificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID);

            // Open application intent
            Intent mainIntent = new Intent(mService, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mainIntent.putExtra(MainActivity.MAINACTIVITY_INTENT_EXTRA_REQUESTEDVIEW, MainActivity.REQUESTEDVIEW.NOWPLAYING.ordinal());
            PendingIntent contentPendingIntent = PendingIntent.getActivity(mService, INTENT_OPENGUI, mainIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
            mNotificationBuilder.setContentIntent(contentPendingIntent);

            // Set pendingintents
            // Previous song action

            Intent prevIntent = new Intent(BackgroundService.ACTION_PREVIOUS);
            PendingIntent prevPendingIntent = PendingIntent.getBroadcast(mService, INTENT_PREVIOUS, prevIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
            NotificationCompat.Action prevAction = new NotificationCompat.Action.Builder(R.drawable.ic_skip_previous_24dp, "Previous", prevPendingIntent).build();


            PendingIntent plikeIntent;
            int likeIcon;
            Intent likeIntent = new Intent(BackgroundService.ACTION_LIKE);
            plikeIntent = PendingIntent.getBroadcast(mService, INTENT_LIKE, likeIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
            likeIcon = track.hasLike() ? R.drawable.ic_heart_filled_24dp : R.drawable.ic_heart_24dp;
            NotificationCompat.Action likeAction = new NotificationCompat.Action.Builder(likeIcon,
                    "Like",
                    plikeIntent).build();


            // Pause/Play action
            PendingIntent playPauseIntent;
            int playPauseIcon;
            if (state.getPlaybackState() == MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_PLAYING) {
                Intent pauseIntent = new Intent(BackgroundService.ACTION_PAUSE);
                playPauseIntent = PendingIntent.getBroadcast(mService, INTENT_PLAYPAUSE, pauseIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
                playPauseIcon = R.drawable.ic_pause_24dp;
            } else {
                Intent playIntent = new Intent(BackgroundService.ACTION_PLAY);
                playPauseIntent = PendingIntent.getBroadcast(mService, INTENT_PLAYPAUSE, playIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
                playPauseIcon = R.drawable.ic_play_arrow_24dp;
            }
            NotificationCompat.Action playPauseAction = new NotificationCompat.Action.Builder(playPauseIcon, "PlayPause", playPauseIntent).build();

            // Stop action

            Intent stopIntent = new Intent(BackgroundService.ACTION_STOP);
            PendingIntent stopPendingIntent = PendingIntent.getBroadcast(mService, INTENT_STOP, stopIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
            NotificationCompat.Action stopActon = new NotificationCompat.Action.Builder(R.drawable.ic_stop_black_24dp, "Stop", stopPendingIntent).build();

            // Next song action
            Intent nextIntent = new Intent(BackgroundService.ACTION_NEXT);
            PendingIntent nextPendingIntent = PendingIntent.getBroadcast(mService, INTENT_NEXT, nextIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
            NotificationCompat.Action nextAction = new NotificationCompat.Action.Builder(R.drawable.ic_skip_next_24dp, "Next", nextPendingIntent).build();

            // Quit action
            Intent quitIntent = new Intent(BackgroundService.ACTION_QUIT_BACKGROUND_SERVICE);
            PendingIntent quitPendingIntent = PendingIntent.getBroadcast(mService, INTENT_QUIT, quitIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
            mNotificationBuilder.setDeleteIntent(quitPendingIntent);

            mNotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            mNotificationBuilder.setSmallIcon(R.drawable.ic_notification_24dp);
            mNotificationBuilder.addAction(prevAction);
            mNotificationBuilder.addAction(playPauseAction);
            mNotificationBuilder.addAction(stopActon);
            mNotificationBuilder.addAction(nextAction);
            mNotificationBuilder.addAction(likeAction);
            MediaStyle notificationStyle = new MediaStyle();
            if (mMediaSession != null) {
                notificationStyle.setMediaSession(mMediaSession.getSessionToken());
            }
            notificationStyle.setShowActionsInCompactView(1,3,4);
            mNotificationBuilder.setStyle(notificationStyle);

            String title = track.getVisibleTitle();

            mNotificationBuilder.setContentTitle(title);

            String secondRow = track.getSubLine(mService);

            // Set the media session metadata
            updateMetadata(track, state);

            mNotificationBuilder.setContentText(secondRow);

            // Remove unnecessary time info
            mNotificationBuilder.setShowWhen(false);

            // Cover but only if changed
            if (mNotification == null ||
                    !track.equalsStringTag(MPDTrack.StringTagTypes.ALBUM, mLastTrack) ||
                    !track.equalsStringTag(MPDTrack.StringTagTypes.ALBUM_MBID, mLastTrack) ||
                    !track.getArtwork("200").equals(mLastTrack.getArtwork("200"))) {
                mLastTrack = track;
                if(track.hasArtwork()) {
                    mLastBitmap = null;
                    mCoverLoader.getImage(mLastTrack, true, -1, -1);
                }
            }

            // Only set image if an saved one is available
            if (mLastBitmap != null) {
                mNotificationBuilder.setLargeIcon(mLastBitmap);
            } else {
                /*
                 * Create a dummy placeholder image for versions greater android 7 because it
                 * does not automatically show the application icon anymore in mediastyle notifications.
                 */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Drawable icon = ContextCompat.getDrawable(mService, R.drawable.notification_placeholder_256dp);

                    Bitmap iconBitmap = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(iconBitmap);
                    DrawFilter filter = new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG, 1);

                    canvas.setDrawFilter(filter);
                    icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    icon.setFilterBitmap(true);


                    icon.draw(canvas);
                    mNotificationBuilder.setLargeIcon(iconBitmap);
                } else {
                    /*
                     * For older android versions set the null icon which will result in a dummy icon
                     * generated from the application icon.
                     */
                    mNotificationBuilder.setLargeIcon(null);
                }
            }
            mNotificationBuilder.setOngoing(!mDismissible);

            // Build the notification
            mNotification = mNotificationBuilder.build();


            // Send the notification away
            mNotificationManager.notify(NOTIFICATION_ID, mNotification);
        }
    }

    /**
     * Updates the Metadata from Androids MediaSession. This sets track/album and stuff
     * for a lockscreen image for example.
     *
     * @param track  Current track.
     * @param status State of the PlaybackService.
     */
    private synchronized void updateMetadata(MPDTrack track, MPDCurrentStatus status) {
        if (track != null && mMediaSession != null) {
            // Try to get old metadata to save image retrieval.

            MediaMetadataCompat oldData = mMediaSession.getController().getMetadata();
            MediaMetadataCompat.Builder metaDataBuilder;
            if (oldData == null) {
                metaDataBuilder = new MediaMetadataCompat.Builder();
            } else {
                metaDataBuilder = new MediaMetadataCompat.Builder(mMediaSession.getController().getMetadata());
            }
            //if(!track.getArtwork().isEmpty())
            metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getStringTag(MPDTrack.StringTagTypes.TITLE));
            metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getStringTag(MPDTrack.StringTagTypes.ALBUM));
            metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getStringTag(MPDTrack.StringTagTypes.ARTIST));
            metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, track.getStringTag(MPDTrack.StringTagTypes.ALBUMARTIST));
            metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.getVisibleTitle());
            metaDataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, track.getTrackNumber());
            metaDataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.getLength() * 1000);
            metaDataBuilder.putRating(
                    MediaMetadataCompat.METADATA_KEY_USER_RATING, RatingCompat.newHeartRating(track.hasLike()));

            PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
            int psState = 0;
            switch (status.getPlaybackState()) {
                case MPD_PLAYING:
                    psState = PlaybackStateCompat.STATE_PLAYING;
                    break;
                case MPD_PAUSING:
                    psState = PlaybackStateCompat.STATE_PAUSED;
                    break;
                case MPD_STOPPED:
                    psState = PlaybackStateCompat.STATE_STOPPED;
                    break;
            }
            playbackStateBuilder.setState(psState, (long) status.getElapsedTime() * 1000, 1.0f);

            playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_PAUSE |
                    PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS +
                    PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_SET_RATING);




            mMediaSession.setRatingType(RatingCompat.RATING_HEART);
            mMediaSession.setMetadata(metaDataBuilder.build());
            mMediaSession.setPlaybackState(playbackStateBuilder.build());
        }
    }

    /**
     * Notifies about a change in MPDs status. If not shown this may be used later.
     *
     * @param status New MPD status
     */
    public synchronized void setMPDStatus(MPDCurrentStatus status) {
        Log.e(TAG, "set mpd status");

        if (mSessionActive) {
            Log.e(TAG, "session active");
            // Check if playing or not. If activate the service as foreground
//            if (status.getPlaybackState() != MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_PLAYING) {
//                Log.e(TAG, "stop foreground");
//                mService.stopForeground(false);
//            } else {
                Log.e(TAG, "start foreground");
                mService.startForeground(NOTIFICATION_ID, mNotification);
                Log.e(TAG, "start foreground success");



            // Only update the notification if playback state really changed
            if ((mLastStatus.getPlaybackState() != status.getPlaybackState()) ||
                    (mLastStatus.getCurrentSongIndex() != status.getCurrentSongIndex())) {
                updateNotification(mLastTrack, status);
            }
            if (mVolumeControlProvider != null) {
                // Notify the mediasession about the new volume
                if (mLastStatus.getVolume() != status.getVolume()) {
                    mVolumeControlProvider.setCurrentVolume(status.getVolume());
                }
            }


        }
        // Save for later usage
        mLastStatus = status;
        Log.e(TAG, "set status success");
    }

    /**
     * Notifies about a change in MPDs track. If not shown this may be used later.
     *
     * @param track New MPD track
     */
    public void setMPDFile(MPDTrack track, boolean forceUpdate) {
        if (mSessionActive && (notificationNeedsUpdate(track) || forceUpdate)) {
            updateNotification(track, mLastStatus);
        }
        // Save for later usage
        mLastTrack = track;
    }

    public boolean notificationNeedsUpdate(MPDTrack track) {
        if (!track.getStringTag(MPDTrack.StringTagTypes.ALBUM).equals(mLastTrack.getStringTag(MPDTrack.StringTagTypes.ALBUM))) {
            return true;
        }

        if (!track.getStringTag(MPDTrack.StringTagTypes.ARTIST).equals(mLastTrack.getStringTag(MPDTrack.StringTagTypes.ARTIST))) {
            return true;
        }

        if (!track.getStringTag(MPDTrack.StringTagTypes.TITLE).equals(mLastTrack.getStringTag(MPDTrack.StringTagTypes.TITLE))) {
            return true;
        }

        if (track.hasLike() != mLastTrack.hasLike()) {
            return true;
        }

        return false;
    }

    /*
     * Receives the generated album picture from the main status helper for the
     * notification controls. Sets it and notifies the system that the
     * notification has changed
     */
    @Override
    public synchronized void receiveBitmap(Bitmap bm, final CoverBitmapLoader.IMAGE_TYPE type) {
        // Check if notification exists and set picture
        mLastBitmap = bm;
        if (type == CoverBitmapLoader.IMAGE_TYPE.ALBUM_IMAGE && mNotification != null && bm != null) {
            updateNotification(mLastTrack, mLastStatus);

            /* Set lockscreen picture and stuff */
            if (mMediaSession != null) {
                MediaMetadataCompat metaData = mMediaSession.getController().getMetadata();
                if (metaData != null) {
                    MediaMetadataCompat.Builder metaDataBuilder;
                    metaDataBuilder = new MediaMetadataCompat.Builder(metaData);
                    metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bm);
                    mMediaSession.setMetadata(metaDataBuilder.build());
                }
            }
        }
    }

    @Override
    public void newAlbumImage(MPDAlbum album) {
        if (mLastTrack.getStringTag(MPDTrack.StringTagTypes.ALBUM).equals(album.getName())) {
            mCoverLoader.getImage(mLastTrack, true, -1, -1);
        }
    }

    public synchronized void setDismissible(boolean dismissible) {
        mDismissible = dismissible;

        // Reopen the notification
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession = null;
        }
        openMediaSession();
        updateNotification(mLastTrack, mLastStatus);
    }

    /**
     * Callback class for MediaControls controlled by android system like BT remotes, etc and
     * Volume keys on some android versions.
     */
    private static class MALPMediaSessionCallback extends MediaSessionCompat.Callback {
        MediaSessionCompat mMediaSession;

        public MALPMediaSessionCallback(MediaSessionCompat session) {
            mMediaSession = session;
        }

        @Override
        public void onPlay() {
            super.onPlay();
            MPDCommandHandler.togglePause();
        }

        @Override
        public void onPause() {
            super.onPause();
            MPDCommandHandler.togglePause();
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            MPDCommandHandler.nextSong();
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            MPDCommandHandler.previousSong();
        }

        @Override
        public void onStop() {
            super.onStop();
            MPDCommandHandler.stop();
        }

        @Override
        public void onSeekTo(long pos) {
            super.onSeekTo(pos);
            MPDCommandHandler.seekSeconds((int) pos / 1000);
            PlaybackStateCompat ps = mMediaSession.getController().getPlaybackState();
            PlaybackStateCompat.Builder psBuilder = new PlaybackStateCompat.Builder(ps);
            psBuilder.setState(ps.getState(), pos, 1.0f);
            mMediaSession.setPlaybackState(psBuilder.build());
        }
    }

    /**
     * Handles volume changes from mediasession callbacks. Will send user requested changes
     * in volume back to the MPD server.
     */
    private class MALPVolumeControlProvider extends VolumeProviderCompat {

        public MALPVolumeControlProvider() {
            super(VOLUME_CONTROL_ABSOLUTE, 100, mLastStatus.getVolume());
        }

        @Override
        public void onSetVolumeTo(int volume) {
            MPDCommandHandler.setVolume(volume);
            setCurrentVolume(volume);
        }

        @Override
        public void onAdjustVolume(int direction) {
            if (direction == 1) {
                MPDCommandHandler.increaseVolume(1);
            } else if (direction == -1) {
                MPDCommandHandler.decreaseVolume(1);
            }
        }
    }

}
