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

import android.os.Handler;
import android.os.Message;

import org.mopidy.mplay.mpdservice.handlers.MPDStatusChangeHandler;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

public abstract class MPDProfileChangeHandler extends Handler {

    /**
     * Handles the change of the status and track of MPD
     * @param msg Message object
     */
    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        if ( msg.obj instanceof MPDServerProfile) {
            onProfileChanged((MPDServerProfile)msg.obj);
        }
    }

    public void profileChanged(MPDServerProfile profile ) {
        if (profile == null) return;
        Message msg = this.obtainMessage();
        msg.obj = profile;
        this.sendMessage(msg);
    }

    abstract protected void onProfileChanged(MPDServerProfile profile);

}
