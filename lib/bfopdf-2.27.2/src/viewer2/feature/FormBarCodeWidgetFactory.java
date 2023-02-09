// $Id: FormBarCodeWidgetFactory.java 15828 2012-06-19 14:47:01Z chris $

package org.faceless.pdf2.viewer2.feature;

import javax.swing.*;
import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.Event;

/**
 * Create annotations to handle {@link WidgetAnnotation} objects belonging to a {@link FormBarCode}.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FormBarCodeWidgetFactory</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11.25
 */
public class FormBarCodeWidgetFactory extends WidgetComponentFactory
{
    /**
     * Create a new FormBarCodeWidgetFactory.
     */
    public FormBarCodeWidgetFactory() {
        super("FormBarCodeWidgetFactory");
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof WidgetAnnotation && ((WidgetAnnotation)annot).getField() instanceof FormBarCode;
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation annot) {
        return createComponent(pagepanel, annot, null);
    }

}
