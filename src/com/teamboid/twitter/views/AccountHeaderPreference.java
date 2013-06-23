package com.teamboid.twitter.views;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.LinearLayout;
import com.teamboid.twitter.R;
import me.kennydude.awesomeprefs.HeaderPreference;
import me.kennydude.awesomeprefs.PreferenceFragment;

public class AccountHeaderPreference extends HeaderPreference {

    public AccountHeaderPreference(Context c, PreferenceFragment f) {
        super(c, f);
    }

    public String url;

    public View getIconView() {
        NetworkedCacheableImageView riv = new NetworkedCacheableImageView(getContext());
        DisplayMetrics outMetrics = new DisplayMetrics();
        getFragment().getActivity().getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        float dp = outMetrics.density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (48 * dp), (int) (48 * dp));
        lp.setMargins(0, 0, (int) (10 * dp), 0);
        riv.setLayoutParams(lp);
        riv.setImageResource(R.drawable.sillouette);
        if (url != null) {
            riv.loadImage(url, false);
        }
        return riv;
    }
}
