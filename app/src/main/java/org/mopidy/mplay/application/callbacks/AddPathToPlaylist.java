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

package org.mopidy.mplay.application.callbacks;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import org.mopidy.mplay.R;
import org.mopidy.mplay.application.fragments.TextDialog;
import org.mopidy.mplay.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;

public class AddPathToPlaylist implements OnSaveDialogListener {
    MPDFileEntry mFile;

    Context mContext;

    public AddPathToPlaylist(MPDFileEntry file, Context context) {
        mFile = file;
        mContext = context;
    }

    @Override
    public void onSaveObject(String title) {
        if (null != mFile) {
            MPDQueryHandler.addURLToSavedPlaylist(title, mFile.getPath());
        }
    }

    @Override
    public void onCreateNewObject() {
        // open dialog in order to save the current playlist as a playlist in the mediastore
        TextDialog textDialog = TextDialog.newInstance(mContext.getResources().getString(R.string.dialog_save_playlist),
                mContext.getResources().getString(R.string.default_playlist_title));

        textDialog.setCallback(text -> MPDQueryHandler.addURLToSavedPlaylist(text, mFile.getPath()));
        textDialog.show(((AppCompatActivity) mContext).getSupportFragmentManager(), "SavePLTextDialog");
    }
}