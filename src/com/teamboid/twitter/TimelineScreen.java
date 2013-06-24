package com.teamboid.twitter;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.annotation.SuppressLint;
import android.app.*;
import android.app.ActionBar.Tab;
import android.content.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.*;
import com.teamboid.twitter.SendTweetTask.Result;
import com.teamboid.twitter.TabsAdapter.BaseGridFragment;
import com.teamboid.twitter.TabsAdapter.BaseListFragment;
import com.teamboid.twitter.TabsAdapter.IBoidFragment;
import com.teamboid.twitter.TabsAdapter.TabInfo;
import com.teamboid.twitter.cab.MessageConvoCAB;
import com.teamboid.twitter.cab.TimelineCAB;
import com.teamboid.twitter.columns.*;
import com.teamboid.twitter.listadapters.SendTweetArrayAdapter;
import com.teamboid.twitter.listadapters.TrendsListAdapter;
import com.teamboid.twitter.listadapters.UserListDisplayAdapter;
import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitter.services.SendTweetService;
import com.teamboid.twitter.utilities.BoidActivity;
import com.teamboid.twitter.utilities.Utilities;
import twitter4j.Relationship;
import twitter4j.UserList;

import java.util.ArrayList;

/**
 * The activity that represents the main timeline screen.
 *
 * @author Aidan Follestad
 */
public class TimelineScreen extends Activity implements ActionBar.TabListener {

    private int lastTheme;
    private boolean lastDisplayReal;
    private boolean lastIconic;

    private TabsAdapter mTabsAdapter;
    private boolean newColumn;
    public boolean filterDefaultColumnSelection = true;
    private ViewPager mViewPager;

    public class SendTweetUpdater extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            if (intent.getAction().equals(AccountService.END_LOAD)) {
                // lets see what we can do
                return;
            } else if (intent.getAction()
                    .equals(SendTweetService.NETWORK_AVAIL)) {
                TimelineScreen.this.invalidateOptionsMenu();
                return;
            }
            try {
                SendTweetArrayAdapter sentTweetBinder = new SendTweetArrayAdapter(
                        TimelineScreen.this, 0, 0,
                        SendTweetService.getInstance().tweets);
                ((ListView) findViewById(R.id.progress_content))
                        .setAdapter(sentTweetBinder);
                if (SendTweetService.getInstance().tweets != null) {
                    sentTweetBinder.notifyDataSetInvalidated();
                    if (SendTweetService.getInstance().tweets.size() > 0) {
                        findViewById(R.id.progress).setVisibility(View.VISIBLE);
                        findViewById(R.id.progress).setAlpha(1.0f);
                        ((SlidingDrawer) findViewById(R.id.progress)).close();
                    } else {
                        if (intent.hasExtra("delete")
                                || intent.hasExtra("dontrefresh")) {
                            findViewById(R.id.progress)
                                    .setVisibility(View.GONE);
                        } else {
                            ViewPropertyAnimator vpa = findViewById(
                                    R.id.progress).animate();
                            vpa.setStartDelay(300);
                            vpa.setDuration(3000);
                            vpa.setListener(new AnimatorListener() {
                                @Override
                                public void onAnimationCancel(Animator arg0) {
                                }

                                @Override
                                public void onAnimationEnd(Animator arg0) {
                                    findViewById(R.id.progress).setVisibility(
                                            View.GONE);
                                    findViewById(R.id.progress_handle)
                                            .setBackgroundColor(
                                                    getResources()
                                                            .getColor(
                                                                    android.R.color.background_dark));
                                }

                                @Override
                                public void onAnimationRepeat(Animator arg0) {
                                }

                                @Override
                                public void onAnimationStart(Animator arg0) {
                                    TextView tv = (TextView) findViewById(R.id.progress_handle);
                                    tv.setText(R.string.sent_tweet);
                                    tv.setBackgroundColor(getResources()
                                            .getColor(
                                                    android.R.color.holo_blue_dark));
                                }

                            });
                            vpa.alpha(0);
                        }
                        return;
                    }
                    boolean errors = false;
                    for (SendTweetTask stt : SendTweetService.getInstance().tweets) {
                        if (stt.result.errorCode != Result.WAITING
                                && !stt.result.sent) {
                            errors = true;
                        }
                    }
                    TextView tv = (TextView) findViewById(R.id.progress_handle);
                    if (errors) {
                        tv.setText(getString(R.string.send_error_tweets)
                                .replace(
                                        "{sending}",
                                        SendTweetService.getInstance().tweets
                                                .size() + ""));
                    } else {
                        if (SendTweetService.getInstance().tweets.size() == 1) {
                            tv.setText(R.string.sending_tweet);
                        } else {
                            tv.setText(getString(R.string.sending_tweets)
                                    .replace(
                                            "{sending}",
                                            SendTweetService.getInstance().tweets
                                                    .size() + ""));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    SendTweetUpdater receiver = new SendTweetUpdater();
    BoidActivity boid;

    private void initialize(Bundle savedInstanceState) {
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        if (!prefs.contains("enable_profileimg_download"))
            prefs.edit().putBoolean("enable_profileimg_download", true)
                    .commit();
        if (!prefs.contains("enable_media_download"))
            prefs.edit().putBoolean("enable_media_download", true).commit();
        if (!prefs.contains("enable_drafts"))
            prefs.edit().putBoolean("enable_drafts", true).commit();
        if (!prefs.contains("textual_userlist_tabs"))
            prefs.edit().putBoolean("textual_userlist_tabs", true).commit();
        if (!prefs.contains("textual_savedsearch_tabs"))
            prefs.edit().putBoolean("textual_savedsearch_tabs", true).commit();
        if (!prefs.contains("textual_profilefeed_tabs"))
            prefs.edit().putBoolean("textual_profilefeed_tabs", true).commit();
        if (!prefs.contains("boid_theme"))
            prefs.edit().putString("boid_theme", "0").commit();
        if (!prefs.contains("upload_service"))
            prefs.edit().putString("upload_service", "twitter").commit();
        if (!prefs.contains("enable_inline_previewing"))
            prefs.edit().putBoolean("enable_inline_previewing", true).commit();
        if (!prefs.contains("enable_iconic_tabs"))
            prefs.edit().putBoolean("enable_iconic_tabs", true).commit();

        ActionBar ab = getActionBar();
        ab.setDisplayShowTitleEnabled(false);
        ab.setDisplayShowHomeEnabled(false);
        ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        // startService(new Intent(this, AccountService.class));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (AccountService.getAccounts().size() > 0) {
            setIntent(intent);
            accountsLoaded();
        }
        if (intent.getExtras() != null) {
            if (intent.getExtras().containsKey("new_column")) {
                newColumn = true;
                restartActivity();
            }
            if (intent.getExtras().containsKey("restart")) {
                restartActivity();
            }
        }
    }

    public void loadColumns(boolean firstLoad, Intent intent) {
        if (AccountService.getAccounts().size() == 0) {
            return;
        } else {
            long lastSel = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext()).getLong("last_sel_account", 0l);
            if (!AccountService.existsAccount(lastSel)) {
                lastSel = AccountService.getAccounts().get(0).getId();
                PreferenceManager
                        .getDefaultSharedPreferences(getApplicationContext())
                        .edit().putLong("last_sel_account", lastSel).commit();
            }
            if (AccountService.getCurrentAccount() == null
                    || (firstLoad && AccountService.selectedAccount != lastSel)) {
                AccountService.selectedAccount = lastSel;
            }
        }

        if (mTabsAdapter == null) {
            mTabsAdapter = new TabsAdapter(this);
            filterDefaultColumnSelection = true;
        } else {
            filterDefaultColumnSelection = true;
            mTabsAdapter.clear();
        }
        mTabsAdapter.setIsHome(true);
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        ArrayList<String> cols = Utilities.jsonToArray(prefs.getString(
                Long.toString(AccountService.getCurrentAccount().getId())
                        + "_columns", ""));
        if (cols.size() == 0) {
            cols.add(TimelineFragment.ID);
            cols.add(MentionsFragment.ID);
            cols.add(MessagesFragment.ID);
            cols.add(TrendsFragment.ID);
            prefs.edit()
                    .putString(
                            Long.toString(AccountService.getCurrentAccount()
                                    .getId()) + "_columns",
                            Utilities.arrayToJson(cols)).commit();
        }
        int index = 0;
        boolean iconic = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext()).getBoolean("enable_iconic_tabs", true);
        for (int i = 0; i < cols.size(); i++) {
            String c = cols.get(i);
            if (c.equals(TimelineFragment.ID)) {
                Tab toAdd = getActionBar().newTab().setTabListener(this);
                if (iconic) {
                    Drawable icon = getTheme().obtainStyledAttributes(
                            new int[]{R.attr.timelineTab}).getDrawable(0);
                    toAdd.setIcon(icon);
                } else
                    toAdd.setText(R.string.timeline_str);
                mTabsAdapter.addTab(toAdd, TimelineFragment.class, index);
            } else if (c.equals(MentionsFragment.ID)) {
                Tab toAdd = getActionBar().newTab().setTabListener(this);
                if (iconic) {
                    Drawable icon = getTheme().obtainStyledAttributes(
                            new int[]{R.attr.mentionsTab}).getDrawable(0);
                    toAdd.setIcon(icon);
                } else
                    toAdd.setText(R.string.mentions_str);
                mTabsAdapter.addTab(toAdd, MentionsFragment.class, index);
            } else if (c.equals(MessagesFragment.ID)) {
                Tab toAdd = getActionBar().newTab().setTabListener(this);
                if (iconic) {
                    Drawable icon = getTheme().obtainStyledAttributes(
                            new int[]{R.attr.messagesTab}).getDrawable(0);
                    toAdd.setIcon(icon);
                } else
                    toAdd.setText(R.string.messages_str);
                mTabsAdapter.addTab(toAdd, MessagesFragment.class, index);
            } else if (c.equals(TrendsFragment.ID)) {
                Tab toAdd = getActionBar().newTab().setTabListener(this);
                if (iconic) {
                    Drawable icon = getTheme().obtainStyledAttributes(
                            new int[]{R.attr.trendsTab}).getDrawable(0);
                    toAdd.setIcon(icon);
                } else
                    toAdd.setText(R.string.trends_str);
                mTabsAdapter.addTab(toAdd, TrendsFragment.class, index);
            } else if (c.equals(FavoritesFragment.ID)) {
                Tab toAdd = getActionBar().newTab().setTabListener(this);
                if (iconic) {
                    Drawable icon = getTheme().obtainStyledAttributes(
                            new int[]{R.attr.favoritesTab}).getDrawable(0);
                    toAdd.setIcon(icon);
                } else
                    toAdd.setText(R.string.favorites_str);
                mTabsAdapter.addTab(toAdd, FavoritesFragment.class, index);
            } else if (c.startsWith(SearchTweetsFragment.ID + "@")) {
                String fromQuery = SearchTweetsFragment.ID + "@from:";
                if (c.toLowerCase().startsWith(fromQuery.toLowerCase())) {
                    // Convert the from:screenname saved search column to a user
                    // feed column
                    c = ProfileTimelineFragment.ID
                            + "@"
                            + c.substring(fromQuery.length())
                            .replace("%40", "");
                    cols.set(index, c);
                    prefs.edit()
                            .putString(
                                    Long.toString(AccountService
                                            .getCurrentAccount().getId())
                                            + "_columns",
                                    Utilities.arrayToJson(cols)).commit();
                } else {
                    String query = c.substring(
                            SearchTweetsFragment.ID.length() + 1).replace("%40",
                            "@");
                    Tab toAdd = getActionBar().newTab().setTabListener(this);
                    if (iconic) {
                        Drawable icon = getTheme().obtainStyledAttributes(
                                new int[]{R.attr.savedSearchTab})
                                .getDrawable(0);
                        toAdd.setIcon(icon);
                        if (prefs.getBoolean("textual_savedsearch_tabs", true)) {
                            toAdd.setText(query);
                        }
                    } else
                        toAdd.setText(query);
                    mTabsAdapter.addTab(toAdd, SearchTweetsFragment.class,
                            index,
                            c.substring(SearchTweetsFragment.ID.length() + 1));
                }
            } else if (c.startsWith(UserListFragment.ID + "@")) {
                String name = c.substring(UserListFragment.ID.length() + 1);
                int id = Integer.parseInt(name.substring(name.indexOf("@") + 1,
                        name.length()));
                name = name.substring(0, name.indexOf("@")).replace("%40", "@");
                if (name.startsWith("@"
                        + AccountService.getCurrentAccount().getUser()
                        .getScreenName())) {
                    name = name.substring(name.indexOf("/") + 1);
                }
                Tab toAdd = getActionBar().newTab().setTabListener(this);
                if (iconic) {
                    Drawable icon = getTheme().obtainStyledAttributes(
                            new int[]{R.attr.userListTab}).getDrawable(0);
                    toAdd.setIcon(icon);
                    if (prefs.getBoolean("textual_userlist_tabs", true))
                        toAdd.setText(name);
                } else
                    toAdd.setText(name);
                mTabsAdapter.addTab(toAdd, UserListFragment.class, index, name,
                        id);
            } else if (c.equals(MyListsFragment.ID)) {
                Tab toAdd = getActionBar().newTab().setTabListener(this);
                if (iconic) {
                    Drawable icon = getTheme().obtainStyledAttributes(
                            new int[]{R.attr.userListTab}).getDrawable(0);
                    toAdd.setIcon(icon);
                    if (prefs.getBoolean("textual_userlist_tabs", true)) {
                        toAdd.setText(R.string.my_lists_str);
                    }
                } else
                    toAdd.setText(R.string.my_lists_str);
                mTabsAdapter.addTab(toAdd, MyListsFragment.class, index);
            }
            if (c.startsWith(ProfileTimelineFragment.ID + "@")) {
                Tab toAdd = getActionBar().newTab().setTabListener(this);
                String screenName = c.substring(ProfileTimelineFragment.ID
                        .length() + 1);
                if (iconic) {
                    Drawable icon = getTheme().obtainStyledAttributes(
                            new int[]{R.attr.userFeedTab}).getDrawable(0);
                    toAdd.setIcon(icon);
                    if (prefs.getBoolean("textual_profilefeed_tabs", true)) {
                        toAdd.setText("@" + screenName);
                    }
                } else
                    toAdd.setText("@" + screenName);
                mTabsAdapter.addTab(toAdd, ProfileTimelineFragment.class,
                        index, screenName);
            }
            index++;
        }

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(4);
        mViewPager.setAdapter(mTabsAdapter);
        mViewPager
                .setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(final int position) {
                        getActionBar().getTabAt(position).select();
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    ((TabsAdapter.IBoidFragment) mTabsAdapter
                                            .getLiveItem(position)).onDisplay();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                        });

                    }
                });

        if (newColumn) {
            newColumn = false;
            getActionBar().setSelectedNavigationItem(
                    getActionBar().getTabCount() - 1);
        } else {
            int defaultColumn = prefs.getInt(
                    Long.toString(AccountService.getCurrentAccount().getId())
                            + "_default_column", 0);
            if (defaultColumn > (getActionBar().getTabCount() - 1)) {
                defaultColumn = getActionBar().getTabCount() - 1;
                prefs.edit()
                        .putInt(Long.toString(AccountService
                                .getCurrentAccount().getId())
                                + "_default_column", defaultColumn).apply();
            }
            getActionBar().setSelectedNavigationItem(defaultColumn);
            ViewPager pager = (ViewPager) findViewById(R.id.pager);
            pager.setAdapter(mTabsAdapter);
            pager.setCurrentItem(defaultColumn);
        }
        filterDefaultColumnSelection = false;
        invalidateOptionsMenu();
    }

    public void recreateAdapters() {
        if (AccountService.feedAdapters != null) {
            for (int i = 0; i < AccountService.feedAdapters.size(); i++) {
                Utilities.recreateFeedAdapter(this,
                        AccountService.feedAdapters.get(i));
            }
        }
        if (AccountService.messageAdapters != null) {
            for (int i = 0; i < AccountService.messageAdapters.size(); i++) {
                Utilities.recreateMessageAdapter(this,
                        AccountService.messageAdapters.get(i));
            }
        }
        if (AccountService.trendsAdapter != null)
            AccountService.trendsAdapter = new TrendsListAdapter(this);
        if (AccountService.myListsAdapter != null) {
            UserList[] before = AccountService.myListsAdapter.toArray();
            AccountService.myListsAdapter = new UserListDisplayAdapter(this);
            AccountService.myListsAdapter.add(before);
        }
        if (AccountService.searchFeedAdapters != null) {
            for (int i = 0; i < AccountService.searchFeedAdapters.size(); i++) {
                Utilities.recreateFeedAdapter(this, AccountService.searchFeedAdapters.get(i));
            }
        }
        if (AccountService.mediaAdapters != null) {
            for (int i = 0; i < AccountService.mediaAdapters.size(); i++) {
                Utilities.recreateMediaFeedAdapter(this,
                        AccountService.mediaAdapters.get(i));
            }
        }
        for (int i = 0; i < getActionBar().getTabCount(); i++) {
            Fragment frag = getFragmentManager().findFragmentByTag(
                    "page:" + Integer.toString(i));
            if (IBoidFragment.class.isInstance(frag)) {
                // TODO: Solve
                //((IBoidFragment) frag).reloadAdapter();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            if (getIntent().getData().getPath().contains("/status/")) {
                startActivity(getIntent().setClass(this, TweetViewer.class));
                finish();
                return;
            } else if (getIntent().getDataString().contains("twitter.com/")
                    && getIntent().getData().getPathSegments().size() == 1) {
                startActivity(getIntent().setClass(this, ProfileScreen.class));
                finish();
                return;
            }
        }

        boid = new BoidActivity(this);
        boid.AccountsReady = new BoidActivity.OnAction() {

            @Override
            public void done() {
                loadColumns(boid.firstLoad, null);
                accountsLoaded();
                invalidateOptionsMenu();
            }
        };
        boid.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        initialize(savedInstanceState);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("lastTheme")
                    || savedInstanceState.containsKey("lastDisplayReal")) {
                lastTheme = savedInstanceState.getInt("lastTheme");
                lastDisplayReal = savedInstanceState
                        .getBoolean("lastDisplayReal");
                setTheme(lastTheme);
                recreateAdapters();
            } else
                setTheme(Utilities.getTheme(getApplicationContext()));
            newColumn = savedInstanceState.getBoolean("newColumn", false);
            if (savedInstanceState.containsKey("lastIconic")) {
                lastIconic = savedInstanceState.getBoolean("lastIconic");
            } else
                lastIconic = PreferenceManager.getDefaultSharedPreferences(
                        getApplicationContext()).getBoolean(
                        "enable_iconic_tabs", true);
        } else {
            setTheme(Utilities.getTheme(getApplicationContext()));
            if (getIntent().getExtras() != null) {
                if (getIntent().getExtras().containsKey("new_column")) {
                    newColumn = true;
                    Intent toSet = getIntent();
                    toSet.removeExtra("new_column");
                    setIntent(toSet);
                }
                if (getIntent().getExtras().containsKey("isRestarted")) {
                    recreateAdapters();
                    Intent toSet = getIntent();
                    toSet.removeExtra("isRestarted");
                    setIntent(toSet);
                }
            }
            lastIconic = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext()).getBoolean("enable_iconic_tabs",
                    true);
            lastDisplayReal = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext()).getBoolean("show_real_names",
                    false);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(SendTweetService.NETWORK_AVAIL);
        filter.addAction(SendTweetService.UPDATE_STATUS);
        filter.addAction(AccountService.END_LOAD);
        registerReceiver(receiver, filter);

        // ActionBar Fix
        // http://stackoverflow.com/questions/8465258/how-can-i-force-the-action-bar-to-be-at-the-bottom-in-ics
        // based on how Google did it
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(actionBar.getDisplayOptions()
                | ActionBar.DISPLAY_SHOW_CUSTOM);
        /*
         * View v = new View(this); actionBar.setCustomView(v, new
		 * ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, 0,
		 * Gravity.CENTER_VERTICAL | Gravity.RIGHT));
		 */
    }

    public void accountsLoaded() {
        invalidateOptionsMenu();
        startService(new Intent(this, SendTweetService.class)
                .setAction(SendTweetService.LOAD_TWEETS));
    }

    public void restartActivity() {
        Intent intent = getIntent().putExtra("isRestarted", true);
        finish();
        startActivity(intent);
    }

    private void showFollowDialog(final Account acc) {
        if (acc.getUser().getScreenName().equals("boidapp"))
            return;
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        if (prefs.getBoolean(acc.getId() + "_follows_boidapp", false))
            return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    final Relationship rel = acc.getClient().showFriendship(acc.getUser().getScreenName(), "boidapp");
                    if (!rel.isSourceFollowingTarget()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                AlertDialog.Builder diag = new AlertDialog.Builder(
                                        TimelineScreen.this);
                                diag.setTitle("@boidapp");
                                diag.setMessage(getString(
                                        R.string.follow_boidapp_prompt)
                                        .replace("{account}",
                                                acc.getUser().getScreenName()));
                                diag.setPositiveButton(R.string.yes_str,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                dialog.dismiss();
                                                new Thread(new Runnable() {
                                                    public void run() {
                                                        try {
                                                            acc.getClient()
                                                                    .createFriendship(
                                                                            "boidapp");
                                                            runOnUiThread(new Runnable() {
                                                                public void run() {
                                                                    prefs.edit()
                                                                            .putBoolean(
                                                                                    acc.getId()
                                                                                            + "_follows_boidapp",
                                                                                    true)
                                                                            .apply();
                                                                }
                                                            });
                                                        } catch (final Exception e) {
                                                            e.printStackTrace();
                                                            runOnUiThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    Toast.makeText(
                                                                            getApplicationContext(),
                                                                            R.string.failed_follow_boidapp,
                                                                            Toast.LENGTH_LONG)
                                                                            .show();
                                                                }
                                                            });
                                                        }
                                                    }
                                                }).start();
                                            }
                                        });
                                diag.setNegativeButton(R.string.no_str,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                dialog.dismiss();
                                                prefs.edit()
                                                        .putBoolean(
                                                                acc.getId()
                                                                        + "_follows_boidapp",
                                                                true).apply();
                                            }
                                        });
                                diag.create().show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(
                                    getApplicationContext(),
                                    getString(
                                            R.string.failed_check_following_boidapp)
                                            .replace(
                                                    "{account}",
                                                    acc.getUser()
                                                            .getScreenName())
                                            + " " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lastTheme == 0)
            lastTheme = Utilities.getTheme(getApplicationContext());
        else if (lastTheme != Utilities.getTheme(getApplicationContext())) {
            lastTheme = Utilities.getTheme(getApplicationContext());
            restartActivity();
            return;
        } else if (lastIconic != PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext()).getBoolean("enable_iconic_tabs", true)) {
            lastIconic = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext()).getBoolean("enable_iconic_tabs",
                    true);
            restartActivity();
            return;
        } else if (lastDisplayReal != PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext())
                .getBoolean("show_real_names", false)) {
            lastDisplayReal = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext())
                    .getBoolean("show_real_names", true);
            restartActivity();
            return;
        }
        TimelineCAB.context = this;
        MessageConvoCAB.context = this;
        if (getActionBar().getTabCount() == 0
                && AccountService.getAccounts().size() > 0)
            loadColumns(false, null);
        if (AccountService.selectedAccount > 0
                && AccountService.getAccounts().size() > 0) {
            if (!AccountService.existsAccount(AccountService.selectedAccount)) {
                AccountService.selectedAccount = AccountService.getAccounts()
                        .get(0).getId();
                loadColumns(false, null);
            } else {
                for (int i = 0; i < getActionBar().getTabCount(); i++) {
                    Fragment frag = getFragmentManager().findFragmentByTag(
                            "page:" + i);
                    if (frag != null && frag instanceof BaseListFragment) {
                        // wut? ((BaseListFragment) frag).filter();
                    }
                }
            }
            if (AccountService.getAccounts().size() == 1) {
                showFollowDialog(AccountService.getAccounts().get(0));
            }
        }
        invalidateOptionsMenu();
        IntentFilter filter = new IntentFilter();
        filter.addAction(SendTweetService.NETWORK_AVAIL);
        filter.addAction(SendTweetService.UPDATE_STATUS);
        filter.addAction(AccountService.END_LOAD);
        registerReceiver(receiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(receiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        boid.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt("lastTheme", lastTheme);
        outState.putBoolean("lastDisplayReal", lastDisplayReal);
        outState.putBoolean("lastIconic", lastIconic);
        outState.putBoolean("newColumn", newColumn);
        super.onSaveInstanceState(outState);
    }

    @SuppressLint("AlwaysShowAction")
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main_actionbar, menu);
        final ArrayList<Account> accs = AccountService.getAccounts();
        // Loading
        try {
            Fragment frag = getFragmentManager().findFragmentByTag(
                    "page:" + getActionBar().getSelectedNavigationIndex());
            if (frag != null) {
                if (((TabsAdapter.IBoidFragment) frag).isRefreshing()) {
                    ProgressBar p = new ProgressBar(this, null,
                            android.R.attr.progressBarStyleSmall);
                    menu.findItem(R.id.refreshAction).setActionView(p)
                            .setEnabled(false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        final MenuItem switcher = menu.findItem(R.id.accountSwitcher);
        final MenuItem myProfile = menu.findItem(R.id.myProfileAction);
        if (accs.size() == 1) {
            menu.findItem(R.id.accountSwitcher).setVisible(false);
            myProfile.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        } else {
            for (int i = 0; i < accs.size(); i++) {
                switcher.getSubMenu().add(
                        "@" + accs.get(i).getUser().getScreenName());
            }
        }
        if (AccountService.getCurrentAccount() != null) {
            switcher.setTitle("@"
                    + AccountService.getCurrentAccount().getUser()
                    .getScreenName());
            if (accs.size() == 1)
                myProfile.setTitle("@"
                        + AccountService.getCurrentAccount().getUser()
                        .getScreenName());
        }

        return true;
    }

    private Boolean performRefresh() {
        if (AccountService.getAccounts().size() == 0)
            return false;
        Fragment frag = getFragmentManager().findFragmentByTag(
                "page:"
                        + Integer.toString(getActionBar()
                        .getSelectedNavigationIndex()));
        if (frag != null) {
            if (frag instanceof BaseListFragment)
                ((BaseListFragment) frag).performRefresh();
            else if (frag instanceof BaseGridFragment)
                ((BaseGridFragment) frag).performRefresh(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.manageColumnsAction:
                startActivityForResult(
                        new Intent(this, ColumnManager.class).putExtra("tab_count",
                                getActionBar().getTabCount()), 900);
                return true;
            case R.id.donateAction:
                startActivity(new Intent(this, DonateActivity.class));
                return true;
            case R.id.refreshAction:
                performRefresh();
                return true;
            case R.id.searchAction:
                if (AccountService.getAccounts().size() == 0)
                    return false;
                super.onSearchRequested();
                return true;
            case R.id.newTweetAction:
                if (AccountService.getAccounts().size() == 0)
                    return false;
                startActivity(new Intent(getApplicationContext(),
                        ComposerScreen.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                return true;
            case R.id.myProfileAction:
                if (AccountService.getAccounts().size() == 0)
                    return false;
                startActivity(new Intent(getApplicationContext(),
                        ProfileScreen.class).putExtra(
                        "screen_name",
                        AccountService.getCurrentAccount().getUser()
                                .getScreenName()).addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP));
                return true;
            case R.id.settingsAction:
                startActivity(new Intent(getApplicationContext(),
                        SettingsScreen.class));
                return true;
            default:
                for (Account acc : AccountService.getAccounts()) {
                    if (acc.getUser().getScreenName()
                            .equals(item.getTitle().toString().substring(1))) {
                        if (AccountService.selectedAccount == acc.getId())
                            break;
                        AccountService.selectedAccount = acc.getId();
                        PreferenceManager
                                .getDefaultSharedPreferences(
                                        getApplicationContext()).edit()
                                .putLong("last_sel_account", acc.getId()).commit();
                        loadColumns(false, null);
                        showFollowDialog(acc);
                        break;
                    }
                }
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction arg1) {
        boolean selected = mTabsAdapter.mTabs.get(tab.getPosition()).aleadySelected;
        if (selected) {
            Fragment frag = getFragmentManager().findFragmentByTag(
                    "page:" + tab.getPosition());
            if (frag != null) {
                if (frag instanceof BaseListFragment)
                    ((BaseListFragment) frag).jumpTop();
                else if (frag instanceof BaseGridFragment)
                    ((BaseGridFragment) frag).jumpTop();
            }
        }
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction arg1) {
        if (!filterDefaultColumnSelection) {
            final String prefName = Long.toString(AccountService
                    .getCurrentAccount().getId()) + "_default_column";
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putInt(prefName, tab.getPosition()).apply();
        }
        TabInfo curInfo = mTabsAdapter.mTabs.get(tab.getPosition());
        curInfo.aleadySelected = true;
        mTabsAdapter.mTabs.set(tab.getPosition(), curInfo);
        if (mViewPager != null) {
            mViewPager.setCurrentItem(tab.getPosition());
        }
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        if (mTabsAdapter.mTabs.size() == 0
                || tab.getPosition() > mTabsAdapter.mTabs.size())
            return;
        TabInfo curInfo = mTabsAdapter.mTabs.get(tab.getPosition());
        curInfo.aleadySelected = false;
        mTabsAdapter.mTabs.set(tab.getPosition(), curInfo);
    }
}