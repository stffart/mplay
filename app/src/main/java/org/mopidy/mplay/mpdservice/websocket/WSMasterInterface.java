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

import org.mopidy.mplay.mpdservice.ConnectionManager;
import org.mopidy.mplay.mpdservice.mpdprotocol.MPDException;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDProfileManager;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDServerProfile;
import org.mopidy.mplay.mpdservice.websocket.types.JSONDevice;
import org.mopidy.mplay.mpdservice.websocket.types.JSONMasterRequest;
import org.mopidy.mplay.mpdservice.websocket.types.responses.JSONMasterResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
    private String mToken = "";
    public String lastActivated = "";

    public static synchronized WSMasterInterface getGenericInstance()  {
        if (mGenericInterface == null) {
            mGenericInterface = new WSMasterInterface();
            mGenericInterface.setInstanceServerParameters(mHostname, mLogin, mPassword, mPort);
        }

        return mGenericInterface;
    }

    public void setServerParameters(String hostname, String login, String password, int port)  {
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
                boolean found = false;
                for(MPDServerProfile profile: profiles) {
                    if (profile.getHostname().equals(uri.getHost()) && profile.getPort() == uri.getPort()) {
                        found = true;
                        if (device.active) {
                            if (!lastActivated.equals(device.name)) {
                                MPDProfileManager.getInstance(null).setActiveProfile(profile, true);
                                WSMasterInterface.getGenericInstance().lastActivated = device.name;
                            }
                        }
                        if(device.me) {
                            MPDProfileManager.getInstance(null).setMasterServer(uri.getHost(),profile.getRemoteHostname(),uri.getPort(),profile.getRemotePort(),profile.getLogin(),profile.getPassword());
                            ConnectionManager.getInstance(null).createPlayer();
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
            throw new MPDException("Cannot connect mopidy");        }
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

    private void setInstanceServerParameters(String hostname, String login, String password, int port)  {
        mHostname = hostname;
        mPassword = password;
        mPort = port;
        String mCloudHostname = MPDProfileManager.getInstance(null).getCloudHostname();
        if (!mCloudHostname.isEmpty())
        {
            mHostname = mCloudHostname;
            mPort = MPDProfileManager.getInstance(null).getCloudPort();
            mLogin = MPDProfileManager.getInstance(null).getMasterLogin();
        }

        try {
            while(mAddListenerLatch) {
                Thread.sleep(100);
            }
            if (mHostname == null) return;
            if (mHostname.isEmpty()) return;
            String protocol = "http";
            if(!mLogin.isEmpty()) {
                mToken = getToken();
                protocol = "https";
            }
            String urlString = protocol+"://"+mHostname+":"+String.valueOf(mPort)+"/master/socketapi/ws";
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            if(connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(allHostsValid);
            }
            connection.connect();
            int code = connection.getResponseCode();
            if (code == 404) { //no master plugin detected
                urlString = protocol+"://"+mHostname+":"+String.valueOf(mPort)+"/mopidy_mopidy/socketapi/ws";
                url = new URL(urlString);
                connection = (HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                if(connection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) connection).setHostnameVerifier(allHostsValid);
                }
                connection.connect();
                code = connection.getResponseCode();
                if ( code == 404) { //no mopidy-mopidy plugin detected
                    return;
                }
            }
            urlString = urlString.replace("http","ws");
            mAddListenerLatch = true;
            factory.setVerifyHostname(false);
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            factory.setSSLContext(sc);
            this.mConnection = factory.createSocket(urlString);
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
}
