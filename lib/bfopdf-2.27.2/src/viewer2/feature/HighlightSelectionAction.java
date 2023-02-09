// $Id: HighlightSelectionAction.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.awt.Color;
import javax.swing.*;

/**
 * A {@link MarkupSelectionAction} that will create an Highlight
 * {@link AnnotationMarkup} on the selected text.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">HighlightSelectionAction</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11.7
 */
public class HighlightSelectionAction extends MarkupSelectionAction {

    public HighlightSelectionAction() {
        super("HighlightSelectionAction");
        setType("Highlight");
        setColor(UIManager.getColor("PDFViewer.Feature.HighlightSelectionAction.color"));
        setDescription("PDFViewer.annot.Highlight");
    }

}