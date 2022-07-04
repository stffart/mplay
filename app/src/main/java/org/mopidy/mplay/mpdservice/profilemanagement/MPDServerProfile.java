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


import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDGenericItem;

public class MPDServerProfile implements MPDGenericItem, Parcelable {
    /**
     * Profile parameters
     */
    private String mProfileName = "";

    /**
     * If this boolean is set the application should use this profile to automatically
     * connect to a server. There should always be only one profile with this flag set
     * to true in the database.
     */
    private boolean mAutoconnect;

    /**
     * Server parameters.
     */
    @NonNull
    private String mHostname = "";
    @NonNull
    private String mLogin = "";

    @NonNull
    private String mPassword = "";
    private int mPort;

    @NonNull
    private String mStreamingURL = "";
    private boolean mStreamingEnabled;

    @NonNull
    private String mHTTPCoverRegex = "";
    private boolean mHTTPCoverEnabled;

    private boolean mMPDCoverEnabled = true;

    private long mCreated;

    boolean mIsLocal = false;

    boolean mIsLocalActive = false;

    public MPDServerProfile() {
        mProfileName = "";
        mHostname = "";
    }

    public MPDServerProfile(String profileName, boolean autoConnect) {
        mProfileName = profileName;
        mAutoconnect = autoConnect;

        mCreated = System.currentTimeMillis();

        /* Just set the default mpd port here */
        mPort = 6680;
    }

    public MPDServerProfile(String profileName, boolean autoConnect, long creationDate) {
        mProfileName = profileName;
        mAutoconnect = autoConnect;

        mCreated = System.currentTimeMillis();

        /* Just set the default mpd port here */
        mPort = 6680;
        mCreated = creationDate;
    }

    protected MPDServerProfile(Parcel in) {
        mProfileName = in.readString();
        mAutoconnect = in.readByte() != 0;
        mHostname = in.readString();
        mPassword = in.readString();
        mPort = in.readInt();
        mStreamingURL = in.readString();
        mStreamingEnabled = in.readByte() != 0;
        mHTTPCoverRegex = in.readString();
        mHTTPCoverEnabled = in.readByte() != 0;
        mMPDCoverEnabled = in.readByte() != 0;
        mCreated = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mProfileName);
        dest.writeByte((byte) (mAutoconnect ? 1 : 0));
        dest.writeString(mHostname);
        dest.writeString(mPassword);
        dest.writeInt(mPort);
        dest.writeString(mStreamingURL);
        dest.writeByte((byte) (mStreamingEnabled ? 1 : 0));
        dest.writeString(mHTTPCoverRegex);
        dest.writeByte((byte) (mHTTPCoverEnabled ? 1 : 0));
        dest.writeByte((byte) (mMPDCoverEnabled ? 1 : 0));
        dest.writeLong(mCreated);
    }



    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MPDServerProfile> CREATOR = new Creator<MPDServerProfile>() {
        @Override
        public MPDServerProfile createFromParcel(Parcel in) {
            return new MPDServerProfile(in);
        }

        @Override
        public MPDServerProfile[] newArray(int size) {
            return new MPDServerProfile[size];
        }
    };

    /**
     *
     * @return The profile name of this profile
     */
    @NonNull
    public String getProfileName() {
        return mProfileName;
    }

    /**
     * Sets the profile name of this profile
     * @param profileName Profile name to set
     */
    public void setProfileName(@NonNull String profileName) {
        mProfileName = profileName;
    }

    /**
     *
     * @return The autoconnect flag of this profile
     */
    public boolean getAutoconnect() {
        return mAutoconnect;
    }

    /**
     * The flag should only be set for one profile. The database helper checks and enforces this.
     * @param autoconnect If profile should be used to automatically connect
     */
    public void setAutoconnect(boolean autoconnect) {
        this.mAutoconnect = autoconnect;
    }

    /**
     *
     * @return Servers hostname or ip address.
     */
    @NonNull
    public String getHostname() {
        return mHostname;
    }

    /**
     *
     * @return Servers login.
     */
    @NonNull
    public String getLogin() {
        return mLogin;
    }

    /**
     * Sets the hostname of this profile
     * @param hostname Hostname to use
     */
    public void setHostname(@NonNull String hostname) {
        this.mHostname = hostname;
    }

    /**
     * Sets the login of this profile
     * @param login Hostname to use
     */
    public void setLogin(@NonNull String login) {
        this.mLogin = login;
    }

    /**
     * The password of a profile can be left empty.
     * @return The password string or an empty string (null).
     */
    @NonNull
    public String getPassword() {
        return mPassword;
    }

    /**
     * Password string. Could be set to null.
     * @param password
     */
    public void setPassword(@NonNull String password) {
        this.mPassword = password;
    }

    /**
     * The port of a server profile. The default port is 6600.
     * @return TCP port of the profile
     */
    public int getPort() {
        return mPort;
    }

    /**
     * Sets the port of the profile. Usually is 6600.
     * @param port TCP port of the profile
     */
    public void setPort(int port) {
        this.mPort = port;
    }


    public void setStreamingURL(@NonNull String url) {
        mStreamingURL = url;
    }

    @NonNull
    public String getStreamingURL() {
        return mStreamingURL;
    }

    public void setStreamingEnabled(boolean enabled) {
        mStreamingEnabled = enabled;
    }

    public boolean getStreamingEnabled() {
        return mStreamingEnabled;
    }

    public void setHTTPRegex(@NonNull String regex) {
        mHTTPCoverRegex = regex;
    }

    @NonNull
    public String getHTTPRegex() {
        return mHTTPCoverRegex;
    }

    public void setHTTPCoverEnabled(boolean enabled) {
        mHTTPCoverEnabled = enabled;
    }

    public boolean getHTTPCoverEnabled() {
        return mHTTPCoverEnabled;
    }

    public void setMPDCoverEnabled(boolean enabled) {
        mMPDCoverEnabled = enabled;
    }

    public boolean getMPDCoverEnabled() {
        return mMPDCoverEnabled;
    }

    /**
     * Creates a string of the server profile. Be careful printing this out, because
     * it includes potential passwords.
     * @return The profile in string form.
     */
    @NonNull
    @Override
    public String toString() {
        String retString = "";

        retString += "Profilename: " + mProfileName + "\n";
        retString += "Profile autoconnect: " + mAutoconnect + "\n";
        retString += "Hostname: " + mHostname + "\n";
        retString += "Password: " + mPassword + "\n";
        retString += "Port: " + mPort + "\n";
        retString += "Created: " + mCreated + "\n";

        return retString;
    }

    @Override
    public String getSectionTitle() {
        return mProfileName;
    }

    public long getCreationDate() {
        return mCreated;
    }

    public boolean isLocalProfile() {
        return mIsLocal;
    }

    public void setLocalProfile(boolean local) {
        mIsLocal = local;
    }

    public boolean isLocalProfileActive() {
        return mIsLocalActive;
    }

    public void setLocalProfileActive(boolean active) {
        mIsLocalActive = active;
    }
}
