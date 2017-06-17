package com.simplecity.amp_library.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.simplecity.amp_library.R;
import com.simplecity.amp_library.model.Playlist;
import com.simplecity.amp_library.ui.adapters.PlaylistAdapter;
import com.simplecity.amp_library.ui.modelviews.EmptyView;
import com.simplecity.amp_library.ui.modelviews.PlaylistView;
import com.simplecity.amp_library.utils.ComparisonUtils;
import com.simplecity.amp_library.utils.DataManager;
import com.simplecity.amp_library.utils.LogUtils;
import com.simplecity.amp_library.utils.MenuUtils;
import com.simplecity.amp_library.utils.MusicUtils;
import com.simplecity.amp_library.utils.PermissionUtils;
import com.simplecityapps.recycler_adapter.model.ViewModel;
import com.simplecityapps.recycler_adapter.recyclerview.RecyclerListener;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class PlaylistFragment extends BaseFragment implements
        MusicUtils.Defs,
        PlaylistAdapter.PlaylistListener {

    public interface PlaylistClickListener {

        void onPlaylistClicked(Playlist playlist);
    }

    private static final String TAG = "PlaylistFragment";

    private static final String ARG_PAGE_TITLE = "page_title";

    private FastScrollRecyclerView mRecyclerView;

    private PlaylistAdapter mPlaylistAdapter;

    @Nullable
    private PlaylistClickListener playlistClickListener;

    private Subscription subscription;

    public PlaylistFragment() {

    }

    public static PlaylistFragment newInstance(String pageTitle) {
        PlaylistFragment fragment = new PlaylistFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PAGE_TITLE, pageTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (getParentFragment() instanceof PlaylistClickListener) {
            playlistClickListener = (PlaylistClickListener) getParentFragment();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        playlistClickListener = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPlaylistAdapter = new PlaylistAdapter();
        mPlaylistAdapter.setListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        mRecyclerView = (FastScrollRecyclerView) inflater.inflate(R.layout.fragment_recycler, container, false);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setRecyclerListener(new RecyclerListener());
        mRecyclerView.setAdapter(mPlaylistAdapter);

        return mRecyclerView;
    }

    @Override
    public void onPause() {
        super.onPause();

        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshAdapterItems();
    }


    private void refreshAdapterItems() {
        PermissionUtils.RequestStoragePermissions(() -> {
            if (getActivity() != null && isAdded()) {

                Observable<List<Playlist>> defaultPlaylistsObservable =
                        Observable.defer(() ->
                                {
                                    List<Playlist> playlists = new ArrayList<>();

                                    Playlist podcastPlaylist = Playlist.podcastPlaylist();
                                    if (podcastPlaylist != null) {
                                        playlists.add(podcastPlaylist);
                                    }

                                    playlists.add(Playlist.recentlyAddedPlaylist());
                                    playlists.add(Playlist.mostPlayedPlaylist());
                                    return Observable.just(playlists);
                                }
                        );

                Observable<List<Playlist>> playlistsObservable = DataManager.getInstance().getPlaylistsRelay();

                subscription = Observable.combineLatest(
                        defaultPlaylistsObservable, playlistsObservable, (defaultPlaylists, playlists) -> {
                            List<Playlist> list = new ArrayList<>();
                            list.addAll(defaultPlaylists);
                            list.addAll(playlists);
                            return list;
                        })
                        .subscribeOn(Schedulers.io())
                        .map(playlists -> Stream.of(playlists)
                                .sorted((a, b) -> ComparisonUtils.compare(a.name, b.name))
                                .sorted((a, b) -> ComparisonUtils.compareInt(a.type, b.type))
                                .map(playlist -> (ViewModel) new PlaylistView(playlist))
                                .collect(Collectors.toList()))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(items -> {
                            if (items.isEmpty()) {
                                mPlaylistAdapter.setItems(Collections.singletonList(new EmptyView(R.string.empty_playlist)));
                            } else {
                                mPlaylistAdapter.setItems(items);
                            }
                        }, error -> LogUtils.logException("PlaylistFragment: Error refreshing adapter", error));
            }
        });
    }

    @Override
    public void onItemClick(View v, int position, Playlist playlist) {
        if (playlistClickListener != null) {
            playlistClickListener.onPlaylistClicked(playlist);
        }
    }

    @Override
    public void onOverflowClick(View v, int position, Playlist playlist) {
        PopupMenu menu = new PopupMenu(PlaylistFragment.this.getActivity(), v);
        MenuUtils.setupPlaylistMenu(menu, playlist);
        menu.setOnMenuItemClickListener(MenuUtils.getPlaylistClickListener(getContext(), playlist));
        menu.show();
    }

    @Override
    protected String screenName() {
        return TAG;
    }
}
