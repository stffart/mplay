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
import android.provider.MediaStore;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.mopidy.mplay.mpdservice.profilemanagement.MPDProfileManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class LocalPlayer {

    private final Context mContext;
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
            try {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            } catch (KeyManagementException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            if (mUri.contains("mopidymopidy")) {
                String[] params = mUri.split(":");
                mUri = params[2]+":"+params[3]+":"+params[4];
            }
            String masterURL = MPDProfileManager.getInstance(null).getMasterURL();
            MediaItem.Builder builder = new MediaItem.Builder();
            builder.setUri(masterURL+"/master/track/"+mUri);
            mPlayer.setMediaItem(builder.build());
            mPlayer.prepare();
            mPlayer.play();
        }
    }
    private class StopRunnable implements Runnable {
        StopRunnable() {
        }
        @Override
        public void run() {
            if (mPlayer != null)
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
    TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
    };
    HostnameVerifier allHostsValid = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };
    private String getToken() throws KeyManagementException, IOException, NoSuchAlgorithmException {
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        String masterURL = MPDProfileManager.getInstance(null).getMasterURL();
        String masterLogin = MPDProfileManager.getInstance(null).getMasterLogin();
        URL loginURL = new URL(masterURL+"/login");

        HttpsURLConnection conn = (HttpsURLConnection) loginURL.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false);
        conn.setHostnameVerifier(allHostsValid);
        Map<String,Object> params = new LinkedHashMap<>();
        params.put("login", masterLogin);
        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String,Object> param : params.entrySet()) {
            if (postData.length() != 0) postData.append('&');
            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            postData.append('=');
            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
        }
        byte[] postDataBytes = postData.toString().getBytes("UTF-8");
        int postLength = postDataBytes.length;

        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty( "Content-Length", Integer.toString( postLength ));
        try(OutputStream os = conn.getOutputStream()) {
            os.write(postDataBytes);
            os.flush();
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String answer = br.readLine();
        Map<String, List<String>> headerFields = conn.getHeaderFields();
        List<String> cookie = headerFields.get("Set-Cookie");
        String cookieParams = cookie.get(0).split("\"")[1];
        Logger.getLogger("test").log(Level.SEVERE, cookieParams.toString());
        return cookieParams;
    }
    String mToken = null;

    public LocalPlayer(Context context) {
        mContext = context;
        mHandler = new Handler();
    }
    
    public void initPlayer() {
        try {
            mToken = getToken();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        DataSource.Factory dataSourceFactory = new DataSource.Factory() {
            @Override
            public DataSource createDataSource() {
                HttpDataSource dataSource = new DefaultHttpDataSource();
                // Set a custom authentication request header.
                dataSource.setRequestProperty("Cookie", "moclauth="+mToken);
                return dataSource;
            }
        };
        mPlayer = new ExoPlayer.Builder(mContext)
                .setMediaSourceFactory(
                        new DefaultMediaSourceFactory(dataSourceFactory))
                .build();

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
