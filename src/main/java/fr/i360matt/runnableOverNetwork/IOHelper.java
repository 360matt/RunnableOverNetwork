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
    public static void writeSmallString (final DataOutput dataOutput, final String str) throws IOException {
        dataOutput.writeByte(str.length());
        dataOutput.writeChars(str);
    }

    /**
     * Safe way to read String
     * @author Fox2Code
     */
    public static String readSmallString (final DataInput dataInput) throws IOException {
        final int l = dataInput.readUnsignedByte();
        final char[] chars = new char[l];
        for (int i = 0; i < l; i++) {
            chars[i] = dataInput.readChar();
        }
        return String.valueOf(chars);
    }

    /**
     * Safe way to write String
     * @author Fox2Code
     */
    public static void writeString (final DataOutput dataOutput, final String str) throws IOException {
        dataOutput.writeShort(str.length());
        dataOutput.writeChars(str);
    }

    /**
     * Safe way to read String
     * @author Fox2Code
     */
    public static String readString (final DataInput dataInput) throws IOException {
        final int l = dataInput.readUnsignedShort();
        final char[] chars = new char[l];
        for (int i = 0; i < l; i++) {
            chars[i] = dataInput.readChar();
        }
        return String.valueOf(chars);
    }
    /**
     * Safe way to write String
     * @author Fox2Code
     */
    public static void writeInts (final DataOutput dataOutput, final int[] ints) throws IOException {
        dataOutput.writeShort(ints.length);
        for (int i : ints)
            dataOutput.writeInt(i);
    }

    /**
     * Safe way to read String
     * @author Fox2Code
     */
    public static int[] readInts (final DataInput dataInput) throws IOException {
        final int l = dataInput.readUnsignedShort();
        final int[] ints = new int[l];
        for (int i = 0; i < l; i++) {
            ints[i] = dataInput.readInt();
        }
        return ints;
    }

    private static final int UNSAFE_TYPE = -1;
    private static final int NULL_TYPE = 0;
    private static final int STRING_TYPE = 1;
    private static final int STRINGS_TYPE = 2;
    private static final int INT_TYPE = 3;
    private static final int INTS_TYPE = 4;
    private static final int CLASS_TYPE = 5;
    private static final int BYTES_TYPE = 6;

    /**
     * deserializer
     * @author Fox2Code
     */
    public static Object deserialize (final DataInputStream dataInputStream, ObjectInputStream objectInputStream,
                                      final ClassLoader classLoader) throws IOException, ClassNotFoundException {
        final int type = dataInputStream.readInt();
        switch (type) {
            case UNSAFE_TYPE:
                if (objectInputStream == null)
                    throw new UnsupportedEncodingException("Unsafe serialisation is not enabled!");
                return objectInputStream.readObject();
            case NULL_TYPE:
                return null;
            case STRING_TYPE:
                return IOHelper.readString(dataInputStream);
            case STRINGS_TYPE:
                String[] strings = new String[dataInputStream.readInt()];
                for (int i = 0; i < strings.length;i++)
                    strings[i] = IOHelper.readString(dataInputStream);
                return strings;
            case INT_TYPE:
                return dataInputStream.readInt();
            case INTS_TYPE:
                return IOHelper.readInts(dataInputStream);
            case BYTES_TYPE:
                byte[] bytes = new byte[dataInputStream.readInt()];
                dataInputStream.read(bytes);
                return bytes;
            case CLASS_TYPE:
                return classLoader.loadClass(IOHelper.readString(dataInputStream));
            default:
                throw new UnsupportedEncodingException("Type "+ type + " is not supported!");
        }
    }

    /**
     * serializer
     * @author Fox2Code
     */
    public static void serialize (final DataOutputStream dataOutputStream, final ObjectOutputStream objectOutputStream,
                                  final Object object,final int maxProtocol) throws IOException {
        if (object == null) {
            dataOutputStream.writeInt(NULL_TYPE);
        } else if (object instanceof String) {
            dataOutputStream.writeInt(STRING_TYPE);
            IOHelper.writeString(dataOutputStream, (String) object);
        } else if (object instanceof String[]) {
            dataOutputStream.writeInt(STRINGS_TYPE);
            String[] strings = (String[]) object;
            dataOutputStream.writeInt(strings.length);
            for (String string : strings) {
                IOHelper.writeString(dataOutputStream, string);
            }
        } else if (object instanceof Integer) {
            dataOutputStream.writeInt(INT_TYPE);
            dataOutputStream.writeInt((Integer) object);
        } else if (object instanceof int[]) {
            dataOutputStream.writeInt(INTS_TYPE);
            IOHelper.writeInts(dataOutputStream, (int[]) object);
        } else if (object instanceof Class<?>) {
            dataOutputStream.writeInt(CLASS_TYPE);
            IOHelper.writeString(dataOutputStream, ((Class<?>) object).getName());
        } else if (object instanceof byte[]) {
            dataOutputStream.writeInt(BYTES_TYPE);
            dataOutputStream.writeInt(((byte[]) object).length);
            dataOutputStream.write((byte[]) object);
        } else {
            if (objectOutputStream == null) {
                throw new UnsupportedEncodingException("Unsafe serialisation is not enabled!");
            }
            dataOutputStream.writeInt(UNSAFE_TYPE);
            objectOutputStream.writeObject(object);
        }
    }
}
