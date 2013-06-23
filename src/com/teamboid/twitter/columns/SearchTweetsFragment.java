package com.teamboid.twitter.columns;

import android.content.Intent;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import com.teamboid.twitter.Account;
import com.teamboid.twitter.ComposerScreen;
import com.teamboid.twitter.R;
import com.teamboid.twitter.TabsAdapter.BaseListFragment;
import com.teamboid.twitter.TweetViewer;
import com.teamboid.twitter.listadapters.FeedListAdapter;
import com.teamboid.twitter.listadapters.MessageConvoAdapter.DMConversation;
import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitter.utilities.Utilities;
import twitter4j.Query;
import twitter4j.Status;

import java.util.ArrayList;

/**
 * Represents the column used in the search screen that displays Tweet search
 * results.
 *
 * @author Aidan Follestad
 */
public class SearchTweetsFragment extends BaseListFragment<Status> {

    private String query;
    public static final String ID = "COLUMNTYPE:SEARCH";

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Status tweet = getAdapter().getItem(position);
        getActivity().startActivity(new Intent(getActivity(), TweetViewer.class)
                .putExtra("tweet_id", id)
                .putExtra("user_name", tweet.getUser().getName())
                .putExtra("user_id", tweet.getUser().getId())
                .putExtra("screen_name", tweet.getUser().getScreenName())
                .putExtra("content", tweet.getText())
                .putExtra("timer", tweet.getCreatedAt().getTime())
                .putExtra("via", tweet.getSource())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override
    public void onStart() {
        query = getArguments().getString("query");

        super.onStart();
        getListView().setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> arg0,
                                                   View arg1, int index, long id) {
                        Status toReply = getAdapter().getItem(index);
                        getActivity().startActivity(new Intent(getActivity(),
                                ComposerScreen.class)
                                .putExtra("reply_to_tweet", toReply)
                                .putExtra("reply_to_name", toReply.getUser().getScreenName())
                                .putExtra("append", Utilities.getAllMentions(toReply.getUser().getScreenName(), toReply.getText()))
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                        return false;
                    }
                });
        setRetainInstance(true);
        setEmptyText(getString(R.string.no_results));
    }

    @Override
    public Status[] getSelectedStatuses() {
        ArrayList<Status> toReturn = new ArrayList<Status>();
        SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();
        if (checkedItems != null) {
            for (int i = 0; i < checkedItems.size(); i++) {
                if (checkedItems.valueAt(i)) {
                    toReturn.add(getAdapter().getItem(checkedItems.keyAt(i)));
                }
            }
        }
        return toReturn.toArray(new Status[0]);
    }

    @Override
    public DMConversation[] getSelectedMessages() {
        return null;
    }

    @Override
    public String getColumnName() {
        return AccountService.getCurrentAccount().getId() + ".saved-" + query.replace("/", "_");
    }

    @Override
    public void setupAdapter() {
        if (AccountService.getCurrentAccount() != null) {
            if (getAdapter() == null) {
                setListAdapter(new FeedListAdapter(getActivity(), SearchTweetsFragment.ID, AccountService.getCurrentAccount()));
            }
        }
    }

    @Override
    public Status[] fetch(long maxId, long sinceId) {
        try {
            final Account acc = AccountService.getCurrentAccount();
            if (acc != null) {
                return acc.getClient().search(new Query(query).maxId(maxId).sinceId(sinceId).count(50)).getTweets().toArray(new Status[0]);
            } else {
                throw new Exception("Account Error");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError(e.getMessage());
        }
        return null;
    }
}
