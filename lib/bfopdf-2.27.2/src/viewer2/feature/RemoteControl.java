package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.util.json.Json;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;

/**
 * The RemoteControl feature provides remote control of the PDFViewer.
 * The exact mechanism is not defined; it's set by configuring the
 * provider by calling {@link #setProvider}. By default there is no
 * provider, and this class adds no functionality to the viewer.
 * @since 2.26
 */
public class RemoteControl extends ViewerFeature {

    private PDFViewer viewer;
    private Preferences preferences;
    private boolean debug;
    private RemoteControlProvider provider;
    private List<Object> q = new ArrayList<Object>();

    public RemoteControl() {
        super("RemoteControl");
    }

    public PDFViewer getViewer() {
        return viewer;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Set the Provider for this class.
     * @param providerClass the fully qualfied classname of an instance of RemoteControlProvider with a public constructor
     * @return true if the provider could be instantiated and set, false otherwise
     */
    public boolean setProvider(String providerClass) {
        RemoteControlProvider p = null;
        try {
            p = (RemoteControlProvider)Class.forName(providerClass).getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            if (debug) {
                e.printStackTrace();
            }
        }
        if (p != null) {
            setProvider(p);
            if (debug) {
                debug("Provider set to "+provider);
            }
        }
        return p != null;
    }

    /**
     * Set the Provider for this class.
     * @param p the provider, or null to remove the current provider.
     */
    public void setProvider(RemoteControlProvider p) {
        if (provider != null) {
            provider.setRemoteControl(null);
        }
        provider = p;
        if (provider != null) {
            provider.setRemoteControl(this);
        }
    }

    @Override public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        this.viewer = viewer;
        if (preferences != null) {
            viewer.setPreferences(preferences);
            preferences = null;
        }
        String val = getFeatureProperty(viewer, "debug");
        if (val != null) {
            setDebug("true".equals(val));
        }
        val = getFeatureProperty(viewer, "provider");
        if (val != null) {
            setProvider(val);
        }

        // Replay any events received before 
        List<Object> q = this.q;
        this.q = null;
        for (int i=0;i<q.size();) {
            final String action = (String)q.get(i++);
            final String data = (String)q.get(i++);
            final byte[] bindata = (byte[])q.get(i++);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    receive(action, data, bindata);
                }
            });
        }
    }

    public void setPreferences(Preferences prefs) {
        if (viewer != null) {
            viewer.setPreferences(prefs);
        } else {
            preferences = prefs;
        }
    }

    protected void debug(String s) {
        System.out.println("RC: "+s);
    }

    /**
     * Send a message to current the RemoteControlProvider. If none is set, this is a no-op
     * @param action the action to send
     * @param data the data to send
     * @param binaryData the binary data to send
     */
    public void send(String action, String data, byte[] binaryData) {
        if (provider != null) {
            try {
                if (debug) {
                    debug("send("+action+", "+data+")");
                }
                provider.send(action, data, binaryData);
            } catch (Exception e) {
                Util.displayThrowable(e, viewer);
            }
        }
    }

    /**
     * Receive a message and act on it. Called by the RemoteControlProvider
     * @param action the action to perform. If null, the action will be retrieved from the JSON field "action"
     * @param data the text data, which will be parsed as JSON.
     * @param binaryData the binary data, which will be parsed as JSON/CBOR only if "data" is null.
     */
    public void receive(String action, String data, byte[] binaryData) {
        if (viewer == null) {
            // Store these to replay later
            q.add(action);
            q.add(data);
            q.add(binaryData);
        } else {
            Json json;
            try {
                if (data != null) {
                    if (debug) {
                        debug("receive("+action+", "+data+")");
                    }
                    json = Json.read(data);
                } else {
                    if (debug) {
                        debug("receive("+action+")");
                    }
                    json = Json.read(new ByteArrayInputStream(binaryData), null);
                }
            } catch (Exception e) {
                return;
            }
            try {
                if (action == null && json.has("action")) {
                    action = json.get("action").stringValue();
                }
                if ("resize".equals(action)) {
                    int width = json.has("width") ? json.get("width").intValue() : 0;
                    int height = json.has("height") ? json.get("height").intValue() : 0;
                    if (width > 0 && height > 0) {
                        JFrame frame = (JFrame)SwingUtilities.getAncestorOfClass(JFrame.class, viewer);
                        if (frame != null) {
                            frame.setSize(width, height);
                        }
                    }
                } else if ("open".equals(action)) {
                    String url = json.has("url") ? json.get("url").stringValue() : null;
                    if (url != null) {
                        try {
                            viewer.loadPDF(new URL(url));
                        } catch (MalformedURLException e) {}
                    }
                }
            } catch (Exception e) {
                Util.displayThrowable(e, viewer);
            }
        }
    }

    /**
     * An interface which should be implemented by the Provider for the RemoteControl class.
     */
    public interface RemoteControlProvider {
        /**
         * Set the RemoteControl using this Provider.
         * @param control if not null, this provider has been added to a RemoteControl, otherwise it has been removed from the previous RemoteControl
         */
        public void setRemoteControl(RemoteControl control);

        /**
         * Send a message
         * @param action the action
         * @param data the data
         * @param binarydata the binary data
         */
        public void send(String action, String data, byte[] binarydata) throws IOException;
    }
    
}
