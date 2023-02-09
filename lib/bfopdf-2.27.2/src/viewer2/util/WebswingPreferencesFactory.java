package org.faceless.pdf2.viewer2.util;

import java.io.*;
import java.awt.*;
import java.util.prefs.*;
import javax.swing.*;
import java.util.concurrent.atomic.*;
import org.faceless.pdf2.viewer2.util.*;
import org.faceless.util.json.Json;
import org.webswing.toolkit.api.action.WebActionListener;
import org.webswing.toolkit.api.action.WebActionEvent;
import org.webswing.toolkit.api.WebswingUtil;
import org.webswing.toolkit.api.WebswingApi;

/**
 * A PreferencesFactory which saves content to Webswing.
 */
public class WebswingPreferencesFactory implements PreferencesFactory, WebActionListener {
    
    private WebswingPreferences root;
    private AtomicBoolean loaded = new AtomicBoolean();

    public synchronized Preferences systemRoot() {
        if (root == null) {
            root = new WebswingPreferences();
            WebswingApi api = WebswingUtil.getWebswingApi();
            api.addBrowserActionListener(this);
            api.sendActionEvent("loadPreferences", null, null);
            // Tricky! We've asked for preferences; they will come back on the EDT, but we
            // need to pause the EDT until they arrive. Run a secondary EDT to wait for the
            // response for 2500ms. Keep it busy, as it will timeout after 1000ms unless it
            // has events.
            final SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            Thread work = new Thread() {
                public void run() {
                    long when = System.currentTimeMillis() + 2500;
                    while (System.currentTimeMillis() < when && !loaded.get()) {
                        try {
                            SwingUtilities.invokeAndWait(new Runnable() { public void run() { } } );
                        } catch (Exception e) {}
                        try {
                            synchronized(loaded) {
                                loaded.wait(200);
                            }
                        } catch (InterruptedException e) {}
                    }
                    if (!loaded.get()) {
                        System.out.println("WARNING: timeout loading preferences");
                    }
                    loop.exit();
                }
            };
            work.start();
            loop.enter();
        }
        return root;
    }

    public Preferences userRoot() {
        return systemRoot();
    }

    @Override public void actionPerformed(WebActionEvent e) {
        if (e.getActionName().equals("loadPreferences")) {
            root.load(Json.read(e.getData()));
            loaded.set(true);
            synchronized(loaded) {
                loaded.notifyAll();
            }
        }
        WebswingUtil.getWebswingApi().removeBrowserActionListener(this);
    }

    public static class WebswingPreferences extends JsonPreferences {
        WebswingPreferences() {
            super(null, "");
            setAutoFlush(200);        // 200ms after last update, push save out
        }

        public void flush() {
            WebswingUtil.getWebswingApi().sendActionEvent("savePreferences", toString(), null);
        }

    }
}
