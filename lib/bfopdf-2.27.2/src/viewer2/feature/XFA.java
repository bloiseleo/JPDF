// $Id: XFA.java 22287 2015-11-25 12:52:40Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.event.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;

/**
 * A class which suppressed warnings about missing XFA support in the PDF Viewer when
 * JavaScript is supported. In the future this may well form the basis of XFA support
 * in the viewer, but for now it's largely a no-op.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">XFA</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class XFA extends ViewerWidget
{
    public XFA() {
        super("XFA");
    }

    public String getCustomJavaScript(String type, String name) {
        if ("Doc".equals(type) && "Open".equals(name)) {
            return "var ADBE = new Object(); var xfa_installed = true; var xfa_version=3.3;";
        } else {
            return null;
        }
    }
}
