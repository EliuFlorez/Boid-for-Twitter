package com.teamboid.twitter;

import com.teamboid.twitter.services.AccountService;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;

import java.io.Serializable;

/**
 * @author Aidan Follestad
 */
public class Account implements Serializable {

    private static final long serialVersionUID = 5774596574060143207L;

    public Account() {
    }

    public Account(Twitter client, AccessToken token) {
        this._client = client;
        this.token = token.getToken();
        this.tokenSecret = token.getTokenSecret();
    }

    private String token;
    private String tokenSecret;
    private User _user;
    /**
     * Stores a long of when we last actually got our user
     */
    private Long lastRefreshed = -1L;
    private transient Twitter _client;

    public Twitter getClient() {
        if (_client == null) {
            TwitterFactory factory = new TwitterFactory();
            _client = factory.getInstance();
            _client.setOAuthConsumer(AccountService.CONSUMER_KEY, AccountService.CONSUMER_SECRET);
            if (token != null)
                _client.setOAuthAccessToken(getToken());
        }
        return _client;
    }

    public AccessToken getToken() {
        return new AccessToken(token, tokenSecret);
    }

    public User getUser() {
        return _user;
    }

    public long getId() {
        return _user.getId();
    }

    public Account setUser(User user) {
        _user = user;
        lastRefreshed = System.currentTimeMillis() / 1000; // seconds
        return this;
    }

    public void refreshUserIfNeeded() throws Exception {
        if (lastRefreshed == null) {
            lastRefreshed = System.currentTimeMillis();
            return;
        }
        if (lastRefreshed <= (System.currentTimeMillis() / 1000) - (60 * 60 * 12)) {
            // Account is older than 12 hours, request in the background to reload
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        _user = _client.verifyCredentials();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public String toString() {
        return "BoidUser[" + getId() + "]";
    }
}