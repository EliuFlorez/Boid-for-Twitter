package com.teamboid.twitter.listadapters;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.teamboid.twitter.Account;
import com.teamboid.twitter.R;
import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitter.utilities.Utilities;
import com.teamboid.twitter.views.NetworkedCacheableImageView;
import twitter4j.User;

/**
 * The list adapter used in the account manager, displays the accounts currenetly added in the AccountService.
 *
 * @author Aidan Follestad
 */
public class AccountListAdapter extends BaseAdapter {

    public AccountListAdapter(Activity context) {
        mContext = context;
    }

    private Activity mContext;

    @Override
    public int getCount() {
        return AccountService.getAccounts().size();
    }

    @Override
    public Object getItem(int position) {
        return AccountService.getAccounts().get(position);
    }

    @Override
    public long getItemId(int position) {
        return AccountService.getAccounts().get(position).getUser().getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RelativeLayout toReturn = null;
        if (convertView != null) toReturn = (RelativeLayout) convertView;
        else toReturn = (RelativeLayout) LayoutInflater.from(mContext).inflate(R.layout.account_list_item, null);
        final Account account = (Account) getItem(position);
        final User curUser = account.getUser();
        NetworkedCacheableImageView profilePic = (NetworkedCacheableImageView) toReturn.findViewById(R.id.accountItemProfilePic);
        profilePic.setImageResource(R.drawable.sillouette);
        profilePic.loadImage(curUser.getProfileImageURL(), false);
        TextView nameTxt = (TextView) toReturn.findViewById(R.id.accountItemName);
        nameTxt.setText(curUser.getName());
        FeedListAdapter.ApplyFontSize(nameTxt, mContext);
        TextView descTxt = (TextView) toReturn.findViewById(R.id.accountItemDescription);
        if (curUser.getDescription() != null && !curUser.getDescription().trim().isEmpty()) {
            descTxt.setText(Utilities.twitterifyText(mContext, curUser.getDescription(), null, null, false, null));
        } else descTxt.setText(mContext.getApplicationContext().getString(R.string.nodescription_str), null);
        FeedListAdapter.ApplyFontSize(descTxt, mContext);
        return toReturn;
    }
}