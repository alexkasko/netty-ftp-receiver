package com.alexkasko.netty.ftp;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

class ConsoleReceiver implements DataReceiver {
    @Override
    public void receive(String name, InputStream data) throws IOException {
        System.out.println("receiving file: [" + name + "]");
        System.out.println("receiving data:");
        IOUtils.copy(data, System.out);
        System.out.println("");
    }
}