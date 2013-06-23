package com.teamboid.twitter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import com.teamboid.twitter.columns.*;
import com.teamboid.twitter.listadapters.ColumnManagerAdapter;
import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitter.utilities.Utilities;
import com.teamboid.twitter.views.DragSortListView;
import com.teamboid.twitter.views.DragSortListView.DropListener;
import twitter4j.ResponseList;
import twitter4j.SavedSearch;
import twitter4j.UserList;

import java.io.*;
import java.util.ArrayList;

public class ColumnManager extends Activity {

    private ColumnManagerAdapter adapt;
    private int lastTheme;

    private DropListener dropListen = new DropListener() {
        @Override
        public void drop(int from, int to) {
            adapt.moveColumn(from, to);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("lastTheme")) {
                lastTheme = savedInstanceState.getInt("lastTheme");
                setTheme(lastTheme);
            } else setTheme(Utilities.getTheme(getApplicationContext()));
        } else setTheme(Utilities.getTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.column_manager);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        adapt = new ColumnManagerAdapter(this);
        final DragSortListView list = (DragSortListView) findViewById(android.R.id.list);
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int index, long id) {
                adapt.removeColumn(index);
                return false;
            }
        });
        list.setDropListener(dropListen);
        list.setAdapter(adapt);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt("lastTheme", lastTheme);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lastTheme == 0) lastTheme = Utilities.getTheme(getApplicationContext());
        else if (lastTheme != Utilities.getTheme(getApplicationContext())) {
            lastTheme = Utilities.getTheme(getApplicationContext());
            recreate();
            return;
        }
        adapt.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        finish();
        startActivity(new Intent(this, TimelineScreen.class).putExtra("restart", true));
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.column_manager_ab, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                startActivity(new Intent(this, TimelineScreen.class).putExtra("restart", true));
                return true;
            case R.id.backupBtn:
                backup();
                return true;
            case R.id.restoreBtn:
                restore();
                return true;
            case R.id.resetBtn:
                adapt.resetColumns();
                return true;
            case R.id.addTimelineColAction:
                adapt.addColumn(TimelineFragment.ID, -1);
                return true;
            case R.id.addMentionsColAction:
                adapt.addColumn(MentionsFragment.ID, -1);
                return true;
            case R.id.addMessagesColAction:
                adapt.addColumn(MessagesFragment.ID, -1);
                return true;
            case R.id.addTrendsColAction:
                adapt.addColumn(TrendsFragment.ID, -1);
                return true;
            case R.id.addSavedSearchColAction:
                Toast.makeText(getApplicationContext(), getString(R.string.loading_savedsearches), Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    public void run() {
                        Account acc = AccountService.getCurrentAccount();
                        try {
                            final ResponseList<SavedSearch> lists = acc.getClient().getSavedSearches();
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    showSavedSearchColumnAdd(lists);
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    showSavedSearchColumnAdd(null);
                                }
                            });
                        }
                    }
                }).start();
                return true;
            case R.id.addFavoritesColAction:
                adapt.addColumn(FavoritesFragment.ID, -1);
                return true;
            case R.id.addUserListColAction:
                Toast.makeText(getApplicationContext(), getString(R.string.loading_lists), Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    public void run() {
                        Account acc = AccountService.getCurrentAccount();
                        try {
                            final ResponseList<UserList> lists = acc.getClient().getUserLists(acc.getId());
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    showUserListColumnAdd(lists);
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(getApplicationContext(), getString(R.string.failed_load_lists), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
                return true;
            case R.id.addMyListsColAction:
                adapt.addColumn(MyListsFragment.ID, -1);
                return true;
            case R.id.addProfileFeedColAction:
                showProfileFeedColumnAdd();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showUserListColumnAdd(final ResponseList<UserList> lists) {
        if (lists == null) return;
        else if (lists.size() == 0) {
            Toast.makeText(getBaseContext(), getString(R.string.no_lists), Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIconAttribute(R.attr.cloudIcon);
        builder.setTitle(R.string.lists_str);
        ArrayList<String> items = new ArrayList<String>();
        for (UserList l : lists) items.add(l.getFullName());
        builder.setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                UserList curList = lists.get(item);
                adapt.addColumn(UserListFragment.ID + "@" + curList.getFullName().replace("@", "%40") +
                        "@" + Long.toString(curList.getId()), -1);
            }
        });
        builder.create().show();
    }

    private void showSavedSearchColumnAdd(final ResponseList<SavedSearch> lists) {
        final Dialog diag = new Dialog(this);
        diag.setTitle(R.string.savedsearch_str);
        diag.setCancelable(true);
        diag.setContentView(R.layout.savedsearch_dialog);
        ArrayList<String> items = new ArrayList<String>();
        for (SavedSearch l : lists) items.add(l.getName());

        final ListView list = (ListView) diag.findViewById(android.R.id.list);
        final EditText input = (EditText) diag.findViewById(android.R.id.input);
        final Button addBtn = (Button) diag.findViewById(R.id.addBtn);

        list.setAdapter(new ArrayAdapter<String>(this, R.layout.trends_list_item, items));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int index, long id) {
                input.setText(lists.get(index).getName());
            }
        });

        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String query = input.getText().toString().trim();
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            AccountService.getCurrentAccount().getClient().createSavedSearch(query);
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(getApplicationContext(), R.string.savedsearch_upload_error, Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(getApplicationContext(), R.string.savedsearch_uploaded, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }).start();
                adapt.addColumn(SearchTweetsFragment.ID + "@" + query.replace("@", "%40"), -1);
                diag.dismiss();
            }
        });

        diag.show();
    }

    private void showProfileFeedColumnAdd() {
        final Dialog diag = new Dialog(this);
        diag.setTitle(R.string.user_timeline_str);
        diag.setCancelable(true);
        diag.setContentView(R.layout.savedsearch_dialog);
        diag.findViewById(android.R.id.list).setVisibility(View.GONE);
        final EditText input = (EditText) diag.findViewById(android.R.id.input);
        final Button addBtn = (Button) diag.findViewById(R.id.addBtn);
        input.setHint(R.string.screen_name_str);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String query = input.getText().toString().trim();
                diag.dismiss();
                adapt.addColumn(ProfileTimelineFragment.ID + "@" + query.replace("@", ""), -1);
            }
        });
        diag.show();
    }

    public void backup() {
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(new File(
                    Environment.getExternalStorageDirectory(), "Boid_ColumnsBackup_" +
                    AccountService.getCurrentAccount().getId() + ".txt").getAbsolutePath()));
            ArrayList<String> cols = adapt.getColumns();
            for (String key : cols) {
                buf.write(key);
                buf.newLine();
            }
            buf.flush();
            buf.close();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(getApplicationContext(), R.string.backed_up_columns, Toast.LENGTH_SHORT).show();
    }

    public void restore() {
        File fi = new File(Environment.getExternalStorageDirectory(), "Boid_ColumnsBackup_" +
                AccountService.getCurrentAccount().getId() + ".txt");
        if (!fi.exists()) {
            fi = new File(Environment.getExternalStorageDirectory(), "Boid_ColumnsBackup.txt");
            if (!fi.exists()) {
                Toast.makeText(getApplicationContext(), R.string.no_column_backup, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        try {
            BufferedReader buf = new BufferedReader(new FileReader(fi.getAbsolutePath()));
            ArrayList<String> cols = new ArrayList<String>();
            while (true) {
                String line = buf.readLine();
                if (line == null) break;
                else if (line.isEmpty()) break;
                cols.add(line);
            }
            adapt.setColumns(cols);
            buf.close();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(getApplicationContext(), R.string.restored_columns, Toast.LENGTH_SHORT).show();
    }

}
