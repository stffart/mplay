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

package org.mopidy.mplay.application.fragments;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

import org.mopidy.mplay.R;
import org.mopidy.mplay.application.callbacks.FABFragmentCallback;
import org.mopidy.mplay.application.utils.ThemeUtils;
import org.mopidy.mplay.mpdservice.ConnectionManager;
import org.mopidy.mplay.mpdservice.profilemanagement.MPDServerProfile;

public class EditProfileFragment extends Fragment {
    public static final String TAG = EditProfileFragment.class.getSimpleName();

    private static final String EXTRA_PROFILE = "profile";

    private String mProfilename;
    private String mHostname;
    private String mPassword;
    private String mLogin;

    private int mPort;

    private String mStreamingURL;
    private boolean mStreamingEnabled;

    private String mHTTPCoverRegex;
    private boolean mHTTPCoverEnabled;

    private boolean mMPDCoverEnabled;

    private TextInputEditText mProfilenameView;
    private TextInputEditText mHostnameView;
    private TextInputEditText mLoginView;
    private TextInputEditText mPasswordView;
    private TextInputEditText mPortView;

    private SwitchCompat mStreamingEnabledView;
    private TextInputEditText mStreamingURLView;

    private SwitchCompat mHTTPCoverEnabledView;
    private TextInputEditText mHTTPCoverRegexView;

    private SwitchCompat mMPDCoverEnabledView;

    private MPDServerProfile mOldProfile;

    private FABFragmentCallback mFABCallback = null;

    private boolean mOptionsMenuHandled = false;

    public static EditProfileFragment newInstance(@Nullable final MPDServerProfile profile) {
        final Bundle args = new Bundle();
        args.putParcelable(EXTRA_PROFILE, profile);

        final EditProfileFragment fragment = new EditProfileFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mProfilenameView = view.findViewById(R.id.fragment_profile_profilename);
        mHostnameView = view.findViewById(R.id.fragment_profile_hostname);
        mLoginView = view.findViewById(R.id.fragment_profile_login);
        mPasswordView = view.findViewById(R.id.fragment_profile_password);
        mPortView = view.findViewById(R.id.fragment_profile_port);

        mStreamingURLView = view.findViewById(R.id.fragment_profile_streaming_url);
        mStreamingEnabledView = view.findViewById(R.id.fragment_profile_streaming_enabled);

        mHTTPCoverRegexView = view.findViewById(R.id.fragment_profile_cover_regex);
        mHTTPCoverEnabledView = view.findViewById(R.id.fragment_profile_http_covers_enabled);

        mMPDCoverEnabledView = view.findViewById(R.id.fragment_profile_use_mpd_cover);

        // Set to maximum tcp port
        InputFilter portFilter = new PortNumberFilter();

        mPortView.setFilters(new InputFilter[]{portFilter});

        /* Check if an artistname/albumame was given in the extras */
        Bundle args = getArguments();
        if (null != args) {
            mOldProfile = args.getParcelable(EXTRA_PROFILE);
            if (mOldProfile != null) {
                mProfilename = mOldProfile.getProfileName();
                mHostname = mOldProfile.getHostname();
                mPassword = mOldProfile.getPassword();
                mLogin = mOldProfile.getLogin();
                mPort = mOldProfile.getPort();

                mStreamingURL = mOldProfile.getStreamingURL();
                mStreamingEnabled = mOldProfile.getStreamingEnabled();

                mHTTPCoverRegex = mOldProfile.getHTTPRegex();
                mHTTPCoverEnabled = mOldProfile.getHTTPCoverEnabled();

                mMPDCoverEnabled = mOldProfile.getMPDCoverEnabled();

                mProfilenameView.setText(mProfilename);
                mLoginView.setText(mLogin);
            } else {
                mHostname = "";
                mProfilename = "";
                mPassword = "";
                mLogin = "";
                mPort = 6680;

                mStreamingEnabled = false;
                mStreamingURL = "";

                mHTTPCoverEnabled = false;
                mHTTPCoverRegex = "";

                mMPDCoverEnabled = true;

                mProfilenameView.setText(getString(R.string.fragment_profile_default_name));
                mLoginView.setText("");
            }
        }

        mHostnameView.setText(mHostname);
        mPasswordView.setText(mPassword);
        mLoginView.setText(mLogin);
        mPortView.setText(String.valueOf(mPort));

        // Show/Hide streaming url view depending on state
        mStreamingEnabledView.setChecked(mStreamingEnabled);
        mStreamingEnabledView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (mStreamingURLView.getText().toString().isEmpty()) {
                    // Check if a text was already set otherwise show an example
                    mStreamingURL = "http://" + mHostnameView.getText().toString() + ":8080";
                    mStreamingURLView.setText(mStreamingURL);
                }
                mStreamingURLView.setVisibility(View.VISIBLE);
            } else {
                mStreamingURLView.setVisibility(View.GONE);
            }

        });

        if (!mStreamingEnabled) {
            mStreamingURLView.setVisibility(View.GONE);
        }
        mStreamingURLView.setText(mStreamingURL);

        // Show/Hide HTTP cover regex view depending on state
        mHTTPCoverEnabledView.setChecked(mHTTPCoverEnabled);
        mHTTPCoverEnabledView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mHTTPCoverRegexView.setText(mHTTPCoverRegex);
                mHTTPCoverRegexView.setVisibility(View.VISIBLE);
            } else {
                mHTTPCoverRegexView.setVisibility(View.GONE);
            }

        });
        if (!mHTTPCoverEnabled) {
            mHTTPCoverRegexView.setVisibility(View.GONE);
        }
        mHTTPCoverRegexView.setText(mHTTPCoverRegex);

        mMPDCoverEnabledView.setChecked(mMPDCoverEnabled);

        mProfilenameView.setSelectAllOnFocus(true);

        setHasOptionsMenu(true);
    }

    /**
     * Called when the fragment is first attached to its context.
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mFABCallback = (FABFragmentCallback) context;
        } catch (ClassCastException e) {
            mFABCallback = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!mOptionsMenuHandled) {
            checkChanged();
        }

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getView();
        if (null != view) {
            imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        }
    }

    private void checkChanged() {
        boolean profileChanged = false;
        if (!mProfilenameView.getText().toString().equals(mProfilename)) {
            profileChanged = true;
            mProfilename = mProfilenameView.getText().toString();
        }
        if (!mHostnameView.getText().toString().equals(mHostname)) {
            profileChanged = true;
            mHostname = mHostnameView.getText().toString();
        }
        if (!mLoginView.getText().toString().equals(mLogin)) {
            profileChanged = true;
            mLogin = mLoginView.getText().toString();
        }
        if (!mPasswordView.getText().toString().equals(mPassword)) {
            profileChanged = true;
            mPassword = mPasswordView.getText().toString();
        }
        if (!mPortView.getText().toString().isEmpty() && Integer.parseInt(mPortView.getText().toString()) != mPort) {
            profileChanged = true;
            mPort = Integer.parseInt(mPortView.getText().toString());
        }
        if (!mStreamingURLView.getText().toString().equals(mStreamingURL)) {
            profileChanged = true;
            mStreamingURL = mStreamingURLView.getText().toString();
        }
        if (mStreamingEnabledView.isChecked() != mStreamingEnabled) {
            profileChanged = true;
            mStreamingEnabled = mStreamingEnabledView.isChecked();
        }
        if (!mHTTPCoverRegexView.getText().toString().equals(mHTTPCoverRegex)) {
            profileChanged = true;
            mHTTPCoverRegex = mHTTPCoverRegexView.getText().toString();
        }
        if (mHTTPCoverEnabledView.isChecked() != mHTTPCoverEnabled) {
            profileChanged = true;
            mHTTPCoverEnabled = mHTTPCoverEnabledView.isChecked();
        }
        if (mMPDCoverEnabledView.isChecked() != mMPDCoverEnabled) {
            profileChanged = true;
            mMPDCoverEnabled = mMPDCoverEnabledView.isChecked();
        }

        if (profileChanged) {
            if (null != mOldProfile) {
                ConnectionManager.getInstance(requireContext().getApplicationContext()).removeProfile(mOldProfile, getActivity());
            } else {
                mOldProfile = new MPDServerProfile(mProfilename, true);
            }
            mOldProfile.setProfileName(mProfilename);
            mOldProfile.setHostname(mHostname);
            mOldProfile.setLogin(mLogin);
            mOldProfile.setPassword(mPassword);
            mOldProfile.setPort(mPort);
            mOldProfile.setStreamingURL(mStreamingURL);
            mOldProfile.setStreamingEnabled(mStreamingEnabled);
            mOldProfile.setHTTPCoverEnabled(mHTTPCoverEnabled);
            mOldProfile.setHTTPRegex(mHTTPCoverRegex);
            mOldProfile.setMPDCoverEnabled(mMPDCoverEnabled);
            ConnectionManager.getInstance(requireContext().getApplicationContext()).addProfile(mOldProfile, getContext());
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(false, null);
            mFABCallback.setupToolbar(getString(R.string.fragment_profile_title), false, false, false);
        }
    }

    private static class PortNumberFilter implements InputFilter {
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            if (end > start) {
                String destTxt = dest.toString();
                String resultingTxt = destTxt.substring(0, dstart) +
                        source.subSequence(start, end) +
                        destTxt.substring(dend);
                try {
                    int port = Integer.parseInt(resultingTxt);
                    if (port > 65535) {
                        return "";
                    }
                    if (port < 1) {
                        return "";
                    }
                } catch (NumberFormatException e) {
                    return "";
                }
            }
            return null;
        }
    }

    /**
     * Initialize the options menu.
     * Be sure to call {@link #setHasOptionsMenu} before.
     *
     * @param menu         The container for the custom options menu.
     * @param menuInflater The inflater to instantiate the layout.
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.fragment_menu_edit_profile, menu);

        // get tint color
        int tintColor = ThemeUtils.getThemeColor(requireContext(), R.attr.malp_color_text_accent);

        Drawable drawable = menu.findItem(R.id.action_save).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_save).setIcon(drawable);

        drawable = menu.findItem(R.id.action_delete).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_delete).setIcon(drawable);

        super.onCreateOptionsMenu(menu, menuInflater);
    }

    /**
     * Hook called when an menu item in the options menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.action_save) {
            checkChanged();
            mOptionsMenuHandled = true;
            requireActivity().onBackPressed();
            return true;
        } else if (itemId == R.id.action_delete) {
            ConnectionManager.getInstance(requireContext().getApplicationContext()).removeProfile(mOldProfile, getContext());
            mOptionsMenuHandled = true;
            requireActivity().onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
