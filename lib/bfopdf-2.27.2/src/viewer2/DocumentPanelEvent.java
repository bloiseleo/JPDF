// $Id: DocumentPanelEvent.java 20861 2015-02-11 10:58:38Z mike $

package org.faceless.pdf2.viewer2;

import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import org.faceless.pdf2.*;

/**
 * Represents an event on a {@link DocumentPanel} indicating that document has changed
 * somehow. To capture these events, implement the {@link DocumentPanelListener} interface
 * and register via the {@link DocumentPanel#addDocumentPanelListener DocumentPanel.addDocumentPanelListener()} method.
 * Be sure to check the {@link #getType} method to see what sort of event it is - current values
 * include:
 * <table summary="" class="defntable">
 * <tr><th>created</th><td>Raised when the DocumentPanel is first created</td></tr>
 * <tr><th>activated</th><td>Raised when the DocumentPanel is activated with a valid PDF</td></tr>
 * <tr><th>deactivated</th><td>Raised when the DocumentPanel is deactivated</td></tr>
 * <tr><th>viewportChanged</th><td>Raised when the DocumentPanel has a new {@link DocumentViewport}  applied to it</td></tr>
 * <tr><th>loaded</th><td>Raised after a PDF is loaded</td></tr>
 * <tr><th>closing</th><td>Raised when the PDF is closing</td></tr>
 * <tr><th>redrawn</th><td>Raised when the PDF has been redrawn somehow</td></tr>
 * <tr><th>pageChanged</th><td>Raised when the DocumentPanel changes which page is being displayed</td></tr>
 * <tr><th>pagePositionChanged</th><td>Raised when the DocumentPanel changes which area of the page is being displayed</td></tr>
 * <tr><th>stateChanged</th><td>Raised when the DocumentPanel's PDF has had its state changed. This event will be fired when the state of the document is updated <i>in the current context</i> - for instance, if the viewer validates a signature, or verifies a document against an {@link OutputProfile}. The nature of the change should be determined from the associated object. It's not for inherent changes to the document (such as when a page is removed) which result in a {@link java.beans.PropertyChangeEvent} from the PDF or one of its components.</td></tr>
 * <tr><th>permissionChanged</th><td>Raised when the DocumentPanel's PDF has had its permissions changed.</td></tr>
 * </table>
 * DocumentPanelEvent's are created with the static "create" methods, but unless you are
 * implementing your own Viewport, there is probably no need to call these methods.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class DocumentPanelEvent {

    private final DocumentPanel panel;
    private final String type;
    private final Object state;

    private PDFPage previousPage;

    /**
     * Create a "created" DocumentPanelEvent
     */
    public static DocumentPanelEvent createCreated(DocumentPanel panel) {
        return new DocumentPanelEvent("created", panel);
    }
    /**
     * Create an "activated" DocumentPanelEvent
     */
    public static DocumentPanelEvent createActivated(DocumentPanel panel) {
        return new DocumentPanelEvent("activated", panel);
    }
    /**
     * Create a "deactivated" DocumentPanelEvent
     */
    public static DocumentPanelEvent createDeactivated(DocumentPanel panel) {
        return new DocumentPanelEvent("deactivated", panel);
    }
    /**
     * Create a "viewportChanged" DocumentPanelEvent
     */
    public static DocumentPanelEvent createViewportChanged(DocumentPanel panel) {
        return new DocumentPanelEvent("viewportChanged", panel);
    }
    /**
     * Create a "pageChanged" DocumentPanelEvent
     */
    public static DocumentPanelEvent createPageChanged(DocumentPanel panel) {
        return new DocumentPanelEvent("pageChanged", panel);
    }
    /**
     * Create a "pagePositionChanged" DocumentPanelEvent
     */
    public static DocumentPanelEvent createPagePositionChanged(DocumentPanel panel) {
        return new DocumentPanelEvent("pagePositionChanged", panel);
    }
    /**
     * Create a "permissionChanged" DocumentPanelEvent
     */
    public static DocumentPanelEvent createPermissionChanged(DocumentPanel panel, String permission) {
        return new DocumentPanelEvent("permissionChanged", panel, permission);
    }
    /**
     * Create a "loaded" DocumentPanelEvent
     */
    public static DocumentPanelEvent createLoaded(DocumentPanel panel) {
        return new DocumentPanelEvent("loaded", panel);
    }
    /**
     * Create a "closing" DocumentPanelEvent
     */
    public static DocumentPanelEvent createClosing(DocumentPanel panel) {
        return new DocumentPanelEvent("closing", panel);
    }
    /**
     * Create a "redrawn" DocumentPanelEvent
     */
    public static DocumentPanelEvent createRedrawn(DocumentPanel panel) {
        return new DocumentPanelEvent("redrawn", panel);
    }
    /**
     * Create a "stateChanged" DocumentPanelEvent
     * @param state the state that has changed.
     * @since 2.12
     */
    public static DocumentPanelEvent createStateChanged(DocumentPanel panel, Object state) {
        return new DocumentPanelEvent("stateChanged", panel, state);
    }


    private DocumentPanelEvent(String type, DocumentPanel panel) {
        this(type, panel, null);
    }

    private DocumentPanelEvent(String type, DocumentPanel panel, Object state) {
        this.type = type;
        this.panel = panel;
        this.state = state;
    }

    public String toString() {
        return "[D:"+type+"]";
    }

    /**
     * Get the DocumentPanel this event was raised on
     */
    public DocumentPanel getDocumentPanel() {
        return panel;
    }

    /**
     * Get the type of the DocumentPanelEvent
     */
    public String getType() {
        return type;
    }

    /**
     * Get the state associated with a "stateChange" events. The type of object can be used
     * to determine the change - for instance, a {@link SignatureProvider.SignatureState} will indicated a
     * change to the validation state of one of the docuemnt's signatures.
     * @since 2.12
     */
    public Object getState() {
        return state;
    }

    void setPreviousPage(PDFPage page) {
        this.previousPage = page;
    }

    PDFPage getPreviousPage() {
        return previousPage;
    }

}
