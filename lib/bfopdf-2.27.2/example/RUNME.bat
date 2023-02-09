@ECHO OFF
::
:: A batch file to run the examples.
::
set OLDCLASSPATH=%CLASSPATH%
set CLASSPATH=.;..\bfopdf.jar;..\bfopdf-cmap.jar;..\bfopdf-qrcode.jar;..\bfopdf-stamp.jar

javac Annotations.java BatchFormFill.java CharacterMap.java Colors.java Concatenate.java CreateBook.java Dump.java Fonts.java FormCreation.java FormFill.java FormProcess.java FormVoodoo.java HelloUnicodeWorld.java HelloWorld.java ImageToPDF.java Images.java Join2Up.java LayoutExample.java PDFToImage.java PathExample.java PrintPDF.java Sign.java Stamp.java Unicode.java ExtractText.java StructuredPDF.java


@ECHO Running HelloWorld
java HelloWorld
@ECHO Running PathExample
java PathExample
@ECHO Running Images
java Images
@ECHO Running Fonts
java Fonts
@ECHO Running Colors
java Colors
@ECHO Running Concatenate
java Concatenate HelloWorld.pdf PathExample.pdf Images.pdf Colors.pdf
@ECHO Running Annotations
java Annotations
@ECHO Running LayoutExample
java LayoutExample
@ECHO Running ImageToPDF
java ImageToPDF resources\demon.tif
@ECHO Running CharacterMap for Times-Roman
java CharacterMap Times-Roman
@ECHO Running CreateBook
java CreateBook
@ECHO Running Stamp
java Stamp CreateBook.pdf
@ECHO Running Join2Up
java Join2Up PathExample.pdf HelloWorld.pdf Annotations.pdf Fonts.pdf CharacterMap.pdf

@ECHO Running FormCreation
java FormCreation
@ECHO Running FormFill FormCreation.pdf
java FormFill FormCreation.pdf
@ECHO Running FormProcess FormFill.pdf
java FormProcess FormFill.pdf
@ECHO Running FormVoodoo
java FormVoodoo
@ECHO Running BatchFormFill
java BatchFormFill
@ECHO Running PDFToImage FormFill.pdf
java PDFToImage FormFill.pdf
@ECHO Running StructuredPDF
java StructuredPDF
@ECHO Running ExtractText Fonts.pdf
java ExtractText Fonts.pdf > Fonts.txt
@ECHO Running HelloUnicodeWorld with %windir%\fonts\times.ttf
java HelloUnicodeWorld %windir%\fonts\times.ttf
@ECHO Running Unicode with %windir%\fonts\times.ttf
java Unicode %windir%\fonts\times.ttf

@ECHO Running Sign on Annotations.pdf
keytool -genkeypair -keystore testkeystore.jks -dname "C=GB, O=Test Org, CN=Test Key" -storepass password -keypass password -keyalg RSA
java Sign --keystore testkeystore.jks --password password Annotations.pdf

@ECHO Running Dump on Annotations.pdf
java Dump Sign.pdf

set CLASSPATH=%OLDCLASSPATH%
set OLDCLASSPATH=
