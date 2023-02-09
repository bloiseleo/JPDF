// $Id: PageFirst.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.PDF;
import java.awt.event.*;
import javax.swing.*;

/**
 * Create a button to jump to the first page.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">PageFirst</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class PageFirst extends NavigationWidget {

    private Action action;

    public PageFirst() {
        super("PageFirst");
        setButton("Navigation.ltr", "PDFViewer.Feature.PageFirst.icon", "PDFViewer.tt.PageFirst");
        setMenu("View\tGoTo\tFirstPage");
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                PageFirst.super.createActionListener().actionPerformed(e);
            }
        };
    }

    protected ActionListener createActionListener() {
        return action;
    }

    protected void pageChanged() {
        action.setEnabled(pdf != null && docpanel.getPageNumber() != 0);
    }

    public void action(ViewerEvent event) {
        if (action.isEnabled()) {
            docpanel.setPageNumber(0);
        }
    }

}
