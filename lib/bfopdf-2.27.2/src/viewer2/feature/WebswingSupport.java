package org.faceless.pdf2.viewer2.feature;

import java.io.*;
import java.util.prefs.*;
import org.faceless.pdf2.viewer2.util.*;
import org.faceless.pdf2.viewer2.util.WebswingPreferencesFactory.WebswingPreferences;
import org.faceless.util.json.Json;
import org.webswing.toolkit.api.action.WebActionListener;
import org.webswing.toolkit.api.action.WebActionEvent;
import org.webswing.toolkit.api.WebswingUtil;
import org.webswing.toolkit.api.WebswingApi;

class WebswingSupport implements RemoteControl.RemoteControlProvider, WebActionListener {

    private WebswingApi api;
    private RemoteControl control;

    WebswingSupport() {
        try {
            // Terrible API, set system property and hope
            System.setProperty("java.util.prefs.PreferencesFactory", WebswingPreferencesFactory.class.getName());
        } catch (Throwable e) {}
        api = WebswingUtil.getWebswingApi();
        if (api == null) {
            throw new IllegalStateException("WebswingApi unavailable");
        }
    }

    @Override public void setRemoteControl(RemoteControl c) {
        this.control = c;
        if (control != null) {
            api.addBrowserActionListener(this);
            if (!(Preferences.systemRoot() instanceof WebswingPreferences)) {
                // System property not set in time; do our own thing for just the viewer
                control.setPreferences(new WebswingPreferencesFactory().systemRoot());
            }
        } else {
            api.removeBrowserActionListener(this);
        }
    }

    @Override public void send(String action, String data, byte[] binarydata) throws IOException {
        WebswingUtil.getWebswingApi().sendActionEvent(action, data, binarydata);
    }

    @Override public void actionPerformed(WebActionEvent e) {
        control.receive(e.getActionName(), e.getData(), e.getBinaryData());
    }

}
