// $Id: RotateAntiClockwise.java 16101 2012-07-24 22:25:35Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;

/**
 * Create a button that will rotate the page 90 degrees anticlockwise.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">RotateAntiClockwise</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.3
 */
public class RotateAntiClockwise extends RotateClockwise {

    // In hindsight we could have done better naming these two classes...

    public RotateAntiClockwise() {
        super("RotateAntiClockwise", -90);
    }

}
