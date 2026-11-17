package com.airuike.netdisk.backend.common;

// 这里的 record 是 Java 17 支持的数据类写法；T 表示 data 可以装不同类型的数据。
/*
我们当前约定：
    字段	含义
    code	成功为 0；当前错误响应使用对应 HTTP 状态码
    message	提示信息
    data	成功返回的数据；错误时为 null
例如以后查询文件列表，data 就可以是一组文件记录。
*/
public record ApiResponse<T>(
        int code,
        String message,
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
