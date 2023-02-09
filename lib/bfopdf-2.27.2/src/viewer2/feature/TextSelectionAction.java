package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.PageExtractor;
import org.faceless.pdf2.PageExtractor.Text;

/**
 * A type of feature that works with the {@link TextTool} feature.
 * When text is selected in the {@link TextTool} and the mouse is
 * right-clicked, the user will have the option to apply one of
 * these actions to the selected text. Typically this is used
 * to highlight, underline etc. text in the PDF
 *
 * @see AreaSelectionAction
 * @since 2.11.7
 */
public interface TextSelectionAction {

    /**
     * Get the name of this Action, to appear in the popup menu.
     */
    public String getDescription();

    /**
     * Run the action.
     * @param docpanel the DocumentPanel this action is being run on.
     * @param ranges the {@link org.faceless.pdf2.viewer2.feature.TextSelection.RangeList} containing the list of selected text items, which may be from multiple pages
     */
    public void selectAction(DocumentPanel docpanel, TextSelection.RangeList ranges);

    /**
     * Indicates whether this action is enabled.
     * @since 2.13
     */
    public boolean isEnabled();

}
