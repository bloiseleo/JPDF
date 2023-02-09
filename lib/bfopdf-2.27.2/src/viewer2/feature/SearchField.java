// $Id: SearchField.java 23104 2016-04-22 15:09:09Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.geom.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;

/**
 * Create a widget that displays a Search field in the Toolbar. When activated, this widget
 * will select or create a {@link SearchPanel.Results} and display the search results.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">SearchField</span></span>
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>regex</th><td><code>true</code> or <code>false</code> for {@link #setUseRegex}</td></tr>
 * </table>
 * </div>
 *
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class SearchField extends ViewerWidget implements DocumentPanelListener
{
    private SearchPanel.Field field;
    private SearchPanel.Results createdResults;
    private boolean regex;

    public SearchField() {
        super("SearchField");

        field = new SearchPanel.Field(10);
        field.setFont(null);
        field.setEditable(true);
        field.setEnabled(false);
        setComponent("Search", field);
        field.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                field.transferFocus();
                final DocumentPanel docpanel = getViewer().getActiveDocumentPanel();
                SearchPanel.Results results = null;
                // 1. Try to find Results panel in existing side panels.
                for (Iterator<SidePanel> i = docpanel.getSidePanels().iterator();results==null && i.hasNext();) {
                    SidePanel sidepanel = i.next();
                    if (sidepanel instanceof SearchPanel.Results) {
                        results = (SearchPanel.Results)sidepanel;
                    }
                }
                if (results == null) {
                    // 2. Try to locate SearchPanel feature
                    SearchPanel searchPanel = (SearchPanel) getViewer().getFeature(SearchPanel.class);
                    if (searchPanel != null) {
                        results = (SearchPanel.Results) searchPanel.createSidePanel();
                    }
                }
                if (results==null) {        // Not found - create and make sure when we hit ESC, we delete
                    final SidePanel previouspanel = docpanel.getSelectedSidePanel();
                    results = new SearchPanel.Results(regex);
                    docpanel.addSidePanel(results);
                    docpanel.setSelectedSidePanel(results);
                    createdResults = results;
                    field.getKeymap().addActionForKeyStroke(KeyStroke.getKeyStroke("ESCAPE"), new AbstractAction() {
                        public void actionPerformed(ActionEvent event) {
                            createdResults.cancel();
                            field.setText("");
                            if (docpanel.getSelectedSidePanel()==createdResults) {
                                docpanel.setSelectedSidePanel(previouspanel);
                            }
                            docpanel.removeSidePanel(createdResults);
                            field.getKeymap().removeKeyStrokeBinding(KeyStroke.getKeyStroke("ESCAPE"));
                        }
                    });
                } else {                    // Found - just make sure when we hit ESC we cancel
                    if (results!=createdResults) {
                        final SearchPanel.Results fresults = results;
                        field.getKeymap().addActionForKeyStroke(KeyStroke.getKeyStroke("ESCAPE"), new AbstractAction() {
                            public void actionPerformed(ActionEvent event) {
                                fresults.cancel();
                                field.setText("");
                                field.getKeymap().removeKeyStrokeBinding(KeyStroke.getKeyStroke("ESCAPE"));
                            }
                        });
                    }
                }
                results.addChangeListener(field);
                docpanel.setSelectedSidePanel(results);
                results.search(field.getText());
            }
        });
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
        SearchPanel panel = viewer.getFeature(SearchPanel.class);
        if (panel != null) {      // If there is a SearchPanel factory, remove its search field. Don't need two.
            panel.setCreateSearchField(false);
        }
        if ("true".equals(getFeatureProperty(viewer, "regex"))) {
            setUseRegex(true);
        }
    }

    /**
     * Set whether searches run through the Search Field created by this class
     * use a regular-expression instead of a text value. The default is false.
     * @param regex whether to use a regular expression or not
     * @since 2.18.3
     */
    public void setUseRegex(boolean regex) {
        this.regex = regex;
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        if (type == "activated") {
            field.setEnabled(true);
        } else if (type == "deactivated") {
            field.setEnabled(false);
        }
    }

}
