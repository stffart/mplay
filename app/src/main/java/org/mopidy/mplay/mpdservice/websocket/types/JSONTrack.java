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

package org.mopidy.mplay.mpdservice.websocket.types;

import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDDirectory;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.ArrayList;

public class JSONTrack extends  JSONSimpleTrack {

    public String name;
    public ArrayList<JSONArtist> artists;
    public JSONAlbum album;
    public String artwork;
    public boolean like;
    public int length;
    public String type;

    public JSONTrack(String uri) {
        super(uri);
    }

    @Override
    public MPDTrack toMPDTrack() {
        MPDTrack result = new MPDTrack(uri);
        result.setURI(uri);
        result.setName(name);
        result.setLike(like);
        result.setArtwork(artwork);
        result.setStringTag(MPDTrack.StringTagTypes.TITLE,name);
        if (album != null) {
            result.setStringTag(MPDTrack.StringTagTypes.ALBUM, album.name);
            result.setStringTag(MPDTrack.StringTagTypes.ALBUM_URI, album.uri);
        }
        if (artists != null && artists.size() > 0) {
            result.setStringTag(MPDTrack.StringTagTypes.ARTIST, artists.get(0).name);
            result.setStringTag(MPDTrack.StringTagTypes.ARTIST_URI, artists.get(0).uri);
        }
        return result;
    }

    public MPDDirectory toMPDDirectory() {
        MPDDirectory dir = new MPDDirectory(uri);
        dir.setName(name);
        dir.setArtwork(artwork);
        dir.setURI(uri);
        return dir;
    }

    public MPDPlaylist toMPDPlaylist() {
        MPDPlaylist result = new MPDPlaylist(uri);
        result.setName(name);
        result.setArtwork(artwork);
        result.setURI(uri);
        return result;
    }

    public MPDAlbum toMPDAlbum() {
        MPDAlbum result = new MPDAlbum(name,uri);
        result.setTitle(name);
        result.setArtwork(artwork);
        return result;
    }
}
