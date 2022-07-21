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

package org.mopidy.mplay.mpdservice.mpdprotocol;

import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;

import java.util.List;

public class MPDCache {

    private List<MPDFileEntry> mPlaylists;
    private MPDCurrentStatus mStatus = null;
    private List<MPDFileEntry> mTrackList;
    private boolean mTrackListValid = false;
    private List<MPDArtist> mAlbumArtists;
    private List<MPDArtist> mArtists;
    private List<MPDArtist> mArtistsSort;
    private List<MPDArtist> mAlbumArtistsSort;

    private List<MPDAlbum> mAlbums;

    private final long mVersion;

    public MPDCache(long version) {
        mVersion = version;
    }

    public long getVersion() {
        return mVersion;
    }

    public List<MPDArtist> getAlbumArtists() {
        if (mAlbumArtists != null) {
            return mAlbumArtists;
        }
        return null;
    }

    public void cacheAlbumArtists(List<MPDArtist> artists) {
        mAlbumArtists = artists;
    }

    public List<MPDArtist> getArtists() {
        if (mArtists != null) {
            return mArtists;
        }
        return null;
    }

    public void cacheArtists(List<MPDArtist> artists) {
        mArtists = artists;
    }

    public List<MPDArtist> getArtistsSort() {
        if (mArtistsSort != null) {
            return mArtistsSort;
        }
        return null;
    }

    public void cacheArtistsSort(List<MPDArtist> artistsSort) {
        mArtistsSort = artistsSort;
    }

    public List<MPDArtist> getAlbumArtistsSort() {
        if (mAlbumArtistsSort != null) {
            return mAlbumArtistsSort;
        }
        return null;
    }

    public void cacheAlbumArtistsSort(List<MPDArtist> albumArtistsSort) {
        mAlbumArtistsSort = albumArtistsSort;
    }


    public List<MPDAlbum> getAlbums() {
        if (mAlbums != null) {
            return mAlbums;
        }
        return null;
    }

    public void cacheAlbums(List<MPDAlbum> albums) {
        mAlbums = albums;
    }

    public boolean trackListValid() {
        return mTrackListValid;
    }
    public void invalidateTrackList() {
        mTrackListValid = false;
    }
    public void cacheTracklist(List<MPDFileEntry> tracks) {
        mTrackList = tracks;
        mTrackListValid = true;
    }

    public List<MPDFileEntry> getTracklist() {
        return mTrackList;
    }

    public void cacheStatus(MPDCurrentStatus status) {
        mStatus = status;
    }

    public MPDCurrentStatus getStatus() throws MPDException {
        if (mStatus == null)
            throw new MPDException.MPDServerException("{Status null} Cannot get current status");
        return mStatus;
    }

    public void cachePlaylists(List<MPDFileEntry> playlists) {
        mPlaylists = playlists;
    }
    public List<MPDFileEntry> getPlaylists() {
        return mPlaylists;
    }

}
