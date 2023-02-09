// $Id: WarningNotifier.java 25278 2017-04-18 11:58:00Z mike $

package org.faceless.pdf2.viewer2.feature;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import java.util.*;
import java.lang.ref.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.*;
import org.faceless.util.log.*;

/**
 * <p>
 * This feature can be used to hook into the logging subsystem and display warnings
 * which would be sent to Log4J or the java.util.logging package.
 * </p><p>
 * We'll be the first to admit this class is something of an afterthought and a slightly
 * unusual fit with the rest of the API, due mainly to the difficulty in associating log
 * messages (which are static, can emanate from any thread, and may not have a PDF object
 * associated with them) to a particular {@link JComponent} for the notification.
 * Consequently this class is referenced from various points in the API, particularly
 * where background threads are started to process the PDF.
 * </p><p>
 * This class will pop up a translucent window in the bottom-right corner of the
 * viewer which contains the warning messages, and each message will expire after 5 seconds.
 * We anticipate this display won't be to everyone's taste, in which case override this
 * class and implement your own {@link warningEvent} method. For example, to log messages
 * to the JavaScript console instead:
 * </p>
 * <pre class="brush:java">
 * WarningNotifier notifier = new WarningNotifier() {
 *   public void warningEvent(JComponent component, Object source, String code, String message, Throwable throwable, Thread thread) {
 *     getViewer().getJSManager().consolePrintln("WARNING "+code+": "+message);
 *   }
 * };
 * </pre>
 * @since 2.17.1
 */
public class WarningNotifier extends ViewerFeature implements DocumentPanelListener {
    
    private static LogListener loglistener;
    private PDFViewer viewer;

    public WarningNotifier() {
        super("WarningNotifier");
        synchronized(WarningNotifier.class) {
            if (loglistener == null) {
                loglistener = new LogListener();
                BFOLogger.getLogger("org.faceless.pdf2").addListener(loglistener);
                Util.LOGGER.addListener(loglistener);
                BFOLogger.getLogger("org.faceless.tiff").addListener(loglistener);
            }
        }
    }

    public boolean isEnabledByDefault() {
        return false;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        this.viewer = viewer;
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        if (type == "loaded") {
            DocumentPanel panel = event.getDocumentPanel();
            PDF pdf = panel.getPDF();
            if (pdf != null) {
                register(pdf, panel);
            }
        } else if (type == "closing") {
            DocumentPanel panel = event.getDocumentPanel();
            PDF pdf = panel.getPDF();
            if (pdf != null) {
                unregister(pdf);
            }
        }
    }

    /**
     * Register a PDF with this object. Any warning messages which
     * can be associated with this PDF will be sent to the specified
     * JComponent
     * @param source the PDF to look for as the source of a warning
     * @param destination the JComponent interested in those warnings - typically a {@link PDFViewer} or {@link DocumentPanel}
     */
    public void register(PDF source, JComponent destination) {
        loglistener.register(this, source, destination);
    }

    /**
     * Register a {@link PDFReader} with this object. Any warning messages which
     * can be associated with this PDF will be sent to the specified
     * JComponent.
     * @param source the PDF to look for as the source of a warning
     * @param destination the JComponent interested in those warnings - typically a {@link PDFViewer} or {@link DocumentPanel}
     */
    public void register(PDFReader source, JComponent destination) {
        loglistener.register(this, source, destination);
    }

    /**
     * Register a {@link Thread} with this object. Any warning messages which
     * can be associated with this Thread will be sent to the specified
     * JComponent. This method is called by the various background threads
     * run in the viewer, e.g. thumbnail painting.
     * @param source the PDF to look for as the source of a warning
     * @param destination the JComponent interested in those warnings - typically a {@link PDFViewer} or {@link DocumentPanel}
     */
    public void register(Thread source, JComponent destination) {
        loglistener.register(this, source, destination);
    }

    /**
     * Unregister a previously registered PDF
     * @param source the source object to unregister
     */
    public void unregister(PDF source) {
        loglistener.unregister(this, source);
    }

    /**
     * Unregister a previously registered PDFReader
     * @param source the source object to unregister
     */
    public void unregister(PDFReader source) {
        loglistener.unregister(this, source);
    }

    /**
     * Unregister a previously registered Thread
     * @param source the source object to unregister
     */
    public void unregister(Thread source) {
        loglistener.unregister(this, source);
    }

    private static class LogListener implements BFOLoggingListener {

        private WeakHashMap<Object,WeakReference<JComponent>> register = new WeakHashMap<Object,WeakReference<JComponent>>();
        private WeakHashMap<Object,WarningNotifier> instances = new WeakHashMap<Object,WarningNotifier>();

        synchronized void register(WarningNotifier instance, Object source, JComponent dest) {
            register.put(source, new WeakReference<JComponent>(dest));
            instances.put(source, instance);
        }

        synchronized void unregister(WarningNotifier instance, Object source) {
            register.remove(source);
            instances.remove(source);
        }

        public boolean isListening(BFOLoggingLevel type, String code) {
            return type == BFOLoggingLevel.WARNING;
        }

        synchronized public boolean loggingEvent(BFOLoggingLevel level, final String code, final String message, final Throwable throwable, Object source) {
            if (level == BFOLoggingLevel.WARNING) {
                WeakReference<JComponent> ref;
                JComponent component;
                ref = register.get(source);
                if (ref == null || (component = ref.get()) == null) {
                    ref = register.get(source = Thread.currentThread());
                }
                final JComponent fcomponent = ref == null ? null : ref.get();
                if (fcomponent != null) {
                    final Object fsource = source;
                    final WarningNotifier instance = instances.get(source);
                    final Thread thread = Thread.currentThread();
                    if (SwingUtilities.isEventDispatchThread()) {
                        instance.warningEvent(fcomponent, source, code, message, throwable, thread);
                    } else {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                instance.warningEvent(fcomponent, fsource, code, message, throwable, thread);
                            }
                        });
                    }
                }
            }
            return false;
        }
    }

    /**
     * This method is called when a warning event can be associated with a JComponent.
     * It is always be called on the Swing EventDispatchThread.
     *
     * @param component the component identified as interested in this warning
     * @param source the source object which triggered the warning for this JComponent by being
     * registered. This may be any type of object, for example a PDF, a PDFReader or a Thread.
     * @param code the unique warning code
     * @param message the warning message
     * @param throwable the stack trace associated with that warning, if applicable.
     * @param thread the Thread on which the warning originated
     */
    public void warningEvent(JComponent component, Object source, String code, String message, Throwable throwable, Thread thread) {
        showWarning(message);
    }

    private Container owner;
    private JWindow window;
    private JPanel windowPanel;

    /**
     * Sample implementation that simply shows the message in a window.
     * This will be run in the AWT event thread.
     */
    private void showWarning(String message) {
        if (window == null) {
            owner = viewer;
            while (owner.getParent() != null) {
                owner = owner.getParent();
            }
            // Possiblities are Frame, Window, something from SWT or ???
            if (owner instanceof Frame) {
                window = new JWindow((Frame)owner);
            } else if (owner instanceof Window) {
                window = new JWindow((Window)owner);
            } else {
                window = new JWindow();
            }
            window.setOpacity(0.5f);
            window.setAlwaysOnTop(true);
            window.setType(Window.Type.UTILITY);
            JPanel borderPanel = new JPanel();
            borderPanel.setLayout(new GridBagLayout());
            windowPanel = new JPanel();
            windowPanel.setLayout(new BoxLayout(windowPanel, BoxLayout.Y_AXIS));
            window.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    window.setVisible(false);
                }
            });
            GridBagConstraints c = new GridBagConstraints();
            c.weightx = c.weighty = 1;
            c.insets = new Insets(5, 5, 5, 5);
            borderPanel.add(windowPanel, c);
            window.setContentPane(borderPanel);
        }
        if (message.length() > 40) {
            message = message.substring(0, 37) + "...";
        }
        final JLabel label = new JLabel(message);
        windowPanel.add(label);
        updateWindowPosition();
        window.setVisible(true);
        new Timer(5000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                windowPanel.remove(label);
                updateWindowPosition();
                if (windowPanel.getComponentCount() == 0) {
                    window.setVisible(false);
                }
            }
        }).start();
    }

    private void updateWindowPosition() {
        window.pack();
        Rectangle bounds = viewer.getBounds();
        Point p = new Point(bounds.width - window.getWidth(), bounds.height - window.getHeight());
        if (owner != null) {
            p = SwingUtilities.convertPoint(viewer, p, owner);
            p.x += owner.getX();
            p.y += owner.getY();
        }
        window.setLocation(p);
    }

}
