package fr.i360matt.runnableOverNetwork;

public interface ConnectionConstants {
    int PROTOCOL_VER = 0;

    int FLAG_ALLOW_REMOTE_CLOSE = 0x01;
    int FLAG_ALLOW_UNSAFE_SERIALISATION = 0x02;

    int ID_KEEP_ALIVE = 0;
    int ID_CLOSE_SERVER = 1;
    int ID_EXEC_CLASS = 2;
    int ID_EXEC_DATA = 3;
    int ID_SEND_DATA = 4;
    int ID_SEND_EXEC_DATA = 5;
    int ID_EXEC_DATA_BLOCKING = 6;
    int ID_EXEC_DATA_FUNC = 7;
    int ID_EXEC_DATA_FUNC_BLOCKING = 8;
    int ID_SEC_BLOCK_ACTIONS = 9;
}
