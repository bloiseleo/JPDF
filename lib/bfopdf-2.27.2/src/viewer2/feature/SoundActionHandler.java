// $Id: SoundActionHandler.java 23291 2016-05-04 11:00:55Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.util.*;
import java.io.*;
import javax.swing.JComponent;
import javax.sound.sampled.*;

/**
 * Create a handler to handler "Sound" actions.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">SoundActionHandler</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class SoundActionHandler extends ActionHandler
{
    private static volatile Thread audioowner;

    /**
     * Create a new SoundActionHandler
     * @since 2.11
     */
    public SoundActionHandler() {
        super("SoundActionHandler");
    }

    public boolean matches(DocumentPanel panel, PDFAction action) {
        return action.getType().equals("Sound");
    }

    public void run(DocumentPanel docpanel, PDFAction action) {
        PDFSound sound = action.getSound();
        if (sound != null) {
            playSound(sound, false, false, docpanel);
        }
    }

    /**
     * Play a sound. This method is static so can be called from elsewhere as well.
     * @param sound the PDFSound object
     * @param mix whether to mix this sound (true) or if it should be the only sound playing (false)
     * @param repeat whether the sound should repeat until the next non-mixed sound is played
     * @param root the Component owning the sound.
     */
    public static void playSound(final PDFSound sound, final boolean mix, final boolean repeat, final JComponent root) {
        if (sound == null) {
            throw new NullPointerException("Sound is null");
        }
        if (root == null) {
            throw new NullPointerException("Component is null");
        }
        try {
            final AudioInputStream audioin = sound.getAudioInputStream();
            Thread thread = new Thread("PDF Audio") {
                public void run() {
                    AudioInputStream in = audioin;
                    audioowner = mix ? null : this;
                    do {
                        AudioFormat format = in.getFormat();
                        SourceDataLine line = null;
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                        try {
                            line = (SourceDataLine)AudioSystem.getLine(info);
                            try {
                                line.open(format);
                                line.start();
                                int n = 0;
                                byte[] buf = new byte[1024];
                                while (root.isDisplayable() && (n=in.read(buf, 0, buf.length)) >= 0 && (audioowner==this || (mix && audioowner==null))) {
                                    line.write(buf, 0, n);
                                }
                            } finally {
                                line.drain();
                                line.close();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        }
                        if (repeat && root.isDisplayable()) {
                            in = sound.getAudioInputStream();
                        }
                    } while (repeat && (audioowner==this || audioowner==null));
                    try {
                        in.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
        } catch (Exception e) {
            Util.displayThrowable(e, root);
        }
    }
}
