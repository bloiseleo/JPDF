// $Id: PagePrevious.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.PDFPage;
import org.faceless.pdf2.PDF;
import java.awt.event.*;
import javax.swing.*;

/**
 * Create a button that jumps to the previous page.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">PagePrevious</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class PagePrevious extends NavigationWidget {

    private Action action;

    public PagePrevious() {
        super("PagePrevious");
        setButton("Navigation.ltr", "PDFViewer.Feature.PagePrevious.icon", "PDFViewer.tt.PagePrevious");
        setMenu("View\tGoTo\tPreviousPage");
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                PagePrevious.super.createActionListener().actionPerformed(e);
            }
        };
    }

    protected ActionListener createActionListener() {
        return action;
    }

    protected void pageChanged() {
        if (pdf == null) {
            action.setEnabled(false);
        } else {
            DocumentViewport viewport = docpanel.getViewport();
            PDFPage page = viewport.getRenderingPage();
            action.setEnabled(page != null && viewport.getPreviousSelectablePageIndex(page) >= 0);
        }
    }

    public void action(ViewerEvent event) {
        if (action.isEnabled()) {
            DocumentViewport viewport = docpanel.getViewport();
            PDFPage page = viewport.getRenderingPage();
            if (page != null) {
                int index = viewport.getPreviousSelectablePageIndex(page);
                if (index >= 0) {
                    docpanel.setPage(docpanel.getPage(index));
                }
            }
        }
    }

}
