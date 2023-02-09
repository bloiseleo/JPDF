import java.awt.Color;
import java.awt.color.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.faceless.pdf2.*;

/** 
 * An example demonstrating how to "preflight" a document - in this case, to convert it
 * to PDF/A. This example has been completely revised for the "new" (as of 2021)
 * preflighting technique, and the class itself - minus the static methods - is a useful
 * template for your own code.
 *
 * An example use might be
 *
 *  java ConvertToPDFA --font arial.ttf --font NotoSansCJKsc-Regular.otf --icc srgb --icc fogra39-300.icc file.pdf
 */
public class ConvertToPDFA {

    public static final int STATE_NEW = 0;          // PDF preflight has not been run
    public static final int STATE_VALID = 1;        // PDF was already valid, no changes were made
    public static final int STATE_FIXED = 2;        // PDF was modified to meet the profile
    public static final int STATE_FIXED_BITMAP = 3; // PDF was modified to meet the profile by rasterizing at least one page.
    public static final int STATE_FAILED = 4;       // PDF failed to convert

    private PDFParser parser;
    private OutputProfiler profiler;
    private ColorSpace intentcs;
    private Collection<ColorSpace> colorSpaces;
    private Collection<OutputProfile> allowedTargets;   // The OutputProfiles we will accept as a target
    private Collection<OutputProfile> retainedTargets;  // Additional OutputProfiles we will keep if already set.
    private OutputProfile defaultTarget;                // If no allowed target is set in the PDF, the target to aim for
    private OutputProfile usedTarget;                   // The target actually used
    private List<OpenTypeFont> fontList;                // Font fonts to substitute in if required
    private int state;                                  // a STATE_ value - the state of the ConvertToPDFA object
    private String message;                             // a message decribing the state, or null

    /**
     * Create a new ConvertToPDFA instance
     * @param pdf the PDF to be procesed
     */
    public ConvertToPDFA(PDF pdf) {
        this.parser = new PDFParser(pdf);
        this.profiler = new OutputProfiler(parser);
        this.state = STATE_NEW;

        setStrategy(OutputProfiler.Strategy.JustFixIt);
        setDefaultTarget(OutputProfile.PDFA1a_2005);
        setTargetProfiles(null);
        setColorSpaces(null);
        setRetainedProfiles(Arrays.asList(OutputProfile.PDFX4, OutputProfile.PDFUA1, OutputProfile.ZUGFeRD1_Basic, OutputProfile.ZUGFeRD1_Comfort, OutputProfile.ZUGFeRD1_Extended));
    }

    /**
     * Return the OutputProfiler used to do the conversion
     */
    public OutputProfiler getOutputProfiler() {
        return profiler;
    }

    /**
     * Set the list of ColorSpace objects to use to "anchor" device-dependent colors
     * in the PDF. The ColorSpace objects should be ICC-based, and really should
     * include one RGB and one CMYK profile. The sRGB profile returned from
     * <code>Color.red.getColorSpace</code> is a good one to use.
     *
     * If you don't have an appropriate profile, many are available from
     * https://www.color.org/registry/index.xalter - We recommend
     *   -- FOGRA39 in Europe ("Coated FOGRA 39 300" is a good choice)
     *   -- SWOP2013 in the Americas
     *   -- Japan Color 2011 in Japan
     * all of which are available for download from the above website.
     *
     * @param list the list of ColorSpaces to use to calibrate colors in the PDF
     */
    public void setColorSpaces(List<? extends ColorSpace> list) {
        this.colorSpaces = list == null ? Collections.<ColorSpace>emptyList() : new ArrayList<ColorSpace>(list);
    }

    /**
     * Set the ColorSpace to use for the OutputIntent. It is usually best to leave
     * this set to null (the default), in which case the OuptutIntent ColorSpace
     * will be chosen from the list supplied to {@link #setColorSpaces}.
     * @param cs the ColorSpace for the OutputIntent, or null to choose automatically
     */
    public void setOutputIntentColorSpace(ColorSpace cs) {
        this.intentcs = cs;
    }

    /**
     * Specify the set OutputProfiles the PDF will be matched to. If the PDF already
     * claims to be compliant to one of these, that's what we'll be matched against.
     * If the value of "targets" is empty, we'll ignore any claims in the PDF.
     */
    public void setTargetProfiles(Collection<OutputProfile> targets) {
        this.allowedTargets = targets == null ? Collections.<OutputProfile>emptyList() : new ArrayList<OutputProfile>(targets);
    }

    /**
     * Specify a set of OutputProfiles the PDF will keep if the PDF already meets
     * them, but which we will not otherwise try to match. Adding (for example)
     * PDF/UA-1 here ensures that if the PDF complied with PDF/UA-1 when loaded, it
     * will still comply with PDF/UA-1 when saved. The default set is PDF/X-4,
     * PDF/UA-1 and ZUGFeRD, and should cause no problems.
     */
    public void setRetainedProfiles(Collection<OutputProfile> targets) {
        this.retainedTargets = targets == null ? Collections.<OutputProfile>emptyList() : new ArrayList<OutputProfile>(targets);
        for (Iterator<OutputProfile> i = retainedTargets.iterator();i.hasNext();) {
            OutputProfile p = i.next();
            // We can't support merging with profiles that only allow one OutputIntent,
            // as we're going to be adding a GTS_PDFA1 intent. This rules out PDF/X-1 and X-3
            if (p.isDenied(OutputProfile.Feature.HasMultipleOutputIntents)) {
                i.remove();
            }
        }
    }

    /**
     * Set the OutputProfile to use as a target profile if none of the profiles
     * set by {@link #setTargetProfiles} apply.
     */
    public void setDefaultTarget(OutputProfile defaultTarget) {
        if (defaultTarget == null) {
            throw new IllegalArgumentException("Default target cannot be null");
        }
        this.defaultTarget = defaultTarget;
    }

    /**
     * Set the list of Fonts to consider for substitution into the PDF in place of
     * unembedded fonts. The most common unembedded fonts are the standard Microsoft
     * System fonts - Times, Arial, Trebuchet etc. - and Chinese/Japanese/Korean
     * fonts, for which we recommend including at least one of Noto CJK fonts from
     * https://www.google.com/get/noto/help/cjk/
     */
    public void setFontList(List<OpenTypeFont> fonts) {
        this.fontList = fonts;
    }

    /**
     * Set the list of {@link OutputProfiler.Strategy} choices, to control how
     * the PDF is converted. The default is {@link OutputProfiler.Strategy#JustFixIt}
     * which should give 100% conversion success.
     */
    public void setStrategy(OutputProfiler.Strategy... strategy) {
        profiler.setStrategy(strategy);
    }

    /**
     * Return the current state of the preflight process.
     */
    public int getState() {
        return state;
    }

    /**
     * Return any message describing the state of the preflight process
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get the OutputProfile this PDF was eventually profiled against.
     */
    public OutputProfile getUsedTarget() {
        return usedTarget;
    }

    /**
     * Run the preflight, and set the state and message as a result.
     */
    public void run() {
        if (state != STATE_NEW) {
            throw new IllegalStateException("Already run");
        }
        retainedTargets.removeAll(allowedTargets);

        // Step 1. Profile the PDF. If we've set a list of allowable "target" profiles, see if it
        // matches any of those. If it does, we don't need to do anything: the PDF is already valid.
        OutputProfile profile = profiler.getProfile();
        Collection<OutputProfile> claimedTargets = profile.getClaimedTargetProfiles();

        // Choose a target from our allowed list
        usedTarget = defaultTarget;
        for (OutputProfile p : claimedTargets) {
            if (allowedTargets.contains(p)) {
                usedTarget = p;
                break;
            }
        }
        // Check if it's already valid.
        if (profile.isCompatibleWith(usedTarget) == null) {
            state = STATE_VALID;
            return;
        }

        // Retain any additional targets we already meet, so long as they're
        // compatible with our chosen target and in our allowed list.
        OutputProfile target = new OutputProfile(usedTarget);
        for (OutputProfile p : claimedTargets) {
            boolean retain = allowedTargets.contains(p);
            if (retainedTargets.contains(p) && profile.isCompatibleWith(p) == null) {
                try {
                    target.merge(p, profile);
                    retain = true;
                } catch (ProfileComplianceException e) {
                    // This combination is disallowed.
                }
            }
            if (!retain) {
                // Remove any claims we can't meet from the PDF. Not strictly
                // required, but we shouldn't be writing a PDF that claims compliance
                // to (for example) PDF/UA-1 if we know the PDF doesn't comply.
                // System.out.println("Removing claim \"" + p.getProfileName() + "\"");
                target.denyClaim(p);
            }
        }

        // Now we try and determine the default ColorSpace for the PDF - the "output intent" -
        // which will not only determine the colorimetry for any device-dependent colors in the PDF,
        // but will also affect how the PDF is displayed in Acrobat. Broadly the approach is:
        // 1. If the PDF specifies one try to reuse it.
        // 2. Otherwise try to guess whether the PDF is mostly print, or mostly screen, and choose
        //    a CMYK or RGB profile accordingly.

        if (intentcs != null) {
            // OutputIntent Test 1: if a valid ColorSpace for the OutputIntent has been specified explicity, use it.
            OutputIntent intent = new OutputIntent("GTS_PDFA1", null, intentcs);
            if (intent.isCompatibleWith(target) == null) {
                target.getOutputIntents().add(intent);
            }
        }
        if (target.getOutputIntent("GTS_PDFA1") == null) {
            // OutputIntent Test 2: otherwise, if a valid PDF/A OutputIntent exists in the PDF, use it.
            OutputIntent intent = profile.getOutputIntent("GTS_PDFA1");
            if (intent != null && intent.isCompatibleWith(target) == null) {
                target.getOutputIntents().add(new OutputIntent(intent));
            }
        }
        for (OutputIntent intent : profile.getOutputIntents()) {
            // OutputIntent Test 3: otherwise, if any other valid OutputIntent exists in the PDF, use it
            // for PDF/A. If the PDF is PDF/X and we want to keep it that way, use it for that too.
            if (intent.isCompatibleWith(target) == null) {
                if (target.getRequiredOutputIntentTypes().contains(intent.getType())) {
                    target.getOutputIntents().add(new OutputIntent(intent));
                }
                if (target.getOutputIntent("GTS_PDFA1") == null) {
                    target.getOutputIntents().add(new OutputIntent("GTS_PDFA1", intent));
                }
            }
        }

        // Pass all the ColorSpaces we've been given to use into a new ProcessColorAction.
        List<ColorSpace> cslist = new ArrayList<ColorSpace>();
        cslist.addAll(profile.getICCColorSpaces());     // Add any ICC ColorSpaces used anywhere in the PDF
        cslist.addAll(colorSpaces);
        OutputProfiler.ProcessColorAction colorAction = new OutputProfiler.ProcessColorAction(target, cslist);
        if (target.getOutputIntent("GTS_PDFA1") == null) {
            // OutputIntent Test 4: we still don't have one. Choose either a CMYK or RGB
            // ColorSpace from the list we've been given - but which one?
            // If the PDF looks like it's print focused, using CMYK will give better results.
            // So if the PDF has a Color Separation called "Cyan", "Magenta" or "Yellow", or
            // it uses a CMYK Blend Mode, or it uses Overprinting, prefer a CMYK profile.
            boolean cmyk = profile.isSet(OutputProfile.Feature.ColorSpaceCMYK) ||
                           profile.isSet(OutputProfile.Feature.AnnotationColorSpaceDeviceCMYK) ||
                           profile.isSet(OutputProfile.Feature.NChannelProcessDeviceCMYK) ||
                           profile.isSet(OutputProfile.Feature.ColorSpaceDeviceCMYKInMatchingParent) ||
                           profile.isSet(OutputProfile.Feature.TransparencyGroupCMYK) ||
                           profile.isSet(OutputProfile.Feature.Overprint);
            for (OutputProfile.Separation s : profile.getColorSeparations()) {
                String name = s.getName();
                cmyk |= name.equals("Cyan") || name.equals("Magenta") || name.equals("Yellow");
            }
            ColorSpace cs = cmyk ? colorAction.getDeviceCMYK() : colorAction.getDeviceRGB();
            if (cs == null) { // First choice not available, fall back to the other.
                cs = !cmyk ? colorAction.getDeviceCMYK() : colorAction.getDeviceRGB();
            }
            if (cs != null) {
                OutputIntent intent = new OutputIntent("GTS_PDFA1", null, cs);
                if (intent.isCompatibleWith(target) == null) {
                    target.getOutputIntents().add(intent);
                    // We've changed the OutputIntent on the target, so recreate the ColorAction
                    colorAction = new OutputProfiler.ProcessColorAction(target, cslist);
                }
            } else {
                // No OutputIntent set. This isn't necessarily fatal; if the PDF already contains
                // only calibrated colors (and for PDF/A-2 or later, no overprinting) then it may
                // be OK. But generally, if you get here you need more ColorSpaces in cslist.
            }
        }
        // Warn if colorspaces are missing, because things are probably going to go wrong.
        if (colorAction.getDeviceCMYK() == null) {
            if (colorAction.getDeviceRGB() == null) {
                System.err.println("WARNING: ConvertToPDFA: ColorAction has no sRGB or CMYK profile, conversion may fail");
            } else {
                System.err.println("WARNING: ConvertToPDFA: ColorAction has no CMYK profile, conversion may fail");
            }
        } else if (colorAction.getDeviceRGB() == null) {
            System.err.println("WARNING: ConvertToPDFA: ColorAction has no RGB profile, conversion may fail");
        }
        profiler.setColorAction(colorAction);

        // Set the fonts to use. Note we clone the fonts passed in here - PDFs
        // should not share fonts! Clone with "new OpenTypeFont(font)"
        OutputProfiler.AutoEmbeddingFontAction fontAction = new OutputProfiler.AutoEmbeddingFontAction();
        for (OpenTypeFont f : fontList) {
            fontAction.add(new OpenTypeFont(f));
        }
        // This next line is what we're assuming you want - "just fix it". Other strategies
        // are available, but will inevitaby result in more conversion failures. See the API
        // docs for details.
        profiler.setFontAction(fontAction);
//        profiler.setRasterizingActionExecutorService(Executors.newFixedThreadPool(3));
        profiler.setRasterizingAction(new OutputProfiler.RasterizingAction() {
            // We override this only so we can get some logging out of it. Normally not necessary
            @Override public void rasterize(OutputProfiler profiler, PDFPage page, OutputProfile pageProfile, ProfileComplianceException e) {
                state = STATE_FIXED_BITMAP;
                message = e == null ? "Rasterized" : "Rasterized due to " + e.getFeature().getFieldName();
                super.rasterize(profiler, page, pageProfile, e);
            }
        });
        // Setup all done. Apply the target profile.
        try {
            state = STATE_FIXED;
            target = modifyTarget(target);
            profiler.apply(target);
        } catch (RuntimeException e) {
            state = STATE_FAILED;
            message = "Failed: " + e.getMessage();
            throw e;
        }
        // Finally, recheck which target was actually used. The "AutoConformance"
        // strategy can change a requested target from PDF/A-2a to PDF/A-2b, for example.
        // So ask the Profiler which one it used.
        // getProfile() will return instantly here, it's already calculated.
        profile = profiler.getProfile();
        boolean found = false;
        for (OutputProfile p : profile.getClaimedTargetProfiles()) {
            if (p == defaultTarget || allowedTargets.contains(p)) {
                usedTarget = p;
                found = true;
                break;
            }
        }
        if (!found) {
            // If we didn't find which target we used in the list of "claimed targets",
            // it's because some sort of customized target was passed in. The best
            // we can do here is to return the actual target that was used.
            usedTarget = target;
        }
    }

    /**
     * This method is the best place to modify the OutputProfile being targeted.
     * For example, if you wanted to remove compression or pretty-print the XMP
     * when its saved, here where to do it.
     * This method should return the modified profile - typically this would
     * be the profile thats's passed in. The default implemention simply
     * returns "target" with no changes.
     * @param target the OutputProfile to modify and return
     * @return the target profile to use
     */
    protected OutputProfile modifyTarget(OutputProfile target) {
        // eg target.setRequired(OutputProfile.Feature.XMPMetaDataPretty);
        // eg target.setDenied(OutputProfile.Feature.RegularCompression);
        return target;
    }

    /**
     * A helper method to quickly verify a PDF against a specified OutputProfile.
     * Returns null if the PDF is valid and matches, otherwise returnn a list of
     * reasons why the PDF failed to verify ("+" means the feature was required
     * but missing, "-" means the feature was set but disallowed)
     * @param pdf the PDF, which should be newly loaded
     * @param target the OutputProfile to verify against.
     */
    public static String verify(PDF pdf, OutputProfile target) {
        OutputProfiler profiler = new OutputProfiler(new PDFParser(pdf));
        OutputProfile profile = profiler.getProfile();
        OutputProfile.Feature[] mismatch = profile.isCompatibleWith(target);
        if (mismatch == null) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("[ERROR] Verify failed against ");
            sb.append(target.getProfileName());
            sb.append(":");
            for (OutputProfile.Feature f : mismatch) {
                sb.append(target.isRequired(f) ? " -" : " +");
                sb.append(f.toString());
            }
            return sb.toString();
        }
    }

    // ---------------------------------------------------------------------------

    private static void help() {
        System.out.println();
        System.out.println("java ConvertToPDFA [--font <file>]+ [--icc <file>]+ --<profilename> [<file.pdf>+ | --files-from <file>]");
        System.out.println("   --font <fontfile>     one or more OpenType fonts to consider for substitution into the PDF");
        System.out.println("   --icc srgb|<iccfile>  one or more ICC profiles to calibrate PDF color against");
        System.out.println("   --<profilename>       which PDF/A profiles to target. Valid options include pdfa1, pdfa2, pdfa3");
        System.out.println("                         and pdfa4");
        System.out.println("   --files-from <file>   specifies a file containing the list of PDF files to process, rether than");
        System.out.println("                         listing them all on the command line.");
        System.out.println();
        System.out.println("   For correct operation, at least two ICC profiles should be supplied; one RGB and one CMYK. The");
        System.out.println("   CMYK must be loaded from an ICC Profile file, but the value \"srgb\" can be ued to load the sRGB");
        System.out.println("   profile. Multiple fonts should be provided; we recommend at least a NotoSansCJK font, and ideally");
        System.out.println("   the Times, Arial and Courier fonts supplied with Windows. Don't forget the bold and italic variants.");
        System.out.println();
        System.out.println("   PDFs will be verified or converted against the specified PDF/A profiles, with the first one the default");
        System.out.println("   choice. If no profiles are specified, any PDF/A profile is accepted, with PDF/A-1 the default");
        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        List<OpenTypeFont> fonts = new ArrayList<OpenTypeFont>();
        List<OutputProfile> targets = new ArrayList<OutputProfile>();
        List<ColorSpace> colorspaces = new ArrayList<ColorSpace>();
        List<String> filenames = new ArrayList<String>();
        ColorSpace intentcs = null;

        if (args.length == 0) {
            help();
            System.exit(1);
        }
        for (int i=0;i<args.length;i++) {
            String s = args[i];
            if (s.equals("--help")) {
                help();
                System.exit(1);
            } else if (s.equals("--font")) {
                OpenTypeFont font = new OpenTypeFont(new File(args[++i]), null);
                fonts.add(font);
            } else if (s.equals("--icc")) {
                String name = args[++i];
                if (name.equalsIgnoreCase("srgb")) {
                    colorspaces.add(Color.red.getColorSpace());
                } else {
                    colorspaces.add(new ICCColorSpace(new File(name)));
                }
            } else if (s.equals("--icc-intent")) {
                String name = args[++i];
                if (name.equalsIgnoreCase("srgb")) {
                    colorspaces.add(Color.red.getColorSpace());
                } else {
                    colorspaces.add(new ICCColorSpace(new File(name)));
                }
                intentcs = colorspaces.get(colorspaces.size() - 1);
            } else if (s.equals("--pdfa1")) {
                targets.add(OutputProfile.PDFA1a_2005);
                targets.add(OutputProfile.PDFA1b_2005);
            } else if (s.equals("--pdfa2")) {
                targets.add(OutputProfile.PDFA2a);
                targets.add(OutputProfile.PDFA2b);
                targets.add(OutputProfile.PDFA2u);
            } else if (s.equals("--pdfa3")) {
                targets.add(OutputProfile.PDFA3a);
                targets.add(OutputProfile.PDFA3b);
                targets.add(OutputProfile.PDFA3u);
            } else if (s.equals("--pdfa4")) {
                targets.add(OutputProfile.PDFA4);
                targets.add(OutputProfile.PDFA4e);
                targets.add(OutputProfile.PDFA4f);
            } else if (s.equals("--files-from")) {
                BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(args[++i]), "UTF-8"));
                while ((s=r.readLine()) != null) {
                    filenames.add(s);
                }
                r.close();
            } else {
                filenames.add(s);
            }
            for (String filename : filenames) {
                File infile = new File(filename);
                PDF pdf = null;
                try {
                    pdf = new PDF(new PDFReader(infile));
                } catch (Exception e) {
                    // PDF completely failed to load - perhaps badly damaged,
                    // password protected or not a PDF? Report it and continue.
                    System.out.println(infile + " [ERROR]: " + e);
                }
                if (pdf != null) {
                    try {
                        // Convert the PDF to PDF/A
                        ConvertToPDFA preflight = new ConvertToPDFA(pdf);
                        if (targets.isEmpty()) {
                            // If no target profiles specified, target any published PDF/A profile
                            // and PDF/A-1b by default.
                            preflight.setDefaultTarget(OutputProfile.PDFA1b_2005);
                            preflight.setTargetProfiles(Arrays.asList(
                                OutputProfile.PDFA1b_2005, OutputProfile.PDFA1a_2005,
                                OutputProfile.PDFA2a, OutputProfile.PDFA2b, OutputProfile.PDFA2u,
                                OutputProfile.PDFA3a, OutputProfile.PDFA3u, OutputProfile.PDFA3b,
                                OutputProfile.PDFA4, OutputProfile.PDFA4e, OutputProfile.PDFA4f));
                        } else {
                            preflight.setDefaultTarget(targets.get(0));
                            preflight.setTargetProfiles(targets);
                        }
                        preflight.setFontList(fonts);
                        if (colorspaces.isEmpty()) {
                            // This technically isn't fatal but it means you haven't specified
                            // the "--icc" parameter - twice - to specify an RGB and CMYK profile.
                            // This is probably oversight, so throw an exception.
                            throw new IllegalStateException("No ColorSpaces specified! Conversion almost always requires you to specify an RGB and CMYK colorspace");
                        }
                        preflight.setColorSpaces(colorspaces);
                        preflight.setOutputIntentColorSpace(intentcs);
                        preflight.run();

                        // Report on the results
                        OutputProfile usedTarget = preflight.getUsedTarget();
                        if (preflight.getState() == STATE_VALID) {
                            // No changes were made.
                            System.out.println(infile + " [OK]: Already valid against " + usedTarget.getProfileName());
                        } else {
                            // Changes were made. Save PDF, then load and verify.
                            // Before we save, add an entry to the "History" in the metadata
                            // noting the conversion. Optional, but metadata is always useful.
                            pdf.getXMP().addHistory("Converted to PDF/A", null, "BFOPDF " + PDF.VERSION, null, null);
                            File outfile = new File("preflight-" + infile.getName());
                            OutputStream out = new FileOutputStream(outfile);
                            pdf.render(out);
                            out.close();

                            String message = null;
                            if (true) {
                                // As an insurance check, this block reloads the PDF we
                                // just created and verifies it really is valid.
                                pdf = new PDF(new PDFReader(outfile));
                                message = verify(pdf, usedTarget);
                            }
                            if (message != null) {
                                // We thought the PDF was repaired, but it wasn't. If
                                // we've done our job properly, this block should't run.
                                System.out.println(infile + ": " + message);
                            } else {
                                // PDF successfully verified.
                                message = preflight.getMessage();
                                if (message == null) {
                                    System.out.println(infile + ": [OK] Converted to " + usedTarget.getProfileName() + " and saved as \"" + outfile + "\"");
                                } else {
                                    System.out.println(infile + ": [OK] Converted to " + usedTarget.getProfileName() + " and saved as \"" + outfile + "\" (" + message + ")");
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Something has gone wrong! You shouldn't get here, but if you
                        // do, log it and go onto the next file.
                        System.out.println(infile + ": [ERROR] " + e);
                        e.printStackTrace(System.out);
                    }
                }
            }
            filenames.clear();
        }
    }

}
