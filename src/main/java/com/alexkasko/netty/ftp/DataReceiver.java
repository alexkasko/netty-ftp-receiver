package com.alexkasko.netty.ftp;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: alexkasko
 * Date: 12/28/12
 */
public interface DataReceiver {
    void receive(String name, InputStream data) throws IOException;
}
