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

package org.mopidy.mplay.application.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.mopidy.mplay.application.artwork.ArtworkManager;
import org.mopidy.mplay.application.artwork.BitmapCache;
import org.mopidy.mplay.application.artwork.storage.ImageNotFoundException;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

public class CoverBitmapLoader {
    private static final String TAG = CoverBitmapLoader.class.getSimpleName();

    private final CoverBitmapListener mListener;

    private final Context mApplicationContext;

    public CoverBitmapLoader(Context context, CoverBitmapListener listener) {
        mApplicationContext = context.getApplicationContext();
        mListener = listener;
    }

    /**
     * Enum to define the type of the image that was retrieved
     */
    public enum IMAGE_TYPE {
        ALBUM_IMAGE,
        ARTIST_IMAGE,
    }

    /**
     * Load the image for the given track from the mediastore.
     */
    public void getImage(MPDTrack track, boolean fetchImage, int width, int height) {
        if (track != null) {
            // start the loader thread to load the image async
            Thread loaderThread = new Thread(new ImageRunner(fetchImage, track, width, height));
            loaderThread.start();
        }
    }

    public void getArtistImage(MPDArtist artist, boolean fetchImage, int width, int height) {
        if (artist == null) {
            return;
        }

        // start the loader thread to load the image async
        Thread loaderThread = new Thread(new ArtistImageRunner(artist, fetchImage, width, height));
        loaderThread.start();
    }

    public void getArtistImage(MPDTrack track, boolean fetchImage, int width, int height) {
        if (track == null) {
            return;
        }

        // start the loader thread to load the image async
        Thread loaderThread = new Thread(new TrackArtistImageRunner(track, fetchImage, width, height));
        loaderThread.start();
    }

    public void getAlbumImage(MPDAlbum album, boolean fetchImage, int width, int height) {
        if (album == null) {
            return;
        }

        // start the loader thread to load the image async
        Thread loaderThread = new Thread(new AlbumImageRunner(album, fetchImage, width, height));
        loaderThread.start();
    }

    private class ImageRunner implements Runnable {
        private final int mWidth;
        private final int mHeight;
        private final MPDTrack mTrack;
        private final boolean mFetchImage;

        public ImageRunner(boolean fetchImage, MPDTrack track, int width, int height) {
            mFetchImage = fetchImage;
            mWidth = width;
            mHeight = height;
            mTrack = track;
        }

        /**
         * Load the image for the given track from the mediastore.
         */
        @Override
        public void run() {
            MPDAlbum tempAlbum = mTrack.getAlbum();
            if (mTrack.hasArtwork()) {
                try {
                    String artwork = mTrack.getArtwork("200");
                    java.net.URL url = new java.net.URL(artwork);
                    HttpURLConnection connection = null;
                    connection = (HttpURLConnection) url
                            .openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    mListener.receiveBitmap(bitmap, IMAGE_TYPE.ALBUM_IMAGE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                // At first get image independent of resolution (can be replaced later with higher resolution)
                Bitmap image = BitmapCache.getInstance().requestAlbumBitmap(tempAlbum);
                if (image != null) {
                    mListener.receiveBitmap(image, IMAGE_TYPE.ALBUM_IMAGE);
                }

                try {
                    // If image was to small get it in the right resolution
                    if (image == null || !(mWidth <= image.getWidth() && mHeight <= image.getHeight())) {
                        Bitmap albumImage = ArtworkManager.getInstance(mApplicationContext).getImage(mTrack, mWidth, mHeight, true);
                        mListener.receiveBitmap(albumImage, IMAGE_TYPE.ALBUM_IMAGE);
                    }
                } catch (ImageNotFoundException e) {
                    if (mFetchImage) {
                        ArtworkManager.getInstance(mApplicationContext).fetchImage(mTrack);
                    }
                }
            }
        }
    }

    private class ArtistImageRunner implements Runnable {
        private final int mWidth;
        private final int mHeight;
        private final MPDArtist mArtist;
        private final boolean mFetchImage;

        public ArtistImageRunner(MPDArtist artist, boolean fetchImage, int width, int height) {
            mArtist = artist;
            mFetchImage = fetchImage;
            mWidth = width;
            mHeight = height;
        }

        /**
         * Load the image for the given track from the mediastore.
         */
        @Override
        public void run() {
            // At first get image independent of resolution (can be replaced later with higher resolution)
            Bitmap image = BitmapCache.getInstance().requestArtistImage(mArtist);
            if (image != null) {
                mListener.receiveBitmap(image, IMAGE_TYPE.ARTIST_IMAGE);
            }

            try {
                // If image was to small get it in the right resolution
                if (image == null || !(mWidth <= image.getWidth() && mHeight <= image.getHeight())) {
                    image = ArtworkManager.getInstance(mApplicationContext).getImage(mArtist, mWidth, mHeight, true);
                    mListener.receiveBitmap(image, IMAGE_TYPE.ARTIST_IMAGE);
                }
            } catch (ImageNotFoundException e) {
                if (mFetchImage) {
                    ArtworkManager.getInstance(mApplicationContext).fetchImage(mArtist);
                }
            }
        }
    }

    private class TrackArtistImageRunner implements Runnable {
        private final int mWidth;
        private final int mHeight;
        private final MPDArtist mArtist;
        private final boolean mFetchImage;

        public TrackArtistImageRunner(MPDTrack track, boolean fetchImage, int width, int height) {
            mArtist = new MPDArtist(track.getStringTag(MPDTrack.StringTagTypes.ARTIST),track.getStringTag(MPDTrack.StringTagTypes.ARTIST_URI));
            if (!track.getStringTag(MPDTrack.StringTagTypes.ARTIST_MBID).isEmpty()) {
                mArtist.addMBID(track.getStringTag(MPDTrack.StringTagTypes.ARTIST_MBID));
            }
            mFetchImage = fetchImage;
            mWidth = width;
            mHeight = height;
        }

        /**
         * Load the image for the given track from the mediastore.
         */
        @Override
        public void run() {
            // At first get image independent of resolution (can be replaced later with higher resolution)
            Bitmap image = BitmapCache.getInstance().requestArtistImage(mArtist);
            if (image != null) {
                mListener.receiveBitmap(image, IMAGE_TYPE.ARTIST_IMAGE);
            }

            try {
                // If image was to small get it in the right resolution
                if (image == null || !(mWidth <= image.getWidth() && mHeight <= image.getHeight())) {
                    image = ArtworkManager.getInstance(mApplicationContext).getImage(mArtist, mWidth, mHeight, true);
                    mListener.receiveBitmap(image, IMAGE_TYPE.ARTIST_IMAGE);
                }
            } catch (ImageNotFoundException e) {
                if (mFetchImage) {
                    ArtworkManager.getInstance(mApplicationContext).fetchImage(mArtist);
                }
            }
        }
    }

    private class AlbumImageRunner implements Runnable {
        private final int mWidth;
        private final int mHeight;
        private final MPDAlbum mAlbum;
        private final boolean mFetchImage;

        public AlbumImageRunner(MPDAlbum album, boolean fetchImage, int width, int height) {
            mAlbum = album;
            mFetchImage = fetchImage;
            mWidth = width;
            mHeight = height;
        }

        /**
         * Load the image for the given track from the mediastore.
         */
        @Override
        public void run() {
            // At first get image independent of resolution (can be replaced later with higher resolution)
            Bitmap image = BitmapCache.getInstance().requestAlbumBitmap(mAlbum);
            if (image != null) {
                mListener.receiveBitmap(image, IMAGE_TYPE.ALBUM_IMAGE);
            }

            try {
                // If image was to small get it in the right resolution
                if (image == null || !(mWidth <= image.getWidth() && mHeight <= image.getHeight())) {
                    Bitmap albumImage = ArtworkManager.getInstance(mApplicationContext).getImage(mAlbum, mWidth, mHeight, true);
                    mListener.receiveBitmap(albumImage, IMAGE_TYPE.ALBUM_IMAGE);
                }
            } catch (ImageNotFoundException e) {
                if (mFetchImage) {
                    ArtworkManager.getInstance(mApplicationContext).fetchImage(mAlbum);
                }
            }
        }
    }


    /**
     * Callback if image was loaded.
     */
    public interface CoverBitmapListener {
        void receiveBitmap(Bitmap bm, IMAGE_TYPE type);
    }
}
