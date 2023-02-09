This directory contains a plugin for Firefox which will allow PDF
files to be opened with the PDF Viewer Applet.

The code is unsupported and is public domain - feel free to
modify it to suit your needs. It has been tested with Firefox
3.0.x and 3.5.x on OS X but shouldn't cause too much trouble
with other releases.

To use:

1. Run the "build.sh" script (on UNIX) to create the "bfopdfviewer.xpi"
   file.

2. Open this file with firefox to install it.

3. Restart Firefox. Now any hyperlinks to a URL ending in ".pdf" will
   have an additional context menu option when you right-click on them:
   the "Open link in PDF Viewer" option will open the linked PDF in a
   pop-up window using the PDF Viewer Applet.

