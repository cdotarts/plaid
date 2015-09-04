package com.example.android.plaid.data;

import android.content.Context;
import android.os.AsyncTask;

import com.example.android.plaid.data.api.AuthInterceptor;
import com.example.android.plaid.data.api.designernews.DesignerNewsService;
import com.example.android.plaid.data.api.designernews.model.StoriesResponse;
import com.example.android.plaid.data.api.dribbble.DribbbleSearch;
import com.example.android.plaid.data.api.dribbble.DribbbleService;
import com.example.android.plaid.data.api.dribbble.model.Like;
import com.example.android.plaid.data.api.dribbble.model.Shot;
import com.example.android.plaid.data.api.producthunt.ProductHuntService;
import com.example.android.plaid.data.api.producthunt.model.PostsResponse;
import com.example.android.plaid.data.prefs.DesignerNewsPrefs;
import com.example.android.plaid.data.prefs.DribbblePrefs;
import com.example.android.plaid.data.prefs.ProductHuntPrefs;
import com.example.android.plaid.data.prefs.SourceManager;
import com.example.android.plaid.ui.FilterAdapter;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;

/**
 * Responsible for loading data from the various sources. Instantiating classes are responsible for
 * providing the {code onDataLoaded} method to do something with the data.
 */
public abstract class DataManager implements FilterAdapter.FiltersChangedListener,
        DataLoadingSubject,
        DribbblePrefs.DribbbleLogoutListener,
        DesignerNewsPrefs.DesignerNewsLogoutListener {

    private final FilterAdapter filterAdapter;
    private DribbblePrefs dribbblePrefs;
    private DribbbleService dribbbleApi;
    private DesignerNewsPrefs designerNewsPrefs;
    private DesignerNewsService designerNewsApi;
    private ProductHuntService productHuntApi;
    private AtomicInteger loadingCount;

    private Map<String, Integer> pageIndexes;

    /**
     * @param filterAdapter
     */
    public DataManager(Context context,
                       FilterAdapter filterAdapter) {
        this.filterAdapter = filterAdapter;
        loadingCount = new AtomicInteger(0);
        setupPageIndexes();

        // setup the API access objects
        createDesignerNewsApi(context);
        createDribbbleApi(context);
        createProductHuntApi();
    }

    public void loadAllDataSources() {
        for (Source filter : filterAdapter.getFilters()) {
            loadSource(filter);
        }
    }

    public abstract void onDataLoaded(List<? extends PlaidItem> data);

    @Override
    public boolean isDataLoading() {
        return loadingCount.get() > 0;
    }

    public void onFiltersChanged(Source changedFilter){
        if (changedFilter.active) {
            loadSource(changedFilter);
        } else {
            // clear the page index for the source
            pageIndexes.put(changedFilter.key, 0);
        }
    }

    private void setupPageIndexes() {
        List<Source> dateSources = filterAdapter.getFilters();
        pageIndexes = new HashMap<>(dateSources.size());
        for (Source source : dateSources) {
            pageIndexes.put(source.key, 0);
        }
    }

    private int getNextPageIndex(String dataSource) {
        int nextPage = pageIndexes.get(dataSource) + 1;
        pageIndexes.put(dataSource, nextPage);
        return nextPage;
    }

    private boolean sourceIsEnabled(String key) {
        return pageIndexes.get(key) != 0;
    }

    private void loadSource(Source source) {
        if (source.active) {
            loadingCount.incrementAndGet();
            int page = getNextPageIndex(source.key);
            switch (source.key) {
                case SourceManager.SOURCE_DESIGNER_NEWS_POPULAR:
                    loadDesignerNewsTopStories(page);
                    break;
                case SourceManager.SOURCE_DESIGNER_NEWS_RECENT:
                    loadDesignerNewsRecent(page);
                    break;
                case SourceManager.SOURCE_DRIBBBLE_FOLLOWING:
                    loadDribbbleFollowing(page);
                    break;
                case SourceManager.SOURCE_DRIBBBLE_LIKES:
                    loadDribbbleLikes(page);
                    break;
                case SourceManager.SOURCE_DRIBBBLE_POPULAR:
                    loadDribbblePopular(page);
                    break;
                case SourceManager.SOURCE_DRIBBBLE_RECENT:
                    loadDribbbleRecent(page);
                    break;
                case SourceManager.SOURCE_DRIBBBLE_DEBUTS:
                    loadDribbbleDebuts(page);
                    break;
                case SourceManager.SOURCE_DRIBBBLE_ANIMATED:
                    loadDribbbleAnimated(page);
                    break;
                case SourceManager.SOURCE_PRODUCT_HUNT:
                    loadProductHunt(page);
                    break;
                default:
                    if (source instanceof Source.DribbbleSearchSource) {
                        loadDribbbleSearch((Source.DribbbleSearchSource) source, page);
                    }
                    break;
            }
        }
    }

    private void loadDesignerNewsTopStories(final int page) {
        designerNewsApi.getTopStories(page, new Callback<StoriesResponse>() {
            @Override
            public void success(StoriesResponse storiesResponse, Response response) {
                if (storiesResponse != null
                        && sourceIsEnabled(SourceManager.SOURCE_DESIGNER_NEWS_POPULAR)) {
                    setPage(storiesResponse.stories, page);
                    setDataSource(storiesResponse.stories,
                            SourceManager.SOURCE_DESIGNER_NEWS_POPULAR);
                    onDataLoaded(storiesResponse.stories);
                }
                loadingCount.decrementAndGet();
            }

            @Override
            public void failure(RetrofitError error) {
                loadingCount.decrementAndGet();
            }
        });
    }

    private void loadDesignerNewsRecent(final int page) {
        designerNewsApi.getRecentStories(page, new Callback<StoriesResponse>() {
            @Override
            public void success(StoriesResponse storiesResponse, Response response) {
                if (storiesResponse != null
                        && sourceIsEnabled(SourceManager.SOURCE_DESIGNER_NEWS_RECENT)) {
                    setPage(storiesResponse.stories, page);
                    setDataSource(storiesResponse.stories,
                            SourceManager.SOURCE_DESIGNER_NEWS_RECENT);
                    onDataLoaded(storiesResponse.stories);
                }
                loadingCount.decrementAndGet();
            }

            @Override
            public void failure(RetrofitError error) {
                loadingCount.decrementAndGet();
            }
        });
    }

    private void loadDribbblePopular(final int page) {
        dribbbleApi.getPopular(page, DribbbleService.PER_PAGE_DEFAULT, new Callback<List<Shot>>() {
            @Override
            public void success(List<Shot> shots, Response response) {
                if (sourceIsEnabled(SourceManager.SOURCE_DRIBBBLE_POPULAR)) {
                    setPage(shots, page);
                    setDataSource(shots, SourceManager.SOURCE_DRIBBBLE_POPULAR);
                    onDataLoaded(shots);
                }
                loadingCount.decrementAndGet();
            }

            @Override
            public void failure(RetrofitError error) {
                loadingCount.decrementAndGet();
            }
        });
    }

    private void loadDribbbleDebuts(final int page) {
        dribbbleApi.getDebuts(page, DribbbleService.PER_PAGE_DEFAULT, new Callback<List<Shot>>() {
            @Override
            public void success(List<Shot> shots, Response response) {
                if (sourceIsEnabled(SourceManager.SOURCE_DRIBBBLE_DEBUTS)) {
                    setPage(shots, page);
                    setDataSource(shots, SourceManager.SOURCE_DRIBBBLE_DEBUTS);
                    onDataLoaded(shots);
                }
                loadingCount.decrementAndGet();
            }

            @Override
            public void failure(RetrofitError error) {
                loadingCount.decrementAndGet();
            }
        });
    }

    private void loadDribbbleAnimated(final int page) {
        dribbbleApi.getAnimated(page, DribbbleService.PER_PAGE_DEFAULT, new Callback<List<Shot>>() {
            @Override
            public void success(List<Shot> shots, Response response) {
                if (sourceIsEnabled(SourceManager.SOURCE_DRIBBBLE_ANIMATED)) {
                    setPage(shots, page);
                    setDataSource(shots, SourceManager.SOURCE_DRIBBBLE_ANIMATED);
                    onDataLoaded(shots);
                }
                loadingCount.decrementAndGet();
            }

            @Override
            public void failure(RetrofitError error) {
                loadingCount.decrementAndGet();
            }
        });
    }

    private void loadDribbbleRecent(final int page) {
        dribbbleApi.getRecent(page, DribbbleService.PER_PAGE_DEFAULT, new Callback<List<Shot>>() {
            @Override
            public void success(List<Shot> shots, Response response) {
                if (sourceIsEnabled(SourceManager.SOURCE_DRIBBBLE_RECENT)) {
                    setPage(shots, page);
                    setDataSource(shots, SourceManager.SOURCE_DRIBBBLE_RECENT);
                    onDataLoaded(shots);
                }
                loadingCount.decrementAndGet();
            }

            @Override
            public void failure(RetrofitError error) {
                loadingCount.decrementAndGet();
            }
        });
    }

    private void loadDribbbleFollowing(final int page) {
        if (dribbblePrefs.isLoggedIn()) {
            dribbbleApi.getFollowing(page, DribbbleService.PER_PAGE_DEFAULT,
                    new Callback<List<Shot>>() {
                        @Override
                        public void success(List<Shot> shots, Response response) {
                            if (sourceIsEnabled(SourceManager.SOURCE_DRIBBBLE_FOLLOWING)) {
                                setPage(shots, page);
                                setDataSource(shots, SourceManager.SOURCE_DRIBBBLE_FOLLOWING);
                                onDataLoaded(shots);
                            }
                            loadingCount.decrementAndGet();
                        }

                        @Override
                        public void failure(RetrofitError error) {
                            loadingCount.decrementAndGet();
                        }
                    });
        } else {
            loadingCount.decrementAndGet();
        }
    }

    private void loadDribbbleLikes(final int page) {
        if (dribbblePrefs.isLoggedIn()) {
            dribbbleApi.getLikes(page, DribbbleService.PER_PAGE_DEFAULT,
                    new Callback<List<Like>>() {
                        @Override
                        public void success(List<Like> likes, Response response) {
                            if (sourceIsEnabled(SourceManager.SOURCE_DRIBBBLE_LIKES)) {
                                // API returns Likes but we just want the Shots
                                List<Shot> likedShots = new ArrayList<>(likes.size());
                                for (Like like : likes) {
                                    likedShots.add(like.shot);
                                }
                                // these will be sorted like any other shot (popularity per page)
                                // TODO figure out a more appropriate sorting strategy for likes
                                setPage(likedShots, page);
                                setDataSource(likedShots, SourceManager.SOURCE_DRIBBBLE_LIKES);
                                onDataLoaded(likedShots);
                            }
                            loadingCount.decrementAndGet();
                        }

                        @Override
                        public void failure(RetrofitError error) {
                            loadingCount.decrementAndGet();
                        }
                    });
        } else {
            loadingCount.decrementAndGet();
        }
    }

    private void loadDribbbleSearch(final Source.DribbbleSearchSource source, final int page) {
        new AsyncTask<Void, Void, List<Shot>>() {
            @Override
            protected List<Shot> doInBackground(Void... params) {
                return DribbbleSearch.search(source.query, DribbbleSearch.SORT_POPULAR, page);
            }

            @Override
            protected void onPostExecute(List<Shot> shots) {
                if (shots != null && shots.size() > 0 && sourceIsEnabled(source.key)) {
                    setPage(shots, page);
                    setDataSource(shots, source.key);
                    onDataLoaded(shots);
                }
                loadingCount.decrementAndGet();
            }
        }.execute();
    }

    private void loadProductHunt(final int page) {
        // this API's paging is 0 based but this class (& sorting) is 1 based so adjust locally
        productHuntApi.getPosts(page - 1, new Callback<PostsResponse>() {
            @Override
            public void success(PostsResponse postsResponse, Response response) {
                if (postsResponse != null && sourceIsEnabled(SourceManager.SOURCE_PRODUCT_HUNT)) {
                    setPage(postsResponse.posts, page);
                    setDataSource(postsResponse.posts,
                            SourceManager.SOURCE_PRODUCT_HUNT);
                    onDataLoaded(postsResponse.posts);
                }
                loadingCount.decrementAndGet();
            }

            @Override
            public void failure(RetrofitError error) {
                loadingCount.decrementAndGet();
            }
        });
    }

    private static void setPage(List<? extends PlaidItem> items, int page) {
        for (PlaidItem item : items) {
            item.page = page;
        }
    }

    private static void setDataSource(List<? extends PlaidItem> items, String dataSource) {
        for (PlaidItem item : items) {
            item.dataSource = dataSource;
        }
    }

    private void createDesignerNewsApi(Context context) {
        designerNewsPrefs = new DesignerNewsPrefs(context);
        designerNewsApi = new RestAdapter.Builder()
                .setEndpoint(DesignerNewsService.ENDPOINT)
                .build()
                .create(DesignerNewsService.class);
    }

    private void createDribbbleApi(Context context) {
        dribbblePrefs = new DribbblePrefs(context);
        dribbbleApi = new RestAdapter.Builder()
                .setEndpoint(DribbbleService.ENDPOINT)
                .setConverter(new GsonConverter(new GsonBuilder()
                        .setDateFormat(DribbbleService.DATE_FORMAT)
                        .create()))
                .setRequestInterceptor(new AuthInterceptor(dribbblePrefs.getAccessToken()))
                .build()
                .create((DribbbleService.class));
    }

    private void createProductHuntApi() {
        productHuntApi = new RestAdapter.Builder()
                .setEndpoint(ProductHuntService.ENDPOINT)
                .setRequestInterceptor(new AuthInterceptor(ProductHuntPrefs.DEVELOPER_TOKEN))
                .build()
                .create(ProductHuntService.class);
    }

    public void onDribbbleLogout(Context context) {
        createDribbbleApi(context); // clear the auth token
    }

    public void onDesignerNewsLogout(Context context) {
        createDesignerNewsApi(context); // clear the auth token
    }
}
