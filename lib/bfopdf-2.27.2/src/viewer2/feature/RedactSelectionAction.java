// $Id: RedactSelectionAction.java 21903 2015-09-08 17:44:23Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.PropertyParser;
import org.faceless.pdf2.*;
import java.awt.Color;
import java.awt.geom.*;
import javax.swing.*;
import java.util.*;
import java.io.IOException;

/**
 * A {@link TextSelectionAction} that will redact the selected
 * area of the PDF using the {@link Redactor} class.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>color</th><td>A 32-bit color value, eg 0x80FF0000 (for translucent red)</td></tr>
 * <tr><th>type</th><td>The type of Redact - Highlight, Underline, StrikeOut or Squiggly</td></tr>
 * <tr><th>description</th><td>The description of this markup</td></tr>
 * <tr><th>kernadjust</th><td>The amount to trim the redacted area to allow for kerning - see {@link Redactor#contractAreaAlongBaseline}. The default is 0.1</td></tr>
 * </table>
 * </div>
 * <span class="featurename">The name of this feature is <span class="featureactualname">RedactSelectionAction</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 *
 * @since 2.11.8
 */
public class RedactSelectionAction extends ViewerFeature implements TextSelectionAction, AreaSelectionAction, DocumentPanelListener {

    private Color redactionColor;
    private float kernadjust = 0.1f;
    private boolean enabled;

    public RedactSelectionAction() {
        super("RedactSelectionAction");
    }

    /**
     * Set the description that's returned by {@link #getDescription}
     */
    public void setColor(Color color) {
        redactionColor = color;
    }

    public Color getColor() {
        return redactionColor;
    }

    public String getDescription() {
        return UIManager.getString("PDFViewer.Redact");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
        setColor(PropertyParser.getColor(getFeatureProperty(viewer, "color"), getColor()));
        String s = getFeatureProperty(viewer, "kernadjust");
        if (s != null) {
            try {
                float v = Float.parseFloat(s);
                if (v >= 0 && v < 0.4) {
                    kernadjust = v;
                }
            } catch (NumberFormatException e) { }
        }
    }

    public void documentUpdated(DocumentPanelEvent event) {
        DocumentPanel docpanel = event.getDocumentPanel();
        String type = event.getType();
        if (type.equals("activated") || (type.equals("permissionChanged") && docpanel == docpanel.getViewer().getActiveDocumentPanel())) {
            enabled = docpanel.getPDF() != null && docpanel.hasPermission("PageEdit");
        } else if (type.equals("deactivated")) {
            enabled = false;
        }
    }

    private Redactor newRedactor() {
        Redactor redactor = new Redactor();
        if (redactionColor != null) {
            redactor.setRedactionColor(redactionColor);
        }
        return redactor;
    }

    public void selectAction(DocumentPanel docpanel, TextSelection.RangeList list) {
        if (!removeSignatures(docpanel)) {
            return;
        }
        Redactor redactor = newRedactor();
        Set<PDFPage> pages = list.getPages();
        for (Iterator<PDFPage> j = pages.iterator();j.hasNext();) {
            PDFPage page = j.next();
            float[] corners = list.getCorners(page);
            Redactor.contractAreaAlongBaseline(corners, kernadjust);
            Area area = new Area();
            for (int i=0;i<corners.length;i+=8) {
                GeneralPath p = new GeneralPath();
                p.moveTo(corners[i+0], corners[i+1]);
                p.lineTo(corners[i+2], corners[i+3]);
                p.lineTo(corners[i+4], corners[i+5]);
                p.lineTo(corners[i+6], corners[i+7]);
                p.closePath();
                area.add(new Area(p));
            }
            redactor.addArea(page, area);
        }
        try {
            redactor.redact();
            removeAnnotations(redactor, docpanel);
        } catch (Exception e) {
            Util.displayThrowable(e, docpanel);
        }
    }

    public void selectArea(PagePanel pagepanel, Rectangle2D area) {
        if (!removeSignatures(pagepanel.getDocumentPanel())) {
            return;
        }
        PDFPage page = pagepanel.getPage();
        Redactor redactor = newRedactor();
        redactor.addArea(page, new Area(area));
        try {
            redactor.redact();
            removeAnnotations(redactor, pagepanel.getDocumentPanel());
        } catch (Exception e) {
            Util.displayThrowable(e, pagepanel.getDocumentPanel().getViewer());
        }
    }

    private boolean removeSignatures(DocumentPanel docpanel) {
        PDF pdf = docpanel.getPDF();
        if (pdf.getBasicOutputProfile().isSet(OutputProfile.Feature.DigitallySigned)) {
            if (JOptionPane.showConfirmDialog(docpanel, UIManager.getString("PDFViewer.ConfirmRedactSignatures"), UIManager.getString("PDFViewer.Confirm"), JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) {
                OutputProfile profile = new OutputProfile(OutputProfile.Default);
                profile.setDenied(OutputProfile.Feature.DigitallySigned);
                new OutputProfiler(pdf).apply(profile);
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    private void removeAnnotations(Redactor redactor, DocumentPanel docpanel) {
        List<PDFAnnotation> annots = redactor.findAnnotations();
        if (annots.size() > 0 && JOptionPane.showConfirmDialog(docpanel, UIManager.getString("PDFViewer.ConfirmRedactAnnotations"), UIManager.getString("PDFViewer.Confirm"), JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) {
            for (int i=0;i<annots.size();i++) {
                annots.get(i).setPage(null);
            }
        }
    }

}
