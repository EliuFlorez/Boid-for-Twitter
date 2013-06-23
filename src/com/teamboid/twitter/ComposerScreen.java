package com.teamboid.twitter;

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.*;
import com.teamboid.twitter.contactsync.AutocompleteService;
import com.teamboid.twitter.listadapters.FeedListAdapter;
import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitter.services.SendTweetService;
import com.teamboid.twitter.utilities.BoidActivity;
import com.teamboid.twitter.utilities.Extractor;
import com.teamboid.twitter.utilities.Utilities;
import com.teamboid.twitter.views.NetworkedCacheableImageView;
import org.json.JSONObject;
import twitter4j.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The activity that represents the tweet composer screen.
 *
 * @author Aidan Follestad and kennydude
 */
public class ComposerScreen extends Activity {

    private SendTweetTask stt = new SendTweetTask();
    private int lastTheme;
    private boolean shownLinksMessage;

    private float locationAccuracy;
    private ResponseList<Place> places;
    private boolean isGettingLocation;
    private int lengthIndic = 140;
    private int draftIndex = -1;

    /**
     * Ensures the UI is loaded with the correct information from stt
     */
    private void loadTask() {
        final EditText content = (EditText) findViewById(R.id.tweetContent);
        content.setText(stt.contents);
        invalidateOptionsMenu();
        initializeAccountSwitcher(false);
    }

    private void loadDrafts() {
        if (!PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext()).getBoolean("enable_drafts", true)) {
            return;
        }
        if (getIntent().getExtras() != null) {
            return;
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        final LinearLayout draftsArea = (LinearLayout) findViewById(R.id.draftsArea);
        draftsArea.removeAllViews();
        draftsArea.setVisibility(View.GONE);
        findViewById(R.id.draftsTitle).setVisibility(View.GONE);

        if (prefs.contains(stt.from.getId() + "_stt_draft")) {
            draftsArea.setVisibility(View.VISIBLE);
            findViewById(R.id.draftsTitle).setVisibility(View.VISIBLE);

            EditText content = (EditText) findViewById(R.id.tweetContent);
            if (content.getText().toString().trim().length() > 0) {
                return; // Don't override if user is tweeting something already!
            }
            for (int i = 0; i < Utilities.jsonToArray(
                    prefs.getString(stt.from.getId() + "_stt_draft", null)).size(); i++) {
                try {
                    final SendTweetTask dt = SendTweetTask
                            .fromJSONObject(new JSONObject(Utilities.jsonToArray(
                                    prefs.getString(stt.from.getId() + "_stt_draft", null)).get(i)));
                    final RelativeLayout item = (RelativeLayout) getLayoutInflater()
                            .inflate(R.layout.draft_item, null);
                    ((TextView) item.findViewById(R.id.text)).setText(dt.contents);
                    if (dt.hasMedia()) {
                        ((ImageView) item.findViewById(R.id.image)).setVisibility(View.VISIBLE);
                    }
                    final int index = i;
                    item.setOnLongClickListener(new OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            final ArrayList<String> draftStore = Utilities
                                    .jsonToArray(prefs.getString(
                                            stt.from.getId() + "_stt_draft",
                                            null));
                            draftsArea.removeView(v);
                            draftStore.remove(index);
                            draftsArea.requestLayout();
                            if (draftStore.size() == 0) {
                                findViewById(R.id.draftsTitle).setVisibility(
                                        View.GONE);
                                draftsArea.setVisibility(View.GONE);
                                prefs.edit()
                                        .remove(stt.from.getId() + "_stt_draft")
                                        .commit();
                            } else {
                                prefs.edit()
                                        .putString(
                                                stt.from.getId() + "_stt_draft",
                                                Utilities
                                                        .arrayToJson(draftStore))
                                        .commit();
                            }
                            return true;
                        }
                    });
                    item.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View arg0) {
                            draftIndex = index;
                            stt = dt;
                            loadTask();
                        }
                    });
                    draftsArea.addView(item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        invalidateOptionsMenu();
    }

    private void saveDraft() {
        if (stt.from == null
                || !PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext()).getBoolean("enable_drafts",
                true) || stt.in_reply_to > 0) {
            finish();
            return;
        }
        final String content = ((EditText) findViewById(R.id.tweetContent))
                .getText().toString().trim();
        if (content.length() == 0 && stt.attachedImage == null) {
            finish();
            return;
        }
        AlertDialog.Builder prompt = new AlertDialog.Builder(this);
        prompt.setTitle(R.string.draft_str);
        prompt.setMessage(R.string.draft_prompt);
        prompt.setPositiveButton(R.string.yes_str,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        stt.contents = content;
                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(getApplicationContext());
                        ArrayList<String> draftStore = Utilities
                                .jsonToArray(prefs.getString(stt.from.getId()
                                        + "_stt_draft", null));
                        try {
                            if (draftIndex > -1) {
                                draftStore.set(draftIndex, stt.toJSONObject()
                                        .toString());
                            } else {
                                draftStore.add(stt.toJSONObject().toString());
                            }
                            prefs.edit()
                                    .putString(stt.from.getId() + "_stt_draft",
                                            Utilities.arrayToJson(draftStore))
                                    .commit();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        dialog.dismiss();
                        finish();
                    }
                });
        prompt.setNegativeButton(R.string.no_str,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                });
        prompt.create().show();
    }

    public static int SELECT_MEDIA = 2939;
    BoidActivity boid;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lengthIndic = 140;
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        boid = new BoidActivity(this);
        boid.AccountsReady = new BoidActivity.OnAction() {
            @Override
            public void done() {
                finishInit();
            }
        };
        boid.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        boid.onDestroy();
    }

    public void finishInit() {
        setContentView(R.layout.composer_screen);
        final EditText content = (EditText) findViewById(R.id.tweetContent);
        content.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                getLengthIndicator();
                invalidateOptionsMenu();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }
        });
        //final EditText content = (EditText) findViewById(R.id.tweetContent);
        if (getIntent().getData() != null) {
            // Reply or Tweet button clicks
            if (getIntent().getDataString().startsWith("/intent/tweet")) {
                // Tweet Intent
                String text = "", q = "";
                if ((q = getIntent().getData().getQueryParameter("text")) != null) {
                    text = q;
                }
                if ((q = getIntent().getData().getQueryParameter("url")) != null) {
                    text += " " + q;
                }
                if ((q = getIntent().getData().getQueryParameter("via")) != null) {
                    text += " via " + q; // TODO: Translate
                }
                if ((q = getIntent().getData().getQueryParameter("hashtags")) != null) {
                    String[] htags = q.split(",");
                    for (String tag : htags) {
                        text += " #" + tag;
                    }
                }
                content.setText(text);
            }
        } else if (getIntent().getExtras() != null) {
            if (getIntent().hasExtra("reply_to")) {
                Status replyTo = (Status) getIntent().getSerializableExtra(
                        "reply_to");
                stt.in_reply_to = replyTo.getId();

                ViewStub replyToL = (ViewStub) findViewById(R.id.replyTo);
                View replyToV = replyToL.inflate();
                FeedListAdapter.createStatusView(replyTo, this, replyToV);
                TextView tv = (TextView) findViewById(R.id.feedItemText);
                tv.setMovementMethod(new LinkMovementMethod());

                tv = (TextView) findViewById(R.id.replyToText);
                tv.setText(getString(R.string.in_reply_to).replace("{user}",
                        replyTo.getUser().getScreenName()));
                tv.setVisibility(View.VISIBLE);
            } else if (getIntent().hasExtra("stt")) {
                try {
                    stt = SendTweetTask.fromBundle(getIntent().getBundleExtra(
                            "stt"));
                    loadTask();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
                if (getIntent().hasExtra(Intent.EXTRA_TEXT))
                    content.setText(getIntent().getStringExtra(
                            Intent.EXTRA_TEXT));
                if (getIntent().hasExtra(Intent.EXTRA_STREAM)) {
                    stt.attachedImageUri = getIntent().getParcelableExtra(
                            Intent.EXTRA_STREAM);
                    stt.isGalleryImage = true;
                    invalidateOptionsMenu();
                }
            }

            if (getIntent().hasExtra("text"))
                content.setText(getIntent().getStringExtra("text"));
            else if (getIntent().hasExtra("append")) {
                content.append(getIntent().getStringExtra("append").replace("@" +
                        AccountService.getCurrentAccount().getUser().getScreenName(), "")
                        .replace("  ", " ").trim() + " ");
            }
            if (getIntent().hasExtra("image")) {
                stt.attachedImage = getIntent().getStringExtra("image");
                invalidateOptionsMenu();
            }
        }
        if (PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext()).getBoolean("attach_location", false)) {
            getLocation();
        }

        initializeAccountSwitcher(true);
        setProgressBarIndeterminateVisibility(false);
        setupAutocomplete();
        content.requestFocus();
    }

    public void appendText(String a) {
        EditText editor = (EditText) findViewById(R.id.tweetContent);

        a = editor.getText().toString() + a;
        if (editor.getText().charAt(editor.getText().length() - 1) != ' ') {
            a = " " + a;
        }

        editor.setText(a);
    }

    HashMap<String, String> autocomplete;
    ScheduledThreadPoolExecutor timer;

    public void setupAutocomplete() {

        final EditText editor = (EditText) findViewById(R.id.tweetContent);
        final LinearLayout l = (LinearLayout) findViewById(R.id.autocompletion);
        // final ScrollView sc = (ScrollView)findViewById(R.id.scroll);
        l.removeAllViews();
        autocomplete = new HashMap<String, String>();
        JSONObject ja = AutocompleteService.readAutocompleteFile(this,
                stt.from.getId());
        if (ja == null)
            return;

        try {
            @SuppressWarnings("rawtypes")
            Iterator i = ja.keys();
            while (i.hasNext()) {
                String key = (String) i.next();
                autocomplete.put(key, ja.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        timer = new ScheduledThreadPoolExecutor(1);
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable arg0) {
            }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1,
                                          int arg2, int arg3) {
            }

            @Override
            public void onTextChanged(CharSequence text, int s, int before,
                                      int count) {
                timer.shutdownNow();
                timer = new ScheduledThreadPoolExecutor(1);
                l.removeAllViews();

                timer.schedule(new Runnable() {
                    @Override
                    public void run() {
                        final boolean b = doRun();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                l.setVisibility(b ? View.VISIBLE : View.GONE);
                            }
                        });
                    }

                    public boolean doRun() {
                        String text = editor.getText().toString();
                        int start = editor.getSelectionStart();

                        final int p = text.lastIndexOf(" ", start);
                        if ((p + 2) >= text.length())
                            return false;
                        Log.d("autocomplete", text.charAt(p + 1) + "");

                        if (text.charAt(p + 1) == '@') {
                            // We are typing @someone
                            String typed = text.subSequence(p + 1, start)
                                    .toString().toLowerCase();
                            if (typed.length() < 2)
                                return false;
                            if (typed.charAt(0) == '@')
                                typed = typed.substring(1);

                            List<String> added = new ArrayList<String>();
                            final List<View> tbA = new ArrayList<View>();

                            boolean r = false;
                            for (final String u : autocomplete.keySet()) {
                                if (u.toLowerCase().contains(typed)) {
                                    r = true;
                                    if (added.contains(autocomplete.get(u))) continue;
                                    added.add(autocomplete.get(u));

                                    final LinearLayout item = (LinearLayout) getLayoutInflater()
                                            .inflate(
                                                    R.layout.autocomplete_item,
                                                    null);
                                    item.setOnClickListener(new OnClickListener() {
                                        @Override
                                        public void onClick(View arg0) {
                                            int start = editor
                                                    .getSelectionStart();
                                            EditText editor = (EditText) findViewById(R.id.tweetContent);
                                            String r = "@"
                                                    + autocomplete.get(u) + " ";
                                            editor.getText().replace(p + 1,
                                                    start, r);
                                            editor.setSelection(p + 1
                                                    + r.length());
                                        }
                                    });

                                    final NetworkedCacheableImageView riv = (NetworkedCacheableImageView) item
                                            .findViewById(R.id.image);
                                    final TextView t = (TextView) item
                                            .findViewById(R.id.name);
                                    SpannableString s = new SpannableString(u);
                                    int selStart = u.toLowerCase().indexOf(
                                            typed);
                                    s.setSpan(
                                            new StyleSpan(Typeface.BOLD),
                                            selStart,
                                            selStart + typed.length(),
                                            SpannableString.SPAN_INCLUSIVE_INCLUSIVE);
                                    t.setText(s);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            riv.loadImage(Utilities.getUserImage(autocomplete.get(u), ComposerScreen.this), false);
                                            tbA.add(item);
                                        }
                                    });
                                }
                            }

                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    for (View v : tbA) {
                                        l.addView(v);
                                    }
                                }

                            });
                            return r;
                        }
                        return false;
                    }
                }, 500, TimeUnit.MILLISECONDS);
            }
        });
    }

    private void initializeAccountSwitcher(boolean firstLoad) {
        ActionBar ab = getActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        if (AccountService.getAccounts().size() > 1) {
            ab.setDisplayShowTitleEnabled(false);
            ab.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            final ArrayList<Account> accs = AccountService.getAccounts();
            ArrayList<String> screenNames = new ArrayList<String>();
            for (Account a : accs)
                screenNames.add("@" + a.getUser().getScreenName());
            ArrayAdapter<String> adapt = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, screenNames);
            adapt.setDropDownViewResource(R.layout.spinner_item_actionbar);
            ab.setListNavigationCallbacks(adapt, new OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(int itemPosition,
                                                        long itemId) {
                    stt.from = accs.get(itemPosition);
                    loadDrafts();
                    setupAutocomplete();
                    return true;
                }
            });
            if (firstLoad) {
                stt.from = AccountService.getCurrentAccount();
                loadDrafts();
            }
            long accExtra = getIntent().getLongExtra("account", 0l);
            for (int i = 0; i < accs.size(); i++) {
                if (accs.get(i).getId() == stt.from.getId()
                        || accs.get(i).getId() == accExtra) {
                    getActionBar().setSelectedNavigationItem(i);
                    break;
                }
            }
        } else if (firstLoad) {
            stt.from = AccountService.getCurrentAccount();
            loadDrafts();
        }
    }

    private int getLengthIndicator() {
        int shortLength = AccountService.configShortURLLength;
        String text = ((EditText) findViewById(R.id.tweetContent)).getText()
                .toString();
        int toReturn = (140 - text.length());
        if (stt.hasMedia())
            toReturn -= (stt.mediaService.equals("twitter") ? AccountService.charactersPerMedia
                    : shortLength);
        List<String> urls = new Extractor().extractURLs(text);
        for (String u : urls) {
            if (!shownLinksMessage) {
                shownLinksMessage = true;
                Toast.makeText(getApplicationContext(),
                        getString(R.string.links_shortened), Toast.LENGTH_SHORT)
                        .show();
            }
            toReturn += (u.length() - shortLength);
        }
        lengthIndic = toReturn;
        if (toReturn > 140) {
            toReturn = 140;
            lengthIndic = 140;
            // TODO RESHOW MEDIA IF ATTACHED BEFORE
        }
        if (toReturn < 0) {
            findViewById(R.id.twitlongerUsed).setVisibility(View.VISIBLE);
            invalidateOptionsMenu();
        } else {
            findViewById(R.id.twitlongerUsed).setVisibility(View.GONE);
            invalidateOptionsMenu();
        }
        invalidateOptionsMenu();
        return toReturn;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lastTheme == 0)
            lastTheme = Utilities.getTheme(getApplicationContext());
        else if (lastTheme != Utilities.getTheme(getApplicationContext())) {
            lastTheme = Utilities.getTheme(getApplicationContext());
            recreate();
        }
    }

    @Override
    public void onBackPressed() {
        saveDraft();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        boid.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (findViewById(R.id.tweetContent) == null) return true;

        getMenuInflater().inflate(R.menu.composer_actionbar, menu);
        if (stt.in_reply_to > 0) {
            menu.findItem(R.id.sendAction).setTitle(
                    getString(R.string.reply_str) + " ("
                            + Integer.toString(lengthIndic) + ")");
        } else {
            menu.findItem(R.id.sendAction).setTitle(
                    getString(R.string.tweet_str) + " ("
                            + Integer.toString(lengthIndic) + ")");
        }

        if (lengthIndic < 0) {
            menu.findItem(R.id.captureAction).setEnabled(false);
            menu.findItem(R.id.galleryAction).setEnabled(false);
        }
        // Check for camera
        PackageManager pm = getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // You can't take a photo if you don't have a camera!
            menu.findItem(R.id.captureAction).setVisible(false);
        }

        if (!stt.isGalleryImage && stt.hasMedia()) {
            MenuItem capAct = menu.findItem(R.id.captureAction);
            capAct.setIcon(getTheme().obtainStyledAttributes(
                    new int[]{R.attr.cameraAttachedIcon}).getDrawable(0));
        } else if (stt.hasMedia()) { // could be uri
            MenuItem galAct = menu.findItem(R.id.galleryAction);
            galAct.setIcon(getTheme().obtainStyledAttributes(
                    new int[]{R.attr.galleryAttachedIcon}).getDrawable(0));
        }

        final EditText content = (EditText) findViewById(R.id.tweetContent);
        if (stt.attachedImage == null
                && content.getText().toString().trim().length() == 0) {
            menu.findItem(R.id.sendAction).setEnabled(false);
        } else {
            menu.findItem(R.id.sendAction).setEnabled(true);
        }

        final MenuItem locate = menu.findItem(R.id.locateAction);
        locate.getSubMenu().clear();

        if (stt.location != null) {
            locate.setIcon(getTheme().obtainStyledAttributes(
                    new int[]{R.attr.locationAttachedIcon}).getDrawable(0));
            if (places == null) {
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            places = AccountService
                                    .getCurrentAccount()
                                    .getClient()
                                    .reverseGeoCode(new GeoQuery(stt.location).accuracy((int) locationAccuracy + "m").granularity("poi"));
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    invalidateOptionsMenu();
                                }
                            });
                        } catch (final Exception e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(),
                                            e.getMessage(), Toast.LENGTH_LONG)
                                            .show();
                                }
                            });
                        }
                    }
                }).start();
            } else {
                for (Place p : places) {
                    locate.getSubMenu().add(p.getFullName())
                            .setIcon(R.drawable.locate_blue);
                }
                locate.getSubMenu()
                        .add(R.string.no_location_str)
                        .setIcon(
                                getTheme()
                                        .obtainStyledAttributes(
                                                new int[]{R.attr.locationDetachedIcon})
                                        .getDrawable(0));
                if (stt.placeId == null) {
                    stt.placeId = places.get(0).getId();
                    Toast.makeText(getApplicationContext(), places.get(0).getFullName(), Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            locate.setIcon(getTheme().obtainStyledAttributes(
                    new int[]{R.attr.locationDetachedIcon}).getDrawable(0));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                saveDraft();
                return true;
            case R.id.locateAction:
                if (stt.location == null) {
                    getLocation();
                }
                return true;
            case R.id.sendAction:
                if (PreferenceManager.getDefaultSharedPreferences(
                        getApplicationContext()).getBoolean("confirm_send_prompt",
                        false)) {
                    AlertDialog.Builder diag = new AlertDialog.Builder(this);
                    diag.setTitle(R.string.tweet_str);
                    diag.setMessage(getString(R.string.confirm_send_prompt)
                            .replace("{account}",
                                    stt.from.getUser().getScreenName()));
                    diag.setPositiveButton(R.string.yes_str,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    dialog.dismiss();
                                    performSend();
                                }
                            });
                    diag.setNegativeButton(R.string.no_str,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    dialog.dismiss();
                                }
                            });
                    diag.create().show();
                } else {
                    performSend();
                }
                return true;
            case R.id.captureAction:
                if (stt.attachedImage != null) {
                    stt.attachedImage = null;
                    stt.attachedImageUri = null;
                    getLengthIndicator();
                } else
                    captureImage();
                return true;
            case R.id.galleryAction:
                if (stt.attachedImage != null) {
                    stt.attachedImage = null;
                    stt.attachedImageUri = null;
                    getLengthIndicator();
                } else
                    selectImage();
                return true;
            default:
                if (item.getTitle().equals(getString(R.string.no_location_str))) {
                    stt.location = null;
                    stt.placeId = null;
                    places = null;
                    invalidateOptionsMenu();
                    return true;
                } else {
                    for (Place loc : places) {
                        if (loc.getFullName().equals(item.getTitle().toString())) {
                            stt.placeId = loc.getId();
                            break;
                        }
                    }
                    return true;
                }
        }
    }

    private void getLocation() {
        if (isGettingLocation)
            return;
        isGettingLocation = true;
        setProgressBarIndeterminateVisibility(true);
        final LocationManager locationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                locationManager.removeUpdates(this);
                isGettingLocation = false;
                locationAccuracy = location.getAccuracy();
                stt.location = new GeoLocation(location.getLatitude(),
                        location.getLongitude());
                setProgressBarIndeterminateVisibility(false);
                invalidateOptionsMenu();
            }

            public void onStatusChanged(String provider, int status,
                                        Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };
        locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }

    private void performSend() {
        if (getLengthIndicator() < 0)
            stt.twtlonger = true;
        stt.contents = ((EditText) findViewById(R.id.tweetContent)).getText()
                .toString();
        stt.replyToName = getIntent().getStringExtra("reply_to_name");
        SendTweetService.addTweet(stt);
        if (draftIndex > -1) {
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());
            final ArrayList<String> draftStore = Utilities.jsonToArray(prefs
                    .getString(stt.from.getId() + "_stt_draft", null));
            draftStore.remove(draftIndex);
            if (draftStore.size() > 0) {
                prefs.edit()
                        .putString(stt.from.getId() + "_stt_draft",
                                Utilities.arrayToJson(draftStore)).commit();
            } else {
                prefs.edit().remove(stt.from.getId() + "_stt_draft").commit();
            }
        }
        finish();
    }

    public static int getFileSize(File in) {
        try {
            FileInputStream fis = new FileInputStream(in);
            int r = fis.available();
            fis.close();
            return r;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static final int CAMERA_SELECT_INTENT = 500;
    public static final int GALLERY_SELECT_INTENT = 600;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            if (requestCode == GALLERY_SELECT_INTENT
                    || requestCode == CAMERA_SELECT_INTENT) {
                if (getFileSize(new File(stt.attachedImage)) == 0) {
                    Log.d("e", "Empty File. Using "
                            + intent.getData().toString());
                    stt.attachedImageUri = intent.getData();
                }
            }
            getLengthIndicator();
            invalidateOptionsMenu();
        } else if (resultCode == RESULT_CANCELED) {
            File attachedCapture = new File(stt.attachedImage);
            if (attachedCapture.exists())
                attachedCapture.delete();
            stt.attachedImage = null;
        }
    }

    private void captureImage() {
        if (!Utilities.isIntentAvailable(this, MediaStore.ACTION_IMAGE_CAPTURE)) {
            Toast.makeText(getApplicationContext(), R.string.no_camera_app,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        stt.isGalleryImage = false;
        stt.attachedImage = Utilities.generateImageFileName(this);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                Uri.fromFile(new File(stt.attachedImage)));
        startActivityForResult(takePictureIntent, CAMERA_SELECT_INTENT);
    }

    private void selectImage() {
        try {
            stt.isGalleryImage = true;
            stt.attachedImage = Utilities.createImageFile(this)
                    .getAbsolutePath();
            Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT, null)
                    .setType("image/*")
                    .putExtra(MediaStore.EXTRA_OUTPUT,
                            Uri.parse(stt.attachedImage))
                    .putExtra("outputFormat", Bitmap.CompressFormat.PNG.name());
            startActivityForResult(galleryIntent, GALLERY_SELECT_INTENT);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), e.getLocalizedMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}