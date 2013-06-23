package com.teamboid.twitter.listadapters;

import android.content.Context;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.teamboid.twitter.R;
import com.teamboid.twitter.TabsAdapter.BoidAdapter;
import com.teamboid.twitter.utilities.Utilities;
import com.teamboid.twitter.views.NetworkedCacheableImageView;
import twitter4j.ResponseList;
import twitter4j.User;

import java.util.ArrayList;

/**
 * The list adapter used for activities that search for users.
 *
 * @author Aidan Follestad
 */
public class SearchUsersListAdapter extends BoidAdapter<User> {

    public SearchUsersListAdapter(Context context) {
        super(context, null, null);
        mContext = context;
        users = new ArrayList<User>();
    }

    private Context mContext;
    private ArrayList<User> users;
    public ListView list;

    public void add(User tweet) {
        if (!update(tweet)) {
            users.add(tweet);
        }
        notifyDataSetChanged();
    }

    public int add(ResponseList<User> toAdd) {
        int before = users.size();
        int added = 0;
        for (User user : toAdd) {
            add(user);
            added++;
        }
        if (before == 0) return added;
        else if (added == before) return 0;
        else return (users.size() - before);
    }

    public void remove(int index) {
        users.remove(index);
        notifyDataSetChanged();
    }

    public void clear() {
        users.clear();
        notifyDataSetChanged();
    }

    public User[] toArray() {
        return users.toArray(new User[0]);
    }

    public Boolean update(User toFind) {
        Boolean found = false;
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getId() == toFind.getId()) {
                found = true;
                users.set(i, toFind);
                notifyDataSetChanged();
                break;
            }
        }
        return found;
    }

    @Override
    public int getCount() {
        return users.size();
    }

    @Override
    public User getItem(int position) {
        return users.get(position);
    }

    @Override
    public long getItemId(int position) {
        return users.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RelativeLayout toReturn = null;
        if (convertView != null) toReturn = (RelativeLayout) convertView;
        else toReturn = (RelativeLayout) LayoutInflater.from(mContext).inflate(R.layout.user_list_item, null);
        final User user = (User) getItem(position);
        final NetworkedCacheableImageView profilePic = (NetworkedCacheableImageView) toReturn.findViewById(R.id.userItemProfilePic);
        profilePic.setImageResource(R.drawable.sillouette);
        profilePic.loadImage(user.getProfileImageURL(), false);
        TextView userName = (TextView) toReturn.findViewById(R.id.userItemName);
        FeedListAdapter.ApplyFontSize(userName, mContext);
        TextView userDesc = (TextView) toReturn.findViewById(R.id.userItemDescription);
        FeedListAdapter.ApplyFontSize(userDesc, mContext);
        if (PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("show_real_names", false)) {
            userName.setText(user.getName());
        } else {
            userName.setText("@" + user.getScreenName());
        }
        if (user.getDescription() != null && !user.getDescription().trim().isEmpty()) {
            userDesc.setText(Utilities.twitterifyText(mContext, user.getDescription().replace("\n", " ").trim(), null, null, false, null));
        } else userDesc.setText(mContext.getApplicationContext().getString(R.string.nodescription_str));
        if (user.isVerified()) ((ImageView) toReturn.findViewById(R.id.userItemVerified)).setVisibility(View.VISIBLE);
        else ((ImageView) toReturn.findViewById(R.id.userItemVerified)).setVisibility(View.GONE);
        return toReturn;
    }

    @Override
    public int getPosition(long id) {
        for (int i = 0; i <= this.getCount() - 1; i++) {
            if (getItem(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }
}