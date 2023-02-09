// $Id: LongRunningTask.java 25876 2017-09-14 14:59:10Z mike $

package org.faceless.pdf2.viewer2.util;

import java.util.Timer;
import java.util.List;
import java.util.*;
import java.beans.*;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import org.faceless.pdf2.viewer2.Util;
import org.faceless.pdf2.viewer2.DocumentPanel;
import org.faceless.util.SoftInterruptibleThread;

/**
 * The superclass for {@link org.faceless.pdf2.viewer2.Importer} and
 * {@link org.faceless.pdf2.viewer2.Exporter}, this general class represents
 * a task which takes some time to run, may optionally be cancelled and which displays
 * some sort of progress dialog.
 *
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10.2
 */
public abstract class LongRunningTask {

    private volatile boolean cancelled, running, cancellable;
    private int delay;
    private JButton cancelbutton;
    private JProgressBar progressbar;
    private Thread thread;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean modal;
    private JDialog dialog;

    public LongRunningTask() {
        this(true, 1000);
    }

    /**
     * Create a new LongRunningTask
     * @param cancellable whether the task is cancellable
     * @param delay the initial delay before opening the dialog
     */
    public LongRunningTask(boolean cancellable, int delay) {
        this.cancellable = cancellable;
        this.delay = delay;
    }

    /**
     * Indicates whether this task will create modal dialogs by default
     * @since 2.13
     */
    public boolean isModal() {
        return modal;
    }

    /**
     * Sets whether this task will create modal dialogs by default.
     * @since 2.13
     */
    public void setModal(boolean flag) {
        this.modal = flag;
    }

    /**
     * Add a PropertyChanageListener to this task. Listeners will be updated
     * on the "state" property of this task, which will move from "running"
     * to either "completed" or "cancelled"
     * @since 2.11.2
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Remove a PropertyChangeListener previously added by {@link #addPropertyChangeListener}
     * @since 2.11.2
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Return the progress of this task - a value from 0 (just started) to 1 (complete).
     * A value of NaN means the progress is indeterminate - if this is is the case when the
     * task is started, the progress bar will not be shown.
     */
    public abstract float getProgress();

    /**
     * Return true if this task can be cancelled before completion by the user.
     */
    public boolean isCancellable() {
        return cancellable;
    }

    /**
     * Set whether this task can be cancelled before completion by the user.
     * Note that the {@link #cancel} method can always be called - this method
     * only affects the state of the "Cancel" button
     * @param cancellable whether the task is user-cancellable
     */
    public void setCancellable(final boolean cancellable) {
        this.cancellable = cancellable;
        if (cancelbutton != null) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    cancelbutton.setEnabled(cancellable);
                }
            });
        }
    }

    /**
     * Return true if this task was cancelled before completion by the user.
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Return true if this task is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Cancel the current task. This can be done regardless of the state
     * of {@link #isCancellable}
     */
    public void cancel() {
        cancelled = true;
        pcs.firePropertyChange("state", "running", "cancelled");
    }

    /**
     * Run the current task. Called by {@link #start}, this method
     * should typically not be called directly.
     */
    public abstract void run() throws Exception;

    /**
     * Start the task. The {@link #run} method is called, and a dialog displayed
     * which will show the tasks progress as determined by the {@link #getProgress} method.
     * If the task is {@link #isCancellable cancellable}, a "Cancel" button is displayed
     * as well, which will call the {@link #cancel} method when clicked on.
     * The dialog's modal property is the modal property of this task.
     * @param parent the JComponent that the dialog should be displayed relative to
     * @param title the title of the dialog window - typically something like "Loading".
     */
    public Thread start(final JComponent parent, String title) {
        return start(parent, title, modal);
    }

    // Manage the situation where multiple dialogs are displayed
    @SuppressWarnings("unchecked")
    private static List<LongRunningTask> getLongRunningTaskList(final JComponent root) {
        List<LongRunningTask> list = (List<LongRunningTask>)root.getClientProperty("bfo.LongRunningTask");
        if (list == null) {
            final List<LongRunningTask> lrt = new LinkedList<LongRunningTask>();
            list = lrt;
            root.putClientProperty("bfo.LongRunningTask", lrt);
            root.getParent().addComponentListener(new ComponentAdapter() {
                public void componentMoved(ComponentEvent event) {
                    updateDialogPositions(root, lrt);
                }
                public void componentResized(ComponentEvent event) {
                    updateDialogPositions(root, lrt);
                }
            });
        }
        return list;
    }

    /**
     * Start the task. The {@link #run} method is called, and a dialog displayed
     * which will show the tasks progress as determined by the {@link #getProgress} method.
     * If the task is {@link #isCancellable cancellable}, a "Cancel" button is displayed
     * as well, which will call the {@link #cancel} method when clicked on.
     * @param parent the JComponent that the dialog should be displayed relative to
     * @param title the title of the dialog window - typically something like "Loading".
     * @param modal whether the task dialog is modal or not
     * @since 2.11.14
     */
    public Thread start(final JComponent parent, String title, boolean modal) {
        dialog = Util.newJDialog(parent, null, modal);
        final Window dialogowner = dialog.getOwner();
        final JComponent root = Util.getRootAncestor(parent);

        final List<LongRunningTask> lrt = getLongRunningTaskList(root);

        final WindowListener windowlistener = new WindowAdapter() {
            // If parent window is closed, cancel this task.
            public void windowClosing(WindowEvent e) {
                LongRunningTask.this.cancel();
                dialogowner.removeWindowStateListener(this);
            }
        };
        dialogowner.addWindowListener(windowlistener);

        progressbar = new JProgressBar(0, 100);
        JPanel content = new JPanel(new BorderLayout(10, 0));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.setContentPane(content);
        if (title != null) {
            content.add(new JLabel(title), BorderLayout.WEST);
        }
        if (getProgress() == getProgress()) {
            content.add(progressbar, BorderLayout.CENTER);
        }
        if (isCancellable()) {
            cancelbutton = new JButton(UIManager.getString("PDFViewer.Cancel"));
            cancelbutton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent event) {
                    LongRunningTask.this.cancel();
                    cancelbutton.setEnabled(false);
                }
            });
            content.add(cancelbutton, BorderLayout.EAST);
        }
        dialog.setUndecorated(true);
        dialog.setResizable(false);

        final Timer timer = new Timer();
        thread = new SoftInterruptibleThread("BFO-LongRunningTask") {
            public void run() {
                try {
                    pcs.firePropertyChange("state", null, "running");
                    LongRunningTask.this.run();
                    pcs.firePropertyChange("state", "running", "completed");
                } catch (Throwable e) {
                    if (!cancelled) {
                        Util.displayThrowable(e, parent);
                    }
                    LongRunningTask.this.cancel();
                }
                running = false;
                if (timer != null) {
                    timer.cancel();
                }
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        lrt.remove(LongRunningTask.this);
                        dialogowner.removeWindowListener(windowlistener);
                        if (dialog.isVisible()) {
                            dialog.dispose();
                            dialog = null;
                        }
                        if (root.isVisible()) {
                            updateDialogPositions(root, lrt);
                        }
                    }
                });
                thread = null;
            }
            public boolean isSoftInterrupted() {
                return isCancelled();
            }
        };

        timer.schedule(new TimerTask() {
            public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (dialog != null && !dialog.isVisible()) {
                            lrt.add(LongRunningTask.this);
                            dialog.pack();
                            updateDialogPositions(root, lrt);
                            dialog.setVisible(true);
                        }
                        if (progressbar != null) {
                            float pp = getProgress();
                            if (pp == pp) {
                                progressbar.setIndeterminate(false);
                                progressbar.setValue(Math.min(100, Math.max(0, (int)(pp * 100))));
                            } else {
                                progressbar.setIndeterminate(true);
                            }
                        }
                    }
                });
            }
        }, modal ? 0 : delay, 100);

        running = true;
        thread.start();
        return thread;
    }

    private static void updateDialogPositions(JComponent root, List<LongRunningTask> irt) {
        if (!irt.isEmpty()) {
            Point center;
            if (root.getParent().isShowing() && !(root.getParent() instanceof Frame && (((Frame)root.getParent()).getExtendedState()&Frame.ICONIFIED) != 0)) {
                 center = new Point(root.getLocationOnScreen());
                 Rectangle psize = root.getBounds();
                 Rectangle ssize = root.getGraphicsConfiguration().getBounds();
                 center.x += Math.min(psize.width, ssize.width - center.x) / 2;
                 center.y += Math.min(psize.height, ssize.height - center.y) / 2;
            } else {
                 center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
            }

            for (Iterator<LongRunningTask> i = irt.iterator();i.hasNext();) {
                JDialog dialog = i.next().dialog;
                center.y -= dialog.getHeight() / 2;
            }
            for (Iterator<LongRunningTask> i = irt.iterator();i.hasNext();) {
                JDialog dialog = i.next().dialog;
                dialog.setLocation(center.x - dialog.getWidth() / 2, center.y);
                center.y += dialog.getHeight();
            }
        }
    }

}
