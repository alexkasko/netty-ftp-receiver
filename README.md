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
        <version>1.2.3</version>
    </dependency>

See usage example in [tests](https://github.com/alexkasko/netty-ftp-receiver/blob/master/src/test/java/com/alexkasko/netty/ftp/FtpServerTest.java).

Javadocs for the latest release are available [here](http://alexkasko.github.com/netty-ftp-receiver/javadocs).

License information
-------------------

This project is released under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Changelog
---------

**1.2.3** (2014-03-12)

 * prevent error reply sending to closed channel

**1.2.2** (2014-02-19)

 * fix NPE on reporting error with `null` message

**1.2.1** (2014-01-19)

 * guard handler state with atomic references
 * incorrect passive advertised address fix
 * socket timeout support for passive mode

**1.2** (2013-12-26)

 * ALLO command support
 * separate advertized address for passive mode

**1.1** (2013-12-20)

 * QUIT command added
 * current directory support in receiver

**1.0.2** (2013-04-30)

 * fix NPE on STOR after connection error

**1.0.1** (2013-04-29)

 * socket closing fix
 * extensive logging

**1.0** (2013-03-14)

 * initial public version
