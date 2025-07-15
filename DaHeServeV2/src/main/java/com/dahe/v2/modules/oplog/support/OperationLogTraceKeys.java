package com.dahe.v2.modules.oplog.support;

public final class OperationLogTraceKeys {

    private OperationLogTraceKeys() {
    }

    /** 请求开始时间（毫秒时间戳）。 */
    public static final String ATTR_START_AT = "dahe.v2.oplog.startAt";
    /** 响应业务码（来自统一 Result）。 */
    public static final String ATTR_RESULT_CODE = "dahe.v2.oplog.resultCode";
    /** 响应业务消息（来自统一 Result）。 */
    public static final String ATTR_RESULT_MESSAGE = "dahe.v2.oplog.resultMessage";
}
