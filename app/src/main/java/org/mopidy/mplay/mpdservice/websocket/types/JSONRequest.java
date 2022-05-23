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

import com.google.gson.ExclusionStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.mopidy.mplay.mpdservice.websocket.types.params.JSONParams;

public class JSONRequest {
    String jsonrpc;
    public int id;
    public String method;
    JSONParams params;

    public JSONRequest(int id, String method) {
        jsonrpc = "2.0";
        this.id = id;
        this.method = method;
    }
    public JSONRequest(int id, String method, JSONParams params) {
        jsonrpc = "2.0";
        this.id = id;
        this.method = method;
        this.params = params;
    }


    public String toJSON() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
    public String toJSON(ExclusionStrategy strategy) {
        Gson gson = new GsonBuilder().setExclusionStrategies(strategy).create();
        return gson.toJson(this);
    }
    public String toJSONWithNulls() {
        Gson gson = new GsonBuilder().serializeNulls().create();
        return gson.toJson(this);
    }

    public void setParams(JSONParams jsonParams) {
        params = jsonParams;
    }
}
