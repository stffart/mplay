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

import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketState;

import org.mopidy.mplay.mpdservice.mpdprotocol.MPDException;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDProfileManager;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDServerProfile;
import org.mopidy.mplay.mpdservice.websocket.types.JSONDevice;
import org.mopidy.mplay.mpdservice.websocket.types.JSONMasterRequest;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONMasterResponse;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class WSMasterInterface {
    private static final String TAG = WSInterface.class.getSimpleName();
    private static WSMasterInterface mGenericInterface;
    private WebSocket mConnection = null;
    private WebSocketFactory factory = new WebSocketFactory();

    private static String mHostname;
    private static int mPort;
    private static String mPassword;
    private static String mLogin;

    private List<WSConnectionStateChangeListener> listeners = new ArrayList<WSConnectionStateChangeListener>();
    private boolean mAddListenerLatch = false;

    public static synchronized WSMasterInterface getGenericInstance() {
        if (mGenericInterface == null) {
            mGenericInterface = new WSMasterInterface();
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
        JSONMasterResponse response = gson.fromJson(message, JSONMasterResponse.class);
        if (response.devices != null)
        {
            List<MPDServerProfile> profiles = MPDProfileManager.getInstance(null).getProfiles();
            for(String deviceName: response.devices.keySet()) {
                JSONDevice device = response.devices.get(deviceName);
                URI uri = null;
                try {
                    uri = new URI(device.ws);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                if(device.me) {
                    MPDProfileManager.getInstance(null).setMasterServer(uri.getHost(),uri.getPort());
                }
                boolean found = false;
                for(MPDServerProfile profile: profiles) {
                    if (profile.getHostname().equals(uri.getHost()) && profile.getPort() == uri.getPort()) {
                        found = true;
                        if (device.active) {
                            MPDProfileManager.getInstance(null).setActiveProfile(profile);
                        }
                        break;
                    }
                }
                if (!found) {
                    MPDServerProfile nprofile = new MPDServerProfile(deviceName,false);
                    nprofile.setHostname(uri.getHost());
                    nprofile.setPort(uri.getPort());
                    MPDProfileManager.getInstance(null).addProfile(nprofile);
                }
            }
        } else
            //responses.put(response.id, message);
        return;
    }

    public void sendRequest(JSONMasterRequest request) {
        if (mConnection == null) return;
        try {
            connect();
        } catch (MPDException e) {
            e.printStackTrace();
        }
        mConnection.sendText(request.toJSON());
    }


    public void connect() throws MPDException {
        if (mConnection == null) return;
        if (mConnection.getState() == WebSocketState.OPEN)
            return;
        if(mConnection.getState() == WebSocketState.CLOSING) {
            while(mConnection.getState() != WebSocketState.CLOSED)
            {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (mConnection.getState() == WebSocketState.CLOSED) {
            try {
                mConnection = mConnection.recreate();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            mConnection.connect();
            JSONMasterRequest request_subscribe = new JSONMasterRequest("subscribe");
            sendRequest(request_subscribe);
            JSONMasterRequest request_list = new JSONMasterRequest("list");
            sendRequest(request_list);
            Log.e(TAG,"MASTER WS CONNECTED");
        } catch (WebSocketException e) {
            Log.e(TAG,String.valueOf(mConnection.getState()));
            e.printStackTrace();
        }
    }

    private void setInstanceServerParameters(String hostname, String login, String password, int port) {
        mHostname = hostname;
        mPassword = password;
        mPort = port;
        try {
            while(mAddListenerLatch) {
                Thread.sleep(100);
            }
            if (mHostname == null) return;
            if (mHostname.isEmpty()) return;

            String urlString = "http://"+mHostname+":"+String.valueOf(mPort)+"/master/socketapi/ws";
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int code = connection.getResponseCode();
            if (code == 404) { //no master plugin detected
                urlString = "http://"+mHostname+":"+String.valueOf(mPort)+"/mopidy_mopidy/socketapi/ws";
                url = new URL(urlString);
                connection = (HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                code = connection.getResponseCode();
                if ( code == 404) { //no mopidy-mopidy plugin detected
                    return;
                }
            }
            urlString = urlString.replace("http://","ws://");
            mAddListenerLatch = true;
            this.mConnection = factory.createSocket(urlString);

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
        }
    }
}
