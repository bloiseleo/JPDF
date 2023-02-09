package org.faceless.pdf2.viewer2.util;

import java.util.*;
import javax.swing.*;
import java.util.prefs.*;
import java.util.concurrent.atomic.*;
import org.faceless.util.json.Json;
import org.faceless.util.json.JsonListener;
import org.faceless.util.json.JsonEvent;

/**
 * A Preferences class backed with a Json structure.
 * The sync() and flush() methods do nothing, and should be overriden
 * The load() method will populate this object from a Json structure loaded elsewhere
 */
public class JsonPreferences extends AbstractPreferences {

    private Json json;
    private Map<String,JsonPreferences> children;
    private int autoFlush;
    private Thread autoWriteThread;

    public JsonPreferences(AbstractPreferences parent, String name) {
        super(parent, name);
        json = Json.read("{}");
        if (parent != null) {
            JsonPreferences p = (JsonPreferences)parent;
            if (p.children == null) {
                p.children = new TreeMap<String,JsonPreferences>();
                p.json.put("children", Json.read("{}"));
            }
            p.children.put(name, this);
            p.json.get("children").put(name, json);
        } else {
            final AtomicLong autoWriteTime = new AtomicLong(0);
            // autoWriteTime is written to from EDT and checked from non-EDT
            // but event handling - json changes, pref flushing - is always
            // done on EDT so no need for synchronization.
            //
            // Small hole if timer expires, job is scheduled, then before it's
            // run the autoWrite is reset due to another change, but in that
            // case the data is just written immediately so not a big deail.
            final Runnable autoWriter = new Runnable() {
                public void run() {
                    long diff;
                    while ((diff = autoWriteTime.get() - System.currentTimeMillis()) > 0) {
                        try {
                            Thread.sleep((int)diff);
                        } catch (Exception e) {}
                    }
                    try {
                        SwingUtilities.invokeAndWait(new Runnable() {
                            public void run() {
                                try {
                                    flush();
                                } catch (BackingStoreException e) {
                                } finally {
                                    autoWriteThread = null;
                                    autoWriteTime.set(0);
                                }
                            }
                        });
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        if (e.getCause() instanceof RuntimeException) {
                             throw (RuntimeException)e.getCause();
                        }
                    } catch (InterruptedException e) {
                    }
                }
            };
            json.addListener(new JsonListener() {
                public void jsonEvent(Json owner, JsonEvent event) {
                    if (autoFlush != 0) {
                        autoWriteTime.set(System.currentTimeMillis() + autoFlush);
                        if (autoWriteThread == null) {
                            autoWriteThread = new Thread(autoWriter);
                            autoWriteThread.start();
                        }
                    }
                }
            });
        }
    }

    /**
     * Auto-save the preferences after the specified number of ms.
     * A number other than zero (eg 250) will batch up changes to the
     * prefs before flushing. Zero to disable.
     */
    public void setAutoFlush(int ms) {
        autoFlush = ms;
    }

    public String toString() {
        return json.toString();
    }

    public void load(Json j) {
        if (j.isMap("data")) {
            json.put("data", j.get("data"));
        }
        if (j.isMap("children")) {
            for (Map.Entry<String,Json> e : j.get("children").mapValue().entrySet()) {
                JsonPreferences jp = new JsonPreferences(this, e.getKey());
                jp.load(e.getValue());
            }
        }
    }

    protected void putSpi(String key, String value) {
        Json data = json.get("data");
        if (data == null) {
            json.put("data", data = Json.read("{}"));
        }
        data.put(key, value);
    }

    protected String getSpi(String key) {
        if (json.isMap("data") && json.get("data").isString(key)) {
            return json.get("data").get(key).stringValue();
        }
        return null;
    }

    protected void removeSpi(String key) {
        if (json.isMap("data")) {
            json.get("data").remove(key);
        }
        
    }

    protected void removeNodeSpi() {
        JsonPreferences parent = (JsonPreferences)parent();
        if (parent != null) {
            parent.children.remove(name());
            parent.json.get("children").remove(name());
        }
    }

    protected String[] keysSpi() {
        Json data = json.get("data");
        if (data == null) {
            return new String[0];
        }
        return data.mapValue().keySet().toArray(new String[0]);
    }

    protected String[] childrenNamesSpi() {
        if (children == null || children.isEmpty()) {
            return new String[0];
        }
        return children.keySet().toArray(new String[0]);
    }

    protected AbstractPreferences childSpi(String name) {
        JsonPreferences sub = children == null ? null : children.get(name);
        if (sub == null) {
            sub = new JsonPreferences(this, name);
        }
        return sub;
    }

    protected void syncSpi() {
    }

    protected void flushSpi() {
    }

    public void flush() throws BackingStoreException {
        if (parent() != null) {
            parent().flush();
        }
    }

    public void sync() {
    }

}
