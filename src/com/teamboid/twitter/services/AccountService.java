package com.teamboid.twitter.services;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import com.teamboid.twitter.Account;
import com.teamboid.twitter.LoginHandler;
import com.teamboid.twitter.R;
import com.teamboid.twitter.WelcomeActivity;
import com.teamboid.twitter.columns.TimelineFragment;
import com.teamboid.twitter.contactsync.AndroidAccountHelper;
import com.teamboid.twitter.listadapters.*;
import com.teamboid.twitter.utilities.Utilities;
import twitter4j.Twitter;
import twitter4j.TwitterAPIConfiguration;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * The service that stays running the background; authorizes, loads, and manages the current user's accounts.
 *
 * @author Aidan Follestad
 */
public class AccountService extends Service {
    public static String END_LOAD = "com.teamboid.twitter.DONE_LOADING_ACCOUNTS";

    public final static String CONSUMER_KEY = "5LvP1d0cOmkQleJlbKICtg";
    public final static String CONSUMER_SECRET = "j44kDQMIDuZZEvvCHy046HSurt8avLuGeip2QnOpHKI";
    public final static String CALLBACK_URL = "boid://auth";
    public final static int AUTH_CODE = 600;
    private static Twitter client;

    /**
     * Starts Authorization for a new Account
     *
     * @param a
     */
    public static void startAuth(final Activity a) {
        final ProgressDialog pd = new ProgressDialog(a);
        pd.setMessage(a.getString(R.string.please_wait));
        pd.show();
        new Thread(new Runnable() {
            public void run() {
                try {
                    client = TwitterFactory.getSingleton();
                    client.setOAuthConsumer(AccountService.CONSUMER_KEY, AccountService.CONSUMER_SECRET);
                    RequestToken requestToken = client.getOAuthRequestToken(CALLBACK_URL);
                    String url = requestToken.getAuthorizationURL();
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pd.dismiss();
                        }
                    });
                    a.startActivityForResult(new Intent(a, LoginHandler.class).putExtra("url", url), AUTH_CODE);
                } catch (final Exception e) {
                    e.printStackTrace();
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pd.dismiss();
                            Toast.makeText(
                                    a.getApplicationContext(),
                                    a.getString(R.string.authorization_error)
                                            + "; " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private static ArrayList<Account> accounts;
    public static ArrayList<FeedListAdapter> feedAdapters;
    public static ArrayList<MediaFeedListAdapter> mediaAdapters;
    public static ArrayList<MessageConvoAdapter> messageAdapters;
    public static TrendsListAdapter trendsAdapter;
    public static ArrayList<FeedListAdapter> searchFeedAdapters;
    public static UserListDisplayAdapter myListsAdapter;

    public static int configShortURLLength;
    public static int charactersPerMedia;
    public static long selectedAccount;

    public static ArrayList<Account> getAccounts() {
        if (accounts == null) accounts = new ArrayList<Account>();
        return accounts;
    }

    public static boolean existsAccount(long accId) {
        boolean found = false;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getId() == accId) {
                found = true;
                break;
            }
        }
        return found;
    }

    public static void setAccount(Context context, User user) {
        if (accounts == null) return;
        Account toSet = null;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getUser().getId() == user.getId()) {
                toSet = accounts.get(i).setUser(user);
                accounts.set(i, toSet);
                break;
            }
        }
        context.getSharedPreferences("profiles-v2", Context.MODE_PRIVATE).edit()
                .putString(toSet.getUser().getId() + "", Utilities.serializeObject(toSet)).commit();
    }


    public static void setAccount(Context context, Account acc) {
        if (accounts == null) {
            accounts = new ArrayList<Account>();
            accounts.add(acc);
        } else {
            for (int i = 0; i < accounts.size(); i++) {
                if (accounts.get(i).getId() == acc.getId()) {
                    accounts.set(i, acc);
                    break;
                }
            }
        }
        context.getSharedPreferences("profiles-v2", Context.MODE_PRIVATE).edit()
                .putString(acc.getUser().getId() + "", Utilities.serializeObject(acc)).commit();
    }

    public static Account getCurrentAccount() {
        if (selectedAccount == 0) {
            if (getAccounts().size() > 0) {
                selectedAccount = getAccounts().get(0).getId();
            } else {
                return null;
            }
        }
        Account toReturn = null;
        for (Account acc : getAccounts()) {
            if (acc.getUser().getId() == selectedAccount) {
                toReturn = acc;
                break;
            }
        }
        return toReturn;
    }

    public static void removeAccount(Context activity, Account acc) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        prefs.edit().remove(Long.toString(acc.getId()) + "_columns").commit();
        prefs.edit().remove(Long.toString(acc.getId()) + "_muting").commit();
        activity.getSharedPreferences("profiles-v2", 0).edit().remove(acc.getUser().getId() + "").commit();
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getToken().equals(acc.getToken())) {
                accounts.remove(i);
                break;
            }
        }
    }

    public static enum VerifyAccountResult {
        OK(1), ALREADY_ADDED(2), FAILURE(3);

        public final int code;

        VerifyAccountResult(int code) {
            this.code = code;
        }
    }

    /**
     * Verify an Account.
     * <p/>
     * Run in your own thread
     *
     * @return Result
     */
    public static VerifyAccountResult verifyAccount(final Activity activity, final String verifier) {
        try {
            final AccessToken token = client.getOAuthAccessToken(verifier);
            final User toAddUser = client.verifyCredentials();
            ArrayList<Account> accs = getAccounts();
            for (Account user : accs) {
                if (user.getUser().getId() == toAddUser.getId()) {
                    return VerifyAccountResult.ALREADY_ADDED;
                }
            }
            Account profile = new Account(client, token).setUser(toAddUser);
            accounts.add(profile);
            activity.getSharedPreferences("profiles-v2", Context.MODE_PRIVATE).edit()
                    .putString(profile.getUser().getId() + "", Utilities.serializeObject(profile)).commit();
            AndroidAccountHelper.addAccount(activity, profile);

            activity.sendBroadcast(new Intent(END_LOAD).putExtra("last_account_count", false));
            return VerifyAccountResult.OK;
        } catch (final Exception e) {
            e.printStackTrace();
            return VerifyAccountResult.FAILURE;
        }
    }

    public boolean loadAccounts() {
        final Map<String, ?> accountStore = getApplicationContext().getSharedPreferences("profiles-v2", 0).getAll();
        if (accountStore.size() == 0) {
            getApplicationContext().startActivity(new Intent(getApplicationContext(), WelcomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return false;
        } else if (getAccounts().size() == accountStore.size()) return false;

        // Android Accounts
        HashMap<String, android.accounts.Account> androidAccounts = AndroidAccountHelper.getAccounts(getApplicationContext());

        final int lastAccountCount = getAccounts().size();
        // Toast.makeText(getApplicationContext(), R.string.loading_accounts, Toast.LENGTH_LONG).show();
        for (final String token : accountStore.keySet()) {
            boolean skip = false;
            for (int i = 0; i < accounts.size(); i++) {
                Account acc = accounts.get(i);
                if (acc.getToken().getToken().equals(token)) {
                    skip = true;
                    break;
                }
            }
            if (skip) continue;

            try {
                final Account toAdd = (Account) Utilities.deserializeObject((String) accountStore.get(token));
                toAdd.refreshUserIfNeeded();
                accounts.add(toAdd);
                if (androidAccounts.containsKey(toAdd.getUser().getId() + "")) {
                    androidAccounts.remove(toAdd.getUser().getId() + "");
                } else {
                    AndroidAccountHelper.addAccount(getApplicationContext(), toAdd);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), getApplicationContext().getString(R.string.failed_load_account) +
                        " " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            //			try {
            //				final User accountUser = toAdd.verifyCredentials();
            //				accounts.add(new Account(getApplicationContext(), toAdd).setUser(accountUser));
            //			} catch (final Exception e) {
            //				e.printStackTrace();
            //				Toast.makeText(getApplicationContext(), getApplicationContext().getString(R.string.failed_load_account) +
            //						" " + e.getMessage(), Toast.LENGTH_LONG).show();
            //			}
        }

        // Now remove dead accounts
        for (android.accounts.Account acc : androidAccounts.values()) {
            Log.d("acc", "Remove Account: " + acc.name);
        }

        if (getAccounts().size() > 0) {
            if (getAccounts().size() != lastAccountCount) {
                selectedAccount = accounts.get(0).getId();
                getApplicationContext().sendBroadcast(new Intent(END_LOAD).putExtra("last_account_count", lastAccountCount == 0));
                return true;
            }
        } else {
            getApplicationContext().startActivity(new Intent(getApplicationContext(),
                    WelcomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return false;
    }

    public void loadTwitterConfig() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long lastConfigUpdate = prefs.getLong("last_config_update", new Date().getTime() - 86400000);
        configShortURLLength = 21;
        charactersPerMedia = 21;
        if (lastConfigUpdate <= (new Date().getTime() - 86400000)) {
            Log.i("BOID", "Loading Twitter config (this should only happen once every 24 hours)...");
            new Thread(new Runnable() {
                public void run() {
                    try {
                        TwitterFactory factory = new TwitterFactory();
                        Twitter twitter = factory.getInstance();
                        twitter.setOAuthConsumer(AccountService.CONSUMER_KEY, AccountService.CONSUMER_SECRET);
                        TwitterAPIConfiguration config = twitter.getAPIConfiguration();
                        configShortURLLength = config.getShortURLLength();
                        charactersPerMedia = config.getCharactersReservedPerMedia();
                        prefs.edit().putInt("shorturl_length", config.getShortURLLength())
                                .putInt("mediachars_length", config.getCharactersReservedPerMedia())
                                .putLong("last_config_update", new Date().getTime()).commit();
                    } catch (final Exception e) {
                        e.printStackTrace();
                        configShortURLLength = 21;
                        charactersPerMedia = 21;
                    }
                }
            }).start();
        }
    }

    /**
     * Used from the WidgetRemoteViewServie
     */
    public static FeedListAdapter getTimelineFeedAdapter(long account) {
        if (feedAdapters == null) return null;
        FeedListAdapter toReturn = null;
        for (FeedListAdapter adapt : feedAdapters) {
            if (TimelineFragment.ID.equals(adapt.ID) && account == adapt.getAccount().getId()) {
                toReturn = adapt;
                break;
            }
        }
        return toReturn;
    }

    public static FeedListAdapter getFeedAdapter(Activity activity, String id, long account) {
        return getFeedAdapter(activity, id, account, true);
    }

    public static FeedListAdapter getFeedAdapter(Activity activity, String id, long account, boolean createIfNull) {
        if (feedAdapters == null) feedAdapters = new ArrayList<FeedListAdapter>();
        FeedListAdapter toReturn = null;
        for (FeedListAdapter adapt : feedAdapters) {
            if (id.equals(adapt.ID) && account == adapt.getAccount().getId()) {
                toReturn = adapt;
                break;
            }
        }
        if (toReturn == null && createIfNull) {
            toReturn = new FeedListAdapter(activity, id, getAccount(account));
            feedAdapters.add(toReturn);
        }
        return toReturn;
    }

    public static void clearFeedAdapter(Activity activity, String id, long account) {
        if (feedAdapters == null) return;
        for (int i = 0; i < feedAdapters.size(); i++) {
            FeedListAdapter curAdapt = feedAdapters.get(i);
            if (curAdapt.ID.equals(id) && curAdapt.getAccount().getId() == account) {
                curAdapt.clear();
                feedAdapters.set(i, curAdapt);
                break;
            }
        }
    }

    public static MediaFeedListAdapter getMediaFeedAdapter(Activity activity, String id, long account) {
        if (mediaAdapters == null) mediaAdapters = new ArrayList<MediaFeedListAdapter>();
        MediaFeedListAdapter toReturn = null;
        for (MediaFeedListAdapter adapt : mediaAdapters) {
            if (id.equals(adapt.ID) && account == adapt.account) {
                toReturn = adapt;
                break;
            }
        }
        if (toReturn == null) {
            toReturn = new MediaFeedListAdapter(activity, id, account);
            mediaAdapters.add(toReturn);
        }
        return toReturn;
    }

    public static MessageConvoAdapter getMessageConvoAdapter(Activity activity, long account) {
        if (messageAdapters == null) messageAdapters = new ArrayList<MessageConvoAdapter>();
        MessageConvoAdapter toReturn = null;
        for (MessageConvoAdapter adapt : messageAdapters) {
            if (account == adapt.account) {
                toReturn = adapt;
                break;
            }
        }
        if (toReturn == null) {
            toReturn = new MessageConvoAdapter(activity, account);
            messageAdapters.add(toReturn);
        }
        return toReturn;
    }

    public static TrendsListAdapter getTrendsAdapter(Activity activity) {
        if (trendsAdapter == null) trendsAdapter = new TrendsListAdapter(activity);
        return trendsAdapter;
    }

    public static FeedListAdapter getSearchFeedAdapter(Activity activity, String id, Account account, String query) {
        if (searchFeedAdapters == null) {
            searchFeedAdapters = new ArrayList<FeedListAdapter>();
        }
        FeedListAdapter toReturn = null;
        for (FeedListAdapter adapt : searchFeedAdapters) {
            if (id.equals(adapt.ID) && account.getId() == adapt.getAccount().getId()) {
                toReturn = adapt;
                break;
            }
        }
        if (toReturn == null) {
            toReturn = new FeedListAdapter(activity, id, account);
            searchFeedAdapters.add(toReturn);
        }
        return toReturn;
    }

    public static UserListDisplayAdapter getMyListsAdapter(Activity activity) {
        if (myListsAdapter == null) myListsAdapter = new UserListDisplayAdapter(activity);
        return myListsAdapter;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        accounts = new ArrayList<Account>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        new Thread(new Runnable() {

            @Override
            public void run() {
                loadTwitterConfig();
                loadAccounts();
            }

        }).start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (accounts.size() > 0) {
            getApplicationContext().sendBroadcast(new Intent(END_LOAD));
        }
        return null;
    }

    public static Account getAccount(long accId) {
        Account result = null;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getId() == accId) {
                result = accounts.get(i);
            }
        }
        return result;
    }
}