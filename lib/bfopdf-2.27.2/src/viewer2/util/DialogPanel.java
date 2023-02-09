// $Id: DialogPanel.java 29231 2018-08-11 23:05:18Z mike $

package org.faceless.pdf2.viewer2.util;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import org.faceless.pdf2.viewer2.Util;

/**
 * General purpose Dialog with OK and (optionally) Cancel button and/or other buttons,
 * intended to give a unified L&amp;F to all dialogs in the viewer
 * <br><br>
 * Typically you would call addComponent, and if action needs to be done on accept or
 * cancel you would override acceptDialog or cancelDialog. For a no-op on these buttons
 * these should just return, otherwise they should call super.acceptDialog/cancelDialog.
 * <br><br>
 * Here's a fairly typical use case:
 * <pre class="brush:java">
 * final JTextField field = new JTextField();
 * DialogPanel panel = new DialogPanel() {
 *     public String validateDialog() {
 *         if (field.getLength() == 0) {
 *             return "Field can't be empty";
 *         }
 *         return null;
 *     }
 * }
 * panel.addComponent(field);
 * if (panel.showDialog(pdfviewer)) {
 *     // Do something with field.getValue();
 * }
 * </pre>
 */
public class DialogPanel extends JPanel {

    private Map<String,Action> actions;
    private Map<KeyStroke,String> keystrokes;
    private boolean response, modal = true;
    private JDialog dialog;

    /**
     * Create a new DialogPanel with an OK and Cancel button
     */
    public DialogPanel() {
        this(true);
    }

    /**
     * Create a new DialogPanel
     * @param cancancel whether a Cancel button should be added
     */
    public DialogPanel(boolean cancancel) {
        this(true, cancancel);
    }

    /**
     * Create a new DialogPanel
     * @param canok whether an OK button should be added
     * @param cancancel whether a Cancel button should be added
     */
    public DialogPanel(boolean canok, boolean cancancel) {
        super(new GridBagLayout());
        actions = new LinkedHashMap<String,Action>();
        keystrokes = new HashMap<KeyStroke,String>();

        if (canok) {
            addButton("ok", UIManager.getString("OptionPane.okButtonText"), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    acceptDialog();
                }
            });
            keystrokes.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "ok");
        }

        if (cancancel) {
            addButton("cancel", UIManager.getString("OptionPane.cancelButtonText"), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    cancelDialog();
                }
            });
            keystrokes.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, InputEvent.CTRL_DOWN_MASK), "cancel");
        }
    }

    /**
     * Validate the dialog - if the dialog cannot be accepted, this
     * method should return a not-null string. Subclasses should override.
     */
    public String validateDialog() {
        return null;
    }

    /**
     * Add a component to the Dialog.
     */
    public void addComponent(JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = gbc.BOTH;
        gbc.gridwidth = gbc.REMAINDER;
        gbc.weightx = gbc.weighty = 1;
        add(component, gbc);
    }

    /**
     * Add a component to the Dialog with the specified label
     */
    public void addComponent(String label, JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = gbc.BOTH;
        gbc.anchor = gbc.WEST;
        gbc.insets = new Insets(0, 4, 0, 8);
        add(new JLabel(label), gbc);

        gbc.weightx = 1;
        gbc.gridwidth = gbc.REMAINDER;
        gbc.insets = new Insets(0, 0, 0, 4);
        add(component, gbc);
    }

    /**
     * Add a button to the Dialog
     * @param name the name of the button (also the button text)
     * @param keystroke the keystroke to activate the button, or null
     * @param action the action to perform on the button, or null to remove the existing button
     */
    public void addButton(String name, KeyStroke keystroke, Action action) {
        addButton(name, name, keystroke, action);
    }

    /**
     * Add a button to the Dialog
     * @param name the name of the button
     * @param text the text to display on the button
     * @param keystroke the keystroke to activate the button, or null
     * @param action the action to perform on the button, or null to remove the existing button
     */
    public void addButton(String name, String text, KeyStroke keystroke, Action action) {
        if (action != null) {
            action.putValue(Action.NAME, text);
            actions.put(name, action);
            keystrokes.put(keystroke, name);
        } else {
            actions.remove(name);
            for (Iterator<String> i = keystrokes.values().iterator();i.hasNext();) {
                if (i.next().equals(name)) {
                    i.remove();
                }
            }
        }
    }

    /**
     * Set the text on the specified button
     */
    public void setButtonText(String name, String text) {
        actions.get(name).putValue(Action.NAME, text);
    }

    /**
     * Set whether this dialog should be modal (the default) or not
     */
    public void setModal(boolean modal) {
        this.modal = modal;
    }

    /**
     * Action to be called when the dialog is cancelled. By default
     * this closes and disposes of the dialog.
     */
    public void cancelDialog() {
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
    }

    /**
     * Action to be called when the dialog is okayed. By default
     * this calls {@link #validateDialog} and if that returns null,
     * close the dialog and set the response to true.
     */
    public void acceptDialog() {
        if (dialog != null) {
            String msg = validateDialog();
            if (msg != null) {
                JOptionPane.showMessageDialog(dialog, msg, null, JOptionPane.ERROR_MESSAGE);
            } else {
                response = true;
                dialog.dispose();
                dialog = null;
            }
        }
    }

    /**
     * Show the Dialog with no window decorations
     * @return true if the dialog was OK, false otherwise
     */
    public boolean showUndecoratedDialog(Component root) {
        return showDialog(root, null, false);
    }

    /**
     * Show the Dialog with usual window decorations
     * @return true if the dialog was OK, false otherwise
     */
    public boolean showDialog(Component root) {
        return showDialog(root, null, true);
    }

    /**
     * Show the Dialog with usual window decorations and the specified title
     * @return true if the dialog was OK, false otherwise
     */
    public boolean showDialog(Component root, String title) {
        return showDialog(root, title, true);
    }

    private boolean showDialog(Component root, String title, boolean decorated) {
        if (dialog != null) {
            throw new IllegalStateException("Dialog already exists");
        }
        dialog = Util.newJDialog(root, title, modal);
        if (!decorated) {
            dialog.setUndecorated(true);
        }

        JRootPane rootPane = dialog.getRootPane();
        JPanel buttonpanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Do this to reorder buttons so "cancel" and "ok" are last!
        // For Windows L&F, cancel button is on the right. For Nimbus
        // check the "isYesLast" option.
        boolean cancelonright =  Util.isLAFWindows() || !UIManager.getBoolean("OptionPane.isYesLast");

        if (actions.containsKey("cancel") && !cancelonright) {
            actions.put("cancel", actions.remove("cancel"));
        }
        if (actions.containsKey("ok")) {
            actions.put("ok", actions.remove("ok"));
        }
        if (actions.containsKey("cancel") && cancelonright) {
            actions.put("cancel", actions.remove("cancel"));
        }
        for (Iterator<Map.Entry<String,Action>> i = actions.entrySet().iterator();i.hasNext();) {
            Map.Entry<String,Action> e = i.next();
            String name = e.getKey();
            Action action = e.getValue();
            rootPane.getActionMap().put(name, action);
            buttonpanel.add(new JButton(action));
            for (Iterator<Map.Entry<KeyStroke,String>> j = keystrokes.entrySet().iterator();j.hasNext();) {
                Map.Entry<KeyStroke,String> e2 = j.next();
                if (e2.getValue().equals(name)) {
                    rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(e2.getKey(), e2.getValue());
                }
            }
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = gbc.REMAINDER;
        gbc.anchor = gbc.EAST;
        gbc.weighty = 0.01;
        add(buttonpanel, gbc);

        dialog.setContentPane(this);
        dialog.setResizable(true);
        dialog.pack();
        dialog.setLocationRelativeTo(root);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event) {
                if (actions.containsKey("cancel")) {
                    cancelDialog();
                } else {
                    acceptDialog();
                }
            }
        });
        dialog.setVisible(true);
        return response;
    }

}
