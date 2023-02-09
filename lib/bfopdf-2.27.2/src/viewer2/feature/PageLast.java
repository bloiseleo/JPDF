// $Id: PageLast.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.PDF;
import java.awt.event.*;
import javax.swing.*;

/**
 * Create a button to jump to the last page.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">PageLast</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class PageLast extends NavigationWidget {

    private Action action;

    public PageLast() {
        super("PageLast");
        setButton("Navigation.ltr", "PDFViewer.Feature.PageLast.icon", "PDFViewer.tt.PageLast");
        setMenu("View\tGoTo\tLastPage");
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                PageLast.super.createActionListener().actionPerformed(e);
            }
        };
    }

    protected ActionListener createActionListener() {
        return action;
    }

    protected void pageChanged() {
        action.setEnabled(pdf != null && docpanel.getPageNumber() < docpanel.getNumberOfPages() - 1);
    }

    public void action(ViewerEvent event) {
        if (action.isEnabled()) {
            docpanel.setPageNumber(docpanel.getNumberOfPages() - 1);
        }
    }

}
