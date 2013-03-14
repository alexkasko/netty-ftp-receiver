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
 * Netty handler, partial implementation of <a href="http://tools.ietf.org/html/rfc959">RFC 959 "File Transfer Protocol (FTP)"</a>
 * for receiving FTP files. Both active and passive modes are supported.
 *
 * @author alexkasko
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

    /**
     * Constructor for FTP active mode
     *
     * @param receiver data receiver implementation
     */
    public FtpServerHandler(DataReceiver receiver) {
        this(receiver, new byte[]{127, 0, 0, 1}, 2121, 4242, 10);
    }

    /**
     * Constructor for FTP passive mode
     *
     * @param receiver data receiver implementation
     * @param passiveAddress passive IP address
     * @param lowestPassivePort lowest bound of passive ports range
     * @param highestPassivePort highest bound of passive ports range
     * @param passiveOpenAttempts number of ports to choose for passive socket open before reporting error
     */
    public FtpServerHandler(DataReceiver receiver, InetAddress passiveAddress, int lowestPassivePort, int highestPassivePort, int passiveOpenAttempts) {
        this(receiver, passiveAddress.getAddress(), lowestPassivePort, highestPassivePort, passiveOpenAttempts);
    }

    /**
     * Constructor for FTP passive mode
     *
     * @param receiver data receiver implementation
     * @param passiveAddress passive IP address
     * @param lowestPassivePort lowest bound of passive ports range
     * @param highestPassivePort highest bound of passive ports range
     * @param passiveOpenAttempts number of ports to choose for passive socket open before reporting error
     */
    public FtpServerHandler(DataReceiver receiver, byte[] passiveAddress, int lowestPassivePort, int highestPassivePort, int passiveOpenAttempts) {
        if(null == receiver) throw new IllegalArgumentException("Provided receiver is null");
        this.receiver = receiver;
        if(null == passiveAddress) throw new IllegalArgumentException("Provided passiveAddress is null");
        this.passiveAddress = passiveAddress;
        if (lowestPassivePort <= 0 || lowestPassivePort >= 1 << 16) throw new IllegalArgumentException(
                "Provided lowestPassivePort: [" + lowestPassivePort + "] ia out of valid range");
        if (highestPassivePort <= 0 || highestPassivePort >= 1 << 16) throw new IllegalArgumentException(
                        "Provided highestPassivePort: [" + highestPassivePort + "] ia out of valid range");
        if (lowestPassivePort > highestPassivePort) throw new IllegalArgumentException(
                "Provided lowestPassivePort: [" + lowestPassivePort + "] must be not greater than " +
                        "highestPassivePort: [" + highestPassivePort + "]");
        this.lowestPassivePort = lowestPassivePort;
        this.highestPassivePort = highestPassivePort;
        if(passiveOpenAttempts <= 0) throw new IllegalArgumentException(
                "Provided passiveOpenAttempts: [" + passiveOpenAttempts + "] must be positive");
        this.passiveOpenAttempts = passiveOpenAttempts;
    }

    /**
     * {@inheritDoc}
     */
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
        else if ("RMD".equals(cmd)) send("550 " + args + ": no such file or directory", ctx, cmd, args);
        else if ("RNFR".equals(cmd)) send("350 File exists, ready for destination name", ctx, cmd, args);
        else if ("RNTO".equals(cmd)) send("250 RNTO command successful", ctx, cmd, args);
        else if ("SYST".equals(cmd)) send("215 UNIX Type: Java custom implementation", ctx, cmd, args);
        else if ("NOOP".equals(cmd)) send("200 OK", ctx, cmd, args);
        else if ("TYPE".equals(cmd)) type(ctx, args);
        else if ("PORT".equals(cmd)) port(ctx, args);
        else if ("PASV".equals(cmd)) pasv(ctx, args);
        else if ("LIST".equals(cmd)) list(ctx, args);
        else if ("STOR".equals(cmd)) stor(ctx, args);
        else send("500 Command unrecognized", ctx, cmd, args);
        lastCommand = cmd;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        e.getCause().printStackTrace();
        send("500 Unspecified error", ctx, e.getCause().getMessage(), "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        send("220 Service ready", ctx, "[connected]", "");
    }

    /**
     * TYPE command handler
     *
     * @param ctx handler context
     * @param args command arguments
     */
    protected void type(ChannelHandlerContext ctx, String args) {
        if ("I".equals(args)) send("200 Type set to IMAGE NONPRINT", ctx, "TYPE", args);
        else if ("A".equals(args)) send("200 Type set to ASCII NONPRINT", ctx, "TYPE", args);
        else send("504 Command not implemented for that parameter", ctx, "TYPE", args);
    }

    /**
     * PORT command handler
     *
     * @param ctx handler context
     * @param args command arguments
     */
    protected void port(ChannelHandlerContext ctx, String args) {
        InetSocketAddress addr = parsePortArgs(args);
        if (null == addr) send("501 Syntax error in parameters or arguments", ctx, "PORT", args);
        else if (null != this.activeSocket && !activeSocket.isClosed()) send("503 Bad sequence of commands", ctx, "PORT", args);
        else {
            try {
                this.activeSocket = new Socket(addr.getAddress(), addr.getPort());
                send("200 PORT command successful", ctx, "PORT", args);
            } catch (IOException e1) {
                send("552 Requested file action aborted", ctx, "PORT", args);
            }
        }
    }

    /**
     * PASV command handler
     *
     * @param ctx handler context
     * @param args command arguments
     * @throws InterruptedException
     */
    protected void pasv(ChannelHandlerContext ctx, String args) throws InterruptedException {
        for(int i=0; i< passiveOpenAttempts; i++) {
            int port = choosePassivePort(lowestPassivePort, highestPassivePort);
            int part1 = (byte) (port >> 8) & 0xff;
            int part2 = (byte) (port >> 0) & 0xff;
            try {
                InetAddress addr = InetAddress.getByAddress(passiveAddress);
                this.passiveSocket = new ServerSocket(port, 50, addr);
                send(String.format("227 Entering Passive Mode (%d,%d,%d,%d,%d,%d)",
                        passiveAddress[0], passiveAddress[1], passiveAddress[2], passiveAddress[3], part1, part2), ctx, "PASV", args);
                break;
            } catch (IOException e1) {
                logger.warn("Fail binding on port: " + port);
                // ensure port change
                Thread.sleep(1);
            }
        }
        if(null == this.passiveSocket) send("551 Requested action aborted", ctx, "PASV", args);
    }

    /**
     * LIST command handler
     *
     * @param ctx handler context
     * @param args command arguments
     */
    protected void list(ChannelHandlerContext ctx, String args) {
        if ("PORT".equals(lastCommand)) {
            send("150 Opening binary mode data connection for LIST " + args, ctx, "LIST", args);
            try {
                activeSocket.getOutputStream().write(CRLF);
                send("226 Transfer complete for LIST", ctx, "", args);
            } catch (IOException e1) {
                send("552 Requested file action aborted", ctx, "LIST", args);
            } finally {
                closeQuetly(activeSocket);
                activeSocket = null;
            }
        } else if ("PASV".equals(lastCommand)) {
            send("150 Opening binary mode data connection for LIST on port: " + passiveSocket.getLocalPort(), ctx, "LIST", args);
            try {
                Socket clientSocket = passiveSocket.accept();
                clientSocket.getOutputStream().write(CRLF);
                clientSocket.getOutputStream().close();
                send("226 Transfer complete for LIST", ctx, "", args);
            } catch (IOException e1) {
                send("552 Requested file action aborted", ctx, "LIST", args);
            } finally {
                closeQuetly(passiveSocket);
                passiveSocket = null;
            }
        } else send("503 Bad sequence of commands", ctx, "LIST", args);
    }

    /**
     * STOP command handler
     *
     * @param ctx handler context
     * @param args command arguments
     */
    protected void stor(ChannelHandlerContext ctx, String args) {
        if ("PORT".equals(lastCommand)) {
            send("150 Opening binary mode data connection for " + args, ctx, "", args);
            try {
                receiver.receive(args, activeSocket.getInputStream());
                send("226 Transfer complete for STOR " + args, ctx, "", args);
            } catch (IOException e1) {
                send("552 Requested file action aborted", ctx, "STOR", args);
            } finally {
                closeQuetly(activeSocket);
                activeSocket = null;
            }
        } else if ("PASV".equals(lastCommand)) {
            send("150 Opening binary mode data connection for " + args, ctx, "STOR", args);
            try {
                Socket clientSocket = passiveSocket.accept();
                receiver.receive(args, clientSocket.getInputStream());
                send("226 Transfer complete for STOR " + args, ctx, "", args);
            } catch (IOException e1) {
                send("552 Requested file action aborted", ctx, "STOR", args);
            } finally {
                closeQuetly(passiveSocket);
                passiveSocket = null;
            }
        } else send("503 Bad sequence of commands", ctx, "STOR", args);
    }

    private static void send(String response, ChannelHandlerContext ctx, String command, String args) {
        if(command.length() > 0) logger.debug("-> " + command + " " + args);
        logger.debug("<- " + response);
        String line = response + "\r\n";
        byte[] data = line.getBytes(ASCII);
        ctx.getChannel().write(wrappedBuffer(data));
    }

    private static InetSocketAddress parsePortArgs(String portArgs) {
        String[] strParts = portArgs.split(",");
        if (strParts.length != 6) return null;
        byte[] address = new byte[4];
        int[] parts = new int[6];
        for (int i = 0; i < 6; i++) {
            try { parts[i] = Integer.parseInt(strParts[i]); }
            catch (NumberFormatException e) { return null; }
            if (parts[i] < 0 || parts[i] > 255) return null;
        }
        for (int i = 0; i < 4; i++) address[i] = (byte) parts[i];
        int port = parts[4] << 8 | parts[5];
        InetAddress inetAddress;
        try { inetAddress = InetAddress.getByAddress(address); }
        catch (UnknownHostException e) { return null; }
        return new InetSocketAddress(inetAddress, port);
    }

    private static int choosePassivePort(int low, int high) {
        int length = high - low;
        int offset = (int) (currentTimeMillis() % length);
        return low + offset;
    }

    private static void closeQuetly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private static void closeQuetly(ServerSocket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
