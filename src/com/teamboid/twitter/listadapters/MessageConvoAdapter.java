package com.teamboid.twitter.listadapters;

import android.app.Activity;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.teamboid.twitter.ProfileScreen;
import com.teamboid.twitter.R;
import com.teamboid.twitter.TabsAdapter.BoidAdapter;
import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitter.utilities.Utilities;
import com.teamboid.twitter.views.NetworkedCacheableImageView;
import twitter4j.DirectMessage;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * The list adapter used for the messages tab, displays a list of conversations
 * that contain messages.
 *
 * @author Aidan Follestad
 */
public class MessageConvoAdapter extends BoidAdapter<MessageConvoAdapter.DMConversation> {

    public static class DMConversation implements Serializable {
        private static final long serialVersionUID = -8519031809073902285L;

        public DMConversation(Long toId, String toName, String toScreenName,
                              DirectMessage initMsg) {
            _toId = toId;
            _toName = toName;
            _toScreen = toScreenName;
            messages = new ArrayList<DirectMessage>();
            messages.add(initMsg);
        }

        private long _toId;
        private String _toName;
        private String _toScreen;
        private ArrayList<DirectMessage> messages;

        public long getToId() {
            return _toId;
        }

        public String getToName() {
            return _toName;
        }

        public String getToScreenName() {
            return _toScreen;
        }

        public DirectMessage getLastMessage() {
            if (messages.size() == 0)
                return null;
            return messages.get(messages.size() - 1);
        }

        public long getLastSender() {
            if (getLastMessage() == null)
                return 0;
            return getLastMessage().getSenderId();
        }

        public boolean getLastSenderIsMe() {
            return (getLastSender() == AccountService.getCurrentAccount()
                    .getId());
        }

        public ArrayList<DirectMessage> getMessages() {
            return messages;
        }

        public void add(DirectMessage msg) {
            if (!contains(msg))
                messages.add(findAppropIndex(msg), msg);
        }

        private boolean contains(DirectMessage toFind) {
            Boolean found = false;
            ArrayList<DirectMessage> itemCache = messages;
            for (DirectMessage msg : itemCache) {
                if (msg.getId() == toFind.getId()) {
                    found = true;
                    break;
                }
            }
            return found;
        }

        private int findAppropIndex(DirectMessage msg) {
            int toReturn = 0;
            ArrayList<DirectMessage> itemCache = messages;
            for (DirectMessage t : itemCache) {
                if (t.getCreatedAt().after(msg.getCreatedAt()))
                    break;
                toReturn++;
            }
            return toReturn;
        }

        public void remove(long id, MessageConvoAdapter adapt) {
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).getId() == id) {
                    messages.remove(i);
                    break;
                }
            }
            adapt.notifyDataSetChanged();
        }
    }

    public MessageConvoAdapter(Activity _context, long _account) {
        super(_context, null, null);
        context = _context;
        items = new ArrayList<DMConversation>();
        account = _account;
    }

    private Activity context;
    private ArrayList<DMConversation> items;
    public ListView list;
    public long account;

    public void add(DMConversation[] convos) {
        for (DMConversation c : convos) {
            if (!update(c))
                items.add(c);
        }
        notifyDataSetChanged();
    }

    public int add(final DirectMessage[] msges) {
        int before = items.size();
        int added = 0;
        for (DirectMessage msg : msges) {
            boolean foundConvo = false;
            for (DMConversation convo : items) {
                if (msg.getSenderId() == AccountService.getCurrentAccount().getId()) {
                    if (msg.getRecipientId() == convo.getToId())
                        foundConvo = true;
                } else if (msg.getSenderId() == convo.getToId())
                    foundConvo = true;
                if (foundConvo) {
                    added++;
                    boolean found = false;
                    for (DirectMessage m : convo.getMessages()) {
                        if (m.getId() == msg.getId()) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) convo.add(msg);
                    break;
                }
            }
            if (!foundConvo) {
                long toId = msg.getSenderId();
                String toName = msg.getSender().getName();
                String toScreen = msg.getSender().getScreenName();
                if (toId == AccountService.getCurrentAccount().getId()) {
                    toId = msg.getRecipientId();
                    toName = msg.getRecipient().getName();
                    toScreen = msg.getRecipient().getScreenName();
                }
                items.add(new DMConversation(toId, toName, toScreen, msg));
            }
            notifyDataSetChanged();
        }
        if (before == 0)
            return added;
        else if (added == before)
            return 0;
        else
            return (items.size() - before);
    }

    public DMConversation find(String screenName) {
        DMConversation toReturn = null;
        for (DMConversation convo : items) {
            if (convo.getToScreenName().equals(screenName)) {
                toReturn = convo;
                break;
            }
        }
        return toReturn;
    }

    public boolean update(DMConversation convo) {
        boolean found = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getToId() == convo.getToId()) {
                found = true;
                items.set(i, convo);
                notifyDataSetChanged();
                break;
            }
        }
        return found;
    }

    public void remove(int index) {
        items.remove(index);
        notifyDataSetChanged();
    }

    public void remove(DMConversation convo) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getToId() == convo.getToId()) {
                items.remove(i);
                notifyDataSetChanged();
                break;
            }
        }
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public DMConversation[] toArray() {
        return items.toArray(new DMConversation[0]);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public DMConversation getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getToId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RelativeLayout toReturn = null;
        if (convertView != null)
            toReturn = (RelativeLayout) convertView;
        else
            toReturn = (RelativeLayout) LayoutInflater.from(context).inflate(
                    R.layout.dm_convo_item, null);
        final DMConversation curItem = items.get(position);
        NetworkedCacheableImageView profileImgView = (NetworkedCacheableImageView) toReturn
                .findViewById(R.id.dmConvoProfileImg);
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                "enable_profileimg_download", true)) {
            profileImgView.setImageResource(R.drawable.sillouette);
            profileImgView.loadImage(Utilities.getUserImage(curItem.getToScreenName(), context), false);
            profileImgView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    context.startActivity(new Intent(context,
                            ProfileScreen.class)
                            .putExtra("screen_name", curItem.getToScreenName())
                            .putExtra("account",
                                    AccountService.getCurrentAccount().getId())
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                }
            });
        } else
            profileImgView.setVisibility(View.GONE);
        final TextView messageTxt = (TextView) toReturn
                .findViewById(R.id.dmConvoMessageTxt);
        if (curItem.getLastSenderIsMe()) {
            View replyIndic = toReturn
                    .findViewById(R.id.dmConvoReplyIndicator);
            replyIndic.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) replyIndic
                    .getLayoutParams();
            layoutParams.addRule(RelativeLayout.RIGHT_OF,
                    R.id.dmConvoProfileImg);
            layoutParams.addRule(RelativeLayout.BELOW,
                    R.id.dmConvoScreenNameTxt);
            layoutParams = (RelativeLayout.LayoutParams) messageTxt
                    .getLayoutParams();
            layoutParams.addRule(RelativeLayout.RIGHT_OF,
                    R.id.dmConvoReplyIndicator);
            layoutParams.addRule(RelativeLayout.BELOW,
                    R.id.dmConvoScreenNameTxt);
            messageTxt.setLayoutParams(layoutParams);
        } else {
            (toReturn.findViewById(R.id.dmConvoReplyIndicator))
                    .setVisibility(View.GONE);
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) messageTxt
                    .getLayoutParams();
            layoutParams.addRule(RelativeLayout.RIGHT_OF,
                    R.id.dmConvoProfileImg);
            layoutParams.addRule(RelativeLayout.BELOW,
                    R.id.dmConvoScreenNameTxt);
            messageTxt.setLayoutParams(layoutParams);
        }
        FeedListAdapter.ApplyFontSize(messageTxt, context);
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                "show_real_names", false)) {
            ((TextView) toReturn.findViewById(R.id.dmConvoScreenNameTxt))
                    .setText(curItem.getToName());
        } else {
            ((TextView) toReturn.findViewById(R.id.dmConvoScreenNameTxt))
                    .setText("@" + curItem.getToScreenName());
        }
        messageTxt.setText(Utilities.twitterifyText(context, curItem
                .getLastMessage().getText().replace("\n", " ").trim(), null,
                null, true, null));
        return toReturn;
    }

    @Override
    public int getPosition(long id) {
        for (int i = 0; i <= this.getCount() - 1; i++) {
            if (getItem(i).getToId() == id) {
                return i;
            }
        }
        return -1;
    }
}