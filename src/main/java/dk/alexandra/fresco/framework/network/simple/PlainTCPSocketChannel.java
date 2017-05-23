/**
 * %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
 * <p>
 * Copyright (c) 2012 - SCAPI (http://crypto.biu.ac.il/scapi)
 * This file is part of the SCAPI project.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * <p>
 * We request that any publication and/or code referring to and/or based on SCAPI contain an appropriate citation to SCAPI, including a reference to
 * http://crypto.biu.ac.il/SCAPI.
 * <p>
 * SCAPI uses Crypto++, Miracl, NTL and Bouncy Castle. Please see these projects for any further licensing issues.
 * %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
 */

package dk.alexandra.fresco.framework.network.simple;

import dk.alexandra.fresco.framework.MPCException;
import edu.biu.scapi.comm.twoPartyComm.SocketPartyData;
import edu.biu.scapi.generals.Logging;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

public class PlainTCPSocketChannel {

    private Thread inputReader;
    private Thread outputWriter;

    private BlockingQueue<byte[]> pendingOutput = new LinkedBlockingQueue<>();
    private BlockingQueue<byte[]> pendingInput = new LinkedBlockingQueue<>();


    private Socket sendSocket;                //A socket used to send messages.
    private Socket receiveSocket;                //A socket used to receive messages.
    private DataOutputStream outStream;        //Used to send a message
    private DataInputStream inStream;            //Used to receive a message.
    private InetSocketAddress socketAddress;    //The address of the other party.
    private SocketPartyData me;                    //Used to send the identity if needed.
    private boolean checkIdentity;            //Indicated if there is a need to verify identity.
    private boolean closed;

    /**
     * A constructor that set the given socket address and set the state of this channel to not ready.
     *
     * @param socketAddress other end's InetSocketAddress
     */
    public PlainTCPSocketChannel(InetSocketAddress socketAddress, boolean checkIdentity, SocketPartyData me) {
        super();
        this.socketAddress = socketAddress;
        this.checkIdentity = checkIdentity;
        this.me = me;
    }


    /**
     * Sends the message to the other user of the channel with TCP protocol.
     *
     * @param msg the object to send.
     * @throws IOException Any of the usual Input/Output related exceptions.
     */
    public void send(byte[] msg) throws IOException {
        try {
            if (msg.length == 0)
                throw new RuntimeException("Insane, why send a null length array?");
            pendingOutput.put(msg);
        } catch (InterruptedException e) {
            throw new MPCException("Error", e);
        }
    }

    /**
     * Receives the message sent by the other user of the channel.
     *
     * @throws ClassNotFoundException The Class of the serialized object cannot be found.
     * @throws IOException            Any of the usual Input/Output related exceptions.
     */
    public byte[] receive() throws ClassNotFoundException, IOException {
        try {
            return pendingInput.take();
        } catch (InterruptedException e) {
            throw new MPCException("Error", e);
        }
    }

    /**
     * Closes the sockets and all other used resources.
     */
    public void close() {

        try {
            closed = true;
            if (sendSocket != null) {
                outStream.close();
                sendSocket.close();

            }
            if (outputWriter != null) {
                outputWriter.interrupt();
            }
            if (inputReader != null) {
                inputReader.interrupt();
            }
            if (receiveSocket != null) {

                inStream.close();
                receiveSocket.close();
            }
        } catch (IOException e) {

            Logging.getLogger().log(Level.WARNING, e.toString());
        }
    }

    /**
     * Checks if the channel os closed or not.
     *
     * @return true if the channel is closed; False, otherwise.
     */
    public boolean isClosed() {
        return receiveSocket.isInputShutdown() || sendSocket.isOutputShutdown() ||
                sendSocket.isClosed() || receiveSocket.isClosed() ||
                !sendSocket.isConnected() || !receiveSocket.isConnected();
    }


    public boolean connect() {

        //try to connect
        Logging.getLogger().log(Level.INFO, "Trying to connect to " + socketAddress.getAddress() + " on port " + socketAddress.getPort());
        try {
            //create and connect the socket. Cannot reconnect if the function connect fails since it closes the socket.
            sendSocket = new Socket(socketAddress.getAddress(), socketAddress.getPort());

            if (sendSocket.isConnected()) {

                if (checkIdentity) {
                    sendIdentity();
                }

                Logging.getLogger().log(Level.INFO, "Socket connected");
                outStream = new DataOutputStream(sendSocket.getOutputStream());

                //After the send socket is connected, need to check if the receive socket is also connected.
                //If so, set the channel state to READY.
                setReady();
            }
        } catch (IOException e) {
            Logging.getLogger().log(Level.FINEST, e.toString());
            System.out.println(e.toString());
            return false;
        }
        return true;
    }

    protected void sendIdentity() throws IOException {
        Logging.getLogger().info("Sending identity" + me.getPort());
        byte[] port = ByteBuffer.allocate(4).putInt(me.getPort()).array();
        sendSocket.getOutputStream().write(port, 0, port.length);
        Logging.getLogger().info("Sent identity" + me.getPort());
    }

    /**
     * Returns if the send socket is connected.
     */
    protected boolean isSendConnected() {

        if (sendSocket != null) {
            return sendSocket.isConnected();

        } else {
            return false;
        }
    }

    protected boolean isConnected() {
        return isSendConnected();
    }

    /**
     * Sets the receive socket and the input stream.
     *
     * @param socket the receive socket to set.
     */
    public void setReceiveSocket(Socket socket) {
        Logging.getLogger().info("Set recive socket" + me.getPort());
        this.receiveSocket = socket;

        try {
            //set the input and output streams
            inStream = new DataInputStream(socket.getInputStream());
            //After the receive socket is connected, need to check if the send socket is also connected.
            //If so, set the channel state to READY.
            setReady();
        } catch (IOException e) {

            Logging.getLogger().log(Level.WARNING, e.toString());
        }
    }

    /**
     * This function sets the channel state to READY in case both send and receive sockets are connected.
     */
    protected void setReady() {
        if (sendSocket != null && receiveSocket != null) {

            if (sendSocket.isConnected() && receiveSocket.isConnected()) {
                closed = false;
                inputReader = new Thread(() -> {
                    while (!closed) {
                        try {
                            int length = inStream.readInt();

                            byte[] bytes = new byte[length];
                            inStream.readFully(bytes);

                            pendingInput.add(bytes);

                        } catch (ClosedChannelException e) {
                            // Closing down
                            Logging.getLogger().info("Closed by interruption");
                        } catch (IOException e) {
                            Logging.getLogger().info("Error: " + e.getMessage());
                            throw new RuntimeException(e);
                        }
                    }
                });
                inputReader.start();
                outputWriter = new Thread(() -> {
                    while (!closed) {
                        try {
                            byte[] msg = pendingOutput.take();

                            int length = msg.length;
                            outStream.writeInt(length);
                            outStream.write(msg);
                        } catch (InterruptedException e) {
                            // All is dandy, we should stop now
                            Logging.getLogger().info("Closed by InterruptException");
                            return;
                        } catch (Exception e) {
                            Logging.getLogger().info("Error: " + e.getMessage());
                            throw new RuntimeException(e);
                        }

                    }
                });
                outputWriter.start();
                //set the channel state to READY
                this.setState(State.READY);
                Logging.getLogger().log(Level.INFO, "state: ready " + toString());
            }
        }
    }

    void enableNage(boolean enableNagle) {
        try {
            sendSocket.setTcpNoDelay(!enableNagle);
            receiveSocket.setTcpNoDelay(!enableNagle);
        } catch (SocketException e) {
            Logging.getLogger().log(Level.WARNING, e.toString());
        }
    }


    private State state = State.NOT_INIT;


    public State getState() {
        return this.state;
    }

    public void setState(State state) {
        this.state = state;
    }


    public static enum State {
        NOT_INIT,
        CONNECTING,
        SECURING,
        READY;

        private State() {
        }
    }

}