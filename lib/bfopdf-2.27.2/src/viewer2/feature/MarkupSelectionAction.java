// $Id: MarkupSelectionAction.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.PropertyParser;
import org.faceless.pdf2.*;
import java.awt.Color;
import java.awt.geom.*;
import javax.swing.UIManager;
import java.util.*;
import javax.swing.undo.*;
import javax.swing.event.*;

/**
 * A {@link TextSelectionAction} that will create an
 * {@link AnnotationMarkup} of the specified type on
 * the selected text.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>color</th><td>A 32-bit color value, eg 0x80FF0000 (for translucent red)</td></tr>
 * <tr><th>type</th><td>The type of Markup - Highlight, Underline, StrikeOut or Squiggly</td></tr>
 * <tr><th>description</th><td>The description of this markup</td></tr>
 * </table>
 * </div>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 *
 * @since 2.11.7
 */
public class MarkupSelectionAction extends ViewerFeature implements TextSelectionAction, AreaSelectionAction, DocumentPanelListener {

    private Color color = Color.yellow;
    private String type = "Highlight";
    private String desc;
    private boolean enabled;

    public MarkupSelectionAction(String name) {
        super(name);
        color = UIManager.getColor("PDFViewer.Feature.MarkupSelectionAction.color");
    }

    /**
     * Set the description that's returned by {@link #getDescription}
     */
    public void setDescription(String desc) {
        this.desc = desc;
    }

    public String getDescription() {
        String localdesc = UIManager.getString(desc);
        if (localdesc!=null) {
            return localdesc;
        } else {
            return desc;
        }
    }

    /**
     * Set the type of {@link AnnotationMarkup}.
     * @param type one of "Highlight", "Underline", "StrikeOut" or "Squiggly"
     */
    public void setType(String type) {
        if (!("Highlight".equals(type) || "Underline".equals(type) || "StrikeOut".equals(type) || "Squiggly".equals(type))) {
            throw new IllegalArgumentException("Invalid type "+type);
        }
        this.type = type;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
        color = PropertyParser.getColor(getFeatureProperty(viewer, "color"), color);
        String text = getFeatureProperty(viewer, "type");
        if (text!=null) {
            setType(text);
        }
        text = getFeatureProperty(viewer, "description");
        if (text!=null) {
            setDescription(text);
        }
    }

    public void documentUpdated(DocumentPanelEvent event) {
        DocumentPanel docpanel = event.getDocumentPanel();
        String type = event.getType();
        if (type.equals("activated") || (type.equals("permissionChanged") && docpanel == docpanel.getViewer().getActiveDocumentPanel())) {
            enabled = docpanel.getPDF() != null && docpanel.hasPermission("Annotate");
        } else if (type.equals("deactivated")) {
            enabled = false;
        }
    }

    public void selectAction(final DocumentPanel docpanel, TextSelection.RangeList list) {
        if (!enabled) {
            return;
        }
        PDFPage lastpage = null;
        final Map<PDFAnnotation,PDFPage> newannots = new LinkedHashMap<PDFAnnotation,PDFPage>();
        for (int i=0;i<=list.size();i++) {
            PDFPage page = i==list.size() ? null : ((TextSelection.Range)list.get(i)).getPage();
            if (page != lastpage && lastpage != null) {
                AnnotationMarkup annot = new AnnotationMarkup(type);
                annot.setColor(color);
                annot.setCorners(list.getCorners(lastpage));
                lastpage.getAnnotations().add(annot);
                newannots.put(annot, lastpage);
            }
            lastpage = page;
        }

        docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new AbstractUndoableEdit() {
            public String getPresentationName() {
                return getDescription();
            }
            public void undo() {
                super.undo();
                for (Iterator<Map.Entry<PDFAnnotation,PDFPage>> i = newannots.entrySet().iterator();i.hasNext();) {
                    Map.Entry<PDFAnnotation,PDFPage> e = i.next();
                    PDFAnnotation annot = e.getKey();
                    annot.setPage(null);
                }
            }
            public void redo() {
                super.redo();
                for (Iterator<Map.Entry<PDFAnnotation,PDFPage>> i = newannots.entrySet().iterator();i.hasNext();) {
                    Map.Entry<PDFAnnotation,PDFPage> e = i.next();
                    PDFAnnotation annot = e.getKey();
                    PDFPage page = e.getValue();
                    annot.setPage(page);
                }
            }
        }));
    }

    public void selectArea(PagePanel pagepanel, Rectangle2D area) {
        if (!enabled) {
            return;
        }
        final PDFPage page = pagepanel.getPage();
        final DocumentPanel docpanel = pagepanel.getDocumentPanel();
        final AnnotationMarkup annot = new AnnotationMarkup(type);
        annot.setColor(color);
        annot.setRectangle((float)area.getMinX(), (float)area.getMinY(), (float)area.getMaxX(), (float)area.getMaxY());
        page.getAnnotations().add(annot);

        docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new AbstractUndoableEdit() {
            public String getPresentationName() {
                return getDescription();
            }
            public void undo() {
                super.undo();
                annot.setPage(null);
            }
            public void redo() {
                super.redo();
                annot.setPage(page);
            }
        }));
    }

}
