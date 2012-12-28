package com.alexkasko.netty.ftp;

import org.jboss.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;

import static java.lang.System.currentTimeMillis;
import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;

/**
 * User: alexkasko
 * Date: 12/27/12
 */
public class FtpServerHandler extends SimpleChannelUpstreamHandler {
    private static final Logger logger = LoggerFactory.getLogger(FtpServerHandler.class);
    private static final byte CR = 13;
    private static final byte LF = 10;
    private static final byte[] CRLF = new byte[]{CR, LF};
    private static final Charset ASCII = Charset.forName("ASCII");

    private final DataReceiver receiver;
    private final byte[] passiveAddress;
    private final int lowestPassivePort;
    private final int highestPassivePort;
    private final int passiveOpenAttempts;

    private String curDir = "/";
    private String lastCommand = "";
    private Socket activeSocket = null;
    private ServerSocket passiveSocket = null;

    public FtpServerHandler(DataReceiver receiver) {
        this.receiver = receiver;
        this.passiveOpenAttempts = 10;
        this.passiveAddress = new byte[]{127, 0, 0, 1};
        this.lowestPassivePort = 2121;
        this.highestPassivePort = 4242;
    }

    // todo checks
    public FtpServerHandler(DataReceiver receiver, byte[] passiveAddress, int lowestPassivePort, int highestPassivePort, int passiveOpenAttempts) {
        this.receiver = receiver;
        this.passiveAddress = passiveAddress;
        this.lowestPassivePort = lowestPassivePort;
        this.highestPassivePort = highestPassivePort;
        this.passiveOpenAttempts = passiveOpenAttempts;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        String message = ((String) e.getMessage()).trim();
        if (message.length() < 3) send("501 Syntax error", ctx, message, "");
        String cmd = 3 == message.length() ? message.substring(0, 3) : message.substring(0, 4).trim();
        String args = message.length() > cmd.length() ? message.substring(cmd.length() + 1) : "";
        // dispatch
        if ("USER".equals(cmd)) send("230 USER LOGGED IN", ctx, cmd, args);
        else if ("CWD".equals(cmd)) { curDir = args; send("250 CWD command successful", ctx, cmd, args); }
        else if ("PWD".equals(cmd)) send("257 \"" + curDir + "\" is current directory", ctx, cmd, args);
        else if ("MKD".equals(cmd)) send("521 \"" + args + "\" directory exists", ctx, cmd, args);
        else if ("DELE".equals(cmd)) send("550 " + args + ": no such file or directory", ctx, cmd, args);
        else if ("RNFR".equals(cmd)) send("350 File exists, ready for destination name", ctx, cmd, args);
        else if ("RNTO".equals(cmd)) send("250 RNTO command successful", ctx, cmd, args);
        else if ("TYPE".equals(cmd)) {
            if ("I".equals(args)) send("200 Type set to IMAGE NONPRINT", ctx, cmd, args);
            else if ("A".equals(args)) send("200 Type set to ASCII NONPRINT", ctx, cmd, args);
            else send("504 Command not implemented for that parameter", ctx, cmd, args);
        } else if ("PORT".equals(cmd)) {
            InetSocketAddress addr = parsePortArgs(args);
            if (null == addr) send("501 Syntax error in parameters or arguments", ctx, cmd, args);
            else if (null != this.activeSocket && !activeSocket.isClosed()) send("503 Bad sequence of commands", ctx, cmd, args);
            else {
                try {
                    this.activeSocket = new Socket(addr.getAddress(), addr.getPort());
                    send("200 PORT command successful", ctx, cmd, args);
                } catch (IOException e1) { send("552 Requested file action aborted", ctx, cmd, args);}
            }
        } else if ("PASV".equals(cmd)) {
            for(int i=0; i< passiveOpenAttempts; i++) {
                int port = choosePassivePort(lowestPassivePort, highestPassivePort);
                int part1 = (byte) (port >> 8) & 0xff;
                int part2 = (byte) (port >> 0) & 0xff;
                try {
                    InetAddress addr = InetAddress.getByAddress(passiveAddress);
                    this.passiveSocket = new ServerSocket(port, 50, addr);
                    send(String.format("227 Entering Passive Mode (%d,%d,%d,%d,%d,%d)",
                            passiveAddress[0], passiveAddress[1], passiveAddress[2], passiveAddress[3], part1, part2), ctx, cmd, args);
                    break;
                } catch (IOException e1) {logger.warn("Fail binding on port: " + port); Thread.sleep(1);}
            }
            if(null == this.passiveSocket) send("551 Requested action aborted", ctx, cmd, args);
        } else if ("LIST".equals(cmd)) {
            if ("PORT".equals(lastCommand)) {
                send("150 Opening binary mode data connection for LIST " + args, ctx, cmd, args);
                try {
                    activeSocket.getOutputStream().write(CRLF);
                    send("226 Transfer complete for LIST " + curDir, ctx, cmd, args);
                } catch (IOException e1) { send("552 Requested file action aborted", ctx, cmd, args);}
                finally { activeSocket.close(); activeSocket = null; }
            } else if("PASV".equals(lastCommand)) {
                send("150 Opening binary mode data connection for LIST on port: " + passiveSocket.getLocalPort(), ctx, cmd, args);
                try {
                    Socket clientSocket = passiveSocket.accept();
                    clientSocket.getOutputStream().write(CRLF);
                    clientSocket.getOutputStream().close();
                    send("226 Transfer complete for LIST " + curDir, ctx, cmd, args);
                } catch (IOException e1) { send("552 Requested file action aborted", ctx, cmd, args);}
                finally { passiveSocket.close(); passiveSocket = null; }
            } else send("503 Bad sequence of commands", ctx, cmd, args);
        } else if ("STOR".equals(cmd)) {
            if ("PORT".equals(lastCommand)) {
                send("150 Opening binary mode data connection for " + args, ctx, cmd, args);
                try {
                    receiver.receive(args, activeSocket.getInputStream());
                    send("226 Transfer complete for STOR " + args, ctx, cmd, args);
                } catch (IOException e1) { send("552 Requested file action aborted", ctx, cmd, args);}
                finally { activeSocket.close(); activeSocket = null; }
            } else if("PASV".equals(lastCommand)) {
                send("150 Opening binary mode data connection for " + args, ctx, cmd, args);
                try {
                    Socket clientSocket = passiveSocket.accept();
                    receiver.receive(args, clientSocket.getInputStream());
                    send("226 Transfer complete for STOR " + args, ctx, cmd, args);
                } catch (IOException e1) { send("552 Requested file action aborted", ctx, cmd, args);}
                finally { passiveSocket.close(); passiveSocket = null; }
            } else send("503 Bad sequence of commands", ctx, cmd, args);
        } else send("500 Command unrecognized", ctx, cmd, args);
        lastCommand = cmd;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        e.getCause().printStackTrace();
        send("500 Unspecified error", ctx, e.getCause().getMessage(), "");
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        send("220 Service ready", ctx, "[connected]", "");
    }

    private static void send(String response, ChannelHandlerContext ctx, String command, String args) {
        logger.debug("-> " + command + " " + args);
        logger.debug("<- " + response);
        String line = response + "\r\n";
        byte[] data = line.getBytes(ASCII);
        ctx.getChannel().write(wrappedBuffer(data));
    }

    // todo: rewriteme
    private static InetSocketAddress parsePortArgs(String arg) {
        String[] elements = arg.split(",");
        if (elements.length != 6) {
            return null;
        }
        byte[] address = new byte[4];
        int[] iElements = new int[6];
        for (int i = 0; i < 6; i++) {
            try {
                iElements[i] = Integer.parseInt(elements[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (iElements[i] < 0 || iElements[i] > 255) {
                return null;
            }
        }
        for (int i = 0; i < 4; i++) {
            address[i] = (byte) iElements[i];
        }
        int port = iElements[4] << 8 | iElements[5];
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            return null;
        }
        return new InetSocketAddress(inetAddress, port);
    }

    private int choosePassivePort(int low, int high) {
        int length = high - low;
        int offset = (int) (currentTimeMillis() % length);
        return low + offset;
    }
}
