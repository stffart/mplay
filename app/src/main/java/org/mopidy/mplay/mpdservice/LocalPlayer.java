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

import android.content.Context;
import android.os.Handler;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;

import org.mopidy.mplay.mpdservice.profilemanagement.MPDProfileManager;

public class LocalPlayer {

    ExoPlayer mPlayer;
    private Handler mHandler;

    private float volume;


    private class PlayURIRunnable implements Runnable {
        private String mUri;
        PlayURIRunnable(String uri) {
            mUri = uri;
        }
        @Override
        public void run() {
            if (mUri.contains("mopidymopidy")) {
                String[] params = mUri.split(":");
                mUri = params[2]+":"+params[3]+":"+params[4];
            }
            String masterURL = MPDProfileManager.getInstance(null).getMasterURL();
            MediaItem mediaItem = MediaItem.fromUri(masterURL+"/master/track/"+mUri);
            mPlayer.setMediaItem(mediaItem);
            mPlayer.prepare();
            mPlayer.play();
        }
    }
    private class StopRunnable implements Runnable {
        StopRunnable() {
        }
        @Override
        public void run() {
            mPlayer.stop();
        }
    }
    private class PauseRunnable implements Runnable {
        PauseRunnable() {
        }
        @Override
        public void run() {
            mPlayer.pause();
        }
    }
    private class PlayRunnable implements Runnable {
        PlayRunnable() {
        }
        @Override
        public void run() {
            mPlayer.play();
        }
    }
    private class SeekRunnable implements Runnable {
        int mPosition;
        SeekRunnable(int position) {
            mPosition = position;
        }
        @Override
        public void run() {
            mPlayer.seekTo(mPosition);
        }
    }
    private class SetVolumeRunnable implements Runnable {
        float mVolume;
        public SetVolumeRunnable(float volume) {
            mVolume = volume;
        }
        @Override
        public void run() {
            int nVolume = Math.round(mPlayer.getDeviceInfo().maxVolume*mVolume);
            mPlayer.setDeviceVolume(nVolume);
        }
    }
    private class GetVolumeRunnable implements Runnable {
        public GetVolumeRunnable() {
        }
        @Override
        public void run() {
            int maxVolume = mPlayer.getDeviceInfo().maxVolume;
            if (maxVolume == 0) volume = 0;
            volume = 1.0f*mPlayer.getDeviceVolume()/maxVolume;
        }
    }

    public LocalPlayer(Context context) {
        mPlayer = new ExoPlayer.Builder(context).build();
        mHandler = new Handler();
    }


    public void playURI(String uri) {
        PlayURIRunnable runnable = new PlayURIRunnable(uri);
        mHandler.post(runnable);
    }

    public void play() {
        PlayRunnable runnable = new PlayRunnable();
        mHandler.post(runnable);
    }

    public void stop() {
        StopRunnable runnable = new StopRunnable();
        mHandler.post(runnable);
    }

    public void pause() {
        PauseRunnable runnable = new PauseRunnable();
        mHandler.post(runnable);
    }

    public void seek(int position) {
        SeekRunnable runnable = new SeekRunnable(position);
        mHandler.post(runnable);
    }

    public void setVolume(float volume) {
        SetVolumeRunnable runnable = new SetVolumeRunnable(volume);
        mHandler.post(runnable);
    }

    public float getVolume() {
        GetVolumeRunnable runnable = new GetVolumeRunnable();
        mHandler.post(runnable);
        return volume;
    }

}
