// $Id: GoToActionHandler.java 40456 2021-06-29 19:28:36Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.geom.*;
import java.awt.*;
import java.io.*;

/**
 * Create an action handler for "GoTo" actions and the named actions that move between
 * pages.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">GoToActionHandler</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class GoToActionHandler extends ActionHandler {

    /**
     * Create a new GoToActionHandler
     * @since 2.11 just calls the public constructor
     */
    public GoToActionHandler() {
        super("GoToActionHandler");
    }

    public boolean matches(DocumentPanel panel, PDFAction action) {
        String type = action.getType();
        return type.equals("GoToE") || type.equals("GoToR") || type.equals("GoToFit") || type.equals("GoTo") || type.equals("GoToFitWidth") || type.equals("GoToFitHeight") || type.equals("GoToFitRectangle") || type.equals("Named:FirstPage") || type.equals("Named:PrevPage") || type.equals("Named:NextPage") || type.equals("Named:LastPage");
    }

    public void run(DocumentPanel docpanel, PDFAction action) {
        runAction(docpanel, action);
    }

    private static void runAction(DocumentPanel docpanel, PDFAction action) {
        PDF pdf = docpanel.getPDF();
        String type = action.getType();
        GoToAction ga = null;

        if (type.equals("GoTo")) {
            ga = new GoToAction();
        } else if (type.equals("GoToFit")) {
            ga = new GoToFit();
        } else if (type.equals("GoToE")) {
            ga = new GoToEmbedded();
        } else if (type.equals("GoToR")) {
            ga = new GoToRemote();
        } else if (type.equals("GoToFitWidth")) {
            ga = new GoToFitWidth();
        } else if (type.equals("GoToFitHeight")) {
            ga = new GoToFitHeight();
        } else if (type.equals("GoToFitRectangle")) {
            ga = new GoToFitRectangle();
        } else if (type.equals("Named:FirstPage")) {
            ga = new GoToNamed(0);
        } else if (type.equals("Named:LastPage")) {
            ga = new GoToNamed(docpanel.getNumberOfPages() - 1);
        } else {
            int now = docpanel.getPageNumber();
            if (type.equals("Named:NextPage") && now + 1 < docpanel.getNumberOfPages()) {
                ga = new GoToNamed(now + 1);
            } else if (type.equals("Named:PrevPage") && now > 0) {
                ga = new GoToNamed(now - 1);
            }
        }

        if (ga != null) {
            ga.init(docpanel, pdf, action);
            int pn = ga.getPageNumber();
            if (pn >= 0) {
                docpanel.getLinearizedSupport().invokeOnPageLoadWithDialog(pn, ga);
            } else {
                ga.run();
            }
        }
    }

    private static class GoToAction implements Runnable {
        DocumentPanel docpanel;
        PDFAction action;
        PDF pdf;

        public GoToAction() {
        }

        public GoToAction(DocumentPanel docpanel, PDF pdf, PDFAction action) {
            init(docpanel, pdf, action);
        }

        void init(DocumentPanel docpanel, PDF pdf, PDFAction action) {
            this.docpanel = docpanel;
            this.pdf = pdf;
            this.action = action;
        }

        int getPageNumber() {
            return action.getPageNumber();
        }

        void go(PDFPage page, float x, float y, float zoom) {
            // x and y are relative to mediabox, but setpage wants them
            // relative to Viewbox and inverted - ugh.
            Rectangle2D crop = PagePanel.getFullPageView(page);
            x -= crop.getMinX();
            y = (float)crop.getMaxY() - y;
            docpanel.setPage(page, x, y, zoom);
        }

        public void run() {
            int num = getPageNumber();
            if (num >= 0) {
                PDFPage page = pdf.getPage(num);
                if (page != null) {
                    float[] coords = action.getGoToCoordinates();
                    float x = Float.NaN, y = Float.NaN, zoom = Float.NaN;
                    if (coords != null && coords.length >= 3) {
                        x = coords[0];
                        y = coords[1];
                        zoom = coords[2];
                    }
                    go(page, x, y, zoom);
                }
            } else {
                String s = action.getStructureElementId();
                if (s != null) {
                    PDFAction a = pdf.getNamedActions().get(s);
                    if (a != null) {
                        runAction(docpanel, a);
                    }
                }
            }
        }
    }

    private static class GoToEmbedded extends GoToAction {
        int getPageNumber() {
            return -1;
        }
        public void run() {
            PDF pdf = this.pdf;
            String filename = action.getRemoteFilename();
            if (filename == null) {
                return;
            }
            // Navigate our way from this PDF to the target PDF.
            for (String part : filename.split("/")) {
                if (part.equals("..")) {
                    EmbeddedFile ef = pdf.getEmbeddedFileSource();
                    pdf = ef == null ? null : ef.getSourcePDF();
                } else {
                    EmbeddedFile ef = pdf.getEmbeddedFiles().get(part);
                    if (ef != null) {
                        try {
                            pdf = null;
                            pdf = ef.getPDF();
                        } catch (Exception e) { }
                    }
                }
                if (pdf == null) {
                    break;
                }
            }
            if (pdf != null) {
                final PDF targetpdf = pdf;
                DocumentPanel[] dplist = docpanel.getViewer().getDocumentPanels();
                for (int i=0;i<dplist.length;i++) {
                    DocumentPanel dp = dplist[i];
                    if (dp.getAllPDFs().contains(targetpdf)) {
                        if (dp != docpanel) {
                            dp.focus();
                        }
                        PDFAction action = this.action;
                        if (action.getPageNumber() < 0) {
                            action = targetpdf.getNamedActions().get(action.getStructureElementId());
                        }
                        if (action != null) {
                            new GoToAction(dp, targetpdf, action).run();
                        } else {
                            // Can't find action; just go to first page
                            dp.setPage(targetpdf.getPage(0));
                        }
                        return;
                    }
                }
                docpanel.getViewer().loadPDF(new PDFParser(targetpdf), filename, 0, null, false, new DocumentPanelListener() {
                    public void documentUpdated(DocumentPanelEvent e) {
                        if (e.getType().equals("loaded")) {
                            new GoToAction(e.getDocumentPanel(), targetpdf, action).run();
                        }
                    }
                });
            }
        }
    }

    private static class GoToRemote extends GoToAction {
        int getPageNumber() {
            return -1;
        }
        public void run() {
            File file = docpanel.getAssociatedFile();
            file = file == null ? new File(action.getRemoteFilename()) : file.toPath().resolve(action.getRemoteFilename()).toFile();

            DocumentPanel[] dplist = docpanel.getViewer().getDocumentPanels();
            for (int i=0;i<dplist.length;i++) {
                DocumentPanel dp = dplist[i];
                File otherfile = dp.getAssociatedFile();
                if (file.equals(otherfile)) {
                    PDF targetpdf = dp.getPDF();
                    PDFAction action = this.action;
                    if (action.getPageNumber() < 0) {
                        action = targetpdf.getNamedActions().get(action.getStructureElementId());
                    }
                    if (action != null) {
                        new GoToAction(docpanel, targetpdf, action).run();
                    }
                    return;
                }
            }
            docpanel.getViewer().loadPDF(file);
            /*
                public void documentUpdated(DocumentPanelEvent e) {
                    if (e.getType().equals("loaded")) {
                        new GoToAction(e.getDocumentPanel(), targetpdf, action).run();
                    }
                }
            */
        }
    }


    private static class GoToFit extends GoToAction {
        public void run() {
            PDFPage page = pdf.getPage(getPageNumber());
            if (page != null) {
                float zoom = docpanel.getViewport().getTargetZoom(DocumentViewport.ZOOM_FIT, page);
                go(page, Float.NaN, Float.NaN, zoom);
            }
        }
    }

    private static class GoToFitWidth extends GoToAction {
        public void run() {
            PDFPage page = pdf.getPage(getPageNumber());
            if (page != null) {
                float[] coords = action.getGoToCoordinates();
                float zoom = docpanel.getViewport().getTargetZoom(DocumentViewport.ZOOM_FITWIDTH, page);
                float y = Float.NaN;
                if (coords != null && coords.length >= 1) {
                    y = coords[0];
                }
                go(page, Float.NaN, y, zoom);
            }
        }
    }

    private static class GoToFitHeight extends GoToAction {
        public void run() {
            PDFPage page = pdf.getPage(getPageNumber());
            if (page != null) {
                float[] coords = action.getGoToCoordinates();
                float x = Float.NaN;
                float zoom = docpanel.getViewport().getTargetZoom(DocumentViewport.ZOOM_FITHEIGHT, page);
                if (coords != null && coords.length >= 1) {
                    x = coords[0];
                }
                go(page, x, Float.NaN, zoom);
            }
        }
    }

    private static class GoToFitRectangle extends GoToAction {
        static final int PAD = 4;
        public void run() {
            PDFPage page = pdf.getPage(getPageNumber());
            if (page != null) {
                float[] coords = action.getGoToCoordinates();
                float x = Float.NaN, y = Float.NaN, zoom = Float.NaN;
                if (coords != null && coords.length >= 4) {
                    Dimension avail = docpanel.getViewport().getViewportSize();
                    double availw = avail.getWidth() - PAD;
                    double availh = avail.getHeight() - PAD;
                    int dpi = Util.getScreenResolution(docpanel);
                    x = Math.min(coords[0], coords[2]);
                    y = Math.max(coords[1], coords[3]);
                    float w = Math.abs(coords[2] - coords[0]);
                    float h = Math.abs(coords[3] - coords[1]);
                    zoom = (float)Math.min(availw / w, availh / h) / dpi * 72;
                    // x and y are the top right of the rectangle
                }
                go(page, x, y, zoom);
            }
        }
    }
    
    private static class GoToNamed extends GoToAction {
        final int pagenumber;
        GoToNamed(int pagenumber) {
            this.pagenumber = pagenumber;
        }
        public void run() {
            PDFPage page = docpanel.getPage(pagenumber);
            go(page, Float.NaN, Float.NaN, Float.NaN);
        }
    }

}
