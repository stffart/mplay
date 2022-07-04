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

package org.mopidy.mplay.mpdservice.handlers.serverhandler;


import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.mopidy.mplay.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.mopidy.mplay.mpdservice.handlers.MPDIdleChangeHandler;
import org.mopidy.mplay.mpdservice.handlers.MPDStatusChangeHandler;
import org.mopidy.mplay.mpdservice.handlers.responsehandler.MPDResponseHandler;
import org.mopidy.mplay.mpdservice.mpdprotocol.MPDException;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDTrack;
import org.mopidy.mplay.mpdservice.websocket.WSInterface;

public class MPDStateMonitoringHandler extends MPDGenericHandler {
    private static final String THREAD_NAME = "MPDStatusHandler";
    private static final String TAG = "StateMonitoring";


    /**
     * Time to idle before resyncing the state with the MPD host (30 seconds).
     */
    private static final int IDLE_TIME = 30 * 1000;

    /**
     * Time used to send interpolated states to the listener (1 second)
     */
    private static final int INTERPOLATE_INTERVAL = 1 * 1000;

    private int mRefreshInterval = INTERPOLATE_INTERVAL;


    private static HandlerThread mHandlerThread = null;
    private static MPDStateMonitoringHandler mHandlerSingleton = null;

    /**
     * Callback handler for the GUI to get notified on updates
     */
    private final ArrayList<MPDStatusChangeHandler> mStatusListeners;

    /**
     * Timer used to periodically resync the state with the mpd server between interpolating
     * the time values.
     */
    private final Timer mResyncTimer;

    private ResynchronizationTask mResynchronizationTask;

    private final Timer mInterpolateTimer;

    private InterpolateTask mInterpolateTask;

    /**
     * Last unix time used to interpolate the time as accurate as possible
     */
    private Long mLastTimeBase;

    /**
     * Used to check if a new file is playing
     */
    private MPDTrack mLastFile;

    private MPDCurrentStatus mLastStatus;

    /**
     * Private constructor for use in singleton.
     *
     * @param looper Looper of a HandlerThread (that is NOT the UI thread)
     */
    private MPDStateMonitoringHandler(Looper looper) {
        super(looper);
        mLastStatus = new MPDCurrentStatus();

        mResyncTimer = new Timer();
        mInterpolateTimer = new Timer();

        mStatusListeners = new ArrayList<>();

        WSInterface.getGenericInstance().addMPDIdleChangeHandler(new IdleStateListener(this, looper));
        WSInterface.getGenericInstance().addMPDConnectionStateChangeListener(new ConnectionStateListener(this, looper));

        mLastStatus = new MPDCurrentStatus();
    }

    /**
     * Private method to ensure that the singleton runs in a separate thread.
     * Otherwise android will deny network access because of UI blocks.
     *
     * @return
     */
    public synchronized static MPDStateMonitoringHandler getHandler() {
        if (null == mHandlerSingleton) {
            mHandlerThread = new HandlerThread(THREAD_NAME);
            mHandlerThread.start();
            mHandlerSingleton = new MPDStateMonitoringHandler(mHandlerThread.getLooper());

        }
        return mHandlerSingleton;
    }

    /**
     * This is the main entry point of messages.
     * Here all possible messages types need to be handled with the MPDConnection.
     *
     * @param msg Message to process.
     */
    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        if (!(msg.obj instanceof MPDHandlerAction)) {
            /* Check if the message object is of correct type. Otherwise just abort here. */
            return;
        }

        MPDHandlerAction mpdAction = (MPDHandlerAction) msg.obj;
        /* Catch MPD exceptions here for now. */
        MPDResponseHandler responseHandler;
        MPDHandlerAction.NET_HANDLER_ACTION action = mpdAction.getAction();


    }

    private void stopInterpolation() {
        synchronized (mInterpolateTimer) {
            if (mInterpolateTask != null) {
                mInterpolateTask.cancel();
                mInterpolateTask = null;
            }
        }
    }

    private void stopResynchronization() {
        synchronized (mResyncTimer) {
            if (mResynchronizationTask != null) {
                mResynchronizationTask.cancel();
                mResynchronizationTask = null;
            }
        }
    }

    private void resynchronizeState() throws MPDException.MPDConnectionException {

        // Stop the interpolation
        stopInterpolation();

        // If a resync timer is running kill it also. It will be restarted when idling again
        stopResynchronization();

        mLastTimeBase = System.nanoTime();
        int volume = WSInterface.getGenericInstance().getCurrentVolume();
        distributeNewVolume(new MPDStatusChangeHandler.Volume(volume));
        MPDCurrentStatus status = null;
        try {
            status = WSInterface.getGenericInstance().getCurrentServerStatus();
        } catch (MPDException e) {
            handleMPDError(e);
            return;
        }

        if (status.getCurrentSongIndex() != mLastStatus.getCurrentSongIndex() || status.getPlaylistVersion() != mLastStatus.getPlaylistVersion()) {
            // New track started playing. Get it and inform the listener.
            try {
                mLastFile = WSInterface.getGenericInstance().getCurrentSong();
            } catch (MPDException e) {
                handleMPDError(e);
            }
            distributeNewTrack(mLastFile);
        }

        mLastStatus = status;
        distributeNewStatus(status);

        startInterpolation();
    }

    private void interpolateState() {
        // Generate a new dummy state
        if (null != mLastStatus) {
            MPDCurrentStatus status = new MPDCurrentStatus(mLastStatus);
            long timeDiff = (System.nanoTime() - mLastTimeBase) / (1000 * 1000 * 1000);

            // FIXME move timestamp to MPDConnection and MPDCurrentStatus (more precise, less time until saved)
            status.setElapsedTime(mLastStatus.getElapsedTime() + (int) timeDiff);
            distributeNewStatus(status);
        }
    }

    private synchronized void startInterpolation() {
        if (WSInterface.getGenericInstance().isConnected()) {
            if (mLastStatus.getPlaybackState() == MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_PLAYING) {
                stopInterpolation();

                mInterpolateTask = new InterpolateTask();
                mInterpolateTimer.schedule(mInterpolateTask, 0, mRefreshInterval);
            }

            stopResynchronization();

            mResynchronizationTask = new ResynchronizationTask();
            mResyncTimer.schedule(mResynchronizationTask, IDLE_TIME);
        }
    }

    public MPDCurrentStatus getLastStatus() {
        return mLastStatus;
    }


    public void registerStatusListener(MPDStatusChangeHandler handler) {
        if (null != handler) {
            synchronized (mStatusListeners) {
                mStatusListeners.add(handler);
                handler.newMPDTrackReady(mLastFile);
            }
        }
    }

    public void unregisterStatusListener(MPDStatusChangeHandler handler) {
        if (null != handler) {
            synchronized (mStatusListeners) {
                mStatusListeners.remove(handler);
            }
        }
    }


    private void distributeNewStatus(MPDCurrentStatus status) {
        synchronized (mStatusListeners) {
            for (MPDStatusChangeHandler handler : mStatusListeners) {
                handler.newMPDStatusReady(status);
            }
        }
    }

    private void distributeNewTrack(MPDTrack track) {
        synchronized (mStatusListeners) {
            for (MPDStatusChangeHandler handler : mStatusListeners) {
                handler.newMPDTrackReady(track);
            }
        }
    }

    private void distributeNewVolume(MPDStatusChangeHandler.Volume volume) {
        synchronized (mStatusListeners) {
            for (MPDStatusChangeHandler handler : mStatusListeners) {
                handler.newVolume(volume);
            }
        }
    }

    private void getState() {
        try {
            mLastStatus = WSInterface.getGenericInstance().getCurrentServerStatus();
            mLastFile = WSInterface.getGenericInstance().getCurrentSong();
        } catch (MPDException e) {
            mLastStatus = new MPDCurrentStatus();
            mLastFile = new MPDTrack("");
        }
        distributeNewStatus(mLastStatus);
        distributeNewTrack(mLastFile);
        try {
            resynchronizeState();
        } catch (MPDException.MPDConnectionException e) {
            e.printStackTrace();
        }
    }

    private void onConnected() {
        Thread stateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                getState();
            }
        });
        stateThread.start();
    }

    private void onDisconnected() {
        synchronized (this) {
            stopInterpolation();
            stopResynchronization();
        }
    }


    public void onIdle() {

    }

    public void onNoIdle() {
        // Server idle is over (reason unclear), resync the state
        try {
            resynchronizeState();
        } catch (MPDException.MPDConnectionException e) {
            e.printStackTrace();
        }
    }

    public void setRefreshInterval(int interval) {
        mRefreshInterval = interval;
    }


    private class ResynchronizationTask extends TimerTask {

        @Override
        public void run() {
            try {
                resynchronizeState();
            } catch (MPDException.MPDConnectionException e) {
                e.printStackTrace();
            }
        }
    }

    private class InterpolateTask extends TimerTask {

        @Override
        public void run() {
            interpolateState();
        }
    }

    private static class IdleStateListener extends MPDIdleChangeHandler {
        WeakReference<MPDStateMonitoringHandler> mParent;

        public IdleStateListener(MPDStateMonitoringHandler parentHandler, Looper looper) {
            super(looper);
            mParent = new WeakReference<>(parentHandler);
        }

        @Override
        protected void onIdle() {
            mParent.get().onIdle();
        }

        @Override
        protected void onNoIdle() {
            mParent.get().onNoIdle();
        }

    }

    private static class ConnectionStateListener extends MPDConnectionStateChangeHandler {
        WeakReference<MPDStateMonitoringHandler> mParent;

        public ConnectionStateListener(MPDStateMonitoringHandler parentHandler, Looper looper) {
            super(looper);
            mParent = new WeakReference<>(parentHandler);
        }

        @Override
        public void onConnected() {
            mParent.get().onConnected();
        }

        @Override
        public void onDisconnected() {
            mParent.get().onDisconnected();
        }
    }
}
