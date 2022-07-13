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

package org.mopidy.mplay.mpdservice;


import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.Context.CONNECTIVITY_SERVICE;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import org.mopidy.mplay.R;
import org.mopidy.mplay.BuildConfig;
import org.mopidy.mplay.application.artwork.network.artprovider.HTTPAlbumImageProvider;
import org.mopidy.mplay.application.artwork.network.artprovider.MPDAlbumImageProvider;
import org.mopidy.mplay.application.background.BackgroundService;
import org.mopidy.mplay.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.mopidy.mplay.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.mopidy.mplay.mpdservice.handlers.serverhandler.MPDStateMonitoringHandler;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDProfileManager;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDServerProfile;
import org.mopidy.mplay.mpdservice.websocket.WSInterface;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Simple class that manages the three MPD Connections (Queries, State monitoring, Commands)
 */
public class ConnectionManager extends MPDConnectionStateChangeHandler {
    private static final String TAG = ConnectionManager.class.getSimpleName();

    /**
     * Short time to wait for reconnect
     */
    private static final int SHORT_RECONNECT_TIME = 10 * 1000;

    /**
     * Long time to wait for reconnect
     */
    private static final int LONG_RECONNECT_TIME = 1 * 60 * 1000;

    /**
     * Time to wait until the disconnect is initiated. This will also start the background service
     * to feed the widget & show the notification (if enabled in shared preferences)
     */
    private static final int DISCONNECT_DELAY_TIME = 500;

    private static final int SHORT_RECONNECT_TRIES = 5;

    private String mHostname;
    private String mRemoteHostname;

    private String mPassword;
    private String mLogin;
    private int mPort;
    private int mRemotePort;

    private boolean mAutoConnect = true;

    private boolean mDisconnectRequested;

    private Timer mReconnectTimer;
    private int mReconnectCounter;

    private Timer mDisconnectTimer;

    private static ConnectionManager mConnectionManager = null;

    private int mUseCounter;

    private MPDServerProfile mServerProfile = new MPDServerProfile();

    private final Context mApplicationContext;
    LocalPlayer mPlayer = null;
    private Context mContext;


    private ConnectionManager(Context context) {
        super(context.getMainLooper());
        mPlayer = new LocalPlayer(context);
        WSInterface.getGenericInstance().setLocalPlayer(mPlayer);
        WSInterface.getGenericInstance().addMPDConnectionStateChangeListener(this);
        mHostname = null;
        mPassword = null;
        mUseCounter = 0;
        mApplicationContext = context.getApplicationContext();
    }

    public synchronized static ConnectionManager getInstance(Context context) {
        if (null == mConnectionManager) {
            mConnectionManager = new ConnectionManager(context);
        }
        return mConnectionManager;
    }


    public void setParameters(MPDServerProfile profile, Context context) {
        if (null == profile) {
            return;
        }
        mHostname = profile.getHostname();
        mPassword = profile.getPassword();
        mRemoteHostname =  MPDProfileManager.getInstance(mContext).getRemoteHostname();
        mPort = profile.getPort();
        mRemotePort = MPDProfileManager.getInstance(mContext).getRemotePort();
        mLogin = profile.getLogin();
        mContext = context;
        MPDProfileManager.getInstance(null).enableRemote(false);
        WifiManager wifiMgr = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean localWifi = false;
        if (wifiMgr.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
            if (ActivityCompat.checkSelfPermission(mContext.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                List<WifiConfiguration> wifiNetworks = wifiMgr.getConfiguredNetworks();
                SupplicantState state = wifiInfo.getSupplicantState();
                if (state == SupplicantState.COMPLETED) {
                    String ssid = wifiInfo.getSSID();
                    if ((ssid.equals("\"IRouterN\"") || ssid.equals("\"IRouterN5\"")))
                    {
                        localWifi = true;
                    }
                }
            }
        }
        if(!localWifi) {
            mHostname = mRemoteHostname;
            mPort = mRemotePort;
            MPDProfileManager.getInstance(null).enableRemote(true);
        }
        MPDProfileManager.getInstance(context).deleteProfile(profile);
        profile.setAutoconnect(true);
        MPDProfileManager.getInstance(context).addProfile(profile);

        mConnectionManager.mServerProfile = profile;
        MPDCommandHandler.getHandler().setServerParameters(mHostname, mLogin, mPassword, mPort);
    }

    public void createPlayer() {
        mPlayer.initPlayer();
    }

    public void reconnectLastServer(Context context) {
        ConnectionManager instance = getInstance(context);

        if (instance.mHostname == null && null != context) {
            // Not connected so far
            autoConnect(context);
        }

        instance.mDisconnectRequested = false;

        MPDCommandHandler.connectToMPDServer();
    }

    public static void disconnectFromServer() {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Disconnecting from server");
        }

        MPDCommandHandler.disconnectFromMPDServer();
        mConnectionManager.mDisconnectRequested = true;
    }

    public void autoConnect(Context context) {
        mConnectionManager.mServerProfile = MPDProfileManager.getInstance(context).getAutoconnectProfile();

        setParameters(mConnectionManager.mServerProfile, context);
    }

    private synchronized void increaseMPDUse(Context context) {
        // First Activity to use MPD, connect
        if (mUseCounter == 0) {
            mUseCounter++;
            if (null != mDisconnectTimer) {
                mDisconnectTimer.cancel();
                mDisconnectTimer.purge();
                mDisconnectTimer = null;
                return;
            }

            /**
             * Make sure that the {@link BackgroundService} is stopped because there is no need for it
             * if the application is shown.
             */
            Intent showNotificationIntent = new Intent(context, BackgroundService.class);
            showNotificationIntent.setAction(BackgroundService.ACTION_QUIT_BACKGROUND_SERVICE);
            context.startService(showNotificationIntent);

            reconnectLastServer(context);
        }

    }

    private synchronized void decreaseMPDUse(Context context) {
        mUseCounter--;

        // Check if it was the last user, then start disconnecting timer
        if (mUseCounter == 0) {
            if (null != mDisconnectTimer) {
                mDisconnectTimer.cancel();
                mDisconnectTimer.purge();
            }
            mDisconnectTimer = new Timer();
            mDisconnectTimer.schedule(new DisconnectTask(), DISCONNECT_DELAY_TIME);

            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Delayed disconnect started");
            }
        }
    }

    /**
     * Increases the use counter of the {@link ConnectionManager}.
     *
     * @param context Context used for connection relevant calls.
     */
    public void registerMPDUse(Context context) {
        increaseMPDUse(context);
    }

    /**
     * Decreases the use counter of the {@link ConnectionManager}.
     *
     * @param context Context used for connection relevant calls.
     */
    public void unregisterMPDUse(Context context) {
        decreaseMPDUse(context);
    }

    @Override
    public synchronized void onConnected() {
        mReconnectCounter = 0;
        mDisconnectRequested = false;

        if (null != mReconnectTimer) {
            mReconnectTimer.cancel();
            mReconnectTimer.purge();
            mReconnectTimer = null;
        }

        // Check if this profile has user-specified HTTP cover loading active
        if (mServerProfile.getHTTPCoverEnabled()) {
            HTTPAlbumImageProvider.getInstance(mApplicationContext).setRegex(mServerProfile.getHTTPRegex());
        }

        // Check if current server can deliver artwork over MPD protocol
        final boolean mpdCoverEnabled = mServerProfile.getMPDCoverEnabled() && WSInterface.getGenericInstance().getServerCapabilities().hasAlbumArt();
        MPDAlbumImageProvider.getInstance().setActive(mpdCoverEnabled);
    }

    public synchronized void setAutoconnect(boolean enabled) {
        mAutoConnect = enabled;
    }

    @Override
    public synchronized void onDisconnected() {
        // Check if disconnect was user requested or not.
        // Also check if reconnect timeout is already running
        if (!mAutoConnect) {
            return;
        }
        if (!mDisconnectRequested && null == mReconnectTimer) {
            mReconnectTimer = new Timer();
            if (mReconnectCounter <= SHORT_RECONNECT_TRIES) {
                mReconnectTimer.schedule(new ReconnectTask(), SHORT_RECONNECT_TIME);
            } else {
                mReconnectTimer.schedule(new ReconnectTask(), LONG_RECONNECT_TIME);
            }
        }
        HTTPAlbumImageProvider.getInstance(mApplicationContext).setRegex(null);
    }

    /**
     * Private {@link TimerTask} to handle the automatic reconnects after a disconnect occured.
     */
    private class ReconnectTask extends TimerTask {
        @Override
        public void run() {
            // Remove existing timer
            if (null != mReconnectTimer) {
                mReconnectTimer.cancel();
                mReconnectTimer.purge();
                mReconnectTimer = null;
            }

            // Increase connection try counter
            mReconnectCounter++;
            reconnectLastServer(null);
        }
    }

    /**
     * Private {@link TimerTask} to handle the delayed disconnect.
     * This will disconnect from the MPD server and start the background service & show
     * the notification if it is enabled in the preferences.
     */
    private class DisconnectTask extends TimerTask {

        @Override
        public void run() {
            MPDCurrentStatus status = MPDStateMonitoringHandler.getHandler().getLastStatus();

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mApplicationContext);

            // Check if the notification setting is enabled (necessary for both the widget and notification)
            boolean showNotification = sharedPref.getBoolean(mApplicationContext.getString(R.string.pref_show_notification_key), mApplicationContext.getResources().getBoolean(R.bool.pref_show_notification_default));
            if (showNotification && status.getPlaybackState() != MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_STOPPED) {

                // Notify the widget to also connect if possible
                Intent connectIntent = new Intent(mApplicationContext, BackgroundService.class);
                connectIntent.setAction(BackgroundService.ACTION_CONNECT);
                mApplicationContext.startService(connectIntent);

                Intent showNotificationIntent = new Intent(mApplicationContext, BackgroundService.class);
                showNotificationIntent.setAction(BackgroundService.ACTION_SHOW_NOTIFICATION);
                mApplicationContext.startService(showNotificationIntent);
            }

            //disconnectFromServer();
            mDisconnectTimer = null;
        }
    }

    public void connectProfile(MPDServerProfile profile, Context context) {
        if (profile.isLocalProfile()) {
            MPDProfileManager.getInstance(null).enableLocalProfile(true);
            return;
        }
        MPDProfileManager.getInstance(null).enableLocalProfile(false);
        if (mHostname != null)
            if (mHostname.equals(profile.getHostname()) && mPort == profile.getPort())
             return;
        disconnectFromServer();
        setParameters(profile, context);
        reconnectLastServer(context);

        // Notify the widget to also connect if possible
        Intent connectIntent = new Intent(context, BackgroundService.class);
        connectIntent.setAction(BackgroundService.ACTION_PROFILE_CHANGED);
        context.startService(connectIntent);
    }


    public void addProfile(MPDServerProfile profile, Context context) {
        MPDProfileManager.getInstance(context).addProfile(profile);

        // Try connecting to the new profile
        setParameters(profile, context);
        reconnectLastServer(context);

        // Notify the widget to also connect if possible
        Intent connectIntent = new Intent(context, BackgroundService.class);
        connectIntent.setAction(BackgroundService.ACTION_PROFILE_CHANGED);
        context.startService(connectIntent);
    }

    public void removeProfile(MPDServerProfile profile, Context context) {
        MPDProfileManager.getInstance(context).deleteProfile(profile);
    }


    public String getProfileName() {
        if (mServerProfile != null) {
            return mServerProfile.getProfileName();
        } else {
            return "";
        }
    }

    public boolean getStreamingEnabled() {
        if (mServerProfile != null) {
            return mServerProfile.getStreamingEnabled();
        } else {
            return false;
        }
    }

}
