// $Id: ActionHandler.java 10479 2009-07-10 09:51:07Z chris $

package org.faceless.pdf2.viewer2;

import org.faceless.pdf2.*;

/**
 * A type of {@link ViewerFeature} that will run a {@link PDFAction} on a document,
 * usually as a result of a link or button being clicked.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public abstract class ActionHandler extends ViewerFeature
{
    /**
     * Create a new ActionHandler
     * @param name the name of this ViewerFeature
     */
    public ActionHandler(String name) {
        super(name);
    }

    public String toString() {
        return "ActionHandler:"+super.toString();
    }

    /**
     * Return true if this ActionFactory can handle the specified {@link PDFAction}
     */
    public abstract boolean matches(DocumentPanel panel, PDFAction action);

    /**
     * Run the specified action
     * @param panel the DocumentPanel running the action
     * @param action the action
     */
    public abstract void run(DocumentPanel panel, PDFAction action);
}
