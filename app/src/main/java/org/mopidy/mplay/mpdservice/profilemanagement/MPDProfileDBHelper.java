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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.mopidy.mplay.BuildConfig;

public class MPDProfileDBHelper extends SQLiteOpenHelper {
    private static final String TAG = MPDProfileManager.class.getSimpleName();
    /**
     * Database name for the profiles database
     */
    public static final String DATABASE_NAME = "andrompd_database";

    /**
     * Database version, used for migrating to new versions.
     */
    public static final int DATABASE_VERSION = 6;

    /**
     * Constructor to create the database.
     *
     * @param context Application context to create the database in.
     */
    public MPDProfileDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Initializes the tables of this database.
     * Should call all table helpers.
     *
     * @param database Database to use for tables.
     */
    @Override
    public void onCreate(SQLiteDatabase database) {
        MPDServerProfileTable.onCreate(database);
    }

    /**
     * Method to migrate the database to a new version. Nothing implemented for now.
     *
     * @param database   Database to migrate to a different version.
     * @param oldVersion Old version of the database to migrate from
     * @param newVersion New version of the database to migrate to
     */
    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Upgrading database from version: " + oldVersion + " to new version: " + newVersion);
        }

        switch (oldVersion) {
            // Upgrade from version 1 to 2 needs introduction of the streaming port and streaming
            // enable column.
            case 1: {
                String sqlString = "ALTER TABLE " + MPDServerProfileTable.SQL_TABLE_NAME + " ADD COLUMN " + MPDServerProfileTable.COLUMN_PROFILE_STREAMING_PORT + " integer;";
                database.execSQL(sqlString);

                sqlString = "ALTER TABLE " + MPDServerProfileTable.SQL_TABLE_NAME + " ADD COLUMN " + MPDServerProfileTable.COLUMN_PROFILE_STREAMING_ENABLED + " integer;";
                database.execSQL(sqlString);
            }
            case 2: {
                // Upgrading from version 2 to 3 needs new http regex columns
                String sqlString = "ALTER TABLE " + MPDServerProfileTable.SQL_TABLE_NAME + " ADD COLUMN " + MPDServerProfileTable.COLUMN_PROFILE_HTTP_COVER_REGEX + " text;";
                database.execSQL(sqlString);

                sqlString = "ALTER TABLE " + MPDServerProfileTable.SQL_TABLE_NAME + " ADD COLUMN " + MPDServerProfileTable.COLUMN_PROFILE_HTTP_COVER_ENABLED + " integer;";
                database.execSQL(sqlString);
            }
            case 3: {
                String sqlString = "ALTER TABLE " + MPDServerProfileTable.SQL_TABLE_NAME + " ADD COLUMN " + MPDServerProfileTable.COLUMN_PROFILE_MPD_COVER_ENABLED + " integer;";
                database.execSQL(sqlString);

                sqlString = "UPDATE " + MPDServerProfileTable.SQL_TABLE_NAME + " SET " + MPDServerProfileTable.COLUMN_PROFILE_MPD_COVER_ENABLED + " = 1;";
                database.execSQL(sqlString);
            }
            case 4: {
                String sqlString = "ALTER TABLE " + MPDServerProfileTable.SQL_TABLE_NAME + " ADD COLUMN " + MPDServerProfileTable.COLUMN_SERVER_LOGIN + " text;";
                database.execSQL(sqlString);

                sqlString = "UPDATE " + MPDServerProfileTable.SQL_TABLE_NAME + " SET " + MPDServerProfileTable.COLUMN_SERVER_LOGIN + " = '';";
                database.execSQL(sqlString);
            }
            case 5: {
                String sqlString = "ALTER TABLE " + MPDServerProfileTable.SQL_TABLE_NAME + " ADD COLUMN " + MPDServerProfileTable.COLUMN_SERVER_REMOTE_HOSTNAME + " text;";
                database.execSQL(sqlString);
                String sqlString2 = "ALTER TABLE " + MPDServerProfileTable.SQL_TABLE_NAME + " ADD COLUMN " + MPDServerProfileTable.COLUMN_SERVER_REMOTE_PORT + " integer;";
                database.execSQL(sqlString2);

                sqlString = "UPDATE " + MPDServerProfileTable.SQL_TABLE_NAME + " SET " + MPDServerProfileTable.COLUMN_SERVER_REMOTE_HOSTNAME + " = '';";
                database.execSQL(sqlString);

            }

            default:
                break;
        }
    }
}
