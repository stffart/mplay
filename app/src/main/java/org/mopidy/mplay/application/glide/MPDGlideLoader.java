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

package org.mopidy.mplay.application.glide;


import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.signature.ObjectKey;

import org.mopidy.mplay.mpdservice.mpdprotocol.MPDException;
import org.mopidy.mplay.mpdservice.websocket.WSInterface;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Loads an {@link InputStream} from a Base 64 encoded String.
 */
 public final class MPDGlideLoader implements ModelLoader<String, ByteBuffer> {


    public class MPDDataFetcher implements DataFetcher<ByteBuffer> {

        private final String model;

        MPDDataFetcher(String model) {
            this.model = model;
        }
        @Override
        public void loadData(Priority priority, DataCallback<? super ByteBuffer> callback) {
            try {
                byte[] imageData = WSInterface.getGenericInstance().getAlbumArt(model,true);
                ByteBuffer byteBuffer = ByteBuffer.wrap(imageData);
                callback.onDataReady(byteBuffer);
            } catch (MPDException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void cleanup() {}

        @Override
        public void cancel() {}

        @Override
        public Class<ByteBuffer> getDataClass() {
            return ByteBuffer.class;
        }

        @Override
        public DataSource getDataSource() {
            return DataSource.LOCAL;
        }


    }


    @Override
    public LoadData<ByteBuffer> buildLoadData(String model, int width, int height, Options options) {
        Key diskCacheKey = new ObjectKey(model);
        return new LoadData<ByteBuffer>(diskCacheKey, new MPDDataFetcher(model));
    }

    @Override
    public boolean handles(String model) {
        String pattern = "[a-zA-Z0-9-]+:(album|artist|track|playlist|directory|event):[a-zA-Z0-9-]+(:[a-zA-Z0-9-]+)?";
        return model.matches(pattern);
    }
}


