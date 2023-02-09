#!/bin/sh

# Run all the examples

ARGS=-Djava.awt.headless=true  # Not needed for most examples, but useful on OS X Java >= 1.4.2_09
CLASSPATH=.:../bfopdf.jar:../bfopdf-cmap.jar:../bfopdf-qrcode.jar:../bfopdf-stamp.jar
export CLASSPATH 
javac Annotations.java BatchFormFill.java CharacterMap.java Colors.java Concatenate.java CreateBook.java Dump.java Fonts.java FormCreation.java FormFill.java FormProcess.java FormVoodoo.java HelloUnicodeWorld.java HelloWorld.java ImageToPDF.java Images.java Join2Up.java LayoutExample.java PDFToImage.java PathExample.java PrintPDF.java Sign.java Stamp.java Unicode.java ExtractText.java StructuredPDF.java

echo Running HelloWorld && java $ARGS HelloWorld
echo Running PathExample && java $ARGS PathExample
echo Running Images && java $ARGS Images
echo Running Fonts && java $ARGS Fonts
echo Running Colors && java $ARGS Colors
echo Running Concatenate && java $ARGS Concatenate HelloWorld.pdf PathExample.pdf Images.pdf Colors.pdf
echo Running Annotations && java $ARGS Annotations
echo Running LayoutExample && java $ARGS LayoutExample
echo Running ImageToPDF && java $ARGS ImageToPDF resources/demon.tif
echo Running CharacterMap for Times-Roman && java $ARGS CharacterMap Times-Roman
echo Running CreateBook && java $ARGS CreateBook
echo Running Stamp && java $ARGS Stamp CreateBook.pdf
echo Running Join2Up && java $ARGS Join2Up PathExample.pdf HelloWorld.pdf Annotations.pdf Fonts.pdf CharacterMap.pdf
echo Running FormCreation && java $ARGS FormCreation
echo Running FormFill && java $ARGS FormFill FormCreation.pdf
echo Running FormProcess && java $ARGS FormProcess FormFill.pdf
echo Running FormVoodoo && java $ARGS FormVoodoo
echo Running BatchFormFill && java $ARGS BatchFormFill
echo Running PDFToImage FormFill.pdf && java $ARGS PDFToImage FormFill.pdf
echo Running StructuredPDF && java $ARGS StructuredPDF
echo Running ExtractText Fonts.pdf && java $ARGS ExtractText Fonts.pdf > Fonts.txt

echo Running Sign
keytool -genkeypair -keystore testkeystore.jks -dname "C=GB, O=Test Org, CN=Test Key" -storepass password -keypass password -keyalg RSA
java Sign --keystore testkeystore.jks --password password Annotations.pdf
echo Running Dump on Sign.pdf && java $ARGS Dump Sign.pdf

echo Skipping HelloUnicodeWorld - need to know a TrueType font first
# java $ARGS HelloUnicodeWorld /path/to/times.ttf
echo Skipping Unicode - need to know a TrueType font first
# java $ARGS Unicode /path/to/times.ttf
