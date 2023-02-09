// $Id: AnnotationAddLine.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * A feature that allows new line annotations to be
 * drawn directly onto the PDF Page.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationAddLine</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11
 */
public class AnnotationAddLine extends AbstractRegionSelector {

    private AnnotationLineFactory factory;
    private final Action action;

    public AnnotationAddLine() {
        super("AnnotationAddLine");
        setButton("Mode", "PDFViewer.Feature.AnnotationAddLine.icon", "PDFViewer.tt.AnnotationAddLine");
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                AnnotationAddLine.super.createActionListener().actionPerformed(e);
            }
        };
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        factory = new AnnotationLineFactory();
        factory.initialize(viewer);
    }

    protected ActionListener createActionListener() {
        return action;
    }

    public void documentUpdated(DocumentPanelEvent event) {
        super.documentUpdated(event);
        String type = event.getType();
        if (type.equals("activated") || (type.equals("permissionChanged") && event.getDocumentPanel() == getViewer().getActiveDocumentPanel())) {
            action.setEnabled(event.getDocumentPanel().getPDF() != null && event.getDocumentPanel().hasPermission("Annotate"));
        } else if (type.equals("deactivated")) {
            action.setEnabled(false);
        }
        if (isSelected() && !action.isEnabled()) {
            setSelected(false);
        }
    }

    protected JComponent createRubberBoxComponent() {
        if (!action.isEnabled()) {
            return null;
        }
        return new JPanel() {
            Point startpoint;
            public void setLocation(int x, int y) {
                super.setLocation(x, y);
                if (startpoint==null) {
                    startpoint = new Point(x, y);
                }
            }

            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                BasicStroke stroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 15, new
                float[] { CRAWLLENGTH, CRAWLLENGTH }, (int)((System.currentTimeMillis()/CRAWLSPEED)%(CRAWLLENGTH*2)));
                g.setColor(Color.black);
                ((Graphics2D)g).setStroke(stroke);
                Rectangle b = getBounds();
                int ex = b.x != startpoint.x ? b.x : b.x + b.width;
                int ey = b.y != startpoint.y ? b.y : b.y + b.height;
                g.drawLine(startpoint.x - b.x, startpoint.y - b.y, ex - b.x, ey - b.y);
            }
        };
    }

    public void action(PagePanel panel, Point2D start, Point2D end) {
        if (!action.isEnabled()) {
            return;
        }
        final PDFPage page = panel.getPage();
        float[] box = page.getBox("ViewBox");
        Rectangle2D rect = new Rectangle2D.Float(box[0], box[1], box[2]-box[0], box[3]-box[1]);
        if (rect.contains(start) && rect.contains(end)) {
            Line2D.Float line = new Line2D.Float(start, end);
            final AnnotationShape annot = (AnnotationShape)factory.createNewAnnotation(0, 0, 0, 0);
            annot.setShape(line);
            annot.setPage(panel.getPage());
            annot.rebuild();

            final DocumentPanel docpanel = panel.getViewport().getDocumentPanel();
            docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new AbstractUndoableEdit() {
                public String getPresentationName() {
                    return UIManager.getString("PDFViewer.annot.Line");
                }
                public void undo() {
                    super.undo();
                    annot.setPage(null);
                }
                public void redo() {
                    super.redo();
                    annot.setPage(page);
                    annot.rebuild();
                }
            }));
        }
    }

}
