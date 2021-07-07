package fr.i360matt.runnableOverNetwork.auth;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractAuth {
    public static final SecureRandom secureRandom = new SecureRandom();

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    public abstract void authenticateServer(final Socket socket,final AuthParams authParams)
            throws IOException, GeneralSecurityException;

    public abstract void authenticateClient(final Socket socket,final AuthParams authParams)
            throws IOException, GeneralSecurityException;

    public static class AuthParams {
        public final Set<Integer> blockedActions;
        public String username;
        public DataInputStream inputStream;
        public DataOutputStream outputStream;
        public boolean allowRemoteClose;
        public boolean allowUnsafeSerialisation;

        public AuthParams(final String username,
                final DataInputStream inputStream,
                final DataOutputStream outputStream) {
            this.blockedActions = new HashSet<>();
            this.username = username;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }
    }
}
