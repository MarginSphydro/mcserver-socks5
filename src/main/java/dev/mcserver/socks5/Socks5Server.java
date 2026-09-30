package dev.mcserver.socks5;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A minimal SOCKS5 CONNECT proxy with no authentication, intended for trusted networks. */
final class Socks5Server implements AutoCloseable {
    private static final int SOCKS_VERSION = 5;
    private static final int METHOD_NO_AUTH = 0;
    private static final int METHOD_UNACCEPTABLE = 0xFF;
    private static final int COMMAND_CONNECT = 1;
    private static final int ADDRESS_IPV4 = 1;
    private static final int ADDRESS_DOMAIN = 3;
    private static final int ADDRESS_IPV6 = 4;

    private final InetAddress bindAddress;
    private final int port;
    private final int connectTimeoutMillis;
    private final Logger logger;
    private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private ServerSocket serverSocket;

    Socks5Server(InetAddress bindAddress, int port, int connectTimeoutMillis, Logger logger) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.connectTimeoutMillis = Math.max(1, connectTimeoutMillis);
        this.logger = logger;
    }

    void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(bindAddress, port));
        running.set(true);
        clients.submit(this::acceptLoop);
        logger.info("SOCKS5 proxy listening on " + bindAddress.getHostAddress() + ':' + port);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                clients.submit(() -> handleClient(client));
            } catch (IOException exception) {
                if (running.get()) {
                    logger.log(Level.WARNING, "SOCKS5 accept failed", exception);
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (client) {
            DataInputStream input = new DataInputStream(client.getInputStream());
            DataOutputStream output = new DataOutputStream(client.getOutputStream());
            negotiateAuthentication(input, output);
            Destination destination = readConnectRequest(input);

            try (Socket remote = new Socket()) {
                remote.connect(new InetSocketAddress(destination.host(), destination.port()), connectTimeoutMillis);
                writeReply(output, 0);
                relay(client, remote);
            } catch (IOException exception) {
                writeReply(output, replyFor(exception));
            }
        } catch (EOFException ignored) {
            // Clients frequently disconnect before completing their handshake.
        } catch (IOException exception) {
            logger.log(Level.FINE, "SOCKS5 client handling failed", exception);
        }
    }

    private static void negotiateAuthentication(DataInputStream input, DataOutputStream output) throws IOException {
        if (input.readUnsignedByte() != SOCKS_VERSION) {
            throw new IOException("Client did not use SOCKS5");
        }
        int methodCount = input.readUnsignedByte();
        byte[] methods = input.readNBytes(methodCount);
        if (methods.length != methodCount) throw new EOFException();
        boolean supportsNoAuth = false;
        for (byte method : methods) supportsNoAuth |= Byte.toUnsignedInt(method) == METHOD_NO_AUTH;
        output.write(new byte[]{SOCKS_VERSION, (byte) (supportsNoAuth ? METHOD_NO_AUTH : METHOD_UNACCEPTABLE)});
        output.flush();
        if (!supportsNoAuth) throw new IOException("Client requires unsupported authentication");
    }

    private static Destination readConnectRequest(DataInputStream input) throws IOException {
        if (input.readUnsignedByte() != SOCKS_VERSION) throw new IOException("Invalid SOCKS5 request version");
        int command = input.readUnsignedByte();
        input.readUnsignedByte(); // reserved
        int addressType = input.readUnsignedByte();
        String host = switch (addressType) {
            case ADDRESS_IPV4 -> InetAddress.getByAddress(input.readNBytes(4)).getHostAddress();
            case ADDRESS_IPV6 -> InetAddress.getByAddress(input.readNBytes(16)).getHostAddress();
            case ADDRESS_DOMAIN -> new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.US_ASCII);
            default -> throw new IOException("Unsupported address type: " + addressType);
        };
        int port = input.readUnsignedShort();
        if (command != COMMAND_CONNECT) throw new IOException("Only SOCKS5 CONNECT is supported");
        if (host.isEmpty()) throw new IOException("Destination host is empty");
        return new Destination(host, port);
    }

    private static void writeReply(DataOutputStream output, int status) throws IOException {
        output.write(new byte[]{SOCKS_VERSION, (byte) status, 0, ADDRESS_IPV4, 0, 0, 0, 0, 0, 0});
        output.flush();
    }

    private static int replyFor(IOException exception) {
        return exception instanceof SocketTimeoutException ? 6 : 5;
    }

    private static void relay(Socket client, Socket remote) throws IOException {
        ExecutorService relayExecutor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            relayExecutor.submit(() -> copy(client, remote));
            copy(remote, client);
        } finally {
            relayExecutor.shutdownNow();
        }
    }

    private static void copy(Socket source, Socket destination) {
        try (InputStream input = source.getInputStream(); OutputStream output = destination.getOutputStream()) {
            input.transferTo(output);
        } catch (IOException ignored) {
            // Closing either endpoint ends both relay directions.
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException exception) {
                logger.log(Level.FINE, "Could not close SOCKS5 server socket", exception);
            }
        }
        clients.shutdownNow();
    }

    private record Destination(String host, int port) { }
}
