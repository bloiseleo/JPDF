// $Id: AnnotationSoundFactory.java 11339 2009-12-16 12:23:16Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.*;

/**
 * <p>
 * Create annotations that handle {@link AnnotationSound} objects
 * </p>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationSound</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class AnnotationSoundFactory extends AnnotationComponentFactory
{
    /**
     * Create a new AnnotationSoundFactory
     * @since 2.11
     */
    public AnnotationSoundFactory() {
        super("AnnotationSound");
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof AnnotationSound;
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation a) {
        final AnnotationSound annot = (AnnotationSound)a;
        final JComponent comp = super.createComponent(pagepanel, a);
        comp.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                SoundActionHandler.playSound(annot.getSound(), false, false, (JComponent)event.getSource());
            }
        });
        return comp;
    }
}
