These classes make up the "org.faceless.pdf2.viewer2" package, and provide the
Swing components for the PDF Viewer.

It is possible to compile them, although not really necessary. First change
the package name from "org.faceless.pdf2.viewer2" to one of your own packages.
This is to avoid a namespace clash wih the presupplied classes, but mostly to
ensure you're aware any Exceptions you encounter after modifying this code are
your own responsibility!

You'll need "bfopdf.jar" and the "plugin.jar" file in the "lib" directory of
your JRE (required for Java/JavaScript communication in the PDFViewerApplet)
in your ClassPath to compile.

PLEASE remember the package has been designed to be extended. You should
rarely, if ever be required to modify and recompile this code - instead, we
strongly recomend writing new code that calls into the public or protected
methods it supplies. The API documentation gives many examples of this, and
with this source code as reference should be enough to get you started.

Most of the customizations that can be imagined are achievable with a new
"feature". We suggest creating your own using the source in the "features"
package as a base, then adding it to the list supplied to the PDFViewer
constructor. Modifying this list may well be the only change you need.


Good luck!


Mike Bremford
CTO, Big Faceless Organization
17 January 2008
------------------------------------------------------------------------------

This code is copyright the Big Faceless Organization. You're welcome to use,
modify and distribute it in any form in your own projects, provided those
projects continue to make use of the Big Faceless PDF library.

The source code for these classes is provided without any warranty or support
and may be subject to change or withdrawn without notice.
