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


import android.database.sqlite.SQLiteDatabase;

public class MPDServerProfileTable {
    /**
     * Table name of the SQL table inside a database
     */
    public static final String SQL_TABLE_NAME = "andrompd_mpd_server_profiles";

    /**
     * Column descriptions
     */
    public static final String COLUMN_PROFILE_NAME = "profile_name";
    public static final String COLUMN_SERVER_HOSTNAME = "server_hostname";
    public static final String COLUMN_SERVER_PASSWORD = "server_password";
    public static final String COLUMN_SERVER_LOGIN = "server_login";
    public static final String COLUMN_SERVER_PORT = "server_port";
    public static final String COLUMN_PROFILE_AUTO_CONNECT = "autoconnect";
    public static final String COLUMN_PROFILE_DATE_CREATED = "date";

    public static final String COLUMN_PROFILE_STREAMING_PORT = "streaming_port";
    public static final String COLUMN_PROFILE_STREAMING_ENABLED = "streaming_enabled";

    public static final String COLUMN_PROFILE_HTTP_COVER_REGEX = "http_cover_regex";
    public static final String COLUMN_PROFILE_HTTP_COVER_ENABLED = "http_cover_enabled";

    public static final String COLUMN_PROFILE_MPD_COVER_ENABLED = "mpd_cover_enabled";

    /**
     * Projection string array used for queries on this table
     */
    public static final String[] PROJECTION_SERVER_PROFILES = {COLUMN_PROFILE_NAME, COLUMN_PROFILE_AUTO_CONNECT,
        COLUMN_SERVER_HOSTNAME, COLUMN_SERVER_LOGIN, COLUMN_SERVER_PASSWORD, COLUMN_SERVER_PORT, COLUMN_PROFILE_DATE_CREATED,
            COLUMN_PROFILE_STREAMING_PORT, COLUMN_PROFILE_STREAMING_ENABLED,
            COLUMN_PROFILE_HTTP_COVER_REGEX, COLUMN_PROFILE_HTTP_COVER_ENABLED, COLUMN_PROFILE_MPD_COVER_ENABLED
    };

    public static final String DATABASE_DROP = "drop table " + SQL_TABLE_NAME +";";

    /**
     * String to initially create the table
     */
    public static final String DATABASE_CREATE = "create table if not exists " +  SQL_TABLE_NAME + " (" +
            COLUMN_PROFILE_NAME + " text," + COLUMN_PROFILE_AUTO_CONNECT + " integer," +
            COLUMN_SERVER_HOSTNAME + " text," + COLUMN_SERVER_LOGIN + " text," + COLUMN_SERVER_PASSWORD + " text," +
            COLUMN_SERVER_PORT  + " integer,"  + COLUMN_PROFILE_DATE_CREATED  + " integer PRIMARY KEY, " +
            COLUMN_PROFILE_STREAMING_PORT  + " integer,"  + COLUMN_PROFILE_STREAMING_ENABLED  + " integer," +
            COLUMN_PROFILE_HTTP_COVER_REGEX  + " text,"  + COLUMN_PROFILE_HTTP_COVER_ENABLED  + " integer," +
            COLUMN_PROFILE_MPD_COVER_ENABLED  + " integer" + " );";

    /**
     * Creates the inital database table.
     * @param database Database to use for table creation.
     */
    public static void onCreate(SQLiteDatabase database) {
        /*
         * Create table in the given database here.
         */
        database.execSQL(DATABASE_DROP);
        database.execSQL(DATABASE_CREATE);
    }
}
