package fr.i360matt.runnableOverNetwork;

import java.io.*;

public class IOHelper {
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void sneakyThrow (final Throwable e) throws E {
        throw (E) e;
    }

    public static byte[] readAllBytes (final InputStream inputStream) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        int nRead;
        baos.reset();
        while ((nRead = inputStream.read(buffer, 0, buffer.length)) != -1) {
            baos.write(buffer, 0, nRead);
        }
        inputStream.close();
        return baos.toByteArray();
    }

    /**
     * Work around a bug in the JVM when reading large amount of data
     * @author Fox2Code
     */
    public static int readLarge (final InputStream inputStream, final byte[] bytes) throws IOException {
        int read = 0;
        if (bytes.length < 0x8FFF) {
            read += inputStream.read(bytes);
        } else {
            int i = 0;
            while (i < bytes.length) {
                read += inputStream.read(bytes, i, Math.min(0x8FFF, bytes.length-i));
                i+=0x8FFF;
            }
        }
        return read;
    }

    /**
     * Safe way to write String
     * @author Fox2Code
     */
    public static void writeString (final DataOutput dataOutput, final String str) throws IOException {
        dataOutput.writeByte(str.length());
        dataOutput.writeChars(str);
    }

    /**
     * Safe way to read String
     * @author Fox2Code
     */
    public static String readString (final DataInput dataInput) throws IOException {
        final int l = dataInput.readUnsignedByte();
        final char[] chars = new char[l];
        for (int i = 0; i < l; i++) {
            chars[i] = dataInput.readChar();
        }
        return String.valueOf(chars);
    }
}
