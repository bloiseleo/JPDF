// $Id: ViewerFeature.java 41473 2021-10-27 11:11:07Z mike $

package org.faceless.pdf2.viewer2;

import org.faceless.pdf2.viewer2.feature.*;
import org.faceless.pdf2.PropertyManager;
import org.faceless.pdf2.PDF;
import java.util.*;
import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * The ViewerFeature class can be used to control the various features of the
 * {@link PDFViewer}, such as widgets, side panels, annotations and action handlers.
 * Most of the interesting methods are in the subclasses, particularly {@link ViewerWidget},
 * but the {@link #getAllFeatures} method is commonly called to return a list of all the
 * features available to the viewer.
 * <p>
 * The list of default features is specified by the <code>org.faceless.pdf2.viewer2.ViewerFeature</code>
 * service provider file. The <code>bfopdf.jar</code> file contains this file in the <code>META-INF/services</code>
 * folder, which lists the classnames of each feature to load by default. Each feature specified this
 * way must be a subclass of <code>ViewerFeature</code>, and contain a zero-parameter constructor or a
 * <code>getInstance()</code> method that returns a singleton. Additional features may be specified
 * this way in any other Jars available to the application. See the Java
 * <a href="http://java.sun.com/j2se/1.4.2/docs/guide/sound/programmer_guide/chapter13.html">Service Provider Interface</a>
 * documentation for more information.
 * </p>
 * See the <a href="doc-files/tutorial.html">viewer tutorial</a> for more detail on how to use this class and the "viewer" package.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public abstract class ViewerFeature {

    private String name;

    /**
     * Return a Collection of all the features available, which can be passed
     * to the {@link PDFViewer} constructor or used as a base for your
     * own set of features.
     * @see #getAllEnabledFeatures
     */
    public static final List<ViewerFeature> getAllFeatures() {
        Set<ViewerFeature> set = new LinkedHashSet<ViewerFeature>(50);
        Set<String> classnames = new HashSet<String>();
        try {
            for (Enumeration<?> e = ViewerFeature.class.getClassLoader().getResources("META-INF/services/" + Util.PACKAGE + ".ViewerFeature");e.hasMoreElements();) {
                java.net.URL url = (java.net.URL)e.nextElement();
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
                String classname;
                while ((classname=reader.readLine())!=null) {
                    classname = classname.replaceAll("#.*", "").trim();
                    if (classname.length() > 0 && classnames.add(classname)) {
                        try {
                            set.add((ViewerFeature)Class.forName(classname, true, Thread.currentThread().getContextClassLoader()).getDeclaredConstructor().newInstance());
                        } catch (Throwable x) {
                            try {
                                @SuppressWarnings("rawtypes") java.lang.reflect.Method m = Class.forName(classname, true, Thread.currentThread().getContextClassLoader()).getMethod("getInstance", new Class[0]);
                                set.add((ViewerFeature)m.invoke(null, new Object[0]));
                            } catch (Throwable x2) {
                                x.printStackTrace();
                            }
                        }
                    }
                }
                reader.close();
            }
        } catch (Throwable x) {
            x.printStackTrace();
        }
        List<ViewerFeature> l = null;
        for (Iterator<ViewerFeature> i = set.iterator();i.hasNext();) {
            if (i.next().getClass()==GenericNamedActionHandler.class) {
                i.remove();
                l = new ArrayList<ViewerFeature>(set);
                l.add(new GenericNamedActionHandler());
            }
        }
        if (l==null) {
            l = new ArrayList<ViewerFeature>(set);
        }
        return Collections.unmodifiableList(l);
    }

    /**
     * Return a list of all features that return true from {@link #isEnabledByDefault}.
     * @since 2.11.7
     */
    public static final List<ViewerFeature> getAllEnabledFeatures() {
        List<ViewerFeature> list = new ArrayList<ViewerFeature>(getAllFeatures());
        for (int i=0;i<list.size();i++) {
            ViewerFeature feature = list.get(i);
            boolean enabled = feature.isEnabledByDefault();
            String s = PDF.getPropertyManager().getProperty("viewer2.feature."+feature.getName());
            if ("false".equals(s)) {
                enabled = false;
            } else if (s != null) {
                enabled = true;
            }
            if (!enabled) {
                list.remove(i--);
            }
        }
        return list;
    }

    /**
     * Create a new ViewerFeature
     * @param name the name of the feature
     */
    protected ViewerFeature(String name) {
        this.name = name;
    }

    /**
     * Set the feature name. This should only be called in the constructor - normally you
     * would set the name by passing it into the constructor, but if you're overriding a
     * feature that doesn't allow you to do this, this method allows you to change it.
     * @since 2.11.20
     */
    protected final void setFeatureName(String name) {
        this.name = name;
    }

    /**
     * Called when the feature is first added to a viewer
     */
    public void initialize(final PDFViewer viewer) {
    }

    /**
     * Called when the PDFViewer containing this feature is closed
     * @since 2.11.18
     */
    public void teardown() {
    }

    /**
     * Return true if this feature is enabled by default (the default).
     * Disabled features are excluded from the list returned by {@link #getAllEnabledFeatures}
     * and by {@link PDFTool}
     * @since 2.11.7
     */
    public boolean isEnabledByDefault() {
        return true;
    }

    /**
     * Return the name of this Feature
     */
    public final String getName() {
        return name;
    }

    public String toString() {
        return name;
    }

    /**
     * Return any custom JavaScript that needs to be run by this feature on the
     * specified JavaScript event. Unlike JavaScript from the document, this
     * JavaScript will be run outside the security sandbox so will have the same
     * permissions as the application.
     * The default implementation returns <code>null</code>.
     * @param type the Event type : "App", "Doc" etc.
     * @param name the Event name : "Init", "Open", "WillClose" etc.
     * @since 2.10.6
     */
    public String getCustomJavaScript(String type, String name) {
        return null;
    }

    /**
     * <p>
     * Get a custom property for this feature, as specified by the viewer. This
     * method provides a convenient, standardised way to access properties for
     * a feature at runtime, by querying the {@link PropertyManager} returned
     * by {@link PDFViewer#getPropertyManager}. The key is prefixed by the name
     * of this class, and if no value is found the superclass is specified.
     * </p><p>
     * For example, calling this method from a {@link KeyStoreSignatureProvider}
     * class as <code>getFeatureProperty(viewer, "name")</code> will first check
     * the property <code>feature.KeyStoreSignatureProvider.name</code>. If no
     * matching value is found, <code>SignatureServiceProvider.name</code>
     * and then <code>ViewerFeature.name</code> are checked. Custom subclasses outside
     * the <code>org.faceless.pdf2.viewer2</code> package will have their full classnames used -
     * as an example, a subclass of <code>KeyStoreSignatureProvider</code> called
     * <code>com.mycompany.MyProvider</code> would check <code>com.mycompany.MyProvider.name</code>
     * first.
     * </p>
     * @param viewer the {@link PDFViewer} to get the PropertyManager from
     * @param key the property key
     * @since 2.11
     */
    public final String getFeatureProperty(PDFViewer viewer, String key) {
        PropertyManager manager = viewer.getPropertyManager();
        String val = null;
        if (manager!=null) {
            Class<?> cl = getClass();
            do {
                String name = cl.getName().replaceAll("^" + Util.PACKAGE + ".", "");
                String propkey = name+"."+key;
                val = manager.getProperty(propkey);
//                System.out.println("Checking "+propkey+" = "+val);
                cl = cl.getSuperclass();
            } while (val==null && cl!=Object.class);
        }
        return val;
    }

    /**
     * Exactly as for {@link #getFeatureProperty getFeatureProperty()} but
     * returns a URL value
     * @since 2.11.23
     */
    public final URL getFeatureURLProperty(PDFViewer viewer, String key) throws MalformedURLException {
        PropertyManager manager = viewer.getPropertyManager();
        URL val = null;
        if (manager!=null) {
            Class<?> cl = getClass();
            do {
                String name = cl.getName().replaceAll("^" + Util.PACKAGE + ".", "");
                String propkey = name+"."+key;
                val = manager.getURLProperty(propkey);
//                System.out.println("Checking "+propkey+" = "+val);
                cl = cl.getSuperclass();
            } while (val==null && cl!=Object.class);
        }
        return val;
    }
}
