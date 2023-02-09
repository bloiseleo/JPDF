// $Id: AnnotationTextCalloutFactory.java 20397 2014-11-28 11:32:24Z chris $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.undo.*;
import javax.swing.event.*;
import java.awt.geom.*;
import java.awt.*;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * <p>
 * Create annotations that handle {@link AnnotationText} objects with callouts.
 * </p>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationTextCallout</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11
 */
public class AnnotationTextCalloutFactory extends AnnotationTextFactory {

    /**
     * Create a new AnnotationTextCalloutFactory
     */
    public AnnotationTextCalloutFactory() {
        super("AnnotationTextCallout");
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof AnnotationText && ((AnnotationText) annot).getCallout() != null;
    }

    public String getAnnotationType() {
        return UIManager.getString("PDFViewer.annot.Callout");
    }

    public PDFAnnotation createNewAnnotation(float x1, float y1, float x2, float y2) {
        AnnotationText annot = (AnnotationText)super.createNewAnnotation(x1, y1, x2, y2);
        float[] dash = null;

        String arrow = "None";
        boolean elbow = false;
        Preferences preferences = getPreferences();
        if (preferences != null) {
            arrow = preferences.get("feature.AnnotationTextFactory.addDefaultCalloutArrow", "None");
            elbow = preferences.getBoolean("feature.AnnotationTextFactory.addDefaultCalloutElbow", false);
        }

        // Create callout
        float x = x1 + ((x2 - x1) * 2) / 3;
        float y = y1 + ((y2 - y1) * 2) / 3;
        annot.setContentRectangle(x1, y1, x, y);
        if (elbow) {
            float ex = x + ((x2 - x) / 2);
            float ey = y + ((y2 - y) / 2);
            annot.setCallout(new float[] { x2, y2, ex, ey, x, y });
        } else {
            annot.setCallout(new float[] { x2, y2, x, y });
        }
        annot.setCalloutEnding(arrow);
        return annot;
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation a) {
        final AnnotationText annot = (AnnotationText)a;
        CalloutComponent comp = new CalloutComponent(annot, pagepanel) {
            public void showPopup(Point point) {
                popupPropertyMenu(annot, this, point);
            }

            public void redrawAnnotation(boolean changed, Rectangle2D content, Point2D[] points) {
                if (changed) {

                    // content rectangle
                    final float[] cr = new float[4];
                    cr[0] = (float) content.getX();
                    cr[1] = (float) (content.getY() + content.getHeight());
                    cr[2] = (float) (content.getX() + content.getWidth());
                    cr[3] = (float) content.getY();
                    final float[] callout = new float[points.length * 2];
                    for (int i = 0; i < points.length; i++) {
                        callout[i * 2] = (float) points[i].getX();
                        callout[(i * 2) + 1] = (float) points[i].getY();
                    }
                    final float[] oldcallout = annot.getCallout();
                    final float[] oldcontent = annot.getContentRectangle();
                    final boolean move = Math.abs((cr[2]-cr[0]) - (oldcontent[2]-oldcontent[0])) < 0.5 && Math.abs(Math.abs(cr[3]-cr[1]) - Math.abs(oldcontent[3]-oldcontent[1])) < 0.5;

                    pagepanel.getDocumentPanel().fireUndoableEditEvent(new UndoableEditEvent(pagepanel.getDocumentPanel(), new AbstractUndoableEdit() {
                        public String getPresentationName() {
                            return move ? UIManager.getString("InternalFrameTitlePane.moveButtonText") : UIManager.getString("InternalFrameTitlePane.sizeButtonText");
                        }
                        public void undo() {
                            super.undo();
                            annot.setContentRectangle(oldcontent[0], oldcontent[1], oldcontent[2], oldcontent[3]);
                            annot.setCallout(oldcallout);
                            annot.rebuild();
                        }
                        public void redo() {
                            super.redo();
                            annot.setContentRectangle(cr[0], cr[1], cr[2], cr[3]);
                            annot.setCallout(callout);
                            annot.rebuild();
                        }
                    }));

                    try {
                        annot.setContentRectangle(cr[0], cr[1], cr[2], cr[3]);
                    } catch (IllegalArgumentException e) {
                        // bad! not allowed
                    }
                    // callout
                    try {
                        annot.setCallout(callout);
                    } catch (IllegalArgumentException e) {
                        // we are naughty
                    }
                    annot.rebuild();

                    // resync from annotation
                    float[] newcr = annot.getContentRectangle();
                    content.setFrame(newcr[0], newcr[1], newcr[2]-newcr[0], newcr[3]-newcr[1]);
                    float[] newcallout = annot.getCallout();
                    for (int i = 0; i < newcallout.length / 2; i++) {
                        points[i].setLocation(newcallout[i * 2], newcallout[(i * 2) + 1]);
                    }
                }
            }
        };
        comp.setToolTipText(annot.getSubject());
        return comp;
    }

    protected void copyAnnotationState(PDFAnnotation source, PDFAnnotation target) {
        super.copyAnnotationState(source, target);

        AnnotationText sourceText = (AnnotationText) source;
        AnnotationText targetText = (AnnotationText) target;
        targetText.setCallout(sourceText.getCallout());
        targetText.setCalloutEnding(sourceText.getCalloutEnding());
    }

    public JComponent createEditComponent(PDFAnnotation gannot, final boolean readonly, boolean create) {
        final AnnotationText annot = (AnnotationText)gannot;
        final Preferences preferences = getPreferences();
        return this.new TextEditor(annot, preferences, readonly, false, true);
    }

}
