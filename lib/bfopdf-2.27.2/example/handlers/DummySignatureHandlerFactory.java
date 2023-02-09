// $Id: DummySignatureHandlerFactory.java 10479 2009-07-10 09:51:07Z chris $

import org.faceless.pdf2.*;

public class DummySignatureHandlerFactory implements SignatureHandlerFactory
{
    public SignatureHandler getHandler() {
        return new DummySignatureHandler();
    }
}
