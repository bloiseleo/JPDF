// $Id: FormChoiceWidgetFactory.java 19740 2014-07-22 13:39:16Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.Event;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;

/**
 * Create annotations to handle {@link WidgetAnnotation} objects belonging to a {@link FormChoice}.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FormChoiceWidgetFactory</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class FormChoiceWidgetFactory extends WidgetComponentFactory {

    /**
     * Create a new FormChoiceWidgetFactory
     * @since 2.10.6
     */
    public FormChoiceWidgetFactory() {
        super("FormChoiceWidgetFactory");
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof WidgetAnnotation && ((WidgetAnnotation)annot).getField() instanceof FormChoice;
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation annot) {
        final WidgetAnnotation widget = (WidgetAnnotation)annot;
        final FormChoice field = (FormChoice)widget.getField();
        JComponent comp;
        if (field.getType()==FormChoice.TYPE_DROPDOWN) {
            comp = createComboComponent(pagepanel, field, widget, true);
        } else if (field.getType()==FormChoice.TYPE_COMBO) {
            comp = createComboComponent(pagepanel, field, widget, false);
        } else {
            comp = createMenuComponent(pagepanel, field, widget);
        }
        return comp;
    }

    private JComponent createComboComponent(final PagePanel pagepanel, final FormChoice field, final WidgetAnnotation widget, boolean readonly) {
        final DocumentPanel docpanel = pagepanel.getDocumentPanel();
        final JSManager js = docpanel.getJSManager();
        final JTextField comp = (JTextField)createComponent(pagepanel, widget, JTextField.class);

        final AbstractDocument document = (AbstractDocument)comp.getDocument();
        final JPopupMenu menu = new JPopupMenu();

        final DocumentFilter documentfilter = new DocumentFilter() {
            public void insertString(FilterBypass fb, int offset, String change, AttributeSet attrs) throws BadLocationException {
                comp.putClientProperty("bfo.HasChanged", "true");
                JSEvent jsevent;
                if ((jsevent=js.runEventFieldKeystroke(docpanel, widget, 0, change, "", false, false, false, offset, offset, false, comp.getText(), false)).getRc()) {
                    fb.insertString(offset, jsevent.getChange(), attrs);
                }
            }
            public void replace(DocumentFilter.FilterBypass fb, int offset, int length, String change, AttributeSet attrs) throws BadLocationException {
                comp.putClientProperty("bfo.HasChanged", "true");
                JSEvent jsevent;
                if ((jsevent=js.runEventFieldKeystroke(docpanel, widget, 0, change, "", false, false, false, offset, offset+length, false, comp.getText(), false)).getRc()) {
                    fb.replace(offset, length, jsevent.getChange(), attrs);
                }
            }
            public void remove(DocumentFilter.FilterBypass fb, int offset, int length) throws BadLocationException {
                comp.putClientProperty("bfo.HasChanged", "true");
                fb.remove(offset, length);
            }
        };

        ActionListener popuplistener = new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                String change = ((JMenuItem)event.getSource()).getText();
                String changeEx = field.getOptions().get(change);
                JSEvent jsevent;
                if ((jsevent=js.runEventFieldKeystroke(docpanel, widget, 0, change, changeEx, false, false, false, -1, -1, false, comp.getText(), false)).getRc()) {
                    comp.putClientProperty("bfo.HasChanged", "true");
                    document.setDocumentFilter(null);
                    comp.setText(changeEx==null ? jsevent.getChange(): jsevent.getChangeEx());
                    comp.putClientProperty("bfo.PopupSelection", comp.getText());
                    document.setDocumentFilter(documentfilter);
                    if (field.isImmediatelyCommitted()) {
                        commitSelection(comp, widget, pagepanel);
                    }
                }
                comp.requestFocusInWindow();
                comp.repaint();
            }
        };

        document.setDocumentFilter(documentfilter);
        for (Iterator<String> i = field.getOptions().keySet().iterator();i.hasNext();) {
            JMenuItem val = new JMenuItem(i.next());
            val.addActionListener(popuplistener);
            menu.add(val);
        }

        comp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                comp.putClientProperty("bfo.HasChanged", "true");
                if (js.runEventFieldKeystroke(docpanel, widget, 2, "", "", false, false, false, -1, -1, false, comp.getText(), true).getRc()) {
                    comp.putClientProperty("bfo.willCommitEvent", event);
                    comp.transferFocus();
                }
            }
        });

        comp.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent event) {
                if (comp.getClientProperty("bfo.HasFocus")==null) {
                    comp.putClientProperty("bfo.HasFocus", "true");
                    js.runEventFieldFocus(docpanel, widget, false, false);
                }
                float fontsize = widget.getTextStyle().getFontSize();
                if (fontsize==0) fontsize = 12;
                fontsize = fontsize * pagepanel.getDPI() / 72;
                if (Math.abs(fontsize - comp.getFont().getSize2D()) > 0.001) {
                    comp.setFont(comp.getFont().deriveFont(fontsize));
                }
                document.setDocumentFilter(null);
                String value = (String)comp.getClientProperty("bfo.PopupSelection");
                if (value==null) {
                    value = field.getValue();
                } else {
                    comp.putClientProperty("bfo.PopupSelection", null);
                }
                Map<String,String> options = field.getOptions();
                for (Iterator<Map.Entry<String,String>> i = options.entrySet().iterator();i.hasNext();) {
                    Map.Entry<String,String> e = i.next();
                    if (e.getValue().equals(value)) {
                        value = e.getKey();
                        break;
                    }
                }
                comp.setText(value);
                document.setDocumentFilter(documentfilter);
                comp.repaint();
            }

            public void focusLost(FocusEvent event) {
                if (!event.isTemporary() && event.getOppositeComponent()!=menu && comp.getClientProperty("bfo.HasFocus")!=null && comp.isValid()) {
                    commitSelection(comp, widget, pagepanel);
                }
            }
        });

        comp.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent event) {
                if (!isWidgetReadOnly(widget, docpanel)) {
                    menu.show(comp, 0, comp.getHeight());
                    menu.requestFocusInWindow();
                }
            }
        });
        return comp;
    }

    private JComponent createMenuComponent(final PagePanel pagepanel, final FormChoice field, final WidgetAnnotation widget) {
        final DocumentPanel docpanel = pagepanel.getDocumentPanel();
        final JSManager js = docpanel.getJSManager();
        @SuppressWarnings("unchecked") final JList<String> comp = (JList<String>)createComponent(pagepanel, widget, JList.class);
        if (field.getType() == FormChoice.TYPE_MULTISCROLLABLE) {
            comp.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        } else {
            comp.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        }

        comp.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent event) {
                int[] oldvals = (int[])comp.getClientProperty("bfo.Selected");
                if (oldvals!=null) {
                    int[] newvals = comp.getSelectedIndices();
                    if (!Arrays.equals(oldvals, newvals)) {
                        comp.putClientProperty("bfo.HasChanged", "true");
                        int count = 0;
                        for (Iterator<Map.Entry<String,String>> j = field.getOptions().entrySet().iterator();j.hasNext();count++) {
                            Map.Entry<String,String> e = j.next();
                            if (count == newvals[newvals.length-1]) {
                                String value = field.getValue();
                                if (value.indexOf("\n") > 0) {
                                    value = value.substring(0, value.indexOf("\n"));
                                }
                                String change = e.getKey();
                                String changeEx = e.getValue();
                                if (js.runEventFieldKeystroke(docpanel, widget, 2, change, changeEx, false, false, false, -1, -1, false, value, false).getRc()) {
                                    comp.putClientProperty("bfo.Selected", newvals);
                                } else {
                                    comp.putClientProperty("bfo.Selected", null);
                                    comp.setSelectedIndices(oldvals);
                                    comp.putClientProperty("bfo.Selected", oldvals);
                                }
                            }
                        }
                    }
                }
            }
        });

        comp.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent event) {
                if (comp.getClientProperty("bfo.HasFocus") == null) {
                    comp.putClientProperty("bfo.HasFocus", "true");
                    js.runEventFieldFocus(docpanel, widget, false, false);
                    float fontsize = widget.getTextStyle().getFontSize();
                    if (fontsize == 0) {
                        fontsize = 12;
                    }
                    fontsize = fontsize * pagepanel.getDPI() / 72;
                    if (Math.abs(fontsize - comp.getFont().getSize2D()) > 0.001) {
                        comp.setFont(comp.getFont().deriveFont(fontsize));
                    }

                    Map<String,String> options = field.getOptions();
                    Vector<String> v = new Vector<String>(options.size());
                    for (Iterator<String> i = options.keySet().iterator();i.hasNext();) {
                        v.add(i.next());
                    }
                    comp.setListData(v);
                    int[] selected = field.getSelectedIndices();
                    comp.setSelectedIndices(selected);
                    comp.putClientProperty("bfo.Selected", selected);
                    comp.repaint();
                }
            }

            public void focusLost(FocusEvent event) {
                if (!event.isTemporary() && comp.getClientProperty("bfo.HasFocus")!=null && comp.isValid()) {
                    comp.putClientProperty("bfo.Selected", null);
                    if (comp.getClientProperty("bfo.HasChanged")!=null) {
                        field.setSelectedIndices(comp.getSelectedIndices());
                        runOtherChange(docpanel, widget);
                        comp.putClientProperty("bfo.HasChanged", null);
                    }
                    js.runEventFieldBlur(docpanel, widget, false, false);
                    comp.setListData(new Vector<String>());
                    comp.repaint();
                    comp.putClientProperty("bfo.HasFocus", null);
                }
            }
        });
        return comp;
    }

    private void commitSelection(final JTextField comp, final WidgetAnnotation widget, final PagePanel pagepanel) {
        final DocumentPanel docpanel = pagepanel.getDocumentPanel();
        final JSManager js = docpanel.getJSManager();
        final FormChoice field = (FormChoice)widget.getField();
        boolean ok = true;
        if (comp.getClientProperty("bfo.HasChanged")!=null) {
            if (comp.getClientProperty("bfo.willCommitEvent")==null) {
                if (!js.runEventFieldKeystroke(docpanel, widget, 1, "", "", false, false, false, -1, -1, false, comp.getText(), true).getRc()) {
                    ok = false;
                }
            }
            if (ok) {
                JSManager console = docpanel.getJSManager();
                String value = comp.getText();
                if (console.runEventFieldValidate(docpanel, widget, value, false, false, "", "", false).getRc()) {
                    Map<String,String> opts = field.getOptions();
                    if (opts.containsKey(value)) {
                        value = opts.get(value);
                    }
                    field.setValue(value);
                    runOtherChange(docpanel, widget);
                } else {
                    ok = false;
                }
            }
            if (ok) {
                js.runEventFieldFormat(docpanel, widget, 1, false);
                js.runEventFieldBlur(docpanel, widget, false, false);
            }
            comp.putClientProperty("bfo.HasChanged", null);
        }
        if (ok) {
            pagepanel.repaint();
            comp.putClientProperty("bfo.HasFocus", null);
        } else {
            comp.requestFocusInWindow();
        }
    }

}
