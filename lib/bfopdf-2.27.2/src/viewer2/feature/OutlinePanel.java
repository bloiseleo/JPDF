// $Id: OutlinePanel.java 39834 2021-04-23 19:33:08Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Create a {@link SidePanel} that will display the document bookmarks, as returned
 * by {@link PDF#getBookmarks}.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ShowHideBookmarks</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class OutlinePanel extends SidePanelFactory {

    /**
     * Create a new OutlinePanel
     * @since 2.11
     */
    public OutlinePanel() {
        super("ShowHideBookmarks");
    }

    public boolean isSidePanelRequired(DocumentPanel docpanel) {
        PDF pdf = docpanel.getPDF();
        // If we are still loading, assume that we are required
        // until all pages have arrived and we can safely say we are not.
        return pdf.getLoadState(-1) != null || pdf.getBookmarks().size() > 0;
    }

    public SidePanel createSidePanel() {
        return new OutlinePanelImpl();
    }
}

/**
 * A {@link SidePanel} representing the "Outline" or "Bookmark" panel.
 *
 * <p><i>
 * This code is copyright the Big Faceless Organization. You're welcome to
 * use, modify and distribute it in any form in your own projects, provided
 * those projects continue to make use of the Big Faceless PDF library.
 * </i></p>
 */
class OutlinePanelImpl extends JPanel implements TreeSelectionListener, SidePanel {

    private JTree outline;
    private DefaultMutableTreeNode root;
    private JScrollPane scrollpane;
    private DocumentPanel docpanel;
    private PDF pdf;
    private Icon icon;
    private JLabel spinner;

    OutlinePanelImpl() {
        super(new BorderLayout());
        setOpaque(true);
        scrollpane = new JScrollPane();
        Util.fixScrollPaneKeyBindings(scrollpane);
        this.icon = UIManager.getIcon("PDFViewer.Feature.OutlinePanel.icon");
        spinner = new JLabel(UIManager.getIcon("PDFViewer.Spinner.icon"));
    }

    public Icon getIcon() {
        return icon;
    }

    public String getName() {
        return "Bookmarks";
    }

    public void panelVisible() {
    }

    public void panelHidden() {
    }

    public void setDocumentPanel(final DocumentPanel docpanel) {
        if (docpanel == this.docpanel) {
            return;
        }
        this.docpanel = docpanel;
        PDF pdf = docpanel == null ? null : docpanel.getPDF();
        if (pdf != null) {
            remove(scrollpane);
            add(spinner, BorderLayout.CENTER);
            revalidate();

            docpanel.getLinearizedSupport().invokeOnCompletion(new Runnable() {
                public void run() {
                    docpanel.getPDF().getBookmarks();
                }
            }, new Runnable() {
                public void run() {
                    if (docpanel != null && docpanel.getPDF() != null) {
                        List<PDFBookmark> bookmarks = docpanel.getPDF().getBookmarks();
                        if (bookmarks.isEmpty()) {
                            docpanel.removeSidePanel(OutlinePanelImpl.this);
                        } else {
                            initializeTree();

                            ArrayList<TreePath> l = new ArrayList<TreePath>();
                            loadOutline(bookmarks, root, l);
                            for (int i=0;i<l.size();i++) {
                                outline.expandPath(l.get(i));
                            }
                            scrollpane.setViewportView(outline);
                            outline.setRootVisible(true);
                            outline.expandRow(0);
                        }
                        remove(spinner);
                        add(scrollpane, BorderLayout.CENTER);
                        docpanel.addSidePanel(OutlinePanelImpl.this);
                        revalidate();
                    }
                }
            });
        }
    }

    private void initializeTree() {
        final Font[] fonts = new Font[4];
        fonts[0] = UIManager.getFont("Tree.font");
        fonts[1] = fonts[0].deriveFont(Font.BOLD);
        fonts[2] = fonts[0].deriveFont(Font.ITALIC);
        fonts[3] = fonts[0].deriveFont(Font.BOLD + Font.ITALIC);

        root = new DefaultMutableTreeNode(null) {
            public String toString() { return UIManager.getString("PDFViewer.Bookmarks"); }
        };
        outline = new JTree(root);
        Insets i = UIManager.getInsets("PDFViewer.Feature.OutlinePanel.margin");
        if (i != null) {
            outline.setBorder(BorderFactory.createEmptyBorder(i.top, i.left, i.bottom, i.right));
        }
        outline.setFont(fonts[0]);
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                PDFBookmark bookmark = (PDFBookmark)((DefaultMutableTreeNode)value).getUserObject();
                if (bookmark != null) {
                    Color color = bookmark.getColor();
                    setTextNonSelectionColor(color==null ? Color.black : color);
                    int fontindex = 0;
                    if (bookmark.isBold()) {
                        fontindex += 1;
                    }
                    if (bookmark.isItalic()) {
                        fontindex += 2;
                    }
                    setFont(fonts[fontindex]);
                }
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                return this;
            }
        };

        // Optionally replace the page icon (Windows had? issues with 8-bit images)
        Icon icon = UIManager.getIcon("PDFViewer.Feature.OutlinePanel.page.icon");
        if (icon != null) {
            renderer.setLeafIcon(icon);
        }
        outline.setCellRenderer(renderer);
        outline.setEditable(false);
        outline.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        outline.addTreeSelectionListener(OutlinePanelImpl.this);

        outline.addMouseMotionListener(new MouseMotionListener() {
            public void mouseMoved(MouseEvent e) {
                int row = outline.getRowForLocation(e.getX(), e.getY());
                setCursor(row == -1 ? null : new Cursor(Cursor.HAND_CURSOR));
            }
            public void mouseDragged(MouseEvent e) {
            }
        });
    }

    private void loadOutline(List<PDFBookmark> bookmarks, DefaultMutableTreeNode root, Collection<TreePath> open) {
        for (Iterator<PDFBookmark> i = bookmarks.iterator();i.hasNext();) {
            final PDFBookmark bookmark = i.next();
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(bookmark) {
                public String toString() {
                    return bookmark.getName();
                }
            };
            root.add(node);
            List<PDFBookmark> children = bookmark.getBookmarks();
            if (children != null) {
                node.setAllowsChildren(true);
                loadOutline(children, node, open);
            }
            if (bookmark.isOpen()) {
                open.add(new TreePath(node.getPath()));
            }
        }
    }

    public void valueChanged(TreeSelectionEvent e) {
        Object o = outline.getLastSelectedPathComponent();
        if (o instanceof DefaultMutableTreeNode) {
            o = ((DefaultMutableTreeNode)o).getUserObject();
            if (o instanceof PDFBookmark) {
                docpanel.getJSManager().runEventBookmarkMouseUp(docpanel, ((PDFBookmark)o));
            }
        }
    }

}
