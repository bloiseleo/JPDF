// $Id: FormResetActionHandler.java 19623 2014-07-11 15:17:50Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.util.*;

/**
 * Create a handler to handler "FormReset" actions.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FormResetActionHandler</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class FormResetActionHandler extends ActionHandler {

    /**
     * Create a new FormResetActionHandler
     * @since 2.11
     */
    public FormResetActionHandler() {
        super("FormResetActionHandler");
    }

    public boolean matches(DocumentPanel panel, PDFAction action) {
        return action.getType().equals("FormReset");
    }

    public void run(DocumentPanel docpanel, PDFAction action) {
        Collection<FormElement> fields = action.getFormSubmitFields();
        for (Iterator<FormElement> i = fields.iterator();i.hasNext();) {
            FormElement field = i.next();
            if (field instanceof FormText) {
                ((FormText)field).setValue(((FormText)field).getDefaultValue());
            } else if (field instanceof FormRadioButton) {
                ((FormRadioButton)field).setValue(((FormRadioButton)field).getDefaultValue());
            } else if (field instanceof FormCheckbox) {
                ((FormCheckbox)field).setValue(((FormCheckbox)field).getDefaultValue());
            } else if (field instanceof FormChoice) {
                ((FormChoice)field).setValue(((FormChoice)field).getDefaultValue());
            }
        }
    }
}
