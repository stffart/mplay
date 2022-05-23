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

package org.mopidy.mplay.application.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.mopidy.mplay.R;
import org.mopidy.mplay.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.mopidy.mplay.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;

import java.lang.ref.WeakReference;
import java.util.List;

public class PlaylistsViewModel extends GenericViewModel<MPDFileEntry> {

    private final PlaylistResponseHandler mPlaylistResponseHandler;

    private final boolean mAddHeader;

    private final boolean mExcludeGenerated;

    private PlaylistsViewModel(@NonNull final Application application, final boolean addHeader) {
        super(application);

        mPlaylistResponseHandler = new PlaylistResponseHandler(this);

        mAddHeader = addHeader;
        mExcludeGenerated = false;

    }

    private PlaylistsViewModel(@NonNull final Application application, final boolean addHeader, final boolean excludeGenerated) {
        super(application);

        mPlaylistResponseHandler = new PlaylistResponseHandler(this);

        mAddHeader = addHeader;
        mExcludeGenerated = excludeGenerated;
    }


    @Override
    void loadData() {
        MPDQueryHandler.getSavedPlaylists(mPlaylistResponseHandler);
    }

    private static class PlaylistResponseHandler extends MPDResponseFileList {
        private final WeakReference<PlaylistsViewModel> mPlaylistViewModel;

        private PlaylistResponseHandler(final PlaylistsViewModel playlistsViewModel) {
            mPlaylistViewModel = new WeakReference<>(playlistsViewModel);
        }

        @Override
        public void handleTracks(final List<MPDFileEntry> fileList, final int start, final int end) {
            final PlaylistsViewModel playlistsViewModel = mPlaylistViewModel.get();

            if (playlistsViewModel != null) {
                if (playlistsViewModel.mAddHeader) {
                    fileList.add(0, new MPDPlaylist(playlistsViewModel.getApplication().getString(R.string.create_new_playlist)));
                }
                for (int i = fileList.size()-1; i >= 0; i--) {
                    MPDFileEntry file = fileList.get(i);
                    if (file.getName().equals("[Radio Streams]"))
                    {
                        fileList.remove(i);
                        continue;
                    }
                    if (playlistsViewModel.mExcludeGenerated) {
                        if (file instanceof MPDPlaylist)
                            if (((MPDPlaylist) file).isGenerated())
                                fileList.remove(i);
                    }
                }
                Log.e("PlaylistViewModel","Set fileList of "+String.valueOf(fileList.size()));
                playlistsViewModel.setData(fileList);
            }
        }
    }

    public static class PlaylistsViewModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        private final boolean mAddHeader;
        private final boolean mExcludeGenerated;

        public PlaylistsViewModelFactory(final Application application, final boolean addHeader) {
            mApplication = application;
            mAddHeader = addHeader;
            mExcludeGenerated = false;
        }

        public PlaylistsViewModelFactory(final Application application, final boolean addHeader, final boolean excludeGenerated) {
            mApplication = application;
            mAddHeader = addHeader;
            mExcludeGenerated = excludeGenerated;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new PlaylistsViewModel(mApplication, mAddHeader, mExcludeGenerated);
        }
    }
}
