package com.teamboid.twitter;

import android.app.*;
import android.app.ActionBar.Tab;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.teamboid.twitter.TabsAdapter.BaseGridFragment;
import com.teamboid.twitter.TabsAdapter.BaseListFragment;
import com.teamboid.twitter.TabsAdapter.TabInfo;
import com.teamboid.twitter.cab.TimelineCAB;
import com.teamboid.twitter.columns.ProfileAboutFragment;
import com.teamboid.twitter.columns.ProfileTimelineFragment;
import com.teamboid.twitter.listadapters.FeedListAdapter;
import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitter.utilities.BoidActivity;
import com.teamboid.twitter.utilities.BoidApplication;
import com.teamboid.twitter.utilities.Utilities;
import com.teamboid.twitter.views.NetworkedCacheableImageView;
import twitter4j.ResponseList;
import twitter4j.User;
import twitter4j.UserList;
import uk.co.senab.bitmapcache.CacheableBitmapDrawable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * The activity that represents the profile viewer.
 *
 * @author Aidan Follestad
 */
public class ProfileScreen extends Activity implements ActionBar.TabListener {

    public static final int LOAD_CONTACT_ID = 1;
    public static final int EDITOR_REQUEST_CODE = 700;

    private int lastTheme;
    private boolean showProgress;
    public FeedListAdapter adapter;
    public User user;
    private ViewPager mViewPager;
    BoidActivity boid;

    public void showProgress(boolean visible) {
        if (showProgress == visible)
            return;
        showProgress = visible;
        setProgressBarIndeterminateVisibility(visible);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("lastTheme")) {
                lastTheme = savedInstanceState.getInt("lastTheme");
                setTheme(lastTheme);
            } else
                setTheme(Utilities.getTheme(getApplicationContext()));
            if (savedInstanceState.containsKey("showProgress"))
                showProgress(true);
        } else
            setTheme(Utilities.getTheme(getApplicationContext()));
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        boid = new BoidActivity(this);
        boid.AccountsReady = new BoidActivity.OnAction() {

            @Override
            public void done() {
                finishCreate(savedInstanceState);
            }
        };
        boid.onCreate(savedInstanceState);
    }

    public void finishCreate(final Bundle savedInstanceState) {
        ActionBar ab = getActionBar();
        ab.setDisplayShowTitleEnabled(false);
        ab.setDisplayShowHomeEnabled(false);
        setContentView(R.layout.profile_screen);
        setProgressBarIndeterminateVisibility(false);
        final ImageView profileImg = (ImageView) findViewById(R.id.userItemProfilePic);
        profileImg.setImageBitmap(Utilities.getRoundedImage(BitmapFactory
                .decodeResource(getResources(), R.drawable.sillouette), 90F));

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            try {
                String screenName = getIntent().getData().getPathSegments()
                        .get(0);
                initializeTabs(savedInstanceState, screenName);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.error_str, Toast.LENGTH_SHORT)
                        .show();
                finish();
            }
        } else if (getIntent().hasExtra("screen_name")) {
            initializeTabs(savedInstanceState,
                    getIntent().getStringExtra("screen_name"));
        } else if (getIntent().getDataString().contains("com.android.contacts")) {
            // Loading from contact (contact syncing)
            getLoaderManager().initLoader(LOAD_CONTACT_ID, null,
                    new LoaderManager.LoaderCallbacks<Cursor>() {

                        @Override
                        public Loader<Cursor> onCreateLoader(int arg0,
                                                             Bundle arg1) {
                            return new CursorLoader(
                                    ProfileScreen.this,
                                    getIntent().getData(),
                                    new String[]{ContactsContract.Data.DATA1},
                                    null, null, null);
                        }

                        @Override
                        public void onLoadFinished(Loader<Cursor> arg0,
                                                   Cursor cursor) {
                            cursor.moveToNext();
                            initializeTabs(
                                    savedInstanceState,
                                    cursor.getString(cursor
                                            .getColumnIndex(ContactsContract.Data.DATA1)));
                        }

                        @Override
                        public void onLoaderReset(Loader<Cursor> arg0) {
                        }

                    });
            setTitle(R.string.please_wait);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        boid.onDestroy();
    }

    private TabsAdapter mTabsAdapter;
    private String mScreenName;

    private void initializeTabs(Bundle savedInstanceState, String screenName) {
        mScreenName = screenName;
        setTitle("@" + screenName);
        mTabsAdapter = new TabsAdapter(this);
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        boolean iconic = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext()).getBoolean("enable_iconic_tabs", true);
        if (iconic) {
            mTabsAdapter.addTab(
                    bar.newTab()
                            .setTabListener(this)
                            .setIcon(
                                    getTheme().obtainStyledAttributes(
                                            new int[]{R.attr.timelineTab})
                                            .getDrawable(0)),
                    ProfileTimelineFragment.class, 0, screenName);
            mTabsAdapter.addTab(
                    bar.newTab()
                            .setTabListener(this)
                            .setIcon(
                                    getTheme().obtainStyledAttributes(
                                            new int[]{R.attr.aboutTab})
                                            .getDrawable(0)),
                    ProfileAboutFragment.class, 1, screenName);
        } else {
            mTabsAdapter.addTab(
                    bar.newTab().setTabListener(this)
                            .setText(R.string.tweets_str),
                    ProfileTimelineFragment.class, 0, screenName);
            mTabsAdapter.addTab(
                    bar.newTab().setTabListener(this)
                            .setText(R.string.about_str),
                    ProfileAboutFragment.class, 1, screenName);
        }

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(4);
        mViewPager.setAdapter(mTabsAdapter);

        if (savedInstanceState != null) {
            getActionBar().setSelectedNavigationItem(
                    savedInstanceState.getInt("lastTab", 0));
        }
        invalidateOptionsMenu();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lastTheme == 0)
            lastTheme = Utilities.getTheme(getApplicationContext());
        else if (lastTheme != Utilities.getTheme(getApplicationContext())) {
            lastTheme = Utilities.getTheme(getApplicationContext());
            recreate();
            return;
        }
        TimelineCAB.context = this;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        if (AccountService.getCurrentAccount() != null
                && AccountService.getCurrentAccount().getUser().getScreenName()
                .equals(mScreenName)) {
            inflater.inflate(R.menu.profile_self_actionbar, menu);
        } else {
            inflater.inflate(R.menu.profile_actionbar, menu);
            if (user != null) {
                if (!getAboutFragment().isBlocked())
                    menu.findItem(R.id.blockAction).setEnabled(true);
                else
                    menu.findItem(R.id.blockAction).setVisible(false);
                menu.findItem(R.id.reportAction).setEnabled(true);
            }
        }
        /* TODO: Uncomment when it's working. */
        Fragment frag = getFragmentManager().findFragmentByTag(
                "page:"
                        + getActionBar()
                        .getSelectedNavigationIndex());
        if (frag != null) {
            if (((TabsAdapter.IBoidFragment) frag).isRefreshing()) {
                ProgressBar p = new ProgressBar(this, null,
                        android.R.attr.progressBarStyleSmall);
                menu.findItem(R.id.refreshAction).setActionView(p)
                        .setEnabled(false);
            }
        }

        if (showProgress) {
            final MenuItem refreshAction = menu.findItem(R.id.refreshAction);
            refreshAction.setEnabled(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
            case R.id.editAction:
                startActivityForResult(
                        new Intent(this, ProfileEditor.class).putExtra(
                                "screen_name", mScreenName), EDITOR_REQUEST_CODE);
                return true;
            case R.id.mentionAction:
                startActivity(new Intent(this, ComposerScreen.class).putExtra(
                        "append", "@" + mScreenName).addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP));
                return true;
            case R.id.pinAction:
                final SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(getApplicationContext());
                ArrayList<String> cols = Utilities.jsonToArray(prefs.getString(
                        Long.toString(AccountService.getCurrentAccount().getId())
                                + "_columns", ""));
                cols.add(ProfileTimelineFragment.ID + "@" + mScreenName);
                prefs.edit()
                        .putString(
                                Long.toString(AccountService.getCurrentAccount()
                                        .getId()) + "_columns",
                                Utilities.arrayToJson(cols)).commit();
                startActivity(new Intent(this, TimelineScreen.class).putExtra(
                        "new_column", true));
                finish();
                return true;
            case R.id.messageAction:
                startActivity(new Intent(getApplicationContext(),
                        ConversationScreen.class).putExtra("screen_name",
                        mScreenName));
                return true;
            case R.id.blockAction:
                if (user == null)
                    return false;
                block();
                return true;
            case R.id.reportAction:
                if (user == null)
                    return false;
                report();
                return true;
            case R.id.refreshAction:
                Fragment frag = getFragmentManager().findFragmentByTag(
                        "page:"
                                + Integer.toString(getActionBar()
                                .getSelectedNavigationIndex()));
                if (frag != null) {
                    if (frag instanceof BaseListFragment) {
                        ((BaseListFragment) frag).performRefresh();
                    } else if (frag instanceof BaseGridFragment) {
                        ((BaseGridFragment) frag).performRefresh(false);
                    }
                }
                return true;
            case R.id.addToListAction:
                final Toast toast = Toast.makeText(getApplicationContext(),
                        getString(R.string.loading_lists), Toast.LENGTH_SHORT);
                toast.show();
                new Thread(new Runnable() {
                    public void run() {
                        Account acc = AccountService.getCurrentAccount();
                        try {
                            final ResponseList<UserList> lists = acc.getClient().getUserLists(acc.getId());
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    toast.cancel();
                                    showAddToListDialog(lists);
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(getApplicationContext(),
                                            getString(R.string.failed_load_lists),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void block() {
        AlertDialog.Builder diag = new AlertDialog.Builder(this);
        diag.setTitle(R.string.block_str);
        diag.setMessage(R.string.confirm_block_str);
        diag.setPositiveButton(R.string.yes_str,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Toast toast = Toast.makeText(ProfileScreen.this,
                                R.string.blocking_str, Toast.LENGTH_LONG);
                        toast.show();
                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    AccountService.getCurrentAccount()
                                            .getClient()
                                            .createBlock(user.getId());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            Toast.makeText(
                                                    getApplicationContext(),
                                                    R.string.failed_block_str,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                                    return;
                                }
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        toast.cancel();
                                        Toast.makeText(ProfileScreen.this,
                                                R.string.success_blocked_str,
                                                Toast.LENGTH_SHORT).show();
                                        recreate(); // TODO Recreation doesn't
                                        // seem to update the screen
                                        // with blocked info for
                                        // some reason
                                    }
                                });
                            }
                        }).start();
                    }
                });
        diag.setNegativeButton(R.string.no_str,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        diag.create().show();
    }

    public void report() {
        AlertDialog.Builder diag = new AlertDialog.Builder(this);
        diag.setTitle(R.string.report_str);
        diag.setMessage(R.string.confirm_report_str);
        diag.setPositiveButton(R.string.yes_str,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Toast toast = Toast.makeText(ProfileScreen.this,
                                R.string.reporting_str, Toast.LENGTH_LONG);
                        toast.show();
                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    AccountService.getCurrentAccount()
                                            .getClient()
                                            .reportSpam(user.getId());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            Toast.makeText(
                                                    getApplicationContext(),
                                                    R.string.failed_report_str,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                                    return;
                                }
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        toast.cancel();
                                        Toast.makeText(ProfileScreen.this,
                                                R.string.reported_str,
                                                Toast.LENGTH_SHORT).show();
                                        recreate();
                                    }
                                });
                            }
                        }).start();
                    }
                });
        diag.setNegativeButton(R.string.no_str,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        diag.create().show();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt("lastTheme", lastTheme);
        outState.putInt("lastTab", getActionBar().getSelectedNavigationIndex());
        if (showProgress) {
            showProgress(false);
            outState.putBoolean("showProgress", true);
        }
        super.onSaveInstanceState(outState);
    }

    public ProfileAboutFragment getAboutFragment() {
        return (ProfileAboutFragment) getFragmentManager().findFragmentByTag("page:1");
    }

    /**
     * Sets up our own views for this
     */
    public void setupViews() {
        final NetworkedCacheableImageView profileImg = (NetworkedCacheableImageView) findViewById(R.id.userItemProfilePic);
        profileImg.setIsRounded(true);
        profileImg.loadImage(user.getProfileImageURL(), false);
        profileImg.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                CacheableBitmapDrawable cached = BoidApplication.get(ProfileScreen.this).getBitmapCache().get(user.getProfileImageURL());
                if (cached != null) {
                    try {
                        String file = Utilities.generateImageFileName(ProfileScreen.this);
                        if (cached.getBitmap().compress(CompressFormat.PNG, 100, new FileOutputStream(file))) {
                            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(new File(file)), "image/*"));
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }

        });
        TextView tv = (TextView) findViewById(R.id.profileTopLeftDetail);
        tv.setText(user.getName() + "\n@" + user.getScreenName());

        ((ViewPager) findViewById(R.id.pager)).setOnPageChangeListener(new OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float offset,
                                       int offsetPixels) {
                // Log.d("profile", position + ", " + offset + ", "+ offsetPixels);
                if (position == 2 && offset <= 1) {
                    findViewById(R.id.profileHeader).setVisibility(View.GONE);
                    Log.d("profile", "GONE");
                } else if (position >= 1) {
                    findViewById(R.id.profileHeader).setX(-offsetPixels);
                    findViewById(R.id.profileHeader).setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageSelected(final int position) {
                getActionBar().getTabAt(position).select();
                invalidateOptionsMenu();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        ((TabsAdapter.IBoidFragment) mTabsAdapter.getLiveItem(position)).onDisplay();
                    }

                });
            }

            @Override
            public void onPageScrollStateChanged(int arg0) {
            }
        });

        try {
            // TODO check for screens big enough to retrieve non-mobile header
            ((NetworkedCacheableImageView) findViewById(R.id.img)).loadImage(user.getProfileBannerMobileURL(), true);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void showAddToListDialog(final ResponseList<UserList> lists) {
        if (lists == null || lists.size() == 0) {
            Toast.makeText(getApplicationContext(), R.string.no_lists,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIconAttribute(R.attr.cloudIcon);
        builder.setTitle(R.string.lists_str);
        ArrayList<String> items = new ArrayList<String>();
        for (UserList l : lists)
            items.add(l.getFullName());
        builder.setItems(items.toArray(new String[0]),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        final UserList curList = lists.get(item);
                        final Toast toast = Toast.makeText(
                                getApplicationContext(),
                                R.string.adding_user_list, Toast.LENGTH_LONG);
                        toast.show();
                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    AccountService.getCurrentAccount().getClient().createUserListMembers(curList.getId(), new long[]{user.getId()});
                                } catch (final Exception e) {
                                    e.printStackTrace();
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            toast.cancel();
                                            Toast.makeText(
                                                    getApplicationContext(),
                                                    e.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    return;
                                }
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        toast.cancel();
                                        Toast.makeText(getApplicationContext(),
                                                R.string.added_user_list,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }).start();
                    }
                });
        builder.create().show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == EDITOR_REQUEST_CODE && resultCode == RESULT_OK) {
            getAboutFragment().performRefresh();
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
        final String prefName = Long.toString(AccountService
                .getCurrentAccount().getId()) + "_default_column";
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putInt(prefName, tab.getPosition()).apply();
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
