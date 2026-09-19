package com.edwinkarolczyk.edhome;

import android.app.Activity;

/** Beta does not call Play; see BetaUpdater for DEV updates. */
public final class PlayUpdateBridge {
    private PlayUpdateBridge() { }
    public static void checkOnce(Activity activity) { }
}
