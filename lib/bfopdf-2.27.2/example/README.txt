This directory contains several example programs which can be used to test the
capabilities of the library.  Before running these example, be sure to add the
"bfopdf.jar" file in the root directory of this package to your CLASSPATH.

UNIX users can just run the script "RUNME.sh", and Windows users can use
the file "RUNME.bat". This will build and run many of the examples.

EXAMPLES THAT MAY ACTUALLY BE USEFUL AS THEY ARE
------------------------------------------------------------------------------
Dump                    Dump a lot of information about a PDF
Concatenate             Joins two PDFs together
ImageToPDF              Convert a bitmap image (TIFF, PNG, JPEG, GIF) to PDF
PDFToImage              Convert a PDF to one or more TIFF, PNG or JPEG images
Preflight               Preflight an existing PDF to the PDF/X-1a:2001 standard
Sign                    Digitally sign a PDF
CharacterMap            Dump an entire font showing all the available characters
PrintPDF                Print a PDF file
ExtractText             Extracts the text from the Document to a file.
DumpText                Performs a low-level dump of all the text in a document
PDFTool                 A general purpose PDF swiss army knife.

EXAMPLES THAT DEMONSTRATE GENERAL FEATURES OF THE LIBRARY
------------------------------------------------------------------------------
HelloWorld              The mandatory "Hello, World" example.
ServletExample          Identical to HelloWorld but creates a PDF from a Servlet
Annotations             Create different types of annotation
BatchFormFill           Shows the best way to fill a template multiple times
Colors                  Demonstrate how to use the various Color Spaces
CreateBook              Creates a 2-up "book" from a plain text file
Fonts                   Demonstrates how to use the different types of font
FormCreation            Creates a new document with Form fields
FormFill                Loads and completes the form created by FormCreation
FormProcess             Display the contents of the form completed by FormFill
FormVoodoo              Shows interesting uses of forms rollovers, Javascript etc.
ServletExample          Sample Servlet showing how to return a PDF from a Servlet
HelloUnicodeWorld       Simple example showing Unicode text
Images                  Demonstrates loading images
LayoutExample           Demonstrates the use of a LayoutBox to position text
LuceneExample           Shows how to use the library to create a Lucene Document
Join2Up                 Convert a PDF to 2-up by pasting pages onto eachother
PathExample             Demonstrates the use of Path Graphics
Stamp                   Add a "Confidential" stamp to a PDF in one of four ways
Unicode                 Create a PDF demonstrating text in 20 languages
