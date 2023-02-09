// $Id: AnnotationRectangleFactory.java 16466 2012-10-23 13:22:08Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.util.ColorChoicePanel;
import javax.swing.*;
import java.awt.geom.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.*;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Create annotations that handle Rectangle {@link AnnotationShape} objects.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationRectangle</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11.7
 */
public class AnnotationRectangleFactory extends AnnotationSimpleShapeFactory {

    public AnnotationRectangleFactory() {
        super("AnnotationRectangle");
        setPreferencePrefix("feature.AnnotationRectangleFactory");
    }

    public String getAnnotationType() {
        return UIManager.getString("PDFViewer.annot.Rectangle");
    }

    protected boolean matchesShape(AnnotationShape annot) {
        Shape shape = annot.getShape();
        return (shape instanceof Rectangle2D);
    }

    public PDFAnnotation createNewAnnotation(float x1, float y1, float x2, float y2) {
        AnnotationShape def = new AnnotationShape(new Rectangle2D.Float(x1, y1, x2-x1, y2-y1));
        PDFStyle style = new PDFStyle();
        Color linecolor = Color.black;
        Color fillcolor = null;
        float weight = 1;
        float[] dash = null;

        Preferences preferences = getPreferences();
        if (preferences!=null) {
            linecolor = ColorChoicePanel.loadColor(preferences, "feature.AnnotationRectangleFactory.lineColor", linecolor);
            fillcolor = ColorChoicePanel.loadColor(preferences, "feature.AnnotationRectangleFactory.fillColor", fillcolor);
            weight = preferences.getFloat("feature.AnnotationRectangleFactory.lineWeight", 1);
            dash = BorderStyleEditor.getDashArray(preferences.get("feature.AnnotationRectangleFactory.dashPattern", "solid"));
        }
        style.setLineColor(linecolor);
        style.setFillColor(fillcolor);
        style.setLineWeighting(weight);
        style.setLineDash(dash, 0);
        def.setStyle(style);
        return def;
    }

}
