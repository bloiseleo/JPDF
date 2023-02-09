// $Id: DesktopSupport.java 41663 2021-11-10 11:44:56Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.PDFPage;
import javax.swing.*;

/**
 * <p>
 * This feature can be added to the viewer in Java 9 or later to make the viewer
 * player nicely with the Desktop, Taskbar and so on. Prior to 2.26.2 this was
 * called AppleSupport and was macOS-specific. It's enabled by default but will
 * have no effect prior to Java 9.
 * </p>
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>taskbarIcon</th><td><code>true</code> or <code>false</code>, for {@link #setTaskbarIcon}, or the path to an image resource to use that instead of the default - the value is passed to {@link #setTaskbarIconResource}.</td></tr>
 * <tr><th>moveMenus</th><td><code>true</code> or <code>false</code>, for {@link #setMoveMenus}</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">DesktopSupport</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11.1
 */
public class DesktopSupport extends ViewerFeature {

    private static final String DEFAULTDOCKICON = "resources/bfodockicon.png";
    private String taskbariconpath = DEFAULTDOCKICON;
    private boolean menus, taskbaricon;

    public DesktopSupport() {
        super("DesktopSupport");
        menus = true;
        taskbaricon = true;
    }

    /**
     * Set whether to move the menus from the application window to the System
     * menu bar and set the about/quit menus to work , if that's supported by the operating system
     * @param menus whether to move the menus or not
     * @since 2.11.6
     */
    public void setMoveMenus(boolean menus) {
        this.menus = menus;
    }

    /**
     * Set whether to set the taskbar icon. The default is true.
     * @param taskbaricon whether to customize the taskbar icon
     * @since 2.11.6
     */
    public void setTaskbarIcon(boolean taskbaricon) {
        this.taskbaricon = taskbaricon;
    }

    /**
     * Set the path to the resource to use for the taskbar icon.
     * The default is "resources/bfodockicon.png" - it will
     * be resolved against this class.
     * @since 2.16
     */
    public void setTaskbarIconResource(String resourcename) {
        if (resourcename == null) {
            resourcename = DEFAULTDOCKICON;
        }
        this.taskbariconpath = resourcename;
    }

    public void initialize(final PDFViewer viewer) {
        super.initialize(viewer);
        String val = getFeatureProperty(viewer, "moveMenus");
        if (val != null && !Util.isJavaFX(viewer)) {
            setMoveMenus("true".equals(val));
        }
        val = getFeatureProperty(viewer, "taskbarIcon");
        if (val != null) {
            if (val.equals("true")) {
                setTaskbarIcon(true);
            } else if (val.equals("false")) {
                setTaskbarIcon(false);
            } else {
                setTaskbarIcon(true);
                setTaskbarIconResource(val);
            }
        }

        // Horrors below so we can compile under Java8, not break anything that can't load these
        // classes, and let the package be renamed
        String s = taskbaricon && taskbariconpath != null ? taskbariconpath : null;
        try {
            Class.forName(getClass().getPackage().getName() + ".DesktopSupportImpl9").getMethod("initialize", PDFViewer.class, Boolean.TYPE, String.class).invoke(null, viewer, menus, s);
        } catch (Throwable e) { }
    }

}
