package com.teamboid.twitter.columns;

import android.content.Intent;
import android.view.View;
import android.widget.ListView;
import com.teamboid.twitter.ProfileScreen;
import com.teamboid.twitter.TabsAdapter.BaseListFragment;
import com.teamboid.twitter.listadapters.MessageConvoAdapter.DMConversation;
import com.teamboid.twitter.listadapters.SearchUsersListAdapter;
import com.teamboid.twitter.services.AccountService;
import twitter4j.Status;
import twitter4j.User;

/**
 * The column used in the search screen to display User search results.
 *
 * @author Aidan Follestad
 */
public class SearchUsersFragment extends BaseListFragment<User> {
    private String query;
    int page = 1;

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        getActivity().startActivity(new Intent(getActivity(), ProfileScreen.class)
                .putExtra("screen_name", ((User) getListAdapter().getItem(position)).getScreenName())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override
    public void onStart() {
        query = getArguments().getString("query");
        super.onStart();
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
    public String getColumnName() {
        return "n/a";
    }

    @Override
    public void setupAdapter() {
        if (AccountService.getCurrentAccount() != null) {
            if (getListAdapter() == null)
                setListAdapter(new SearchUsersListAdapter(getActivity()));
        }
    }

    @Override
    public User[] fetch(long maxId, long sinceId) {
        try {
            if (maxId != -1)
                page++;
            else
                page = 1;
            return AccountService.getCurrentAccount().getClient().searchUsers(query, page).toArray(new User[0]);
        } catch (Exception e) {
            e.printStackTrace();
            showError(e.getMessage());
            return null;
        }
    }
}
