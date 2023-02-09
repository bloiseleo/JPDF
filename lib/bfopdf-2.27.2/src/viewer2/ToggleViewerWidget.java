// $Id: ToggleViewerWidget.java 39834 2021-04-23 19:33:08Z mike $

package org.faceless.pdf2.viewer2;

import javax.swing.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.*;
import java.util.*;

/**
 * An subclass of {@link ViewerWidget} which causes the widget to be toggled on
 * or off, rather than simply pushed. ToggleViewerWidget objects may be part of
 * a "group", in which case when this object is selected, all objects in the
 * same group are deselected.
 *
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10
 */
public abstract class ToggleViewerWidget extends ViewerWidget {

    private final String groupname;
    private JComponent owner;
    private boolean active;

    /**
     * Create a new ToggleViewerWidget
     * @param name the name of the Widget
     * @param groupname the group this widget is part of, or <code>null</code> for no group
     */
    protected ToggleViewerWidget(String name, String groupname) {
        super(name);
        this.groupname = groupname;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        setGroupOwner(viewer);
    }

    public void teardown() {
        super.teardown();
        owner = null;
    }

    /**
     * Return the name of the group this widget is part of, or <code>null</code>
     * if no group was specified
     */
    public final String getGroupName() {
        return groupname;
    }

    /**
     * Set the "owner" for this Widget's group. This is typically the
     * <code>PDFViewer</code> and set automatically - the only time
     * this method needs to be called manually is when no PDFViewer is
     * in use and these widgets are being applied directly to a
     * {@link DocumentPanel}.
     * @param comp the JComponent that owns the group this widget is part of
     */
    @SuppressWarnings("unchecked") public final void setGroupOwner(JComponent comp) {
        if (owner == comp) {
            return;
        }
        if (owner != null) {
            throw new IllegalStateException("Already set an owner");
        }
        this.owner = comp;
        if (groupname != null) {
            Set<ToggleViewerWidget> group = (Set<ToggleViewerWidget>)comp.getClientProperty("group.set."+groupname);
            if (group == null) {
                group = new HashSet<ToggleViewerWidget>();
                comp.putClientProperty("group.set."+groupname, group);
            }
            group.add(this);
        }
    }

    /**
     * Return the "owner" of this Widget's group.
     */
    public final JComponent getGroupOwner() {
        return owner;
    }

    /**
     * Toggle the active state of this widget by calling {@link #setSelected}
     */
    public void action(ViewerEvent event) {
        setSelected(!isSelected());
    }

    /**
     * Set whether this Widget is selected. If active is true, this method
     * will automatically call <code>setSelected(false)</code> on all the other
     * widgets in this group. Subclasses should override this method to do
     * whatever they need to do when their active status is changed.
     * @param selected whether this Widget is active or not
     */
    public void setSelected(boolean selected) {
        if (selected != isSelected()) {
            if (owner != null && owner.getClientProperty("group.active."+groupname) == this && !selected) {
                owner.putClientProperty("group.active."+groupname, null);
            }
            active = selected;
            JComponent comp = getComponent();
            if (comp instanceof AbstractButton) {
                ((AbstractButton)comp).setSelected(active);
            }
            Object menu = getViewer().getNamedComponent("Menu"+getName());
            if (menu instanceof JCheckBoxMenuItem) {
                ((JCheckBoxMenuItem) menu).setSelected(active);
            }
            if (selected && owner != null) {
                ToggleViewerWidget old = (ToggleViewerWidget)owner.getClientProperty("group.active."+groupname);
                owner.putClientProperty("group.active."+groupname, this);
                if (old != this && old != null) {
                    old.setSelected(false);
                }
            }

            if (getViewer() != null) {
                if (isSelected()) {
                    DocumentPanel docpanel = getViewer().getActiveDocumentPanel();
                    if (docpanel != null) {
                        updateViewport(docpanel.getViewport(), true);
                    }
                } else {
                    DocumentPanel[] docpanels = getViewer().getDocumentPanels();
                    if (docpanels != null) {
                        for (int i=0;i<docpanels.length;i++) {
                            updateViewport(docpanels[i].getViewport(), false);
                        }
                    }
                }
            } else {
                DocumentPanel docpanel = (DocumentPanel)getGroupOwner();
                if (docpanel != null) {
                    updateViewport(docpanel.getViewport(), isSelected());
                }
            }
        }
    }

    /**
     * Called from {@link #setSelected(boolean)}, this method is called in each
     * viewport in use by the {@link PDFViewer} or {@link DocumentPanel} so
     * it can update its status. By default it is a no-op.
     * @since 2.10.3
     */
    protected void updateViewport(DocumentViewport viewport, boolean selected) {
    }


    /**
     * Return whether this ViewerWidget is selected
     */
    public boolean isSelected() {
        return active;
    }

    /**
     * Return the selected item in the specified group, or <code>null</code>
     * if none of the items in that group are selected or no such group
     * exists. To get the selected item in this item's group, pass in the
     * value of {@link #getGroupName}
     * @param groupname the name of the group
     */
    public ToggleViewerWidget getGroupSelection(String groupname) {
        if (owner != null && groupname != null) {
            return (ToggleViewerWidget)owner.getClientProperty("group.active."+groupname);
        } else {
            return null;
        }
    }

    /**
     * Return a Collection of other {@link ToggleViewerWidget} objects in the
     * specified group. To return the Collection containing objects in this
     * group, pass in the value of {@link #getGroupName}.
     * @param groupname the name of the group
     */
    @SuppressWarnings("unchecked") public Collection<ToggleViewerWidget> getWidgets(String groupname) {
        if (owner != null && groupname != null) {
            Set<ToggleViewerWidget> group = (Set<ToggleViewerWidget>)owner.getClientProperty("group.set."+groupname);
            return group == null ? null : Collections.unmodifiableSet(group);
        } else {
            return null;
        }
    }
}
