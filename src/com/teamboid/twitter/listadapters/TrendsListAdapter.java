package com.teamboid.twitter.listadapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.teamboid.twitter.R;
import com.teamboid.twitter.TabsAdapter.BoidAdapter;
import twitter4j.Trend;

import java.util.ArrayList;

/**
 * The list adapter used for the trends column.
 *
 * @author Aidan Follestad
 */
public class TrendsListAdapter extends BoidAdapter<Trend> {

    public TrendsListAdapter(Context timeline) {
        super(timeline, null, null);
        mActivity = timeline;
        trends = new ArrayList<Trend>();
    }

    private Context mActivity;
    private ArrayList<Trend> trends;
    public String id;

    public void add(Trend[] trs) {
        for (Trend tr : trs) trends.add(tr);
        notifyDataSetChanged();
    }

    public void add(Trend toAdd) {
        trends.add(toAdd);
    }

    public void clear() {
        trends.clear();
        notifyDataSetChanged();
    }

    public Trend[] toArray() {
        return trends.toArray(new Trend[0]);
    }

    private Boolean contains(Trend toFind) {
        Boolean found = false;
        ArrayList<Trend> itemCache = trends;
        for (Trend trend : itemCache) {
            if (trend.getQuery().equals(toFind.getQuery())) {
                found = true;
                break;
            }
        }
        return found;
    }

    @Override
    public int getCount() {
        return trends.size();
    }

    @Override
    public Trend getItem(int position) {
        return trends.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView toReturn = null;
        if (convertView != null) toReturn = (TextView) convertView;
        else toReturn = (TextView) LayoutInflater.from(mActivity).inflate(R.layout.trends_list_item, null);
        FeedListAdapter.ApplyFontSize(toReturn, mActivity);
        Trend curItem = trends.get(position);
        toReturn.setText(curItem.getName());
        return toReturn;
    }

    @Override
    public int getPosition(long id) {
        return (int) id;
    }
}

