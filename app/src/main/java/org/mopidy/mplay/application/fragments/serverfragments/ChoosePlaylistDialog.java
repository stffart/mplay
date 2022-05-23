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

package org.mopidy.mplay.application.fragments.serverfragments;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mopidy.mplay.R;
import org.mopidy.mplay.application.adapters.FileAdapter;
import org.mopidy.mplay.application.callbacks.OnSaveDialogListener;
import org.mopidy.mplay.application.utils.ThemeUtils;
import org.mopidy.mplay.application.viewmodels.GenericViewModel;
import org.mopidy.mplay.application.viewmodels.PlaylistsViewModel;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.mopidy.mplay.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;

public class ChoosePlaylistDialog extends GenericMPDFragment<MPDFileEntry> {

    private static final String EXTRA_SHOW_NEW_ENTRY = "show_newentry";
    private static final String EXTRA_EXCLUDE_GENERATED = "exclude_generated";

    /**
     * Listener to save the bookmark
     */
    private OnSaveDialogListener mSaveCallback;

    private boolean mShowNewEntry;
    private boolean mExcludeGenerated;

    public static ChoosePlaylistDialog newInstance(final boolean showNewEntry) {
        return ChoosePlaylistDialog.newInstance(showNewEntry,false);
    }

    public static ChoosePlaylistDialog newInstance(final boolean showNewEntry, final boolean excludeGenerated) {
        final Bundle args = new Bundle();
        args.putBoolean(EXTRA_SHOW_NEW_ENTRY, showNewEntry);
        args.putBoolean(EXTRA_EXCLUDE_GENERATED, excludeGenerated);
        final ChoosePlaylistDialog fragment = new ChoosePlaylistDialog();
        fragment.setArguments(args);
        return fragment;
    }


    public void setCallback(OnSaveDialogListener callback) {
        mSaveCallback = callback;
    }

    /**
     * Create the dialog to choose to override an existing bookmark or to create a new bookmark.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();

        if (null != args) {
            mShowNewEntry = args.getBoolean(EXTRA_SHOW_NEW_ENTRY);
            mExcludeGenerated = args.getBoolean(EXTRA_EXCLUDE_GENERATED);
        }

        // Use the Builder class for convenient dialog construction
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());

        mAdapter = new FileAdapter(requireActivity(), false, false);

        builder.setTitle(getString(R.string.dialog_choose_playlist)).setAdapter(mAdapter, (dialog, which) -> {
            if (null == mSaveCallback) {
                return;
            }
            if (which == 0) {
                // open save dialog to create a new playlist
                mSaveCallback.onCreateNewObject();
            } else {
                // override existing playlist
                MPDPlaylist playlist = (MPDPlaylist) mAdapter.getItem(which);
                String objectTitle = playlist.getPath();
                mSaveCallback.onSaveObject(objectTitle);
            }
        }).setNegativeButton(R.string.dialog_action_cancel, (dialog, id) -> {
            // User cancelled the dialog don't save object
            requireDialog().cancel();
        });

        getViewModel().getData().observe(this, this::onDataReady);

        // set divider
        AlertDialog dlg = builder.create();
        dlg.getListView().setDivider(new ColorDrawable(ThemeUtils.getThemeColor(requireContext(), R.attr.malp_color_divider)));
        dlg.getListView().setDividerHeight(getResources().getDimensionPixelSize(R.dimen.list_divider_size));

        return dlg;
    }

    @Override
    GenericViewModel<MPDFileEntry> getViewModel() {
        return new ViewModelProvider(this, new PlaylistsViewModel.PlaylistsViewModelFactory(requireActivity().getApplication(), mShowNewEntry, mExcludeGenerated)).get(PlaylistsViewModel.class);
    }
}
