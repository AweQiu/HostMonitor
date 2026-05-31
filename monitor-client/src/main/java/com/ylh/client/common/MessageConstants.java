package com.ylh.client.common;

public final class MessageConstants {

    private MessageConstants() {
    }

    public static final String TYPE_AUTH = "AUTH";
    public static final String TYPE_HOST_INFO = "HOST_INFO";
    public static final String TYPE_HOST_METRIC = "HOST_METRIC";
    public static final String TYPE_PROCESS_LIST = "PROCESS_LIST";
    public static final String TYPE_PROCESS_LIST_FULL = "PROCESS_LIST_FULL";
    public static final String TYPE_PROCESS_LIST_DELTA = "PROCESS_LIST_DELTA";
    public static final String TYPE_CONNECTED = "CONNECTED";
    public static final String TYPE_OK = "OK";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_CMD = "CMD";
    public static final String TYPE_CONFIG = "CONFIG";

    public static final String CMD_SCREEN_START = "SCREEN_START";
    public static final String CMD_SCREEN_STOP = "SCREEN_STOP";
    public static final String CMD_PROCESS_RESYNC = "PROCESS_RESYNC";
    public static final String CMD_CONFIG_UPDATE = "CONFIG_UPDATE";
}
