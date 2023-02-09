// $Id: AnnotationLinkFactory.java 20463 2014-12-10 18:22:44Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;

/**
 * Create annotations that handle {@link AnnotationLink} objects.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationLink</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class AnnotationLinkFactory extends AnnotationComponentFactory
{
    /**
     * Return a new AnnotationLinkFactory
     */
    public AnnotationLinkFactory() {
        super("AnnotationLink");
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof AnnotationLink;
    }

    public String getAnnotationType() {
        return UIManager.getString("PDFViewer.annot.Link");
    }

    public JComponent createComponent(final PagePanel pagepanel, final PDFAnnotation annot) {
        final JComponent comp = super.createComponent(pagepanel, annot);
        PDFAction action = ((AnnotationLink)annot).getAction();
        if (action!=null) {
            comp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String type = action.getType();
            String tooltip = null;
            if (type.equals("URL")) {
                tooltip = action.getURL();
            } else if (type.startsWith("GoTo")) {
                int pagenumber = action.getPageNumber();
                if (pagenumber >= 0) {
                    tooltip = UIManager.getString("PDFViewer.Page")+" "+pagenumber;
                }
            } else if (type.equals("FormJavaScript")) {
               tooltip = "JavaScript";
            } else {
                tooltip = type;
            }
            if (tooltip!=null) {
                comp.setToolTipText(tooltip);
            }
            comp.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent event) {
                    // Can't test isPopupTrigger here, on OSX that's a mouseReleased event
                    // Check it's a plain left-click
                    if (event.getButton() == 1 && (event.getModifiersEx() & ~(MouseEvent.BUTTON1_DOWN_MASK)) == 0) {
                        DocumentPanel panel = pagepanel.getViewport().getDocumentPanel();
                        panel.getJSManager().runEventLinkMouseUp(panel, (AnnotationLink)annot);
                    }
                }
            });
        }
        return comp;
    }

    protected void copyAnnotationState(PDFAnnotation source, PDFAnnotation target) {
        super.copyAnnotationState(source, target);

        AnnotationLink sourceLink = (AnnotationLink) source;
        AnnotationLink targetLink = (AnnotationLink) target;

        float[] corners = sourceLink.getCorners();
        if (corners != null) {
            targetLink.setCorners(corners[0], corners[1],
                    corners[2], corners[3],
                    corners[4], corners[5],
                    corners[6], corners[7]);
        }
        targetLink.setStyle(sourceLink.getStyle());
        targetLink.setAction(sourceLink.getAction());
    }

}
