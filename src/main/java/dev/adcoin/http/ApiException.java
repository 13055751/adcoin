package dev.adcoin.http;

/** HTTP API 业务异常：携带响应状态码与错误码（JSON 的 error 字段）。 */
public final class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String code) {
        super(code);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public String code() {
        return getMessage();
    }
}