/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2013, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.tomahawk_android.fragments;

import org.tomahawk.libtomahawk.collection.Album;
import org.tomahawk.libtomahawk.collection.Artist;
import org.tomahawk.libtomahawk.collection.Playlist;
import org.tomahawk.libtomahawk.infosystem.InfoRequestData;
import org.tomahawk.libtomahawk.infosystem.InfoSystem;
import org.tomahawk.libtomahawk.infosystem.User;
import org.tomahawk.libtomahawk.resolver.PipeLine;
import org.tomahawk.libtomahawk.resolver.Query;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.activities.TomahawkMainActivity;
import org.tomahawk.tomahawk_android.adapters.TomahawkListAdapter;
import org.tomahawk.tomahawk_android.services.PlaybackService;
import org.tomahawk.tomahawk_android.utils.FragmentUtils;
import org.tomahawk.tomahawk_android.utils.TomahawkListItem;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * {@link TomahawkFragment} which offers both local and non-local search functionality to the user.
 */
public class SearchableFragment extends TomahawkFragment
        implements CompoundButton.OnCheckedChangeListener {

    public static final String SEARCHABLEFRAGMENT_QUERY_STRING
            = "org.tomahawk.tomahawk_android.SEARCHABLEFRAGMENT_QUERY_ID";

    private String mCurrentQueryString;

    /**
     * Restore the {@link String} inside the search {@link TextView}. Either through the
     * savedInstanceState {@link Bundle} or through the a {@link Bundle} provided in the Arguments.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState
                .containsKey(SEARCHABLEFRAGMENT_QUERY_STRING)
                && savedInstanceState.getString(SEARCHABLEFRAGMENT_QUERY_STRING) != null) {
            mCurrentQueryString = savedInstanceState.getString(SEARCHABLEFRAGMENT_QUERY_STRING);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getArguments() != null && getArguments().containsKey(SEARCHABLEFRAGMENT_QUERY_STRING)
                && getArguments().getString(SEARCHABLEFRAGMENT_QUERY_STRING) != null) {
            mCurrentQueryString = getArguments().getString(SEARCHABLEFRAGMENT_QUERY_STRING);
        }

        // Initialize our onlineSourcesCheckBox
        CheckBox onlineSourcesCheckBox = (CheckBox) getActivity()
                .findViewById(R.id.search_onlinesources_checkbox);
        onlineSourcesCheckBox.setOnCheckedChangeListener(this);

        // If we have restored a CurrentQueryString, start searching, so that we show the proper
        // results again
        if (mCurrentQueryString != null) {
            resolveFullTextQuery(mCurrentQueryString);
            getActivity().setTitle(mCurrentQueryString);
        }
        updateAdapter();
    }

    /**
     * Save the {@link String} inside the search {@link TextView}.
     */
    @Override
    public void onSaveInstanceState(Bundle out) {
        out.putString(SEARCHABLEFRAGMENT_QUERY_STRING, mCurrentQueryString);
        super.onSaveInstanceState(out);
    }

    /**
     * Called every time an item inside a ListView or GridView is clicked
     *
     * @param view the clicked view
     * @param item the TomahawkListItem which corresponds to the click
     */
    @Override
    public void onItemClick(View view, TomahawkListItem item) {
        TomahawkMainActivity activity = (TomahawkMainActivity) getActivity();
        if (item instanceof Query) {
            Query query = (Query) item;
            PlaybackService playbackService = activity.getPlaybackService();
            if (playbackService != null && playbackService.getCurrentQuery() == item) {
                playbackService.playPause();
            } else {
                Playlist playlist = Playlist.fromQueryList(mCurrentQueryString, mShownQueries);
                if (playbackService != null) {
                    playbackService.setPlaylist(playlist, playlist.getEntryWithQuery(query));
                    Class clss = mContainerFragmentClass != null ? mContainerFragmentClass
                            : ((Object) this).getClass();
                    playbackService.setReturnFragment(clss, getArguments());
                    playbackService.start();
                }
            }
        } else if (item instanceof Album) {
            FragmentUtils.replace(activity, getActivity().getSupportFragmentManager(),
                    TracksFragment.class, item.getCacheKey(),
                    TomahawkFragment.TOMAHAWK_ALBUM_KEY, mCollection);
        } else if (item instanceof Artist) {
            FragmentUtils.replace(activity, getActivity().getSupportFragmentManager(),
                    AlbumsFragment.class, item.getCacheKey(),
                    TomahawkFragment.TOMAHAWK_ARTIST_KEY, mCollection);
        } else if (item instanceof User) {
            FragmentUtils.replace(activity, getActivity().getSupportFragmentManager(),
                    SocialActionsFragment.class, ((User) item).getId(),
                    TomahawkFragment.TOMAHAWK_USER_ID,
                    SocialActionsFragment.SHOW_MODE_SOCIALACTIONS);
        }
    }

    /**
     * Called, when the checkbox' state has been changed,
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        resolveFullTextQuery(mCurrentQueryString);
    }

    /**
     * Display all {@link org.tomahawk.libtomahawk.resolver.Result}s of the {@link
     * org.tomahawk.libtomahawk.resolver.Query} with the given key
     */
    public void getQueryResults(String queryKey) {
        Query query = Query.getQueryByKey(queryKey);
        mCurrentQueryString = query.getFullTextQuery();
        mShownQueries = query.getTrackQueries();
    }

    public void getInfoResults(String requestId) {
        InfoRequestData data = InfoSystem.getInstance().getInfoRequestById(requestId);
        mShownAlbums.clear();
        mShownArtists.clear();
        mShownUsers.clear();
        for (Album album : data.getResultList(Album.class)) {
            mShownAlbums.add(album);
        }
        for (Artist artist : data.getResultList(Artist.class)) {
            mShownArtists.add(artist);
        }
        for (User user : data.getResultList(User.class)) {
            mShownUsers.add(user);
        }
    }

    /**
     * Update this {@link TomahawkFragment}'s {@link TomahawkListAdapter} content
     */
    @Override
    protected void updateAdapter() {
        if (!mIsResumed) {
            return;
        }

        TomahawkMainActivity activity = (TomahawkMainActivity) getActivity();
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        ArrayList<TomahawkListItem> listItems = new ArrayList<TomahawkListItem>();
        if (!mShownArtists.isEmpty()) {
            listItems.addAll(mShownArtists);
        }
        if (!mShownAlbums.isEmpty()) {
            listItems.addAll(mShownAlbums);
        }
        if (!mShownUsers.isEmpty()) {
            listItems.addAll(mShownUsers);
        }
        if (!mShownQueries.isEmpty()) {
            listItems.addAll(mShownQueries);
        }
        if (getListAdapter() == null) {
            TomahawkListAdapter tomahawkListAdapter = new TomahawkListAdapter(activity,
                    layoutInflater, listItems, this);
            tomahawkListAdapter.setShowCategoryHeaders(true);
            tomahawkListAdapter.setShowResolvedBy(true);
            setListAdapter(tomahawkListAdapter);
        } else {
            ((TomahawkListAdapter) getListAdapter()).setListItems(listItems);
        }

        updateShowPlaystate();
    }

    /**
     * Invoke the resolving process with the given fullTextQuery {@link String}
     */
    public void resolveFullTextQuery(String fullTextQuery) {
        ((TomahawkMainActivity) getActivity()).closeDrawer();
        mCurrentQueryString = fullTextQuery;
        CheckBox onlineSourcesCheckBox = (CheckBox) getActivity()
                .findViewById(R.id.search_onlinesources_checkbox);
        String queryId = PipeLine.getInstance().resolve(fullTextQuery,
                !onlineSourcesCheckBox.isChecked());
        if (onlineSourcesCheckBox.isChecked()) {
            mCurrentRequestIds.clear();
            String requestId = InfoSystem.getInstance().resolve(fullTextQuery);
            mCurrentRequestIds.add(requestId);
        } else {
            mShownArtists.clear();
            mShownAlbums.clear();
            mShownUsers.clear();
            updateAdapter();
        }
        if (queryId != null) {
            mCorrespondingQueryIds.clear();
            mCorrespondingQueryIds.add(queryId);
        }
    }

    @Override
    protected void onPipeLineResultsReported(ArrayList<String> queryKeys) {
        boolean needsUpdate = false;
        for (String key : queryKeys) {
            if (mCorrespondingQueryIds.contains(key)) {
                getQueryResults(key);
                needsUpdate = true;
            }
        }
        if (needsUpdate) {
            updateAdapter();
        }
    }

    @Override
    protected void onInfoSystemResultsReported(String requestId) {
        if (mCurrentRequestIds.contains(requestId)) {
            getInfoResults(requestId);
            updateAdapter();
        }
    }

    @Override
    public void onPanelCollapsed() {
        if (mCurrentQueryString != null) {
            getActivity().setTitle(mCurrentQueryString);
        }
    }

    @Override
    public void onPanelExpanded() {
    }
}
