// $Id: Colors.java 18783 2013-12-18 12:34:20Z mike $

import java.io.*;
import java.awt.*;
import java.awt.color.*;
import org.faceless.pdf2.*;

/**
 * An example demonstrating the different types of color available for use
 * in the PDF library.
 *
 * Note that due to bugs in several older implementations of the java.awt.color.*
 * package (notably many of the IBM implementations), aspects of this example
 * may not work on all platforms.
 */
public class Colors {

    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4");
        final int step = 90;
        int y = 720;

        PDFStyle text = new PDFStyle();
        text.setFont(new StandardFont(StandardFont.HELVETICA), 9);
        text.setFillColor(Color.black);

        // First example is the most common case - draw a red rectangle
        // using the sRGB colorspace, which is the Java default.
        PDFStyle color = new PDFStyle();
        color.setLineColor(Color.black);
        color.setFillColor(Color.red);
        page.setStyle(color);
        page.drawRectangle(50, y+50, 100, y);
        page.setStyle(text);
        page.drawText("Red in the default sRGB colorspace", 120, y+20);
        y -= step;

        // Next, use the java.awt.GradientPaint class to define a
        // red-to-blue gradient, in the sRGB colorspace again.
        PDFStyle gradient = new PDFStyle();
        gradient.setLineColor(Color.black);
        gradient.setFillColor(new GradientPaint(50, y, Color.red, 150, y, Color.blue));
        page.setStyle(gradient);
        page.drawRectangle(50, y+50, 100, y);
        page.setStyle(text);
        page.drawText("A red-blue gradient in the sRGB colorspace", 120, y+20);
        y -= step;

        // The org.faceless.pdf2.PDFPattern class defines a repeating
        // two color pattern which can be used as a paint for shapes or text.
        PDFStyle pattern = new PDFStyle();
        pattern.setLineColor(Color.black);
        pattern.setFillColor(new PDFPattern("Star", 0, 0, 50, 50, Color.red, Color.blue));
        page.setStyle(pattern);
        page.drawRectangle(50, y+50, 100, y);
        page.setStyle(text);
        page.drawText("A pattern using the ColorPattern class", 120, y+20);
        y -= step;

        // The CMYKColorSpace class defines a color in the four-color CMYK
        // range. This is not a calibrated colorspace, so to a certain
        // extent the exact output color is device-dependent, but as
        // this colorspace has a larger range, and as most printers are CMYK
        // based, this is still a useful space to use.
        PDFStyle cmyk = new PDFStyle();
        cmyk.setLineColor(Color.black);
        Color cmykblue = CMYKColorSpace.getColor(1.0f, 0.73f, 0f, 0.02f);
        cmyk.setFillColor(cmykblue);
        page.setStyle(cmyk);
        page.drawRectangle(50, y+50, 100, y);
        page.setStyle(text);
        page.drawText("A color in the uncalibrated CMYK ColorSpace", 120, y+20);
        y -= step;

        // The LabColorSpace class defines a color in the three-color CIELab
        // range. This is a calibrated colorspace, and it's normally used as
        // the fallback color for Spot colors rather than on its own.
        PDFStyle lab = new PDFStyle();
        lab.setLineColor(Color.black);
        Color labblue = LabColorSpace.getColor(26.18f, 18.64f, -59.95f);
        lab.setFillColor(labblue);
        page.setStyle(lab);
        page.drawRectangle(50, y+50, 100, y);
        page.setStyle(text);
        page.drawText("A color in the calibrated CIELAB ColorSpace", 120, y+20);
        y -= step;

        // The SpotColorSpace allows a specific "ink" to be specified. If
        // the output device has this ink it's used, but otherwise the
        // backup color (which must be specified) will be used instead.
        PDFStyle spot = new PDFStyle();
        spot.setLineColor(Color.black);
        spot.setFillColor(new SpotColorSpace("PANTONE Reflex Blue C", labblue).getColor(1));
        page.setStyle(spot);
        page.drawRectangle(50, y+50, 100, y);
        page.setStyle(text);
        page.drawText("The PANTONE Reflex Blue spot color with a CIELAB fallback", 120, y+20);
        y -= step;


        // From here it gets more difficult, as some older implementations
        // of Java don't handle non-sRGB colorspaces at all, and are liable
        // to throw anything, including Exceptions and Errors.
        // Encasing the block in a "try/catch (Throwable)" will handle these.

        // First up, the CS_GRAY colorspace allows grayscale colors to be
        // specified.
        //
        try {
            ColorSpace grayspace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
            PDFStyle gray = new PDFStyle();
            gray.setLineColor(Color.black);
            float[] comp = { 0.5f };
            gray.setFillColor(new Color(grayspace, comp, 1));
            page.setStyle(gray);
            page.drawRectangle(50, y+50, 100, y);
        } catch (Throwable e) {
            System.err.println("WARNING: Gray ColorSpace: Caught "+e);
        }
        page.setStyle(text);
        page.drawText("A 50% gray color in the grayscale ColorSpace", 120, y+20);
        y -= step;

        // Finally, try loading a specific ICC profile from a file.
        try {
            ICC_Profile prof = ICC_Profile.getInstance("resources/NTSC.icm");
            ICC_ColorSpace ntsc = new ICC_ColorSpace(prof);
            float[] comp = { 1.0f, 0.0f, 0.0f };

            PDFStyle icc = new PDFStyle();
            icc.setLineColor(Color.black);
            icc.setFillColor(new Color(ntsc, comp, 1));
            page.setStyle(icc);
            page.drawRectangle(50, y+50, 100, y);
        } catch (Throwable e) {
            System.err.println("WARNING: ICC ColorSpace: Caught " + e);
        }
        page.setStyle(text);
        page.drawText("A color in the NTSC ICC ColorSpace", 120, y+20);
        y -= step;

        OutputStream fo = new FileOutputStream("Colors.pdf");
        pdf.render(fo);
        fo.close();
    }
}
