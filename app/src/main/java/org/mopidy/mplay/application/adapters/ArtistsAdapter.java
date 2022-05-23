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

package org.mopidy.mplay.application.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import org.mopidy.mplay.R;
import org.mopidy.mplay.application.artwork.ArtworkManager;
import org.mopidy.mplay.application.listviewitems.GenericGridItem;
import org.mopidy.mplay.application.listviewitems.ImageListItem;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDArtist;

public class ArtistsAdapter extends GenericSectionAdapter<MPDArtist> implements ArtworkManager.onNewArtistImageListener {

    private final Context mContext;

    private final boolean mUseList;

    private int mListItemSize;

    private final ArtworkManager mArtworkManager;

    public ArtistsAdapter(Context context, boolean useList) {
        super();

        mContext = context;

        mUseList = useList;
        if (mUseList) {
            mListItemSize = (int) context.getResources().getDimension(R.dimen.material_list_item_height);
        }
        mArtworkManager = ArtworkManager.getInstance(context.getApplicationContext());

    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MPDArtist artist = (MPDArtist) getItem(position);
        String label = artist.getArtistName();

        if (label.isEmpty()) {
            label = mContext.getResources().getString(R.string.no_artist_tag);
        }

        if (mUseList) {
            // Check if a view can be recycled
            ImageListItem listItem;
            if (convertView != null) {
                listItem = (ImageListItem) convertView;
                // Make sure to reset the layoutParams in case of change (rotation for example)
                listItem.setText(label);
            } else {
                // Create new view if no reusable is available
                listItem = new ImageListItem(mContext, label, null, this);
            }

            // This will prepare the view for fetching the image from the internet if not already saved in local database.
            listItem.prepareArtworkFetching(mArtworkManager, artist);
            // Check if the scroll speed currently is already 0, then start the image task right away.
            if (mScrollSpeed == 0) {
                listItem.setImageDimension(mListItemSize, mListItemSize);
                listItem.startCoverImageTask();
            }
            return listItem;
        } else {
            GenericGridItem gridItem;
            ViewGroup.LayoutParams layoutParams;
            final int size = ((GridView) parent).getColumnWidth();
            // Check if a view can be recycled
            if (convertView == null) {
                // Create new view if no reusable is available
                gridItem = new GenericGridItem(mContext, label, this);
                layoutParams = new android.widget.AbsListView.LayoutParams(size, size);
            } else {
                gridItem = (GenericGridItem) convertView;
                gridItem.setTitle(label);
                layoutParams = gridItem.getLayoutParams();
                layoutParams.height = size;
                layoutParams.width = size;
            }

            // Make sure to reset the layoutParams in case of change (rotation for example)
            gridItem.setLayoutParams(layoutParams);
            if (artist.hasArtwork())
                gridItem.setArtwork(artist.getArtwork("200"));
            else
                gridItem.setArtwork(artist.getURI());
            /*
            // This will prepare the view for fetching the image from the internet if not already saved in local database.
            gridItem.prepareArtworkFetching(mArtworkManager, artist);

            // Check if the scroll speed currently is already 0, then start the image task right away.
            if (mScrollSpeed == 0) {
                gridItem.setImageDimension(size, size);
                gridItem.startCoverImageTask();
            }
            */

            return gridItem;
        }
    }

    @Override
    public void newArtistImage(MPDArtist artist) {
        notifyDataSetChanged();
    }
}
