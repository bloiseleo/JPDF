// $Id: AnnotationComponentFactory.java 41473 2021-10-27 11:11:07Z mike $

package org.faceless.pdf2.viewer2;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import org.faceless.pdf2.*;
import org.faceless.util.AdobeComposite;
import org.faceless.pdf2.viewer2.util.DialogPanel;
import java.awt.event.*;
import javax.swing.plaf.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.swing.undo.*;
import org.faceless.pdf2.viewer2.feature.Undo;
import java.awt.geom.*;
import java.util.prefs.Preferences;
import java.text.*;
import java.util.*;

/**
 * A type of ViewerFeature that creates a {@link JComponent} to represent a
 * {@link PDFAnnotation} on the page. Typically AnnotationComponentFactories
 * are singleton objects, as they do not need to be tied to a specific viewer.
 * For viewing components, all that needs to be overridden are the
 * {@link #matches matches()} and possibly {@link #createComponent createComponent()}
 * methods. If you want to use this factory to edit and/or create new annotations,
 * it's necessary to override the
 * {@link #createEditComponent createEditComponent()},
 * {@link #getAnnotationType getAnnotationType()} and
 * {@link #createNewAnnotation createNewAnnotation()} methods as well.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature or any of its subclasses.
 * <table summary="" style="margin-bottom:1em; border-bottom:1px solid #CCC;">
 * <tr><th>delete</th><td>If set to false, the "delete" option will not be available when an annotation is right-clicked</td></tr>
 * <tr><th>edit</th><td>If set to false, the "edit" option will not be available when an annotation is right-clicked</td></tr>
 * <tr><th>flatten</th><td>If set to false, the "flatten" option will not be available when an annotation is right-clicked</td></tr>
 * <tr><th>edit.set.author</th><td>If set to true, editing an annotation will set the author. The default is false</td></tr>
 * <tr><th>edit.display.field1</th><td>When editing an annotation, the field to display in the top-left of the dialog. Can be set to "author", "subject", "creationDate" or "modifyDate", and the default is "creationDate"</td></tr>
 * <tr><th>edit.display.field2</th><td>When editing an annotation, the field to display in the top-right of the dialog. Can be set to "author", "subject", "creationDate" or "modifyDate", and the default is "author"</td></tr>
 * </table>
 * For example, to remove the "flatten" option for all annotations and the "delete" option for
 * {@link AnnotationStamp} objects only, you could insert this code for an application:
 * <pre class="brush:java">
 * System.setProperty("org.faceless.pdf2.viewer2.AnnotationComponentFactory.flatten", "false");
 * System.setProperty("org.faceless.pdf2.viewer2.feature.AnnotationStampFactory.delete", "false");
 * </pre>
 * </div>
 *
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public abstract class AnnotationComponentFactory extends ViewerFeature {

    private PDFViewer viewer;

    public static transient final Border FOCUSBORDER = new LineBorder(Color.gray, 1) {
        private transient final Stroke stroke = new BasicStroke(1, 0, 0, 1, new float[] { 1, 1 }, 0);
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            if (c.isFocusOwner()) {
                DocumentPanel docpanel = (DocumentPanel)SwingUtilities.getAncestorOfClass(DocumentPanel.class, c);
                if (docpanel == null || docpanel.getJSManager().getAppFocusRect()) {
                    ((Graphics2D)g).setStroke(stroke);
                    super.paintBorder(c, g, x, y, width, height);
                }
            }
        }
        public boolean isBorderOpaque() {
            return false;
        }
    };

    /**
     * Create a new AnnotationComponentFactory
     * @param name the name of this ViewerFeature
     */
    public AnnotationComponentFactory(String name) {
        super(name);
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        this.viewer = viewer;
    }

    /**
     * Indicates whether annotations may be created, removed, or edited.
     */
    public boolean isFactoryReadOnly(DocumentPanel panel) {
        return panel != null && panel.hasPermission("Annotate");
    }

    /**
     * Return the Preferences object used by the viewer (may be <code>null</code>)
     * @since 2.11.7
     */
    public Preferences getPreferences() {
        return viewer==null ? null : viewer.getPreferences();
    }

    /**
     * Return the Viewer this Factory is a part of
     * @since 2.11.7
     */
    public PDFViewer getViewer() {
        return viewer;
    }

    public String toString() {
        return "AnnotationComponentFactory:"+super.toString();
    }

    /**
     * Return true if this AnnotationComponentFactory could create a {@link JComponent}
     * for the specified {@link PDFAnnotation}.
     */
    public abstract boolean matches(PDFAnnotation annot);

    /**
     * <p>
     * Return a JComponent that will visually represent the specified PDFAnnotation.
     * The default implementation returns a JPanel that will display the annotation
     * appearance - it's usually best to call super.createComponent() then add any
     * required listeners in the subclasses.
     * </p><p>
     * By default this method returns a JCompoment whose {@link JComponent#paintComponent paintComponent()}
     * method is overridden to call <code>paintComponent(this, this.ui, g)</code> followed by
     * <code>paintComponentAnnotations(this, g)</code> - typically, the main reason to override this
     * method is when a different type of object is required (eg. a {@link JTextField})
     * </p>
     * @param pagepanel the panel the JComponent will be added to
     * @param annot the annotation
     * @see #paintComponentAnnotations paintComponentAnnotations()
     * @see #paintComponent paintComponent()
     */
    public JComponent createComponent(PagePanel pagepanel, PDFAnnotation annot) {
        JComponent comp = new JPanel() {
            public void paintComponent(Graphics g) {
                 AnnotationComponentFactory.paintComponent(this, this.ui, g);
                 paintComponentAnnotations(this, g);
                 //g.setColor(Color.yellow);
                 //g.drawRect(0, 0, getWidth()-1, getHeight()-1);
            }
        };
        comp.setCursor(null);
        comp.setLayout(null);
        comp.setBorder(FOCUSBORDER);
        comp.setOpaque(false);
        return comp;
    }

    /**
     * Paint any visible annotations that should be drawn on top of the annotation.
     * These are not to be confused with {@link PDFAnnotation} - these annotations
     * are simply graphical additions to the AWT component. A good example is
     * digital signature fields, which should display a tick, cross or question-mark
     * depending on the current verified status of the signature. The default implementation
     * is a no-op.
     * @see #paintComponent paintComponent()
     * @see #createComponent createComponent()
     * @since 2.11.7
     */
    protected void paintComponentAnnotations(JComponent c, Graphics g) {
    }

    /**
     * Return a JComponent that can be used to edit the annotation or display additional
     * information. If the annotation has no dialog that should work with it in this
     * way, this method should return null (the default)
     * @since 2.10
     * @param annot the annotation
     * @param readonly whether we are displaying or editing the annotation
     * @param create if readonly if false, whether we are editing an existing or creating a new annotation
     */
    public JComponent createEditComponent(PDFAnnotation annot, boolean readonly, boolean create) {
        return null;
    }

    private static String getStockDetail(PDFAnnotation annot, String field) {
        if ("author".equalsIgnoreCase(field)) {
            return annot.getAuthor();
        } else if ("subject".equalsIgnoreCase(field)) {
            return annot.getSubject();
        } else if ("creationdate".equalsIgnoreCase(field)) {
            Calendar d = annot.getCreationDate();
            return d == null ? null : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(d.getTime());
        } else if ("modifydate".equalsIgnoreCase(field)) {
            Calendar d = annot.getModifyDate();
            return d == null ? null : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(d.getTime());
        } else {
            return null;
        }
    }

    /**
     * Convenience method that can be used by factories to add universal details
     * about the annotation (date, author etc) to the EditComponent in a standardized
     * way (assuming that the <code>editorpane</code> is using a GridBagLayout).
     * @since 2.11.19
     */
    protected void addStockDetailsToEditComponent(PDFAnnotation annot, JComponent editorpane) {
        if (editorpane.getLayout() instanceof GridBagLayout) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = gbc.NONE;
            gbc.weightx = 1;

            String tl = getStockDetail(annot, getFeatureProperty(viewer, "edit.display.field1"));
            if (tl == null) {
                tl = getStockDetail(annot, "creationDate");
            }
            String tr = getStockDetail(annot, getFeatureProperty(viewer, "edit.display.field2"));
            if (tr == null) {
                tr = getStockDetail(annot, "author");
            }

            if (tl != null) {
                gbc.anchor = GridBagConstraints.WEST;
                gbc.gridwidth = tr == null ? gbc.REMAINDER : 1;
                editorpane.add(new JLabel(tl), gbc);
            }
            if (tr != null) {
                gbc.anchor = gbc.EAST;
                gbc.gridwidth = gbc.REMAINDER;
                editorpane.add(new JLabel(tr), gbc);
            }
        }
    }

    /**
     * Called after an annotation is added or edited - by default, a no-op
     * @param annot the annotation
     * @param pagepanel the PagePanel the Annotation was added on
     * @param action one of "create", "edit", "flatten" or "delete"
     * @since 2.11.12
     */
    public void postEdit(PDFAnnotation annot, PagePanel pagepanel, String action) {
    }

    /**
     * Return the name of the type of widgets this AnnotationComponentFactory creates
     * or edits.
     * @since 2.10
     */
    public String getAnnotationType() {
        return null;
    }

    /**
     * Return a brand new annotation of the type that is edited with this factory. If
     * this factory cannot be used to create a new annotation, this method can return
     * <code>null</code> (the default)
     * @since 2.10
     */
    public PDFAnnotation createNewAnnotation() {
        return createNewAnnotation(0, 0, 1, 1);
    }

    /**
     * Returns a new annotation of this factory's type with specified
     * bounds.
     * @param x1 the lower left x coordinate
     * @param y1 the lower left y coordinate
     * @param x2 the top right x coordinate
     * @param y2 the top right y coordinate
     */
    public PDFAnnotation createNewAnnotation(float x1, float y1, float x2, float y2) {
        return null;
    }

    /**
     * A convenient static method which can be called to paint the content of a Component
     * with the standard PDF-centric way (painting its appearance streams). This is usually
     * called by the {@link JComponent#paintComponent paintComponent()} method of the
     * {@link JComponent} returned from {@link #createComponent createComponent}
     * to do the actual painting.
     *
     * @param comp the JComponent we're painting
     * @param ui the <code>ui</code> field of that component
     * @param g the Graphics object we're painting to
     * @see #paintComponentAnnotations paintComponentAnnotations()
     * @see #createComponent createComponent()
     * @since 2.11.7
     */
    public static final void paintComponent(JComponent comp, ComponentUI ui, Graphics g) {
        Graphics2D g2 = (Graphics2D)g.create();
        try {
            if (!(comp instanceof JPanel) && ui!=null && comp.isFocusOwner()) {
                g2.setColor(Color.white);       // Do this ourselves as comp is not opaque
                g2.fillRect(0, 0, comp.getWidth(), comp.getHeight());
                ui.update(g2, comp);
            } else {
                BufferedImage[] images = (BufferedImage[])comp.getClientProperty("images");
                Composite[] composites = (Composite[])comp.getClientProperty("imagecomposites");
                if (images!=null && composites!=null) {
                    String state = (String)comp.getClientProperty("state");
                    int statenum = state==null || "N".equals(state) ? 0 : "D".equals(state) ? 1 : 2;
                    BufferedImage image = images[statenum];
                    Composite composite = composites[statenum];
                    if (image==null) {
                        image = images[0];
                        composite = composites[0];
                    }
                    if (composite instanceof AdobeComposite && Util.JAVA_MINOR_VERSION > 6) {
                        // This will be a highlight annotation
                        // https://bugs.openjdk.java.net/browse/JDK-8041585
                        // use workaround. Verified in JDK11.0.4 as still an issue.
                        PDFAnnotation annot = (PDFAnnotation) comp.getClientProperty("pdf.annotation");
                        if (annot instanceof AnnotationMarkup) {
                            String type = ((AnnotationMarkup) annot).getType();
                            if ("Highlight".equals(type)) {
                                Color color = annot.getColor();
                                float[] comps = color.getRGBColorComponents(new float[3]);
                                color = new Color(comps[0], comps[1], comps[2], 0.5f);
                                g2.setColor(color);
                                g2.fillRect(0, 0, comp.getWidth(), comp.getHeight());
                                return;
                            }
                        }
                    }
                    if (image!=null) {
                        double iw = image.getWidth();
                        double ih = image.getHeight();
                        double cw = (double) comp.getWidth();
                        double ch = (double) comp.getHeight();
                        if (composite!=null) {
                            g2.setComposite(composite);
                        }
                        AffineTransform tran = new AffineTransform();
                        if (iw!=cw || ih!=ch) {
                            tran.scale(cw/iw, ch/ih);
                        }
                        g2.drawRenderedImage(image, tran);
                        PDFAnnotation annot = (PDFAnnotation)comp.getClientProperty("pdf.annotation");
                        DocumentPanel docpanel = (DocumentPanel)SwingUtilities.getAncestorOfClass(DocumentPanel.class, comp);
                        if (annot instanceof WidgetAnnotation && !isAnnotationReadOnly(annot, docpanel)) {
                            if (docpanel != null && docpanel.getJSManager().getAppRuntimeHighlight()) {
                                Color c = docpanel.getJSManager().getAppRuntimeHighlightColor();
                                if (Util.canUseComposites()) {
                                    g2.setComposite(AdobeComposite.getInstance("Multiply"));
                                    g2.setColor(c);
                                    try {
                                        g2.fillRect(0, 0, comp.getWidth(), comp.getHeight());
                                    } catch (InternalError e) {
                                        // Java 8 on OSX/Ubuntu throwing "Not Implemented" for this, cheers Oracle.
                                        Util.setCanUseComposites(false);
                                    }
                                }
                                if (!Util.canUseComposites()) {
                                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
                                    g2.setColor(c);
                                    g2.fillRect(0, 0, comp.getWidth(), comp.getHeight());
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Cause the specified JComponent to be positioned at the PDF co-ordinates
     * specified by <code>rect</code>.
     * If the page is scrolled or zoomed the component will be zoomed and resized to match.
     * @param component the Component
     * @param rect the component location in PDF space
     * @since 2.11.25
     */
    public static final void bindComponentLocation(JComponent component, Rectangle2D rect) {
        bindComponentLocation(component, new float[] { (float)rect.getMinX(), (float)rect.getMinY(), (float)rect.getMaxX(), (float)rect.getMaxY() });
    }

    /**
     * Cause the specified JComponent to be positioned at the PDF co-ordinates
     * specified by <code>rect</code>.
     * If the page is scrolled or zoomed the component will be zoomed and resized to match.
     * @param component the Component
     * @param rect the component location in PDF space, specified as [x1, y1, x2, y2]
     */
    public static final void bindComponentLocation(JComponent component, float[] rect) {
        if (component!=null) component.putClientProperty("pdf.rect", rect);
    }

    /**
     * Cause the specified JComponent to be positioned at the specified PDF co-ordinates.
     * If the page is scrolled or zoomed the component will be zoomed and resized to match.
     * @param component the Component
     * @param x1 the left-most X co-ordinate of the component in PDF space
     * @param y1 the bottom-most Y co-ordinate of the component in PDF space
     * @param x2 the right-most X co-ordinate of the component in PDF space
     * @param y2 the top-most Y co-ordinate of the component in PDF space
     */
    public static final void bindComponentLocation(JComponent component, float x1, float y1, float x2, float y2) {
        bindComponentLocation(component, new float[] { x1, y1, x2, y2 });
    }

    /**
     * Cause the specified JComponent to be positioned at same position as the PDFAnnotation.
     * If the page is scrolled or zoomed the component will be zoomed and resized to match.
     * @param component the Component
     * @param annot the PDFAnnotation to bind the components position to
     */
    public static final void bindComponentLocation(JComponent component, PDFAnnotation annot) {
        if (component!=null) component.putClientProperty("pdf.annotation", annot);
    }

    private static final AnnotationComponentFactory DEFAULT = new AnnotationComponentFactory("Default") {
        public boolean matches(PDFAnnotation annot) {
            return true;
        }
    };

    static AnnotationComponentFactory getDefaultFactory() {
        return DEFAULT;
    }

    /**
     * Given an Annotation component created by a subclass of this factory, add
     * appropriate handlers to it to make it interact with mouse movement by the user.
     * @param comp the JComponent
     * @param annot the PDFAnnotation
     * @param showproperties whether the Component should show properties on double-click or popup menu on right-click. If the component is read-only the dialog will not allow edits.
     * @param movable whether the Component should be movable. If the annotation is {@link PDFAnnotation#isReadOnly read-only} or {@link PDFAnnotation#isPositionLocked locked} this value will be overridden
     * @param resizable whether the Component should be resizable as opposed to just draggable. If the annotation is {@link PDFAnnotation#isReadOnly read-only} or {@link PDFAnnotation#isPositionLocked locked} this value will be overridden
     * @since 2.12
     */
    public void makeComponentInteractive(JComponent comp, PDFAnnotation annot, boolean showproperties, boolean movable, boolean resizable, PagePanel pagepanel) {
        AnnotationDragListener listener = this.new AnnotationDragListener(comp, annot, showproperties, movable, resizable, pagepanel.getDocumentPanel());
        comp.addMouseListener(listener);
        comp.addMouseMotionListener(listener);
    }

    /**
     * Copies the properties of a given source annotation created by this
     * factory to a target annotation of the same type.
     * This is used to make a temporary copy of an annotation for editing
     * purposes.
     * @param source the annotation to copy properties from
     * @param target the annotation to copy properties to
     * @since 2.11.7
     */
    protected void copyAnnotationState(PDFAnnotation source, PDFAnnotation target) {
        target.setColor(source.getColor());
        target.setOpacity(source.getOpacity());
        target.setContents(source.getContents());
        target.setColor(source.getColor());
        target.setOpacity(source.getOpacity());
        target.setSubject(source.getSubject());
        target.setInReplyTo(source.getInReplyTo());
        target.setAuthor(source.getAuthor());
        target.setCreationDate(source.getCreationDate());
        target.setUniqueID(source.getUniqueID());
        target.setVisible(source.isVisible());
        target.setPrintable(source.isPrintable());
        target.setReadOnly(source.isReadOnly());
        target.setLocked(source.isPositionLocked(), source.isContentLocked());
    }

    private static final int DRAGZONE = 10;

    private static boolean isAnnotationReadOnly(PDFAnnotation annot, DocumentPanel docpanel) {
        if (annot instanceof WidgetAnnotation) {
            return annot.isReadOnly() || ((WidgetAnnotation)annot).getField().isReadOnly() || (docpanel != null && !docpanel.hasPermission("FormFill"));
        } else {
            return annot.isReadOnly() || (docpanel != null && !docpanel.hasPermission("Annotate"));
        }
    }

    private class AnnotationDragListener implements MouseListener, MouseMotionListener, DocumentPanelListener  {
        private Point orig, pageorig;
        private final Component comp;
        private final PDFAnnotation annot;
        private boolean movable, resizable;
        private final boolean showproperties, factorymovable, factoryresizable;

        AnnotationDragListener(Component comp, PDFAnnotation annot, boolean showproperties, boolean movable, boolean resizable, DocumentPanel docpanel) {
            this.comp = comp;
            this.annot = annot;
            this.showproperties = showproperties;
            this.factoryresizable = resizable;
            this.factorymovable = movable;
            this.resizable = !isAnnotationReadOnly(annot, docpanel) && !annot.isPositionLocked() && factoryresizable;
            this.movable = this.resizable || (!isAnnotationReadOnly(annot, docpanel) && !annot.isPositionLocked() && factorymovable);

            int cursortype = movable ? Cursor.MOVE_CURSOR : Cursor.HAND_CURSOR;
            comp.setCursor(Cursor.getPredefinedCursor(cursortype));
            docpanel.addDocumentPanelListener(this);
        }

        public void documentUpdated(DocumentPanelEvent event) {
            DocumentPanel docpanel = event.getDocumentPanel();
            this.resizable = !isAnnotationReadOnly(annot, docpanel) && !annot.isPositionLocked() && factoryresizable;
            this.movable = this.resizable || (!isAnnotationReadOnly(annot, docpanel) && !annot.isPositionLocked() && factorymovable);
        }

        public void mouseClicked(MouseEvent event) {
            if (event.isPopupTrigger() && showproperties) {
                orig = null;
                popupPropertyMenu(annot, comp, event.getPoint());
            } else if (event.getClickCount() == 2 && showproperties) {
                orig = null;
                Action edit = createEditAction(annot, comp);
                if (edit != null) {
                    edit.actionPerformed(new ActionEvent(event.getSource(), event.getID(), "edit"));
                }
            }
        }

        public void mouseMoved(MouseEvent event) {
            if (movable) {
                int cursortype = movable ? Cursor.MOVE_CURSOR : Cursor.HAND_CURSOR;
                int x = event.getX();
                int y = event.getY();
                if (resizable) {
                    if (x < DRAGZONE) {
                        if (y < DRAGZONE) {
                            cursortype = Cursor.NW_RESIZE_CURSOR;
                        } else if (comp.getHeight()-y < DRAGZONE) {
                            cursortype = Cursor.SW_RESIZE_CURSOR;
                        } else {
                            cursortype = Cursor.W_RESIZE_CURSOR;
                        }
                    } else if (comp.getWidth() - x < DRAGZONE) {
                        if (y < DRAGZONE) {
                            cursortype = Cursor.NE_RESIZE_CURSOR;
                        } else if (comp.getHeight()-y < DRAGZONE) {
                            cursortype = Cursor.SE_RESIZE_CURSOR;
                        } else {
                            cursortype = Cursor.E_RESIZE_CURSOR;
                        }
                    } else if (y < DRAGZONE) {
                        cursortype = Cursor.N_RESIZE_CURSOR;
                    } else if (comp.getHeight() - y < DRAGZONE) {
                        cursortype = Cursor.S_RESIZE_CURSOR;
                    }
                }
                comp.setCursor(Cursor.getPredefinedCursor(cursortype));
            }
        }

        public void mouseExited(MouseEvent event) {
            if (orig != null) {
                updateAnnotToMatchComp();
                orig = null;
            }
        }

        public void mousePressed(MouseEvent event) {
            if (event.isPopupTrigger() && showproperties) {
                mouseClicked(event);
            } else if (movable) {
                orig = event.getPoint();
                pageorig = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), SwingUtilities.getAncestorOfClass(PagePanel.class, event.getComponent()));
            }
        }

        public void mouseReleased(MouseEvent event) {
            if (event.isPopupTrigger() && showproperties) {
                mouseClicked(event);
            } else if (movable) {
                Point pagepoint = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), SwingUtilities.getAncestorOfClass(PagePanel.class, event.getComponent()));
                if (!pagepoint.equals(pageorig)) {
                    updateAnnotToMatchComp();
                }
            }
            orig = null;
            pageorig = null;
        }

        private boolean isValidPoint(MouseEvent event) {
            Point p = event.getPoint();
            Component pagepanel = SwingUtilities.getAncestorOfClass(PagePanel.class, event.getComponent());
            p = SwingUtilities.convertPoint(event.getComponent(), p, pagepanel);
            Rectangle r = new Rectangle(0, 0, pagepanel.getWidth(), pagepanel.getHeight());
            return r.contains(p);
        }

        public void mouseDragged(MouseEvent event) {
            if (orig == null) {
                mousePressed(event);
            } else {
                int type = comp.getCursor().getType();
                if (isValidPoint(event)) {
                    int dx = event.getX() - orig.x;
                    int dy = event.getY() - orig.y;
                    Rectangle r = comp.getBounds();
                    boolean moving = type==Cursor.MOVE_CURSOR;
                    boolean resizing = type==Cursor.W_RESIZE_CURSOR || type==Cursor.NW_RESIZE_CURSOR || type==Cursor.SW_RESIZE_CURSOR || type==Cursor.E_RESIZE_CURSOR || type==Cursor.NE_RESIZE_CURSOR || type==Cursor.SE_RESIZE_CURSOR || type==Cursor.N_RESIZE_CURSOR || type==Cursor.S_RESIZE_CURSOR;
                    if (resizing && resizable) {
                        int tx = type==Cursor.W_RESIZE_CURSOR || type==Cursor.NW_RESIZE_CURSOR || type==Cursor.SW_RESIZE_CURSOR ? -1 : type==Cursor.E_RESIZE_CURSOR || type==Cursor.NE_RESIZE_CURSOR || type==Cursor.SE_RESIZE_CURSOR ? 1 : 0;
                        int ty = type==Cursor.N_RESIZE_CURSOR || type==Cursor.NW_RESIZE_CURSOR || type==Cursor.NE_RESIZE_CURSOR ? -1 : type==Cursor.S_RESIZE_CURSOR || type==Cursor.SW_RESIZE_CURSOR || type==Cursor.SE_RESIZE_CURSOR ? 1 : 0;
                        orig.setLocation(event.getX(), event.getY());
                        if (tx<0) { r.x += dx; r.width -= dx; orig.x -= dx; }
                        if (tx>0) { r.width += dx; }
                        if (ty<0) { r.y += dy; r.height -= dy; orig.y -= dy; }
                        if (ty>0) { r.height += dy; }
                        comp.setBounds(r);
                    } else if (moving && movable) {
                        r.x += dx;
                        r.y += dy;
                        orig.setLocation(event.getX() - dx, event.getY() - dy);
                        comp.setBounds(r);
                    }
                }
            }
        }

        private void updateAnnotToMatchComp() {
            if (orig != null) {
                final boolean move = comp.getCursor().getType() == Cursor.MOVE_CURSOR;
                PagePanel pagepanel = (PagePanel)SwingUtilities.getAncestorOfClass(PagePanel.class, comp);
                Rectangle r = comp.getBounds();
                final Point2D tl = pagepanel.getPDFPoint(r.x, r.y);
                final Point2D br = pagepanel.getPDFPoint(r.x + r.width - 1, r.y + r.height - 1);
                final float[] oldrect = annot.getRectangle();
                final float x1 = (float)tl.getX();
                final float y1 = (float)tl.getY();
                final float x2 = (float)br.getX();
                final float y2 = (float)br.getY();
                annot.setRectangle(x1, y2, x2, y1);
                annot.rebuild();

                pagepanel.getDocumentPanel().fireUndoableEditEvent(new UndoableEditEvent(pagepanel.getDocumentPanel(), new AbstractUndoableEdit() {
                    public String getPresentationName() {
                        return move ? UIManager.getString("InternalFrameTitlePane.moveButtonText") : UIManager.getString("InternalFrameTitlePane.sizeButtonText");
                    }
                    public void undo() {
                        super.undo();
                        if (oldrect != null) {
                            annot.setRectangle(oldrect[0], oldrect[1], oldrect[2], oldrect[3]);
                        }
                        annot.rebuild();
                    }
                    public void redo() {
                        super.redo();
                        annot.setRectangle(x1, y1, x2, y2);
                        annot.rebuild();
                    }
                }));
            }
        }

        public void mouseEntered(MouseEvent event) {
        }

    }

    /**
     * Display the Property menu for this annotation. This method is called by an
     * {@link AnnotationComponentFactory} when the component is right-clicked, and it
     * should display the edit and delete actions.
     * @since 2.11.7
     */
    protected void popupPropertyMenu(PDFAnnotation annot, Component comp, Point where) {
        Action edit = createEditAction(annot, comp);
        Action delete = createDeleteAction(annot, comp);
        Action flatten = createFlattenAction(annot, comp);
        JPopupMenu menu = new JPopupMenu();
        if (edit!=null) {
            menu.add(edit);
        }
        if (flatten!=null) {
            menu.add(flatten);
        }
        if (delete!=null) {
            menu.add(delete);
        }
        if (menu.getComponentCount() > 0) {
            menu.pack();
            menu.show(comp, where.x, where.y);
        }
        menu.setSelected(null);
    }

    protected Action createEditAction(PDFAnnotation annot, Component comp) {
        if ("false".equals(getFeatureProperty(viewer, "edit"))) {
            return null;
        } else {
            return this.new EditAction(annot, comp);
        }
    }

    protected Action createDeleteAction(PDFAnnotation annot, Component comp) {
        DocumentPanel docpanel = (DocumentPanel)SwingUtilities.getAncestorOfClass(DocumentPanel.class, comp);
        if ("false".equals(getFeatureProperty(viewer, "delete")) || isAnnotationReadOnly(annot, docpanel)) {
            return null;
        } else {
            return this.new DeleteAction(annot, comp);
        }
    }

    protected Action createFlattenAction(PDFAnnotation annot, Component comp) {
        DocumentPanel docpanel = (DocumentPanel)SwingUtilities.getAncestorOfClass(DocumentPanel.class, comp);
        if ("false".equals(getFeatureProperty(viewer, "flatten")) || isAnnotationReadOnly(annot, docpanel)) {
            return null;
        } else {
            return this.new FlattenAction(annot, comp);
        }
    }


    private class EditAction extends AbstractAction {

        private PDFAnnotation annot, backup, editable;
        private Component comp;
        private JDialog dialog;

        EditAction(PDFAnnotation annot, Component comp) {
            super(UIManager.getString("PDFViewer.Edit"));
            this.annot = annot;
            this.comp = comp;
        }

        public void actionPerformed(ActionEvent event) {
            PagePanel pagepanel = (PagePanel)SwingUtilities.getAncestorOfClass(PagePanel.class, comp);
            boolean readonly = isAnnotationReadOnly(annot, pagepanel.getDocumentPanel());
            editable = createNewAnnotation();
            copyAnnotationState(annot, editable);
            editable.setModifyDate(annot.getModifyDate());

            DialogPanel dialog = new DialogPanel(!readonly);
            JComponent editcomp = createEditComponent(editable, readonly, false);
            if (editcomp != null) {
                dialog.addComponent(editcomp);
                if (dialog.showDialog(pagepanel.getDocumentPanel(), UIManager.getString("PDFViewer.Annotation")) && !readonly) {
                    backup = createNewAnnotation();
                    copyAnnotationState(annot, backup);
                    backup.setModifyDate(annot.getModifyDate());
                    if (getFeatureProperty(viewer, "edit.set.author") != null) {
                        editable.setAuthor(getViewer().getCurrentUser());
                    }
                    copyAnnotationState(editable, annot);
                    annot.rebuild();

                    postEdit(annot, pagepanel, "edit");

                    pagepanel.getDocumentPanel().fireUndoableEditEvent(new UndoableEditEvent(pagepanel.getDocumentPanel(), new AbstractUndoableEdit() {
                        public String getPresentationName() {
                            return UIManager.getString("PDFViewer.Edit");
                        }
                        public void undo() {
                            super.undo();
                            copyAnnotationState(backup, annot);
                            annot.setModifyDate(backup.getModifyDate());
                            annot.rebuild();
                        }
                        public void redo() {
                            super.redo();
                            copyAnnotationState(editable, annot);
                            annot.setModifyDate(editable.getModifyDate());
                            annot.rebuild();
                        }
                    }));
                }
            }
        }

    }

    private class DeleteAction extends AbstractAction {

        final PDFAnnotation annot;
        final Component comp;

        DeleteAction(PDFAnnotation annot, Component comp) {
            super(UIManager.getString("PDFViewer.Delete"));
            this.annot = annot;
            this.comp = comp;
        }

        public void actionPerformed(ActionEvent event) {
            PagePanel pagepanel = (PagePanel)SwingUtilities.getAncestorOfClass(PagePanel.class, comp);
            DocumentPanel docpanel = pagepanel.getDocumentPanel();
            final PDFPage page = annot.getPage();
            annot.setPage(null);
            postEdit(annot, pagepanel, "delete");

            docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new AbstractUndoableEdit() {
                public String getPresentationName() {
                    return UIManager.getString("PDFViewer.Delete");
                }
                public void undo() {
                    super.undo();
                    annot.setPage(page);
                    annot.rebuild();
                }
                public void redo() {
                    super.redo();
                    annot.setPage(null);
                }
            }));

        }

    }

    private class FlattenAction extends AbstractAction {

        final PDFAnnotation annot;
        final Component comp;

        FlattenAction(PDFAnnotation annot, Component comp) {
            super(UIManager.getString("PDFViewer.Flatten"));
            this.annot = annot;
            this.comp = comp;
        }

        public void actionPerformed(ActionEvent event) {
            PagePanel pagepanel = (PagePanel)SwingUtilities.getAncestorOfClass(PagePanel.class, comp);
            DocumentPanel docpanel = pagepanel.getDocumentPanel();
            PDFPage page = annot.getPage();
            annot.flatten();
            page.flush();
            postEdit(annot, pagepanel, "flatten");
            docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, Undo.DISCARD));
        }

    }

}
