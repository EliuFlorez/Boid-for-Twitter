package com.teamboid.twitter.columns;

import android.content.Intent;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import com.teamboid.twitter.Account;
import com.teamboid.twitter.ConversationScreen;
import com.teamboid.twitter.R;
import com.teamboid.twitter.TabsAdapter.BaseListFragment;
import com.teamboid.twitter.cab.MessageConvoCAB;
import com.teamboid.twitter.listadapters.MessageConvoAdapter;
import com.teamboid.twitter.listadapters.MessageConvoAdapter.DMConversation;
import com.teamboid.twitter.services.AccountService;
import twitter4j.DirectMessage;
import twitter4j.ResponseList;
import twitter4j.Status;

import java.util.ArrayList;

/**
 * Represents the column that displays the current user's direct messaging conversations.
 *
 * @author Aidan Follestad
 */
public class MessagesFragment extends BaseListFragment<DMConversation> {

    public static final String ID = "COLUMNTYPE:MESSAGES";

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        startActivity(new Intent(getActivity(), ConversationScreen.class)
                .putExtra("screen_name", ((DMConversation) getAdapter().getItem(position)).getToScreenName())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override
    public void onStart() {
        super.onStart();
        getListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        getListView().setMultiChoiceModeListener(MessageConvoCAB.choiceListener);
        setRetainInstance(true);
        setEmptyText(getString(R.string.no_messages));
    }

    @Override
    public Status[] getSelectedStatuses() {
        return null;
    }

    @Override
    public DMConversation[] getSelectedMessages() {
        if (getAdapter() == null) return null;
        ArrayList<DMConversation> toReturn = new ArrayList<DMConversation>();
        SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();
        if (checkedItems != null) {
            for (int i = 0; i < checkedItems.size(); i++) {
                if (checkedItems.valueAt(i)) {
                    toReturn.add((DMConversation) getAdapter().getItem(checkedItems.keyAt(i)));
                }
            }
        }
        return toReturn.toArray(new DMConversation[0]);
    }

    @Override
    public String getColumnName() {
        return AccountService.getCurrentAccount().getId() + ".dm_list";
    }

    @Override
    public void setupAdapter() {
        if (AccountService.getCurrentAccount() != null) {
            MessageConvoAdapter adapt = AccountService.getMessageConvoAdapter(getActivity(),
                    AccountService.getCurrentAccount().getId());
            adapt.list = getListView();
            setListAdapter(adapt);
        }
    }

    @Override
    public DMConversation[] fetch(long maxId, long sinceId) {
        try {
            Account acc = AccountService.getCurrentAccount();

            final ArrayList<DirectMessage> messages = new ArrayList<DirectMessage>();
            ResponseList<DirectMessage> recv = acc.getClient().getDirectMessages();
            if (recv != null && recv.size() > 0) {
                for (DirectMessage msg : recv) messages.add(msg);
            }
            ResponseList<DirectMessage> sent = acc.getClient().getSentDirectMessages();
            if (sent != null && sent.size() > 0) {
                for (DirectMessage msg : sent) messages.add(msg);
            }

            // REALLY BAD!
            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    ((MessageConvoAdapter) getListAdapter()).add(messages.toArray(new DirectMessage[]{}));
                }

            });
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            showError(e.getMessage());
            return null;
        }
    }
}
