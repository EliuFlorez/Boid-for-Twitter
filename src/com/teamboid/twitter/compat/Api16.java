package com.teamboid.twitter.compat;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Notification.Builder;

/**
 * Api level 16 (Jellybean) only methods!
 *
 * @author kennydude and Aidan Follestad
 */
@TargetApi(16)
public class Api16 {

    public static void setLowPirority(Builder nb) {
        nb.setPriority(Notification.PRIORITY_LOW);
    }
}