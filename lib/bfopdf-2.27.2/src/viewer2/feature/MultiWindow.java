// $Id: MultiWindow.java 24902 2017-02-28 13:41:05Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;

/**
 * A special feature to enable internal frames inside the viewer. Without this feature,
 * a {@link PDFViewer} will only be able to display one PDF at a time.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">MultiWindow</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public final class MultiWindow extends ViewerFeature
{
    private static MultiWindow instance;

    /**
     * Return the singleton instance of this class
     */
    public static ViewerFeature getInstance() {
        if (instance==null) instance = new MultiWindow();
        return instance;
    }

    private MultiWindow() {
        super("MultiWindow");
    }
}
