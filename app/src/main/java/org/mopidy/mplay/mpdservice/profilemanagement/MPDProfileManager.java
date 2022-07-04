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

package org.mopidy.mplay.mpdservice.profilemanagement;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.collection.ArraySet;

import org.mopidy.mplay.mpdservice.handlers.MPDStatusChangeHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

public class MPDProfileManager extends Observable {
    private static final String TAG = "ProfileManager";


    /**
     * Instance of the helper class to initialize the database.
     */
    private final MPDProfileDBHelper mDBHelper;

    private static MPDProfileManager mInstance;
    private String mMasterHost;
    private int mMasterPort;

    private MPDProfileManager(Context context) {
        /* Create instance of the helper class to get the writable DB later. */
        mDBHelper = new MPDProfileDBHelper(context.getApplicationContext());
    }

    public static synchronized MPDProfileManager getInstance(Context context) {
        if (null == mInstance) {
            mInstance = new MPDProfileManager(context);
        }

        return mInstance;
    }

    private final ArrayList<MPDProfileChangeHandler> mProfileListeners = new ArrayList<>();

    public void registerProfileListener(MPDProfileChangeHandler handler) {
        if (null != handler) {
            synchronized (mProfileListeners) {
                mProfileListeners.add(handler);
                //handler.profileChanged(getAutoconnectProfile());
            }
        }
    }

    public void unregisterProfileListener(MPDProfileChangeHandler handler) {
        if (null != handler) {
            synchronized (mProfileListeners) {
                mProfileListeners.remove(handler);
            }
        }
    }

    private void distributeProfile() {
        synchronized (mProfileListeners) {
            for (MPDProfileChangeHandler handler : mProfileListeners) {
                handler.profileChanged(getAutoconnectProfile());
            }
        }
    }

    /**
     * Creates a list of all available server profiles.
     *
     * @return The list of currently saved server profiles.
     */
    public synchronized List<MPDServerProfile> getProfiles() {
        ArrayList<MPDServerProfile> profileList = new ArrayList<>();

        /* Query the database table for profiles */
        SQLiteDatabase db = mDBHelper.getReadableDatabase();
        Cursor cursor = db.query(MPDServerProfileTable.SQL_TABLE_NAME, MPDServerProfileTable.PROJECTION_SERVER_PROFILES, null, null, null, null, MPDServerProfileTable.COLUMN_PROFILE_NAME);


        /* Iterate over the cursor and create MPDServerProfile objects */
        if (cursor.moveToFirst()) {
            do {
                /* Profile parameters */
                String profileName = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_NAME));
                boolean autoConnect = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_AUTO_CONNECT)) == 1;

                /* Server parameters */
                String serverHostname = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_SERVER_HOSTNAME));
                String serverLogin = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_SERVER_LOGIN));
                String serverPassword = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_SERVER_PASSWORD));
                int serverPort = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_SERVER_PORT));
                long creationDate = cursor.getLong(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_DATE_CREATED));

                /* Streaming parameters */
                String streamingURL = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_STREAMING_PORT));
                boolean streamingEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_STREAMING_ENABLED)) == 1;

                /* HTTP cover parameters */
                String httpCoverRegex = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_HTTP_COVER_REGEX));
                boolean httpCoverEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_HTTP_COVER_ENABLED)) == 1;

                // MPD Cover parameter
                boolean mpdCoverEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_MPD_COVER_ENABLED)) == 1;

                /* Create temporary object to append to list. */
                MPDServerProfile profile = new MPDServerProfile(profileName, autoConnect, creationDate);
                profile.setHostname(serverHostname);
                profile.setLogin(serverLogin);
                profile.setPassword(serverPassword);
                profile.setPort(serverPort);

                profile.setStreamingURL(streamingURL);
                profile.setStreamingEnabled(streamingEnabled);

                profile.setHTTPRegex(httpCoverRegex);
                profile.setHTTPCoverEnabled(httpCoverEnabled);

                profile.setMPDCoverEnabled(mpdCoverEnabled);

                /* Finish and add to list */
                profileList.add(profile);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return profileList;
    }

    /**
     * Adds a new server profile. There is no way to change a profile directly in the table.
     * Just delete and readd the profile.
     *
     * @param profile Profile to add to the database.
     */
    public synchronized void addProfile(MPDServerProfile profile) {
        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        if (profile.isLocalProfile())
            return;

        /* Check if autoconnect is set, if it is, all other autoconnects need to be set to 0 */
        if (profile.getAutoconnect()) {
            ContentValues autoConValues = new ContentValues();
            autoConValues.put(MPDServerProfileTable.COLUMN_PROFILE_AUTO_CONNECT, 0);

            /* Update the table columns to 0. */
            db.update(MPDServerProfileTable.SQL_TABLE_NAME, autoConValues, MPDServerProfileTable.COLUMN_PROFILE_AUTO_CONNECT + "=?", new String[]{"1"});
        }


        /* Prepare the sql transaction */
        ContentValues values = new ContentValues();

        /* Profile parameters */
        values.put(MPDServerProfileTable.COLUMN_PROFILE_NAME, profile.getProfileName());
        values.put(MPDServerProfileTable.COLUMN_PROFILE_AUTO_CONNECT, profile.getAutoconnect());

        /* Server parameter */
        values.put(MPDServerProfileTable.COLUMN_SERVER_HOSTNAME, profile.getHostname());
        values.put(MPDServerProfileTable.COLUMN_SERVER_LOGIN, profile.getLogin());

        values.put(MPDServerProfileTable.COLUMN_SERVER_PASSWORD, profile.getPassword());
        values.put(MPDServerProfileTable.COLUMN_SERVER_PORT, profile.getPort());
        values.put(MPDServerProfileTable.COLUMN_PROFILE_DATE_CREATED, profile.getCreationDate());

        /* Streaming parameters */
        values.put(MPDServerProfileTable.COLUMN_PROFILE_STREAMING_PORT, profile.getStreamingURL());
        values.put(MPDServerProfileTable.COLUMN_PROFILE_STREAMING_ENABLED, profile.getStreamingEnabled());

        /* HTTP cover parameters */
        values.put(MPDServerProfileTable.COLUMN_PROFILE_HTTP_COVER_REGEX, profile.getHTTPRegex());
        values.put(MPDServerProfileTable.COLUMN_PROFILE_HTTP_COVER_ENABLED, profile.getHTTPCoverEnabled());

        // MPD cover parameter
        values.put(MPDServerProfileTable.COLUMN_PROFILE_MPD_COVER_ENABLED, profile.getMPDCoverEnabled());

        /* Insert the table in the database */
        db.insert(MPDServerProfileTable.SQL_TABLE_NAME, null, values);

        db.close();

        notifyObservers();
    }


    /**
     * Removes a profile from the database. Make sure that you provide the correct profile.
     *
     * @param profile Profile to remove.
     */
    public synchronized void deleteProfile(MPDServerProfile profile) {
        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        /* Create the where clauses */
        String whereClause = MPDServerProfileTable.COLUMN_PROFILE_DATE_CREATED + "=?";

        String[] whereValues = {String.valueOf(profile.getCreationDate())};
        db.delete(MPDServerProfileTable.SQL_TABLE_NAME, whereClause, whereValues);

        db.close();

        notifyObservers();
    }


    /**
     * This method is convient to call to easily get the automatic connect server profile (if any).
     *
     * @return Profile to connect to otherwise null.
     */
    public synchronized MPDServerProfile getAutoconnectProfile() {
        SQLiteDatabase db = mDBHelper.getReadableDatabase();
        /* Query the database table for profiles */
        Cursor cursor = db.query(MPDServerProfileTable.SQL_TABLE_NAME, MPDServerProfileTable.PROJECTION_SERVER_PROFILES, MPDServerProfileTable.COLUMN_PROFILE_AUTO_CONNECT + "=?", new String[]{"1"}, null, null, null);


        /* Iterate over the cursor and create MPDServerProfile objects */
        if (cursor.moveToFirst()) {
            /* Profile parameters */
            String profileName = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_NAME));
            boolean autoConnect = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_AUTO_CONNECT)) == 1;

            /* Server parameters */
            String serverHostname = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_SERVER_HOSTNAME));
            String serverLogin = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_SERVER_LOGIN));

            String serverPassword = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_SERVER_PASSWORD));
            int serverPort = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_SERVER_PORT));
            long creationDate = cursor.getLong(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_DATE_CREATED));

            /* Streaming parameters */
            String streamingURL = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_STREAMING_PORT));
            boolean streamingEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_STREAMING_ENABLED)) == 1;

            /* HTTP cover parameters */
            String httpCoverRegex = cursor.getString(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_HTTP_COVER_REGEX));
            boolean httpCoverEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_HTTP_COVER_ENABLED)) == 1;

            // MPD Cover parameter
            boolean mpdCoverEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(MPDServerProfileTable.COLUMN_PROFILE_MPD_COVER_ENABLED)) == 1;

            /* Create temporary object to append to list. */
            MPDServerProfile profile = new MPDServerProfile(profileName, autoConnect, creationDate);
            profile.setHostname(serverHostname);
            profile.setPassword(serverPassword);
            profile.setPort(serverPort);
            profile.setLogin(serverLogin);
            profile.setStreamingURL(streamingURL);
            profile.setStreamingEnabled(streamingEnabled);

            profile.setHTTPRegex(httpCoverRegex);
            profile.setHTTPCoverEnabled(httpCoverEnabled);

            profile.setMPDCoverEnabled(mpdCoverEnabled);

            cursor.close();
            db.close();
            return profile;
        }

        cursor.close();
        db.close();
        return null;
    }

    public void setMasterServer(String host, int port) {
        mMasterHost = host;
        mMasterPort = port;
    }

    public String getMasterURL() {
        return "http://"+mMasterHost+":"+String.valueOf(mMasterPort);
    }

    public void setActiveProfile(MPDServerProfile profile) {

        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        if (profile.isLocalProfile())
            return;

        /* Check if autoconnect is set, if it is, all other autoconnects need to be set to 0 */
        ContentValues autoConValues = new ContentValues();
        autoConValues.put(MPDServerProfileTable.COLUMN_PROFILE_AUTO_CONNECT, 0);

        /* Update the table columns to 0. */
        db.update(MPDServerProfileTable.SQL_TABLE_NAME, autoConValues, MPDServerProfileTable.COLUMN_PROFILE_AUTO_CONNECT + "=?", new String[]{"1"});

        ContentValues autoConValuesOn = new ContentValues();
        autoConValuesOn.put(MPDServerProfileTable.COLUMN_PROFILE_AUTO_CONNECT, 1);

        db.update(MPDServerProfileTable.SQL_TABLE_NAME, autoConValuesOn, MPDServerProfileTable.COLUMN_PROFILE_NAME +"=?", new String[]{profile.getProfileName()});
        db.close();

        distributeProfile();
        notifyObservers();


    }
}
