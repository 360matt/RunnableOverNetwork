package fr.i360matt.runnableOverNetwork.auth;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Auth that use AES to confirm authentification
 */
public class AESPasswordAuth extends AbstractAuth {
    private final SecretKeySpec passwordKeySpec;

    public AESPasswordAuth(final String password) {
        this(sha256().digest(password.getBytes(StandardCharsets.UTF_8)));
    }

    public AESPasswordAuth(final byte[] key) {
        this.passwordKeySpec = new SecretKeySpec(key, "AES");
    }

    @Override
    public void authenticateServer(final Socket socket,final AuthParams authParams)
            throws IOException, GeneralSecurityException {
        final DataInputStream authIn = authParams.inputStream;
        final DataOutputStream authOut = authParams.outputStream;
        final int checkKey = authIn.readInt();
        final byte[] randomKey = new byte[28];
        secureRandom.nextBytes(randomKey);
        authOut.write(randomKey);
        authOut.flush();
        Cipher cipherAes = Cipher.getInstance("AES");
        try {
            cipherAes.init(Cipher.DECRYPT_MODE, this.passwordKeySpec);
            byte[] cipherBytes = new byte[32];
            authIn.read(cipherBytes);
            cipherBytes = cipherAes.doFinal(cipherBytes);
            if (!Arrays.equals(cipherBytes, randomKey)) {
                authOut.writeByte(1); authOut.flush();
                socket.close(); return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            authOut.writeByte(1); authOut.flush();
            socket.close(); return;
        }
        authOut.writeByte(0); authOut.flush();
        final byte[] spec = new byte[16];
        secureRandom.nextBytes(spec);
        cipherAes = Cipher.getInstance("AES");
        cipherAes.init(Cipher.ENCRYPT_MODE, this.passwordKeySpec);
        byte[] block = cipherAes.doFinal(spec);
        if (block.length != 32) throw new IOException("Len is "+ block.length);
        authOut.write(block); authOut.flush();
        final DataInputStream secIn = this.makeInputStream(socket.getInputStream(), spec);
        final DataOutputStream secOut = this.makeOutputStream(socket.getOutputStream(), spec);
        final int secCheckKey = secureRandom.nextInt();
        secOut.writeInt(secCheckKey);
        secOut.writeInt(checkKey); secOut.flush();
        if (secIn.readInt() != (checkKey ^ secCheckKey)) { // Blocked here
            socket.close(); // We shouldn't fail this check this late? MiTM?
            return;
        }
        authParams.inputStream = secIn;
        authParams.outputStream = secOut;
    }

    @Override
    public void authenticateClient(final Socket socket,final AuthParams authParams)
            throws IOException, GeneralSecurityException {
        final DataInputStream authIn = authParams.inputStream;
        final DataOutputStream authOut = authParams.outputStream;
        final int checkKey = secureRandom.nextInt();
        authOut.writeInt(checkKey); authOut.flush();
        final byte[] randomKey = new byte[28];
        authIn.read(randomKey);
        Cipher cipherAes = Cipher.getInstance("AES");
        cipherAes.init(Cipher.ENCRYPT_MODE, this.passwordKeySpec);
        byte[] block = cipherAes.doFinal(randomKey);
        if (block.length != 32) throw new IOException("Len is "+ block.length);
        authOut.write(block); authOut.flush();
        if (authIn.readByte() != 0) {
            throw new IOException("Invalid password");
        }
        cipherAes = Cipher.getInstance("AES");
        cipherAes.init(Cipher.DECRYPT_MODE, this.passwordKeySpec);
        byte[] cipherBytes = new byte[32];
        authIn.read(cipherBytes);
        byte[] spec;
        try {
            spec = cipherAes.doFinal(cipherBytes);
            if (spec.length != 16)
                throw new IOException("MiTM Detected");
        } catch (Exception e) {
            throw new IOException("MiTM Detected", e);
        }
        final DataInputStream secIn = this.makeInputStream(socket.getInputStream(), spec);
        final DataOutputStream secOut = this.makeOutputStream(socket.getOutputStream(), spec);
        int secCheckKey = secIn.readInt(); // Blocked here
        if (secIn.readInt() != checkKey) {
            socket.close();
            throw new IOException("MiTM Detected");
        }
        secOut.writeInt(checkKey ^ secCheckKey); secOut.flush();
        authParams.inputStream = secIn;
        authParams.outputStream = secOut;
    }

    // Java implementation is broken, true crash on first packet
    private static final boolean JAVA_SUPPORT_STREAM_CIPHER = false;

    private DataInputStream makeInputStream(InputStream inputStream,byte[] spec) throws GeneralSecurityException {
        if (!JAVA_SUPPORT_STREAM_CIPHER) return new DataInputStream(inputStream);
        Cipher decryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
        decryptCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(spec, "AES"), new IvParameterSpec(spec));
        return new DataInputStream(new CipherInputStream(inputStream, decryptCipher));
    }

    private DataOutputStream makeOutputStream(OutputStream outputStream,byte[] spec) throws GeneralSecurityException {
        if (!JAVA_SUPPORT_STREAM_CIPHER) return new DataOutputStream(outputStream);
        Cipher encryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(spec, "AES"), new IvParameterSpec(spec));
        return new DataOutputStream(new CipherOutputStream(outputStream, encryptCipher));
    }
}
