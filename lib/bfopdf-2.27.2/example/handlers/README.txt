The classes in this directly demonstrate how to create a custom signature handler.
Please see the Javadoc API documentation, in particular for the SignatureHandler
and FormSignature classes, to understand what's going on.

In order to run the example, simply compile and run "SignAndVerify.java". This
will create a file called "Signed.pdf", which is a blank PDF 'signed' with our
dummy handler. It then loads the PDF in and verifies it.

You can also compile and run "DummySignatureProvider.java", which demonstrates
integrating a custom signature handler with the viewer.
