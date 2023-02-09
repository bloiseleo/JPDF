// $Id: BackgroundSignatureVerifier.java 29715 2018-10-01 16:32:58Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.*;

/**
 * This feature will cause signatures in the PDF to be verified automatically when a PDF is loaded
 * by the viewer, using a thread that runs transparently in the background.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">BackgroundSignatureVerifier</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11.7
 */
public class BackgroundSignatureVerifier extends ViewerFeature implements DocumentPanelListener, PDFBackgroundTask
{
    private static final int RUNNING=0, PAUSING=1, PAUSED=2, DONE=3;
    private volatile int state;

    public BackgroundSignatureVerifier() {
        super("BackgroundSignatureVerifier");
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public boolean isEnabledByDefault() {
        return false;
    }

    public void documentUpdated(DocumentPanelEvent event) {
        if (event.getType()=="loaded") {
            DocumentPanel docpanel = event.getDocumentPanel();
            startVerification(docpanel, docpanel.getPDF().getForm().getElements().values());
        }
    }

    private synchronized int getMyState() {
        return state;
    }

    private synchronized void setMyState(int state) {
        this.state = state;
        notifyAll();
    }

    public boolean isPaused() {
        return getMyState()==PAUSED;
    }

    public boolean isRunning() {
        return getMyState()!=DONE;
    }

    public synchronized void pause() {
        if (getMyState()==RUNNING) {
            setMyState(PAUSING);
            while (!isPaused()) {
                try {
                    wait();
                } catch (InterruptedException e) {}
            }
        }
    }

    public synchronized void unpause() {
        if (isPaused()) {
            setMyState(RUNNING);
        }
    }

    /**
     * Start a background thread that runs the {@link #verify verify()} method
     * @param docpanel the DocumentPanel containing the signatures being verified
     * @param fields a Collection containing the fields to verify
     */
    public void startVerification(final DocumentPanel docpanel, final Collection<? extends FormElement> fields) {
        if (docpanel !=null) {
            final List<FormSignature> copy = new ArrayList<FormSignature>();
            for (Iterator<? extends FormElement> i = fields.iterator();i.hasNext() && docpanel.isDisplayable();) {
                FormElement o = i.next();
                if (o instanceof FormSignature && ((FormSignature)o).getState() == FormSignature.STATE_SIGNED) {
                    copy.add((FormSignature)o);
                }
            }
            Thread thread = new Thread("BFO-SignatureVerifier") {
                public void run() {
                    verify(docpanel, copy);
                }
            };
            thread.setPriority(Thread.MIN_PRIORITY + 2);
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Verify the specified collection of fields. Fields will be verified if
     * only one {@link SignatureProvider} can be found that {@link SignatureProvider#canVerify canVerify()}
     * them.
     * @param docpanel the DocumentPanel containing the signatures being verified
     * @param fields a Collection containing the fields to verify. Only signed {@link FormSignature}
     * fields from this Collection will be checked, so it's safe to pass
     * <code>pdf.getForm().getElements().values()</code> to check all fields (assuming the field map isn't
     * going to be modified while the operation is running)
     */
    public void verify(final DocumentPanel docpanel, final Collection<FormSignature> fields) {
        setMyState(RUNNING);
        final Map<FormSignature,SignatureProvider> providers = new HashMap<FormSignature,SignatureProvider>();
        for (Iterator<FormSignature> i = fields.iterator();i.hasNext() && docpanel.isDisplayable();) {
            final FormSignature field = i.next();
            SignatureProvider.SignatureState state = SignatureProvider.getSignatureState(docpanel, field);
            if (state == null) {
                try {
                    SwingUtilities.invokeAndWait(new Runnable() {
                        public void run() {
                            if (docpanel.getViewer() != null && !docpanel.getViewer().isClosing()) {
                                SignatureProvider.selectVerifyProvider(docpanel, field, null, null, new ActionListener() {
                                    public void actionPerformed(ActionEvent event) {
                                        providers.put(field, (SignatureProvider)event.getSource());
                                    }
                                });
                            }
                        }
                    });
                } catch (Exception e) {
                }
                SignatureProvider provider = providers.get(field);
                if (provider != null) {
                    final SignatureProvider.SignatureState newstate = provider.verify(docpanel, field);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            if (docpanel.getViewer() != null && !docpanel.getViewer().isClosing()) {
                                SignatureProvider.setSignatureState(docpanel, field, newstate);
                            }
                        }
                    });
                }
            }
            synchronized(this) {
                if (getMyState() == PAUSING) {
                    setMyState(PAUSED);
                }
                while (getMyState() == PAUSED) {
                    try {
                        wait();
                    } catch (InterruptedException e) {}
                }
            }
        }
        setMyState(DONE);
        docpanel.repaint();
    }
}
