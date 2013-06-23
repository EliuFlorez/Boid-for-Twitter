package com.teamboid.twitter.columns;

import com.teamboid.twitter.TabsAdapter.BaseTimelineFragment;
import com.teamboid.twitter.services.AccountService;
import twitter4j.Paging;
import twitter4j.Status;

/**
 * Represents the column that displays the current user's mentions.
 *
 * @author Aidan Follestad
 */
public class MentionsFragment extends BaseTimelineFragment {

    public static final String ID = "COLUMNTYPE:MENTIONS";

    @Override
    public String getColumnName() {
        return AccountService.getCurrentAccount().getId() + ".mentions";
    }

    @Override
    public Status[] fetch(long maxId, long sinceId) {
        try {
            Paging paging = new Paging(getMaxPerLoad());
            if (maxId != -1) {
                paging.setMaxId(maxId);
            }
            if (sinceId != -1) {
                paging.setSinceId(sinceId);
            }
            return AccountService.getCurrentAccount().getClient().getMentionsTimeline(paging).toArray(new Status[0]);
        } catch (Exception e) {
            e.printStackTrace();
            showError(e.getMessage());
            return null;
        }
    }

    @Override
    public String getAdapterId() {
        return ID;
    }
}
