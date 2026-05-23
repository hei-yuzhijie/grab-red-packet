package yuzhijie.redpacket.common;


public class ApiResponse<T> {

    private Integer code;
    private String message;

    private T data;

    public ApiResponse() {
    }

    public ApiResponse(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
    /**
     * 成功响应
     *
     * @param data 数据
     * @param <T>  类型
     * @return 响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "操作成功", data);
    }

    /**
     * 成功响应（自定义消息）
     *
     * @param message 消息
     * @param data    数据
     * @param <T>     类型
     * @return 响应对象
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    /**
     * 失败响应
     *
     * @param message 错误消息
     * @param <T>     类型
     * @return 响应对象
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }

    /**
     * 失败响应（自定义状态码）
     *
     * @param code    状态码
     * @param message 错误消息
     * @param <T>     类型
     * @return 响应对象
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}