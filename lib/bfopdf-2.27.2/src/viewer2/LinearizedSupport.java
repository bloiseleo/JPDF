// $Id: LinearizedSupport.java 39802 2021-04-21 15:32:05Z mike $

package org.faceless.pdf2.viewer2;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.List;
import java.beans.*;
import java.io.*;
import javax.swing.*;
import org.faceless.pdf2.PDF;
import org.faceless.pdf2.LoadState;
import org.faceless.pdf2.viewer2.util.LongRunningTask;
import org.faceless.util.SoftInterruptibleThread;

/**
 * A class which handles all the support for Linearized document loaded by a DocumentPanel.
 *
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see DocumentPanel#getLinearizedSupport
 * @see PDF#getLoadState
 * @since 2.14
 */
public class LinearizedSupport implements PropertyChangeListener {

    private Map<Integer,Support> supportmap;
    private DocumentPanel docpanel;
    private List<Runnable> triggerqueue;
    private volatile Thread triggerthread;

    class Support {
        final LoadState state;
        private List<Runnable> runlist;
        private LongRunningTask dialog;

        Support(LoadState state) {
            this.state = state;
        }

        void enqueue(Runnable r, boolean showdialog) {
            if (runlist == null) {
                runlist = new ArrayList<Runnable>();
            }
            runlist.add(r);
            if (showdialog && dialog == null) {
                dialog = new LongRunningTask() {
                    public float getProgress() {
                        return 1 - (float)state.getBytesRemaining() / state.getBytes();
                    }
                    public void run() {
                        while (!isCancelled()) {
                            synchronized(this) {
                                try {
                                    wait();
                                } catch (InterruptedException e) {}
                            }
                        }
                    }
                };
                dialog.setCancellable(false);
                dialog.setModal(false);
                dialog.start(docpanel, UIManager.getString("PDFViewer.Loading"));
            }
        }

        void close() {
            if (dialog != null) {
                dialog.cancel();
                synchronized(dialog) {
                    dialog.notifyAll();
                }
            }
            if (runlist != null) {
                for (int i=0;i<runlist.size();i++) {
                    runlist.get(i).run();
                }
            }
        }
    }

    LinearizedSupport(final DocumentPanel docpanel) {
        this.docpanel = docpanel;
        PDF pdf = docpanel.getPDF();
        supportmap = new HashMap<Integer,Support>();
        if (pdf != null) {
            int numpages = docpanel.getNumberOfPages();
            for (int i=0;i<numpages;i++) {
                LoadState state = pdf.getLoadState(i);
                if (state != null) {
                    supportmap.put(Integer.valueOf(i), new Support(state));
                }
            }
            final LoadState state = pdf.getLoadState(-1);
            if (state != null) {
                supportmap.put(null, new Support(state));
                Thread t = new Thread("BFO-LinearizedSupport-Progress") {
                    public void run() {
                        float oldprogress = (float)state.getBytesRemaining() / state.getBytes();
                        while (state.getBytesRemaining() > 0 && docpanel.getLinearizedSupport() == LinearizedSupport.this ) {
                            float progress = (float)state.getBytesRemaining() / state.getBytes();
                            if (progress != oldprogress) {
                                docpanel.firePropertyChange("loadProgress", oldprogress, progress);
                                oldprogress = progress;
                            }
                            try { Thread.sleep(1000); } catch (InterruptedException e) {}
                        }
                        docpanel.firePropertyChange("loadProgress", oldprogress, 1);
                        synchronized(triggerqueue) {
                            triggerqueue.notifyAll();
                        }
                    }
                };
                t.setDaemon(true);
                t.start();
            }
            pdf.addPropertyChangeListener(this);
            triggerqueue = new ArrayList<Runnable>();
        }
    }


    public void propertyChange(PropertyChangeEvent event) {
        if ("pageLoaded".equals(event.getPropertyName())) {
            int pagenumber = ((Integer)event.getNewValue()).intValue();
            //System.err.println("pageLoaded "+pagenumber);
            //if (pagenumber == -1)
            //    System.err.println("all pages loaded");
            Support support = supportmap.remove(pagenumber < 0 ? null : Integer.valueOf(pagenumber));
            if (support != null) {
                support.close();
            }
        }
    }

    /**
     * Run an event when the specified page has loaded. A progress dialog will be 
     * displayed until it has. This method will also trigger a load of the page if it's
     * not already been done.
     * @param pagenumber the page number
     * @param r the task to run on load, which will be run on the Swing EDT
     */
    public void invokeOnPageLoadWithDialog(int pagenumber, Runnable r) {
        invokeOnPageLoad(pagenumber, r, true);
    }

    /**
     * Run an event when the specified page has loaded.
     * No progress updates will be displayed, and the page load will not be triggered.
     * @param pagenumber the page number
     * @param r the task to run on load, which will be run on the Swing EDT
     */
    public void invokeOnPageLoad(int pagenumber, Runnable r) {
        invokeOnPageLoad(pagenumber, r, false);
    }

    /**
     * Run an event when all pages have been loaded.
     * No progress updates will be displayed, and the page load will not be forced.
     * @param r the task to run on load, which will be run on the Swing EDT
     */
    public void invokeOnDocumentLoad(Runnable r) {
        invokeOnPageLoad(-1, r, false);
    }

    private void invokeOnPageLoad(final int pagenumber, final Runnable r, final boolean dialog) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    invokeOnPageLoad(pagenumber, r, dialog);
                }
            });
        } else {
            Support support = supportmap.get(pagenumber < 0 ? null : Integer.valueOf(pagenumber));
            if (support == null) {
                r.run();
            } else {
                if (dialog) {
                   triggerLoad(pagenumber);
                }
                support.enqueue(r, dialog);
            }
        }
    }

    private void triggerLoad(final int pagenumber) {
        invokeOnCompletion(new Runnable() {
            public void run() {
                PDF pdf = docpanel.getPDF();
                if (pdf != null) {
                    docpanel.getPage(pagenumber);
                }
            }
        }, null);
    }

    /**
     * Schedule the task to be run on the EDT when the precondition task (run in a background thread)
     * has completed. For example, to run a task on the event queue only when the form is
     * completely loaded:
     * <pre class="brush:java">
     * invokeOnCompletion(new Runnable() {
     *     public void run() {
     *         pdf.getForm().getElements();
     *     }
     * }, new Runable() {
     *    public void run() {
     *        // Do something with form.
     *    }
     * }
     * </pre>
     * @param precondition the Runnable object that must complete first - will be run in a background thread
     * @param task the optional task to be run when the precondition completes - will be run on the EDT
     */
    public void invokeOnCompletion(Runnable precondition, Runnable task) {
        synchronized(triggerqueue) {
            triggerqueue.add(precondition);
            triggerqueue.add(task);
            if (triggerthread == null) {
                triggerthread = new SoftInterruptibleThread("BFO-Linearized-TriggerThread") {
                    private Runnable pop() {
                        synchronized(triggerqueue) {
                            return isSoftInterrupted() || triggerqueue.isEmpty() ? null : triggerqueue.remove(0);
                        }
                    }
                    public void run() {
                        Runnable pre, post;
                        while ((pre=pop()) != null) {
                            post = pop();
                            pre.run();
                            if (post != null) {
                                SwingUtilities.invokeLater(post);
                            }
                        }
                        triggerthread = null;
                    }
                    public boolean isSoftInterrupted() {
                        return docpanel.getLinearizedSupport() != LinearizedSupport.this;
                    }
                };
                triggerthread.setPriority((Thread.MAX_PRIORITY + Thread.MIN_PRIORITY) / 2);
                triggerthread.setDaemon(true);
                triggerthread.start();
            }
        }
    }

    /**
     * Return true if the specified page is loaded, false otherwise
     * @param pagenumber the pagenumber to query
     */
    public boolean isPageLoaded(int pagenumber) {
        return !supportmap.containsKey(Integer.valueOf(pagenumber));
    }

    /**
     * Return true if the PDF is fully loaded, false otherwise
     */
    public boolean isFullyLoaded() {
        return !supportmap.containsKey(null);
    }

    /**
     * Get the progress of the document load, from 0 to 1 (fully loaded)
     */
    public float getLoadProgress() {
        Support support = supportmap.get(null);
        if (support == null) {
            return 1;
        } else {
            return 1 - (float)support.state.getBytesRemaining() / support.state.getBytes();
        }
    }

}
