// $Id: AnnotationStampFactory.java 39802 2021-04-21 15:32:05Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.geom.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.*;
import java.util.*;

/**
 * Create annotations that handle {@link AnnotationStamp} objects.
 * Currently all that is handled is the ability to drag them if they're not readonly.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>stampList</th><td>A comma seperated list of stamp names, to be passed in to {@link #setStampList setStampList()}</td></tr>
 * <tr><th>stampHeight</th><td>The height of each stamp in the edit dialog, as passed in to {@link #setStampHeight}</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationStamp</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class AnnotationStampFactory extends AnnotationComponentFactory {

    private int stampheight = 40;
    private String[] stamplist;

    public static final String[] PREDEFINED = {
        "stamp.stencil.Approved",
        "stamp.stencil.AsIs",
        "stamp.stencil.Confidential",
        "stamp.stencil.Departmental",
        "stamp.stencil.Draft",
        "stamp.stencil.Experimental",
        "stamp.stencil.Expired",
        "stamp.stencil.Final",
        "stamp.stencil.ForComment",
        "stamp.stencil.ForPublicRelease",
        "stamp.stencil.NotApproved",
        "stamp.stencil.NotForPublicRelease",
        "stamp.stencil.Sold",
        "stamp.stencil.TopSecret",
        "stamp.standard.Approved",
        "stamp.standard.Completed",
        "stamp.standard.Confidential",
        "stamp.standard.Draft",
        "stamp.standard.Final",
        "stamp.standard.ForComment",
        "stamp.standard.ForPublicRelease",
        "stamp.standard.InformationOnly",
        "stamp.standard.NotApproved",
        "stamp.standard.NotForPublicRelease",
        "stamp.standard.PreliminaryResults",
        "stamp.standard.Void",
        "stamp.signhere.Accepted",
        "stamp.signhere.Rejected",
        "stamp.signhere.SignHere",
        "stamp.signhere.InitialHere",
        "stamp.signhere.Witness",
    };

    /**
     * Return a new AnnotationStampFactory
     */
    public AnnotationStampFactory() {
        super("AnnotationStamp");
        setStampList(PREDEFINED);
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        String val = getFeatureProperty(viewer, "stampList");
        if (val != null) {
            setStampList(val.split(", *"));
        }
        val = getFeatureProperty(viewer, "stampHeight");
        if (val != null) {
            try {
                setStampHeight(Math.max(10, Integer.parseInt(val)));
            } catch (Exception e) {}
        }
    }

    /**
     * Set the list of stamp names to choose from. This
     * defaults to the list in {@link #PREDEFINED}
     * @since 2.11.19
     */
    public void setStampList(String[] stamplist) {
        if (stamplist == null || stamplist.length == 0) {
            throw new IllegalArgumentException("Stamplist length must be > 1");
        }
        this.stamplist = stamplist;
    }

    /**
     * Set the height of the stamp images in the edit component.
     * The default value is 40
     */
    public void setStampHeight(int stampheight) {
        this.stampheight = stampheight;
    }

    public boolean matches(PDFAnnotation annot) {
        if (annot instanceof AnnotationStamp) {
            String type = annot.getType();
            for (int i=0;i<stamplist.length;i++) {
                if (stamplist[i].equals(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getAnnotationType() {
        return UIManager.getString("PDFViewer.annot.Stamp");
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation a) {
        final AnnotationStamp annot = (AnnotationStamp)a;
        final JComponent comp = super.createComponent(pagepanel, a);
        makeComponentInteractive(comp, annot, !annot.isReadOnly(), true, true, pagepanel);
        return comp;
    }

    public PDFAnnotation createNewAnnotation(float x1, float y1, float x2, float y2) {

        AnnotationStamp ret = new AnnotationStamp();
        ret.setRectangle(x1, y1, x2, y2);
        return ret;
    }

    public JComponent createEditComponent(PDFAnnotation annot, boolean readonly, boolean create) {
        try {
            final AnnotationStamp stamp = (AnnotationStamp) annot;
            String type = stamp.getType();
            DocumentPanel dp = getViewer().getActiveDocumentPanel();
            PDFParser parser = dp.getParser();  // This is arbitrary

            JPanel panel = new JPanel(new GridBagLayout());
            addStockDetailsToEditComponent(annot, panel);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.weightx = gbc.weighty = 1;
            gbc.gridwidth = gbc.REMAINDER;
            gbc.fill = gbc.BOTH;

            final StampView sv = new StampView(parser, stampheight, stamplist);
            final JList<String> list = new JList<String>(stamplist) {
                public Dimension getPreferredScrollableViewportSize() {
                    return new Dimension(sv.getMaxStampWidth(), stampheight * 4);
                }
            };
            list.setCellRenderer(sv);
            list.setSelectedValue(type, false);
            list.setFixedCellHeight(stampheight);
            list.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    stamp.setType((String)list.getSelectedValue(), 1);
                }
            });
            if (readonly) {
                list.setEnabled(false);
            }
            panel.add(new JScrollPane(list), gbc);
            return panel;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            // If bfopdf-stamp.jar is missing
            return null;
        }
    }

    protected void copyAnnotationState(PDFAnnotation source, PDFAnnotation target) {
        super.copyAnnotationState(source, target);

        AnnotationStamp sourceStamp = (AnnotationStamp) source;
        AnnotationStamp targetStamp = (AnnotationStamp) target;
        targetStamp.setType(sourceStamp.getType(), sourceStamp.getOpacity());
    }

    private class StampView extends JLabel implements ListCellRenderer<String> {
        private final PDFParser parser;
        private Map<String,ImageIcon> images = new HashMap<String,ImageIcon>();
        private final int height;
        private int maxwidth;

        StampView(PDFParser parser, int height, String[] stamplist) {
            this.parser = parser;
            this.height = height;
            setHorizontalAlignment(CENTER);
            setOpaque(true);
            for (int i=0;i<stamplist.length;i++) {
                ImageIcon icon = getIcon(stamplist[i], parser);     // So we can throw exception if no bfopdf-stamp.jar
                maxwidth = Math.max(maxwidth, icon.getIconWidth());
            }
        }

        int getMaxStampWidth() {
            return maxwidth;
        }

        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            setIcon(getIcon(value, parser));
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            setEnabled(list.isEnabled());
            return this;
        }

        synchronized ImageIcon getIcon(String value, PDFParser parser) {
            ImageIcon img = images.get(value);
            if (img == null) {
                images.put(value, img = new ImageIcon(getStampImage(getStamp(value), height, parser)));
            }
            return img;
        }
    }

    /**
     * Create the image for the specified AnnotationStamp
     * @param stamp the stamp to render
     * @param height the height of the stamp image
     * @param parser the PDFParser to use to convert
     * @since 2.1.19
     */
    public BufferedImage getStampImage(AnnotationStamp stamp, int height, PDFParser parser) {
        float rw = stamp.getRecommendedWidth();
        float rh = stamp.getRecommendedHeight();
        PDFPage page = new PDFPage((int) rw, (int) rh);
        stamp.setPage(page);
        stamp.setRectangle(0, 0, page.getWidth(), page.getHeight());
        PagePainter painter = parser.getPagePainter(page);
        painter.setBackground(null);
        float dpi = height / rh * 72;
        return painter.getImage(dpi, PDFParser.RGBA);
    }

    /**
     * Get an AnnotationStamp for the specified stamp name. By default
     * this calls <code>new {@link AnnotationStamp}(stampname, 1)</code>,
     * but it can be overridden to handle custom stamps if necessary.
     * @since 2.1.19
     */
    public AnnotationStamp getStamp(String stampname) {
        return new AnnotationStamp(stampname, 1);
    }

}
