Netty handler for receiving files over FTP
==========================================

[Netty](http://netty.io/) handler, partial implementation of RFC 959 "File Transfer Protocol (FTP)"
for receiving FTP files. Both active and passive modes are supported.

Depends on Netty 3 and [slf4j-api](http://www.slf4j.org/).

Netty 4 compatibility and more complete FTP support is implemented in [fork by codingtony](https://github.com/codingtony/netty-ftp-receiver).

Library is available in [Maven cental](http://repo1.maven.org/maven2/com/alexkasko/netty/):

    <dependency>
        <groupId>com.alexkasko.netty</groupId>
        <artifactId>netty-ftp-receiver</artifactId>
        <version>1.0.2</version>
    </dependency>

See usage example in [tests](https://github.com/alexkasko/netty-ftp-receiver/blob/master/src/test/java/com/alexkasko/netty/ftp/FtpServerTest.java).

Javadocs for the latest release are available [here](http://alexkasko.github.com/netty-ftp-receiver/javadocs).

License information
-------------------

This project is released under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Changelog
---------

**1.0.2** (2013-04-30)

 * fix NPE on STOR after connection error

**1.0.1** (2013-04-29)

 * socket closing fix
 * extensive logging

**1.0** (2013-03-14)

 * initial public version
