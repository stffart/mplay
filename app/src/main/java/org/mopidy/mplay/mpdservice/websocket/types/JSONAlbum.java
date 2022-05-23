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

import java.util.ArrayList;

public class JSONAlbum {
    public String uri;
    public String name;
    public String artwork;
    public ArrayList<JSONArtist> artists;
    public ArrayList<JSONTrack> tracks;
    public String __model__;

    public MPDAlbum toMPDAlbum() {
        MPDAlbum album = new MPDAlbum(name,uri);
        album.setTitle(name);
        album.setArtwork(artwork);
        if (artists.size() > 0)
          album.setArtistName(artists.get(0).name);
        return album;
    }
    public MPDDirectory toMPDDirectory() {
        MPDDirectory result = new MPDDirectory(uri);
        result.setName(name);
        result.setURI(uri);
        result.setArtwork(artwork);
        result.setType("album");
        return result;
    }
}
