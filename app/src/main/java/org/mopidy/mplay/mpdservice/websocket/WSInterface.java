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

package org.mopidy.mplay.mpdservice.websocket;

import android.util.Log;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketState;

import org.mopidy.mplay.mpdservice.LocalPlayer;
import org.mopidy.mplay.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.mopidy.mplay.mpdservice.handlers.MPDIdleChangeHandler;
import org.mopidy.mplay.mpdservice.mpdprotocol.MPDCache;
import org.mopidy.mplay.mpdservice.mpdprotocol.MPDCapabilities;
import org.mopidy.mplay.mpdservice.mpdprotocol.MPDCommands;
import org.mopidy.mplay.mpdservice.mpdprotocol.MPDException;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDOutput;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDStatistics;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDTrack;
import org.mopidy.mplay.mpdservice.websocket.types.JSONAlbum;
import org.mopidy.mplay.mpdservice.websocket.types.JSONArtist;
import org.mopidy.mplay.mpdservice.websocket.types.JSONSimplePlaylist;
import org.mopidy.mplay.mpdservice.websocket.types.JSONSimpleTrack;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsPlaylist;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsRemove;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsStartEnd;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsStartEndPosition;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsTLID;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsTimePosition;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsURI;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsBoolean;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsURIList;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsURIListWithPosition;
import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParamsVolume;
import org.mopidy.mplay.mpdservice.websocket.types.JSONPlaylist;
import org.mopidy.mplay.mpdservice.websocket.types.JSONSearchAlbumQuery;
import org.mopidy.mplay.mpdservice.websocket.types.JSONSearchAnyQuery;
import org.mopidy.mplay.mpdservice.websocket.types.JSONSearchArtistQuery;
import org.mopidy.mplay.mpdservice.websocket.types.JSONSearchResult;
import org.mopidy.mplay.mpdservice.websocket.types.JSONSearchTrackQuery;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONBrowseResponse;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONLookupResponse;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONPlaylistResponse;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONPlaylistsResponse;
import org.mopidy.mplay.mpdservice.websocket.types.JSONRequest;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONResponse;
import org.mopidy.mplay.mpdservice.websocket.types.JSONSearchParams;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONSearchResponse;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONSimpleResponse;
import org.mopidy.mplay.mpdservice.websocket.types.JSONTLTrack;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONTLTrackResponse;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONTLTracksResponse;
import org.mopidy.mplay.mpdservice.websocket.types.JSONTrack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import kotlin.RequiresOptIn;


public class WSInterface  {
    private static final String TAG = WSInterface.class.getSimpleName();
    private final String mLogTag;

    private WebSocket mConnection = null;
    private WebSocketFactory factory = new WebSocketFactory();

    private static String mHostname;
    private static int mPort;
    private static String mPassword;
    private static String mLogin;
    private static String mToken = "";

    private MPDCache mCache;

    private List<WSConnectionStateChangeListener> listeners = new ArrayList<WSConnectionStateChangeListener>();

    private static final long MAX_IMAGE_SIZE = 50 * 1024 * 1024; // 50 MB

    //private static WSInterface mArtworkInterface;
    //private static WSInterface mBackgroundInterface;
    private static WSInterface mGenericInterface;

    private int requestId = 1;

    boolean mPlayHere = false;
    
    ExecutorService es = Executors.newFixedThreadPool(2);
    ListeningExecutorService service = MoreExecutors.listeningDecorator(es);

    private HashMap<Integer,String> responses = new HashMap<>();
    private MPDIdleChangeHandler mIDLEChangeHandler;
    private LocalPlayer mPlayer = null;

    boolean mAddListenerLatch = false;

    private WSInterface(boolean autoDisconnect, String TAG) {
        this.mLogTag = TAG;
    }

    public static synchronized WSInterface getGenericInstance() {
        if (mGenericInterface == null) {
            mGenericInterface = new WSInterface(false, "WSGeneric");
            mGenericInterface.setInstanceServerParameters(mHostname, mLogin, mPassword, mPort);
        }

        return mGenericInterface;
    }


    public void setServerParameters(String hostname, String login, String password, int port) {
        mHostname = hostname;
        mPassword = password;
        mPort = port;
        mLogin = login;


        if (mGenericInterface != null) {
            mGenericInterface.setInstanceServerParameters(hostname, login, password, port);
        }

    }

    private void parseMessage(String message) {
        Gson gson = new Gson();
        JSONResponse response = gson.fromJson(message, JSONResponse.class);
        if (response.event != null)
        {
            Log.e(TAG,message);
            processEvent(response);
        } else
          responses.put(response.id, message);
        return;
    }

    private void processEvent(JSONResponse response) {
        switch (response.event) {
            case "track_playback_started":
                if(mPlayHere) {
                    if(mPlayer != null) {
                        mPlayer.playURI(response.tl_track.track.uri);
                    }
                }
                break;
            case "track_playback_paused":
                if(mPlayHere) {
                    if(mPlayer != null) {
                        mPlayer.pause();
                    }
                }
                break;
            case "track_playback_resumed":
                if(mPlayHere) {
                    if(mPlayer != null) {
                        mPlayer.play();
                    }
                }
                break;
            case "track_playback_ended":
                if(mPlayHere) {
                    if(mPlayer != null) {
                        mPlayer.stop();
                    }
                }
                break;

            case "tracklist_changed":
                mCache.invalidateTrackList();
            case "playback_state_changed":
            case "volume_changed":
                mIDLEChangeHandler.noIdle();
                break;
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

        URL loginURL = new URL("https://"+mHostname+":"+String.valueOf(mPort)+"/login");
        HttpsURLConnection conn = (HttpsURLConnection) loginURL.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false);
        conn.setHostnameVerifier(allHostsValid);
        Map<String,Object> params = new LinkedHashMap<>();
        params.put("login", mLogin);
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

    private void setInstanceServerParameters(String hostname, String login, String password, int port) {
        mCache = new MPDCache(0);
        mHostname = hostname;
        mPassword = password;
        if(mHostname == null)
            return;
        mLogin = login;
        mPort = port;
        try {
            while(mAddListenerLatch) {
                Thread.sleep(100);
            }
            if (mHostname == null) return;
            if (mHostname.isEmpty()) return;
            mAddListenerLatch = true;
            String protocol = "ws";

            String uri = "/mopidy/ws";
            if(!mLogin.isEmpty()) {
                mToken = getToken();
                protocol = "wss";
                uri = "/master/mopidyapi/ws";
            }
            factory.setVerifyHostname(false);
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            factory.setSSLContext(sc);
            this.mConnection = factory.createSocket(protocol+"://"+mHostname+":"+String.valueOf(mPort)+uri);

            this.mConnection.addHeader("Cookie", "moclauth="+mToken);
            for (WSConnectionStateChangeListener listener: listeners)
                mConnection.addListener(listener);
            mAddListenerLatch = false;
            mConnection.addListener(new WebSocketAdapter() {
                @Override
                public void onTextMessage(WebSocket websocket, String message) throws Exception {
                    parseMessage(message);
                }
                @Override
                public void onDisconnected(WebSocket websocket,
                                           WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame,
                                           boolean closedByServer) {


                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    public void addMPDConnectionStateChangeListener(MPDConnectionStateChangeHandler listener) {
        if (mConnection == null) return;
        WSConnectionStateChangeListener wslistener = new WSConnectionStateChangeListener(listener);
        listeners.add(wslistener);
        mConnection.addListener(wslistener);

    }

    public MPDCapabilities getServerCapabilities() {
        MPDCapabilities caps = new MPDCapabilities("17.1.0", null, null);
        return caps;
    }

    public synchronized void removeMPDConnectionStateChangeListener(MPDConnectionStateChangeHandler mConnectionCallback) {
        for (WSConnectionStateChangeListener listener: listeners) {
            if (listener.listener == mConnectionCallback) {
                listeners.remove(listener);
                mConnection.removeListener(listener);
                break;
            }
        }
    }

    public boolean isConnected() {
        if (mConnection == null) return false;
        return mConnection.isOpen();
    }



    public byte[] getAlbumArt(String model, boolean b) throws MPDException {
        return null;
    }

    public void connect() throws  MPDException {
        if (mConnection == null) return;
        while(mConnection.getState() == WebSocketState.CLOSING ||
                mConnection.getState() == WebSocketState.CONNECTING )
        {
            try {
                Log.e(TAG, "Start connection");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (mConnection.getState() == WebSocketState.OPEN)
            return;
        Log.e(TAG, "Start connection 2");

        if (mConnection.getState() == WebSocketState.CLOSED) {
            try {
                mConnection = mConnection.recreate();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        cancelWaiters = false;
        Log.e(TAG, "Start connection 3");
        try {
            mConnection.connect();
        } catch (WebSocketException e) {
            Log.e(TAG,String.valueOf(mConnection.getState()));
            e.printStackTrace();
            return;
        }
        Log.e(mLogTag,"WS CONNECTED");
        mIDLEChangeHandler.noIdle();
   }

    public void disconnect() {
        if(mConnection != null)
            mConnection.disconnect();
        Log.e(mLogTag,"WS DISCONNECTED");
    }

    public List<MPDFileEntry> getAlbumTracks(String albumName, String albumMBID) throws MPDException {
        Log.e(TAG, "getalbumtracks");
        return new ArrayList<>();
    }

    public void nextSong() throws MPDException.MPDConnectionException {
        Log.e(TAG, "next");
        JSONRequest request_next = new JSONRequest(getNextID(), "core.playback.next");
        sendRequest(request_next);
    }

    public void previousSong() throws MPDException.MPDConnectionException {
        JSONRequest request_time_position = new JSONRequest(getNextID(), "core.playback.get_time_position");
        sendRequest(request_time_position);
        Gson gson = new Gson();
        String response_time_position = waitResponse(request_time_position);
        JSONSimpleResponse time_position = gson.fromJson(response_time_position, JSONSimpleResponse.class);
        if (time_position.result != null) {
            Float position_ms = Float.valueOf(time_position.result);
            if (position_ms > 7500) {
                seekSeconds(0);
                return;
            }
        }
        Log.e(TAG, "previous");
        JSONRequest request_previous = new JSONRequest(getNextID(), "core.playback.previous");
        sendRequest(request_previous);
    }

    public void stopPlayback() throws MPDException.MPDConnectionException {
        Log.e(TAG, "stop");
        JSONRequest request_stop = new JSONRequest(getNextID(), "core.playback.stop");
        sendRequest(request_stop);

    }

    public void pause(boolean p) throws MPDException.MPDConnectionException {
        Log.e(TAG, "pause");
        if(p) {
            JSONRequest request_pause = new JSONRequest(getNextID(), "core.playback.pause");
            sendRequest(request_pause);
            String message = waitResponse(request_pause);
            Log.e(TAG,message);
        } else
        {
            JSONRequest request_play = new JSONRequest(getNextID(), "core.playback.play");
            sendRequest(request_play);
        }
    }

    public int getCurrentVolume() throws MPDException.MPDConnectionException {
        if (mPlayHere) {
            return Math.round(mPlayer.getVolume()*100);
        } else {
            JSONRequest request_volume = new JSONRequest(getNextID(), "core.mixer.get_volume");
            sendRequest(request_volume);
            Gson gson = new Gson();
            String response_volume = waitResponse(request_volume);
            JSONSimpleResponse volume = gson.fromJson(response_volume, JSONSimpleResponse.class);
            if (volume.result != null)
                return Integer.valueOf(volume.result);
            else
                return 0;

        }
    }

    public void sendRequest(JSONRequest request) throws MPDException.MPDConnectionException {
        if (mConnection == null)
            return;
        try {
            connect();
            if (!isConnected())
                throw new MPDException.MPDConnectionException("Cannot connect to mopidy");
        } catch (MPDException e) {
            e.printStackTrace();
            throw new MPDException.MPDConnectionException("Cannot connect to mopidy");
        }
        mConnection.sendText(request.toJSON());
    }

    public MPDCurrentStatus getCurrentServerStatus() throws MPDException {
        MPDCurrentStatus result = new MPDCurrentStatus();
        Gson gson = new Gson();

        JSONRequest request_current_track = new JSONRequest(getNextID(), "core.playback.get_current_tl_track");
        sendRequest(request_current_track);


        JSONRequest request_volume = new JSONRequest(getNextID(), "core.mixer.get_volume");
        sendRequest(request_volume);
        JSONRequest request_consume = new JSONRequest(getNextID(), "core.tracklist.get_consume");
        sendRequest(request_consume);
        JSONRequest request_random = new JSONRequest(getNextID(), "core.tracklist.get_random");
        sendRequest(request_random);
        JSONRequest request_repeat = new JSONRequest(getNextID(), "core.tracklist.get_repeat");
        sendRequest(request_repeat);
        JSONRequest request_single = new JSONRequest(getNextID(), "core.tracklist.get_single");
        sendRequest(request_single);
        JSONRequest request_state = new JSONRequest(getNextID(), "core.playback.get_state");
        sendRequest(request_state);
        JSONRequest request_length = new JSONRequest(getNextID(), "core.tracklist.get_length");
        sendRequest(request_length);
        JSONRequest request_version = new JSONRequest(getNextID(), "core.tracklist.get_version");
        sendRequest(request_version);
        JSONRequest request_time_position = new JSONRequest(getNextID(), "core.playback.get_time_position");
        sendRequest(request_time_position);

        String response_volume = waitResponse(request_volume);
        JSONSimpleResponse volume = gson.fromJson(response_volume, JSONSimpleResponse.class);
        if (volume.result == null)
            return mCache.getStatus();
        if (mPlayHere) {
            result.setVolume(Math.round(mPlayer.getVolume()*100));
        } else
            result.setVolume(Integer.valueOf(volume.result));

        String response_consume = waitResponse(request_consume);
        JSONSimpleResponse consume = gson.fromJson(response_consume, JSONSimpleResponse.class);
        if (consume.result == null)
            return mCache.getStatus();
        result.setConsume(Boolean.valueOf(consume.result) ? 1: 0);

        String response_random = waitResponse(request_random);
        JSONSimpleResponse random = gson.fromJson(response_random, JSONSimpleResponse.class);
        if (random.result == null)
            return mCache.getStatus();
        result.setRandom(Boolean.valueOf(random.result) ? 1: 0);

        String response_repeat = waitResponse(request_repeat);
        JSONSimpleResponse repeat = gson.fromJson(response_repeat, JSONSimpleResponse.class);
        if (repeat.result == null)
            return mCache.getStatus();
        result.setRepeat(Boolean.valueOf(repeat.result) ? 1: 0);

        String response_single = waitResponse(request_single);
        JSONSimpleResponse single = gson.fromJson(response_single, JSONSimpleResponse.class);
        if (single.result == null)
            return mCache.getStatus();
        result.setSinglePlayback(Boolean.valueOf(single.result) ? 1: 0);

        String response_length = waitResponse(request_length);
        JSONSimpleResponse length = gson.fromJson(response_length, JSONSimpleResponse.class);
        if (length.result == null)
            return mCache.getStatus();
        result.setPlaylistLength(Integer.valueOf(length.result));

        String response_version = waitResponse(request_version);
        JSONSimpleResponse version = gson.fromJson(response_version, JSONSimpleResponse.class);
        if (version.result == null)
            return mCache.getStatus();
        result.setPlaylistVersion(Integer.valueOf(version.result));

        String response_time_position = waitResponse(request_time_position);
        JSONSimpleResponse time_position = gson.fromJson(response_time_position, JSONSimpleResponse.class);
        if (time_position.result == null)
            return mCache.getStatus();
        result.setElapsedTime(Integer.valueOf(time_position.result)/1000.0f);



        String response_state = waitResponse(request_state);
        JSONSimpleResponse state = gson.fromJson(response_state, JSONSimpleResponse.class);
        if (state.result == null)
            return mCache.getStatus();
        switch( state.result ) {
            case "playing":
                result.setPlaybackState(MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_PLAYING);
                break;
            case "stopped":
                result.setPlaybackState(MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_STOPPED);
                break;
            case "paused":
                result.setPlaybackState(MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_PAUSING);
                break;
        }

        String response_current_track = waitResponse(request_current_track);
        JSONTLTrackResponse current_track = gson.fromJson(response_current_track, JSONTLTrackResponse.class);
        if (current_track.result != null) {
            JSONRequest request_index = new JSONRequest(getNextID(), "core.tracklist.index", new JSONParamsTLID(current_track.result.tlid));
            sendRequest(request_index);
            result.setTrackLength(current_track.result.track.length/1000.0f);

            String message_index = waitResponse(request_index);
            JSONSimpleResponse index = gson.fromJson(message_index, JSONSimpleResponse.class);
            if(index.result == null)
                throw new MPDException("Track index result is null");
            result.setCurrentSongIndex(Integer.valueOf(index.result));

        }
        mCache.cacheStatus(result);
        return result;
    }

    public void playSongIndex(int currentSongIndex) throws MPDException.MPDConnectionException {
        JSONRequest request_current_track = new JSONRequest(getNextID(), "core.tracklist.slice", new JSONParamsStartEnd(currentSongIndex,currentSongIndex+1));
        sendRequest(request_current_track);
        Gson gson = new Gson();
        String message = waitResponse(request_current_track);
        JSONTLTracksResponse tl_track = gson.fromJson(message, JSONTLTracksResponse.class);
        if (tl_track.result != null && tl_track.result.size() > 0) {
            JSONRequest request_play = new JSONRequest(getNextID(), "core.playback.play", new JSONParamsTLID(tl_track.result.get(0).tlid));
            sendRequest(request_play);
            String message_play = waitResponse(request_play);
        }
    }

    public void setRandom(boolean random) throws MPDException.MPDConnectionException {
        Log.e(TAG, "set random");
        JSONRequest request_random = new JSONRequest(getNextID(), "core.tracklist.set_random",new JSONParamsBoolean(random));
        sendRequest(request_random);
    }

    public void setRepeat(boolean repeat) throws MPDException.MPDConnectionException {
        Log.e(TAG, "set repeat");
        JSONRequest request_repeat = new JSONRequest(getNextID(), "core.tracklist.set_repeat",new JSONParamsBoolean(repeat));
        sendRequest(request_repeat);

    }

    public void setSingle(boolean single) throws MPDException.MPDConnectionException {
        Log.e(TAG, "set single");
        JSONRequest request_repeat = new JSONRequest(getNextID(), "core.tracklist.set_single",new JSONParamsBoolean(single));
        sendRequest(request_repeat);
    }

    public void setConsume(boolean consume) throws MPDException.MPDConnectionException {
        Log.e(TAG, "set consume");
        JSONRequest request_repeat = new JSONRequest(getNextID(), "core.tracklist.set_consume",new JSONParamsBoolean(consume));
        sendRequest(request_repeat);
    }

    public void seekSeconds(int seekTo) throws MPDException.MPDConnectionException {
        Log.e(TAG, "SEEK");
        JSONRequest request_seek = new JSONRequest(getNextID(), "core.playback.seek",new JSONParamsTimePosition(seekTo));
        sendRequest(request_seek);
        String message = waitResponse(request_seek);
        Log.e(TAG,message);
        mIDLEChangeHandler.noIdle();
    }

    public void setVolume(int volume) throws  MPDException {
        if(mPlayHere) {
            mPlayer.setVolume(volume/100.0f);
            mIDLEChangeHandler.noIdle();
        } else
            setServerVolume(volume);
    }

    public void setServerVolume(int volume) throws  MPDException {
        Log.e(TAG, "Setvolume");
        JSONRequest request_volume = new JSONRequest(getNextID(), "core.mixer.set_volume",new JSONParamsVolume(volume));
        sendRequest(request_volume);
    }


    public void toggleOutput(int outputID) {
        Log.e(TAG, "toggleoutput");
        if (outputID == 2) {
            mPlayHere = true;
        } else {
            mPlayHere = false;
            mPlayer.stop();
        }
    }

    public void enableOutput(int outputID) {
        Log.e(TAG, "enableoutput");
    }

    public void disableOutput(int outputID) {
        Log.e(TAG, "disableoutput");
    }

    private int getNextID() {
        requestId++;
        return requestId;
    }
    private String waitResponse(JSONRequest request) {
        return waitResponse(request,1,false);
    }


    private boolean cancelWaiters = false;
    private int mWaiters = 0;
    private String waitResponse(JSONRequest request, int maxRetry, boolean nowaiter) {
        int waittime = 50;
        if(!nowaiter)  mWaiters++;
        try {
            while(responses.get(request.id) == null) {
                    Thread.sleep(100);
                    waittime--;
                if (mConnection == null) {
                        mWaiters--;
                        return "{}";
                }
                if(waittime < 0 ||  mConnection.getState() == WebSocketState.CLOSED) {
                    Log.e(TAG,"Wait response Fail "+request.method+" "+String.valueOf(waittime)+" "+String.valueOf(mConnection.getState()));
                    if (cancelWaiters) {
                        mWaiters--;
                        return "{}";
                    }
                    if (mConnection.getState() == WebSocketState.CLOSED) {
                       mWaiters--;
                       return "{}";
                    }
                    if (maxRetry > 0) {
                        sendRequest(request);
                        return waitResponse(request,maxRetry-1,true);
                    } else {
                        mWaiters--;
                        return "{}";
                    }
                }
            }
        } catch (InterruptedException | MPDException.MPDConnectionException e) {
            e.printStackTrace();
        }
        mWaiters--;
        return responses.get(request.id);
    }

    public List<MPDAlbum> getAlbums() throws MPDException.MPDConnectionException {
        int id = getNextID();
        JSONRequest request = new JSONRequest(id, "core.library.search");
        JSONSearchParams params = new JSONSearchParams(new JSONSearchAlbumQuery("_____"));
        request.setParams(params);
        sendRequest(request);
        String message = waitResponse(request);
        Gson gson = new Gson();
        JSONSearchResponse response = gson.fromJson(message, JSONSearchResponse.class);

        ArrayList<MPDAlbum> result = new ArrayList<>();
        if(response.result != null && response.result.size() > 0)
            for (JSONAlbum album: response.result.get(0).albums) {
                result.add(album.toMPDAlbum());
            }
        return result;
    }

    public List<MPDAlbum> getAlbumsInPath(String path) {
        Log.e(TAG, "getalbumsinpath");
        return new ArrayList<MPDAlbum>();
    }

    public void clearPlaylist() throws MPDException.MPDConnectionException {
        Log.e(TAG, "clearplaylist");
        JSONRequest request_clear = new JSONRequest(getNextID(), "core.tracklist.clear");
        sendRequest(request_clear);
    }

    public void addAlbumTracks(String albumURI, String artistName, String mbid) throws MPDException {
        Log.e(TAG, "addalbumtracks");
        List<MPDFileEntry> tracks = getAlbumTracks(albumURI);
        ArrayList<String> track_uris = new ArrayList<>();
        for(MPDFileEntry track: tracks) {
            track_uris.add(track.getURI());
        }
        JSONRequest request_load = new JSONRequest(getNextID(), "core.tracklist.add",new JSONParamsURIList(track_uris));
        sendRequest(request_load);
        String message = waitResponse(request_load);
        Log.e(TAG,message);
    }

    public List<MPDAlbum> getArtistAlbums(String artistUri) {
        Log.e(TAG, "getartistalbums");
        JSONRequest request_files = new JSONRequest(getNextID(), "core.library.browse", new JSONParamsURI(artistUri));
        mConnection.sendText(request_files.toJSONWithNulls());
        Gson gson = new Gson();
        String message = waitResponse(request_files);
        JSONBrowseResponse refs = gson.fromJson(message, JSONBrowseResponse.class);
        ArrayList<MPDAlbum> result = new ArrayList<MPDAlbum>();
        if(refs.result != null)
            for (JSONTrack track: refs.result) {
                result.add(track.toMPDAlbum());
            }
        return result;
    }

    public List<MPDAlbum> getArtistSortAlbums(String artistName) {
        Log.e(TAG, "getartistsortalbums");

        return new ArrayList<MPDAlbum>();
    }

    public List<MPDArtist> getArtists() throws MPDException.MPDConnectionException {
        int id = getNextID();
        JSONRequest request = new JSONRequest(id, "core.library.search");
        JSONSearchParams params = new JSONSearchParams(new JSONSearchArtistQuery("_____"));
        request.setParams(params);
        sendRequest(request);
        Gson gson = new Gson();
        String message = waitResponse(request);
        JSONSearchResponse response = gson.fromJson(message, JSONSearchResponse.class);

        ArrayList<MPDArtist> result = new ArrayList<>();
        if(response.result != null && response.result.size() > 0)
            if(response.result.get(0).artists != null)
            for (JSONArtist artist: response.result.get(0).artists) {
                result.add(artist.toMPDArtist());
            }
        return result;
    }

    public List<MPDArtist> getArtistsSort() {
        Log.e(TAG, "getartistssort");
        return new ArrayList<MPDArtist>();
    }

    public List<MPDArtist> getAlbumArtists() {
        Log.e(TAG, "getalbumartists");
        return new ArrayList<MPDArtist>();
    }

    public List<MPDArtist> getAlbumArtistsSort() {
        Log.e(TAG, "getalbumartistssort");
        return new ArrayList<MPDArtist>();
    }

    public List<MPDFileEntry> getAlbumTracks(String albumURI) {
        Log.e(TAG, "getalbumtracks");
        JSONRequest request_files = new JSONRequest(getNextID(), "core.library.lookup", new JSONParamsURIList(albumURI));
        mConnection.sendText(request_files.toJSONWithNulls());
        Gson gson = new Gson();
        String message = waitResponse(request_files);
        JSONLookupResponse refs = gson.fromJson(message, JSONLookupResponse.class);
        ArrayList<MPDFileEntry> result = new ArrayList<MPDFileEntry>();
        if (refs.result != null)
            for(String key: refs.result.keySet()) {
                for (JSONTrack track : refs.result.get(key)) {
                        result.add(track.toMPDTrack());
                }
            }

        return result;
    }

    public List<MPDFileEntry> getAllTracks() {
        Log.e(TAG, "getalltracks");
        return new ArrayList<MPDFileEntry>();
    }

    public List<MPDFileEntry> getFiles(String path) {
        Log.e(TAG, "getfiles");
        String uri = path;
        if (path.isEmpty()) {
            uri = null;
        }
        JSONRequest request_files = new JSONRequest(getNextID(), "core.library.browse", new JSONParamsURI(uri));
        mConnection.sendText(request_files.toJSONWithNulls());
        Gson gson = new Gson();
        String message = waitResponse(request_files);
        JSONBrowseResponse refs = gson.fromJson(message, JSONBrowseResponse.class);
        ArrayList<MPDFileEntry> result = new ArrayList<MPDFileEntry>();
        for (JSONTrack track: refs.result) {
            if ("track".equals(track.type))
                result.add(track.toMPDTrack());
            else
                result.add(track.toMPDDirectory());
        }
        // new ArrayList<MPDFileEntry>();
        return result;
    }

    public List<MPDFileEntry> getArtistAlbumTracks(String albumName, String artistName, String albumMBID) {
        Log.e(TAG, "getartistalbumtracks");
        return new ArrayList<MPDFileEntry>();
    }

    public List<MPDFileEntry> getArtistSortAlbumTracks(String albumName, String artistName, String albumMBID) {
        Log.e(TAG, "getartistsortalbumtracks");
        return new ArrayList<MPDFileEntry>();
    }

    public List<MPDFileEntry> getCurrentPlaylist() throws MPDException {
        Log.e(TAG, "getcurrentplaylist");
        if(mCache.trackListValid())
            return mCache.getTracklist();
        JSONRequest request_tracklist = new JSONRequest(getNextID(), "core.tracklist.get_tl_tracks");
        sendRequest(request_tracklist);
        Gson gson = new Gson();
        String message = waitResponse(request_tracklist);
        JSONTLTracksResponse tl_tracks = gson.fromJson(message, JSONTLTracksResponse.class);
        ArrayList<MPDFileEntry> result = new ArrayList<MPDFileEntry>();
        if (tl_tracks.result == null) {
            Log.e(TAG, message);
//            return mCache.getTracklist();
            new MPDException("Cannot get current tracklist");
        }
        for (JSONTLTrack track: tl_tracks.result) {
            result.add(track.track.toMPDTrack());
        }
        mCache.cacheTracklist(result);
        return result;
    }

    public List<MPDFileEntry> getCurrentPlaylistWindow(int start, int end) {
        Log.e(TAG, "getcurrentplaylistwindow");
        JSONRequest request_tracklist = new JSONRequest(getNextID(), "core.tracklist.slice",new JSONParamsStartEnd(start,end));
        mConnection.sendText(request_tracklist.toJSON());
        Gson gson = new Gson();
        String message = waitResponse(request_tracklist);
        JSONTLTracksResponse tl_tracks = gson.fromJson(message, JSONTLTracksResponse.class);
        ArrayList<MPDFileEntry> result = new ArrayList<MPDFileEntry>();
        if(tl_tracks.result != null)
            for (JSONTLTrack track: tl_tracks.result) {
                result.add(track.track.toMPDTrack());
            }
        return result;
    }

    public List<MPDFileEntry> getSavedPlaylist(String playlistName) {
        JSONRequest request_playlist = new JSONRequest(getNextID(), "core.playlists.lookup", new JSONParamsURI(playlistName));
        mConnection.sendText(request_playlist.toJSON());
        Gson gson = new Gson();
        String message = waitResponse(request_playlist);
        JSONPlaylistResponse playlist = gson.fromJson(message, JSONPlaylistResponse.class);
        ArrayList<MPDFileEntry> result = new ArrayList<MPDFileEntry>();
        if (playlist.result != null)
            if (playlist.result.tracks != null)
                for (JSONTrack track: playlist.result.tracks) {
                    result.add(track.toMPDTrack());
                }
        return result;
    }

    public List<MPDFileEntry> getPlaylists() throws MPDException {
        Log.e(TAG, "getplaylists");
        JSONRequest request_playlists = new JSONRequest(getNextID(), "core.playlists.as_list");
        sendRequest(request_playlists);
        Gson gson = new Gson();
        String message = waitResponse(request_playlists);
        JSONPlaylistsResponse playlists = gson.fromJson(message, JSONPlaylistsResponse.class);
        ArrayList<MPDFileEntry> result = new ArrayList<MPDFileEntry>();
        if (playlists.result == null) {
            Log.e(TAG, "Cannot get playlists from " + message);
            if (mCache.getPlaylists() != null)
                return mCache.getPlaylists();

            throw new MPDException("Cannot get playlists");
        }
        for (JSONPlaylist playlist: playlists.result) {
            result.add(playlist.toMPDPlaylist());
        }
        mCache.cachePlaylists(result);
        return result;
    }

    public void doLike(String likeURI) {
        URL url = null;
        try {
            url = new URL("http://"+mHostname+":6680/musicbox_darkclient/"+likeURI);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            InputStream responseStream = con.getInputStream();
            Scanner s = new Scanner(responseStream).useDelimiter("\\A");
            String result = s.hasNext() ? s.next(): "";
            Log.e(TAG,result);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void savePlaylist(String playlistName) {
        Log.e(TAG, "saveplaylist deprecated");
    }

    public void savePlaylist(JSONSimplePlaylist playlist) throws MPDException.MPDConnectionException {
        Log.e(TAG, "saveplaylist");
        JSONRequest request_playlist = new JSONRequest(getNextID(), "core.playlists.save", new JSONParamsPlaylist(playlist));
        sendRequest(request_playlist);
        Gson gson = new Gson();
        String message = waitResponse(request_playlist);
        JSONPlaylistResponse result = gson.fromJson(message, JSONPlaylistResponse.class);
    }

    public void addSongToPlaylist(String playlistName, String path) throws MPDException.MPDConnectionException {
        Log.e(TAG, "addsongtoplaylist");
        JSONRequest request_playlist = new JSONRequest(getNextID(), "core.playlists.lookup", new JSONParamsURI(playlistName));
        mConnection.sendText(request_playlist.toJSON());
        Gson gson = new Gson();
        String message = waitResponse(request_playlist);
        JSONPlaylistResponse playlist = gson.fromJson(message, JSONPlaylistResponse.class);

        JSONSimpleTrack new_track = new JSONSimpleTrack(path);
        ArrayList<JSONSimpleTrack> new_tracks = new ArrayList<>();
        new_tracks.add(new_track);
        for(JSONSimpleTrack track: playlist.result.tracks) {
            JSONSimpleTrack uritrack = new JSONSimpleTrack(track.uri);
            new_tracks.add(uritrack);
        }
        JSONSimplePlaylist new_playlist = new JSONSimplePlaylist(playlist.result.uri,playlist.result.name,new_tracks);

        savePlaylist(new_playlist);
    }

    public void removeSongFromPlaylist(String playlistName, int position) throws MPDException.MPDConnectionException {
        Log.e(TAG, "removesongfromplaylist");
        JSONRequest request_playlist = new JSONRequest(getNextID(), "core.playlists.lookup", new JSONParamsURI(playlistName));
        mConnection.sendText(request_playlist.toJSON());
        Gson gson = new Gson();
        String message = waitResponse(request_playlist);
        JSONPlaylistResponse playlist = gson.fromJson(message, JSONPlaylistResponse.class);

        ArrayList<JSONSimpleTrack> new_tracks = new ArrayList<>();
        for(JSONSimpleTrack track: playlist.result.tracks) {
            JSONSimpleTrack uritrack = new JSONSimpleTrack(track.uri);
            new_tracks.add(uritrack);
        }
        new_tracks.remove(position);
        JSONSimplePlaylist new_playlist = new JSONSimplePlaylist(playlist.result.uri,playlist.result.name,new_tracks);

        savePlaylist(new_playlist);
    }

    public void removePlaylist(String playlistName) throws MPDException.MPDConnectionException {
        Log.e(TAG, "removeplaylist");
        JSONRequest request_delete = new JSONRequest(getNextID(), "core.playlists.delete",new JSONParamsURI(playlistName));
        sendRequest(request_delete);
        String message = waitResponse(request_delete);
        Log.e(TAG,message);
    }

    public void loadPlaylist(String playlistName) throws MPDException.MPDConnectionException {
        Log.e(TAG, "loadplaylist");
        List<MPDFileEntry> tracks = getSavedPlaylist(playlistName);
        ArrayList<String> track_uris = new ArrayList<>();
        for(MPDFileEntry track: tracks) {
            track_uris.add(track.getURI());
        }
        JSONRequest request_load = new JSONRequest(getNextID(), "core.tracklist.add",new JSONParamsURIList(track_uris));
        sendRequest(request_load);
        String message = waitResponse(request_load);
        Log.e(TAG,message);
    }

    public void addArtistSortAlbumTracks(String albumname, String artistname, String albumMBID) {
        Log.e(TAG, "addartistsort");
    }

    public void addArtist(String artistname, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder) {
        Log.e(TAG, "addartist");
    }

    public void addArtistSort(String artistname, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder) {
        Log.e(TAG, "addartistsort");
    }

    public void addSong(String url) throws MPDException.MPDConnectionException {
        Log.e(TAG, "addsong");
        JSONRequest request_current_track = new JSONRequest(getNextID(), "core.tracklist.add", new JSONParamsURIList(url));
        sendRequest(request_current_track);
        Gson gson = new Gson();
        String message = waitResponse(request_current_track);
        JSONTLTracksResponse tl_track = gson.fromJson(message, JSONTLTracksResponse.class);
        if (tl_track.result != null && tl_track.result.size() > 0) {
            JSONRequest request_play = new JSONRequest(getNextID(), "core.playback.play", new JSONParamsTLID(tl_track.result.get(0).tlid));
            sendRequest(request_play);
            String message_play = waitResponse(request_play);
        }
    }

    public void addSongatIndex(String url, int i) throws MPDException.MPDConnectionException {
        Log.e(TAG, "addsongatindex");
        JSONRequest request_add_track = new JSONRequest(getNextID(), "core.tracklist.add", new JSONParamsURIListWithPosition(url,i));
        sendRequest(request_add_track);
        Gson gson = new Gson();
        String message = waitResponse(request_add_track);
        JSONTLTracksResponse tl_track = gson.fromJson(message, JSONTLTracksResponse.class);
    }

    public List<MPDFileEntry> getPlaylistFindTrack(String url) {
        Log.e(TAG, "playlistfindtrack");
        return new ArrayList<MPDFileEntry>();

    }

    public void shufflePlaylist() throws MPDException.MPDConnectionException {
        Log.e(TAG, "shuffle");
        JSONRequest request_shuffle = new JSONRequest(getNextID(), "core.tracklist.shuffle");
        sendRequest(request_shuffle);
    }

    public void moveSongFromTo(int index, int currentSongIndex) throws MPDException.MPDConnectionException {
        Log.e(TAG, "moveSong");
        JSONRequest request_move_track = new JSONRequest(getNextID(), "core.tracklist.move", new JSONParamsStartEndPosition(index,index+1,currentSongIndex));
        sendRequest(request_move_track);
        Gson gson = new Gson();
        String message = waitResponse(request_move_track);
        JSONTLTracksResponse tl_track = gson.fromJson(message, JSONTLTracksResponse.class);
    }

    public void removeIndex(int index) throws MPDException.MPDConnectionException {
        Log.e(TAG, "removeIndex");
        removeRange(index,index+1);
    }

    public void removeRange(int start, int end) throws MPDException.MPDConnectionException {
        Log.e(TAG, "removeRange");
        JSONRequest request_current_track = new JSONRequest(getNextID(), "core.tracklist.slice", new JSONParamsStartEnd(start,end));
        sendRequest(request_current_track);
        Gson gson = new Gson();
        String message = waitResponse(request_current_track);
        JSONTLTracksResponse tl_track = gson.fromJson(message, JSONTLTracksResponse.class);
        if (tl_track.result != null && tl_track.result.size() > 0) {
            JSONRequest request_remove_track = new JSONRequest(getNextID(), "core.tracklist.remove", new JSONParamsRemove(tl_track.result.get(0).tlid));
            sendRequest(request_remove_track);
            String message_remove = waitResponse(request_remove_track);
            Log.e(TAG,message_remove);
        }
    }

    public List<MPDOutput> getOutputs() {
        Log.e(TAG, "getOutputs");
        ArrayList<MPDOutput> outputs = new ArrayList<MPDOutput>();
        outputs.add(new MPDOutput("server", !mPlayHere, 1));
        outputs.add(new MPDOutput("phone", mPlayHere, 2));
        return outputs;
    }

    public MPDStatistics getServerStatistics() {
        Log.e(TAG, "getServerStatistics");
        return new MPDStatistics();
    }

    public void updateDatabase(String updatePath) {
        Log.e(TAG, "updateDatabase");
    }

    public List<MPDFileEntry> getSearchedFiles(String term, MPDCommands.MPD_SEARCH_TYPE type) throws MPDException.MPDConnectionException {
        Log.e(TAG, "getSearchedFiles");
        int id = getNextID();
        JSONRequest request = new JSONRequest(id, "core.library.search");
        JSONSearchParams params;
        switch (type) {
            case MPD_SEARCH_ALBUM:
                params = new JSONSearchParams(new JSONSearchAlbumQuery(term));
                break;
            case MPD_SEARCH_TRACK:
                params = new JSONSearchParams(new JSONSearchTrackQuery(term));
                break;
            case MPD_SEARCH_ARTIST:
                params = new JSONSearchParams(new JSONSearchArtistQuery(term));
                break;
            default:
                params = new JSONSearchParams(new JSONSearchAnyQuery(term));
                break;

        }
        request.setParams(params);
        sendRequest(request);
        String message = waitResponse(request);
        Gson gson = new Gson();
        JSONSearchResponse response = gson.fromJson(message, JSONSearchResponse.class);

        ArrayList<MPDFileEntry> result = new ArrayList<>();
        for (JSONSearchResult res: response.result) {
            if (res.albums != null)
              for (JSONAlbum album: res.albums) {
                  result.add(album.toMPDDirectory());
              }
            if (res.artists != null)
              for (JSONArtist artist: res.artists) {
                  result.add(artist.toMPDDirectory());
              }
            if (res.tracks != null)
              for (JSONTrack track: res.tracks) {
                  result.add(track.toMPDTrack());
              }
        }
        return result;
    }

    public void addSearchedFiles(String term, MPDCommands.MPD_SEARCH_TYPE type) {
        Log.e(TAG, "addSearchedFiles");
    }

    public void addTrackList(List<MPDFileEntry> searchResults) throws MPDException.MPDConnectionException {
        Log.e(TAG, "addTrackList");
        ArrayList<String> uris = new ArrayList<>();
        for(MPDFileEntry entry: searchResults) {
            if (entry.getURI().contains("track:")) {
                uris.add(entry.getURI());
            } else {
                JSONRequest request_files = new JSONRequest(getNextID(), "core.library.lookup", new JSONParamsURIList(entry.getURI()));
                mConnection.sendText(request_files.toJSONWithNulls());
                Gson gson = new Gson();
                String message = waitResponse(request_files);
                JSONLookupResponse res = gson.fromJson(message, JSONLookupResponse.class);
                for (String key : res.result.keySet()) {
                    for (JSONTrack track: res.result.get(key))
                      if (track.uri.contains("track:"))
                        uris.add(track.uri);
                }
            }
        }
        JSONRequest request_add_tracks = new JSONRequest(getNextID(), "core.tracklist.add", new JSONParamsURIList(uris));
        sendRequest(request_add_tracks);
        String message = waitResponse(request_add_tracks);
        Log.e(TAG, message);
    }

    public MPDTrack getCurrentSong() throws MPDException {
        JSONRequest request_current_track = new JSONRequest(getNextID(), "core.playback.get_current_tl_track");
        sendRequest(request_current_track);
        Gson gson = new Gson();
        String message = waitResponse(request_current_track);
        JSONTLTrackResponse tl_track = gson.fromJson(message, JSONTLTrackResponse.class);
        if (tl_track.result == null) {
            return new MPDTrack("");
        }
        return tl_track.result.track.toMPDTrack();
    }

    public void addMPDIdleChangeHandler(MPDIdleChangeHandler idleStateListener) {
        mIDLEChangeHandler = idleStateListener;
    }

    public void setLocalPlayer(LocalPlayer mPlayer) {
        this.mPlayer = mPlayer;
    }

    public void startLocalPlayer() {
        if(mPlayHere)
            return;
        try {
            MPDTrack currentTrack = getCurrentSong();
            if (currentTrack == null)
                return;
            if (currentTrack.getURI().isEmpty())
                return;
            mPlayer.playURI(currentTrack.getURI());
            mPlayHere = true;
            JSONRequest request_time_position = new JSONRequest(getNextID(), "core.playback.get_time_position");
            sendRequest(request_time_position);
            String response_time_position = waitResponse(request_time_position);
            Gson gson = new Gson();
            JSONSimpleResponse time_position = gson.fromJson(response_time_position, JSONSimpleResponse.class);
            if (time_position.result != null)
                mPlayer.seek(Integer.valueOf(time_position.result));
            setServerVolume(0);
        } catch (MPDException e) {
            e.printStackTrace();
        }

    }

    public void stopLocalPlayer() {
        mPlayHere = false;
        mPlayer.stop();
        try {
            setServerVolume(10);
        } catch (MPDException e) {
            e.printStackTrace();
        }
    }


}
