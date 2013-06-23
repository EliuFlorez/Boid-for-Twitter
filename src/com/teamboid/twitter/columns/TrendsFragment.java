package com.teamboid.twitter.columns;

import android.content.Intent;
import android.view.View;
import android.widget.ListView;
import com.teamboid.twitter.Account;
import com.teamboid.twitter.SearchScreen;
import com.teamboid.twitter.TabsAdapter;
import com.teamboid.twitter.listadapters.MessageConvoAdapter.DMConversation;
import com.teamboid.twitter.services.AccountService;
import twitter4j.Status;
import twitter4j.Trend;
import twitter4j.Trends;

/**
 * Represents the column that displays current trends.
 *
 * @author Aidan Follestad
 */
public class TrendsFragment extends TabsAdapter.BaseListFragment<Trend> {

    public static String ID = "COLUMNTYPE:TRENDS";

    @Override
    public String getColumnName() {
        return ID;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        getActivity().startActivity(new Intent(getActivity(), SearchScreen.class)
                .putExtra("query", getAdapter().getItem(position).getQuery())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override
    public Trend[] fetch(long maxId, long sinceId) {
        try {
            Account acc = AccountService.getCurrentAccount();
            Trends trends = acc.getClient().getPlaceTrends(1);
            //TODO location based trends
            return trends.getTrends();
        } catch (Exception e) {
            e.printStackTrace();
            showError(e.getMessage());
        }
        return null;
    }

    @Override
    public Status[] getSelectedStatuses() {
        return null;
    }

    @Override
    public DMConversation[] getSelectedMessages() {
        return null;
    }

    @Override
    public void setupAdapter() {
        setListAdapter(AccountService.getTrendsAdapter(getActivity()));
    }
}