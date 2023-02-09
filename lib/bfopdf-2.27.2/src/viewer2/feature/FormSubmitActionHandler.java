// $Id: FormSubmitActionHandler.java 41659 2021-11-10 09:23:06Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.LongRunningTask;
import org.faceless.util.Base64;
import org.faceless.pdf2.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.*;
import org.w3c.dom.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.net.*;
import java.io.*;
import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

/**
 * Create a handler to handler "FormSubmit" actions.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>timeout</th><td>The connection timeout value in milliseconds (0 is infinite)</td></tr>
 * <tr><th>readTimeout</th><td>The read timeout value in milliseconds (0 is infinite)</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FormSubmitActionHandler</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.3
 */
public class FormSubmitActionHandler extends ActionHandler {

    private int conntimeout, readtimeout;

    /**
     * Flag value for {@link #submit} meaning empty fields should be submitted
     * @since 2.11.18
     */
    public static final int FLAG_EMPTYFIELDS = 1;

    /**
     * Flag value for {@link #submit} meaning incremental updates should be submitted (not currently implemented)
     * @since 2.11.18
     */
    public static final int FLAG_INCREMENTAL = 2;

    /**
     * Flag value for {@link #submit} meaning dates should be submitted in canonical format
     * @see FDF#getCanonicalDate
     * @since 2.11.18
     */
    public static final int FLAG_CANONICALDATES = 4;

    /**
     * Flag value for {@link #submit} meaning the Filename should be submitted
     * @since 2.11.18
     */
    public static final int FLAG_FKEY = 8;

    /**
     * Flag value for {@link #submit} meaning the Form should be emdedded in the FDF (not currently implemnted)
     * @since 2.11.18
     */
    public static final int FLAG_EMBEDFORM = 16;

    /**
     * Flag value for {@link #submit} meaning the annotation {@link PDFAnnotation#getUniqueID} should be submitted
     * @since 2.11.18
     * @see FDF#setIncludeUniqueID
     */
    public static final int FLAG_NMKEY = 32;

    /**
     * Annotation value for {@link #submit} meaning no annotations should be submitted
     * @since 2.11.18
     */
    public static final int NOANNOTATIONS = 0;

    /**
     * Annotation value for {@link #submit} meaning all annotations should be submitted
     * @since 2.11.18
     */
    public static final int ALLANNOTATIONS = 1;

    /**
     * Annotation value for {@link #submit} meaning only annotations where the
     * {@link PDFAnnotation#getAuthor author} matches the {@link PDFViewer#getCurrentUser current user}.
     * @since 2.11.18
     */
    public static final int USERANNOTATIONS = 2;

    private static final int OK=0, DONE=1, CANCELLED=-1;

    /**
     * Create a new FormSubmitActionHandler
     * @since 2.11
     */
    public FormSubmitActionHandler() {
        super("FormSubmitActionHandler");
    }

    public boolean matches(DocumentPanel panel, PDFAction action) {
        return action.getType().equals("FormSubmit");
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        String val = getFeatureProperty(viewer, "timeout");
        if (val != null) {
            setTimeout(Integer.parseInt(val));
        }
        val = getFeatureProperty(viewer, "readTimeout");
        if (val != null) {
            setReadTimeout(Integer.parseInt(val));
        }
    }

    /**
     * Set the timeout in milliseconds that should be used when connecting
     * to a URL. A value of 0 (the default) means infinite.
     * @param timeout the number of milliseconds to wait while connecting to a URL before timing out
     */
    public void setTimeout(int timeout) {
        this.conntimeout = timeout;
    }

    /**
     * Set the timeout in milliseconds that should be used when reading from
     * a URL. A value of 0 (the default) means infinite.
     * @param timeout the number of milliseconds to wait while reading from a URL before timing out
     */
    public void setReadTimeout(int timeout) {
        this.readtimeout = timeout;
    }

    public void run(final DocumentPanel docpanel, final PDFAction action) {

        Collection<FormElement> fields = action.getFormSubmitFields();
        String url = action.getURL();
        int flags = action.getFormSubmitFlags();
        boolean flag_exclude = (flags&1)!=0;
        boolean flag_includeempty = (flags&2)!=0;
        boolean flag_exportformat = (flags&4)!=0;
        boolean flag_getmethod = (flags&8)!=0;
        boolean flag_coords = (flags&16)!=0;
        boolean flag_xfdf = (flags&32)!=0;
        boolean flag_append = (flags&64)!=0;
        boolean flag_annots = (flags&128)!=0;
        boolean flag_pdf = (flags&256)!=0;
        boolean flag_canon = (flags&512)!=0;
        boolean flag_otherannots = (flags&1024)!=0;
        boolean flag_nofile = (flags&2048)!=0;
        boolean flag_embedform = (flags&4096)!=0;

        if (flag_exclude) {
            Collection<FormElement> t = docpanel.getPDF().getForm().getElements().values();
            t.removeAll(fields);
            fields = t;
        }

        Point coords = null;
        String method;
        int submitflags = 0;
        int annotations = flag_annots ? flag_otherannots ? USERANNOTATIONS : ALLANNOTATIONS : NOANNOTATIONS;

        if (flag_pdf) {
            method = "PDF";
        } else if (flag_xfdf) {
            method = "XFDF";
        } else if (flag_exportformat && flag_getmethod) {
            method = "HTMLGET";
        } else if (flag_exportformat) {
            method = "HTML";
        } else {
            method = "FDF";
        }

        if (flag_canon) {
            submitflags |= FLAG_CANONICALDATES;
        }
        if (flag_includeempty) {
            submitflags |= FLAG_EMPTYFIELDS;
        }
        if (!flag_nofile) {
            submitflags |= FLAG_FKEY;
        }
        if (flag_embedform) {
            submitflags |= FLAG_EMBEDFORM;
        }
        if (flag_append) {
            submitflags |= FLAG_INCREMENTAL;
        }
        if (flag_coords) {
            // coords = ...
        }

        submit(docpanel, url, method, fields, annotations, flags, coords);
    }

    /**
     * Submit the document form. This method is intended to be called from the <code>Doc.submitForm</code>
     * JavaScript method.
     * @param docpanel the DocumentPanel
     * @param url the URL the submit will be made to
     * @param method the format - one of FDF, XFDF, HTML, HTMLGET, XML, or PDF
     * @param fields the list of field names to include - may be null (for all fields). List will be moderated by the <code>FLAG_EMPTY</code> parameter
     * @param annotations one of {@link #NOANNOTATIONS}, {@link #USERANNOTATIONS} or {@link #ALLANNOTATIONS}
     * @param flags a logical or of the various {@link #FLAG_CANONICALDATES flags}
     * @see FDF
     * @since 2.11.18
     */
    public void submit(final DocumentPanel docpanel, String url, String method, String[] fields, int annotations, int flags) {
        Collection<FormElement> actualfields = new ArrayList<FormElement>();
        Map<String,FormElement> elements = docpanel.getPDF().getForm().getElements();
        if (fields == null) {
            actualfields = elements.values();
        } else {
            for (int i=0;i<fields.length;i++) {
                FormElement e = elements.get(fields[i]);
                if (e != null && !actualfields.contains(e)) {
                    actualfields.add(e);
                }
            }
        }
        submit(docpanel, url, method, actualfields, annotations, flags, null);
    }

    private void submit(final DocumentPanel docpanel, final String url, String method, Collection<FormElement> fields, int annotations, int flags, Point coords) {

        URL realurl = Util.toURL(docpanel, url);
        StringBuilder missing = new StringBuilder();
        final Submission submission = new Submission(docpanel, realurl, method);
        if ((flags&FLAG_FKEY) != 0) {
            File file = docpanel.getAssociatedFile();
            if (file != null) {
                submission.filename = file.getName();
            }
        }
        submission.canonicaldates = (flags&FLAG_CANONICALDATES) != 0;
        submission.includenm = (flags&FLAG_NMKEY) != 0;
        submission.incremental = (flags&FLAG_INCREMENTAL) != 0;
        submission.embedform = (flags&FLAG_EMBEDFORM) != 0;
//        submission.coords = coords;
        boolean empty = (flags&FLAG_EMPTYFIELDS) != 0;

        Form form = docpanel.getPDF().getForm();
        for (Iterator<Map.Entry<String,FormElement>> i = form.getElements().entrySet().iterator();i.hasNext();) {
            Map.Entry<String,FormElement> e = i.next();
            String key = e.getKey();
            FormElement elt = e.getValue();
            if (fields == null || fields.contains(elt)) {
                String value = elt.getValue();
                if (elt.isRequired() && (value == null || value.length() == 0)) {
                    missing.append(e.getKey()+"\n");
                } else if (empty || (value != null && value.length() > 0)) {
                    submission.fields.put(key, elt);
                }
            }
        }
        if (missing.length() > 0) {
            String msg = "The following fields are mandatory:\n" + missing;
            JOptionPane.showMessageDialog(docpanel, msg, UIManager.getString("PDFViewer.Error"), JOptionPane.ERROR_MESSAGE);
        }

        if (annotations != NOANNOTATIONS) {
            String currentuser = docpanel.getViewer().getCurrentUser();
            submission.annotations = new ArrayList<PDFAnnotation>();
            int num = docpanel.getNumberOfPages();
            for (int i=0;i<num;i++) {
                PDFPage page = docpanel.getPage(i);
                if (page.getPDF() == docpanel.getPDF()) {
                    List<PDFAnnotation> pageannots = page.getAnnotations();
                    for (int j=0;j<pageannots.size();j++) {
                        PDFAnnotation annot = pageannots.get(j);
                        if (annot instanceof AnnotationText || annot instanceof AnnotationMarkup || annot instanceof AnnotationNote || annot instanceof AnnotationFile || annot instanceof AnnotationSound || annot instanceof AnnotationStamp || annot instanceof AnnotationShape) {
                            if (annotations == ALLANNOTATIONS || (currentuser!=null && currentuser.equals(annot.getAuthor()))) {
                                submission.annotations.add(annot);
                            }
                        }
                    }
                }
            }
        }

        submission.start(docpanel, Util.getUIString("PDFViewer.SubmittingTo", url), true);
    }

    private class Submission extends LongRunningTask {

        private final DocumentPanel docpanel;
        private final PDF pdf;
        private HttpURLConnection con;
        private URL url;
        private final String method;
        boolean canonicaldates, includenm, embedform, incremental;
//        Point coords;
        String filename;
        Map<String,FormElement> fields;
        List<PDFAnnotation> annotations;

        Submission(DocumentPanel docpanel, URL url, String method) {
            super(true, 0);
            this.docpanel = docpanel;
            this.pdf = docpanel.getPDF();
            this.url = url;
            this.method = method;
            this.fields = new HashMap<String,FormElement>();
            this.annotations = new ArrayList<PDFAnnotation>();
        }

        public String toString() {
            return "u="+url+" m="+method+" c="+isCancelled()+" c="+canonicaldates+" nm="+includenm+" embed="+embedform+" incr="+incremental+" file="+filename+" fields="+fields.keySet()+" annots="+annotations;
        }

        /**
         * Actually do the submission. Create the content, submit it to
         * the URL, wait for the response and if it's an FDF, create the
         * FDF and return it. Theoretically we could return other types
         * of content here too.
         */
        private Object submit() throws IOException {
            Object o;
            try {
                return JSEngine.doPrivileged(new Callable<Object>() {
                    public Object call() throws IOException {
                        boolean post = true;
                        if (method.equals("HTMLGET")) {
                            url = createGetURL();
                            post = false;
                        }

                        URLConnection ucon = (URLConnection)url.openConnection();
                        if (!(ucon instanceof HttpURLConnection)) {
                            throw new IllegalArgumentException("Cannot submit form to \""+url+"\": not HTTP");
                        }
                        con = (HttpURLConnection)ucon;
                        con.setUseCaches(false);
                        try {
                            // These two methods only introduced in 1.5
                            con.setConnectTimeout(conntimeout);
                            con.setReadTimeout(readtimeout);
                        } catch (NoSuchMethodError e) { }
                        con.setInstanceFollowRedirects(true);
                        con.setRequestProperty("Accept", "application/vnd.fdf, application/vnd.adobe.xfdf");
                        con.setRequestProperty("X-Test", this.toString());
                        con.setRequestProperty("User-Agent", "Mozilla/4.0 ("+System.getProperty("os.name")+"/"+System.getProperty("os.version")+") BFOPDF/"+PDF.VERSION+" Java/"+System.getProperty("java.version"));
                        if (url.getUserInfo() != null) {
                            con.setRequestProperty("Authorization", "Basic "+Base64.encode(url.getUserInfo().getBytes("ISO-8859-1")));
                        }

                        if (post) {
                            con.setRequestMethod("POST");
                            con.setDoOutput(true);

                            if (method.equals("XFDF")) {
                                submitXFDF(con);
                            } else if (method.equals("PDF")) {
                                submitPDF(con);
                            } else if (method.equals("HTML")) {
                                submitHTMLPost(con);
                            } else if (method.equals("XML")) {
                                submitXML(con);
                            } else if (method.equals("FDF")) {
                                submitFDF(con);
                            } else {
                                throw new IllegalStateException("Unknown method "+method);
                            }
                            if (isCancelled()) {
                                try {
                                    con.getOutputStream().close();
                                } catch (IOException e) { }
                            }
                        } else {                                // HTML GET
                            con.setRequestMethod("GET");
                            con.setDoOutput(false);
                            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        }

                        // Now we've sent the request, read the response. The only valid response
                        // we recognise is an FDF or XFDF, although there's no reason in principle
                        // why we couldn't display something else (eg. dialog containing HTML response)
                        //
                        Object response = null;
                        if (!isCancelled()) {
                            int status = con.getResponseCode();
                            if (status >= 200 && status <= 299) {
                                String type = con.getContentType();
                                if ("application/vnd.fdf".equals(type) || "application/vnd.adobe.xfdf".equals(type)) {
                                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                                    if (copyStream(con.getInputStream(), bout)) {
                                        response = new FDF(new ByteArrayInputStream(bout.toByteArray()));
                                    }
                                }
                            } else if (status >= 400) {
                                String enc = con.getContentEncoding();
                                if (enc == null) {
                                    enc = "ISO-8859-1";
                                }
                                Reader in = new InputStreamReader(con.getErrorStream(), enc);
                                StringWriter out = new StringWriter();
                                int c;
                                while ((c=in.read())>=0) {
                                    out.write(c);
                                }
                                String msg = out.toString();
                                msg = msg.replaceAll("<head>.*</head>", "");
                                throw new SyncFailedException(msg);
                            }
                        }
                        con.disconnect();
                        return response;
                    }
                });
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException)e;
                } else {
                    throw (RuntimeException)e;
                }
            }
        }

        private URL createGetURL() throws IOException {
            StringBuilder urlt = new StringBuilder();
            urlt.append(url.toString());
            if (urlt.indexOf("#") >= 0) {
                urlt.setLength(urlt.indexOf("#"));
            }
            if (urlt.indexOf("?") >= 0) {
                urlt.setLength(urlt.indexOf("?"));
            }
            urlt.append("?");
            for (Iterator<Map.Entry<String,FormElement>> i = fields.entrySet().iterator();i.hasNext();) {
                Map.Entry<String,FormElement> e = i.next();
                String key = e.getKey();
                String value = canonicaldates ? FDF.getCanonicalDate(e.getValue()) : e.getValue().getValue();
                if (value == null) {
                    value = "";
                }
                urlt.append(URLEncoder.encode(key, "UTF-8"));
                urlt.append('=');
                urlt.append(URLEncoder.encode(value, "UTF-8"));
                if (i.hasNext()) {
                    urlt.append("&");
                }
            }
            // TODO coords, although it's never going to be used.
            return new URL(urlt.toString());
        }

        private void submitHTMLPost(HttpURLConnection con) throws IOException {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(bout, "ISO-8859-1");
            for (Iterator<Map.Entry<String,FormElement>> i = fields.entrySet().iterator();i.hasNext();) {
                Map.Entry<String,FormElement> e = i.next();
                String key = e.getKey();
                String value = canonicaldates ? FDF.getCanonicalDate(e.getValue()) : e.getValue().getValue();
                if (value == null) {
                    value = "";
                }
                writer.write(URLEncoder.encode(key, "UTF-8"));
                writer.write('=');
                writer.write(URLEncoder.encode(value, "UTF-8"));
                if (i.hasNext()) {
                    writer.write('&');
                }
            }
            writer.close();

            if (!isCancelled()) {
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                con.setRequestProperty("Content-Length", Integer.toString(bout.size()));
                OutputStream out = con.getOutputStream();
                if (copyStream(new ByteArrayInputStream(bout.toByteArray()), out)) {
                    out.close();
                }
            }
        }

        private void submitXFDF(HttpURLConnection con) throws IOException {
            FDF fdf = new FDF(pdf);
            fdf.setIncludeEmptyFields(true);
            fdf.setIncludeUniqueID(includenm);
            fdf.setCanonicalDates(canonicaldates);
            fdf.setFields(fields.values());
            fdf.setAnnotations(annotations);
            fdf.setFile(filename);

            Document xfdf = fdf.getXFDF();
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            try {
                Transformer tr = TransformerFactory.newInstance().newTransformer();
                tr.transform(new DOMSource(xfdf), new StreamResult(bout));
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }
            bout.close();

            if (!isCancelled()) {
                con.setRequestProperty("Content-Type", "application/vnd.adobe.xfdf; charset=UTF-8");
                con.setRequestProperty("Content-Length", Integer.toString(bout.size()));
                OutputStream out = con.getOutputStream();
                if (copyStream(new ByteArrayInputStream(bout.toByteArray()), out)) {
                    out.close();
                }
            }
        }

        private void submitXML(HttpURLConnection con) throws IOException {
            Document document;
            try {
                document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Element root = document.createElement("fields");
            document.appendChild(root);
            root.appendChild(document.createTextNode("\n"));
            for (Iterator<Map.Entry<String,FormElement>> i = fields.entrySet().iterator();i.hasNext();) {
                Map.Entry<String,FormElement> e = i.next();
                String key = e.getKey();
                String value = e.getValue().getValue();
                if (value == null) {
                    value = "";
                }
                Element e2 = document.createElement(key);
                root.appendChild(e2);
                root.appendChild(document.createTextNode("\n"));
                e2.appendChild(document.createTextNode(value));
            }

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            try {
                Transformer tr = TransformerFactory.newInstance().newTransformer();
                tr.transform(new DOMSource(document), new StreamResult(bout));
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }
            bout.close();

            if (!isCancelled()) {
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                con.setRequestProperty("Content-Length", Integer.toString(bout.size()));
                OutputStream out = con.getOutputStream();
                if (copyStream(new ByteArrayInputStream(bout.toByteArray()), out)) {
                    out.close();
                }
            }
        }

        private void submitPDF(HttpURLConnection con) throws IOException {
            setCancellable(false);
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            pdf.render(bout);
            bout.close();
            setCancellable(true);

            if (!isCancelled()) {
                con.setRequestProperty("Content-Type", "application/pdf");
                con.setRequestProperty("Content-Length", Integer.toString(bout.size()));
                OutputStream out = con.getOutputStream();
                if (copyStream(new ByteArrayInputStream(bout.toByteArray()), out)) {
                    out.close();
                }
            }
        }

        private void submitFDF(HttpURLConnection con) throws IOException {
            FDF fdf = new FDF(pdf);
            fdf.setIncludeEmptyFields(true);
            fdf.setIncludeUniqueID(includenm);
            fdf.setCanonicalDates(canonicaldates);
            fdf.setFields(fields.values());
            fdf.setAnnotations(annotations);
            fdf.setFile(filename);

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            fdf.render(bout);
            bout.close();

            if (!isCancelled()) {
                con.setRequestProperty("Content-Type", "application/vnd.fdf");
                con.setRequestProperty("Content-Length", Integer.toString(bout.size()));
                OutputStream out = con.getOutputStream();
                if (copyStream(new ByteArrayInputStream(bout.toByteArray()), out)) {
                    out.close();
                }
            }
        }

        /**
         * Copy a Stream from the InputStream to the OutputStream.
         * Returns true if the whole stream completed, false if it was interrupted.
         */
        private boolean copyStream(InputStream in, OutputStream out) throws IOException {
            byte[] buf = new byte[8192];
            int len = 0;
            while (len >= 0 && !isCancelled()) {
                len = in.read(buf, 0, buf.length);
                if (len > 0) {
                    out.write(buf, 0, len);
                }
            }
            in.close();
            out.flush();
            return !isCancelled();
        }

        public float getProgress() {
            return Float.NaN;
        }

        public void run() {
            try {
                Object response = submit();
                if (response != null && response instanceof FDF) {
                    pdf.importFDF((FDF)response);
                }
            } catch (Throwable e) {
                if (!isCancelled()) {
                    if (e instanceof SyncFailedException) { // Not really sync failed, HTTP protocol error
                        JOptionPane.showMessageDialog(null, e.getMessage(), UIManager.getString("PDFViewer.Error"), JOptionPane.ERROR_MESSAGE);
                    } else {
                        Util.displayThrowable(e, null);
                    }
                }
            }
        }

        public void cancel() {
            super.cancel();
            if (con != null) {
                con.disconnect();
            }
        }

    }

}
