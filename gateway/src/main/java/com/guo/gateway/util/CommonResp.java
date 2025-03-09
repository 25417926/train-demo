package com.guo.gateway.util;


public class CommonResp<T> {

    /**
     * 业务上的成功或失败
     */
    private boolean success = true;
    /**
     * 响应码
     */
    private Integer code;
    /**
     * 返回信息
     */
    private String message;

    /**
     * 返回泛型数据，自定义类型
     */
    private T content;

    public CommonResp() {
    }

    public CommonResp(boolean success, String message, T content, Integer code) {
        this.success = success;
        this.message = message;
        this.content = content;
        this.code = code;
    }

    public CommonResp(T content) {
        this.content = content;
    }

    public CommonResp(boolean success, String message, Integer code) {
        this.success = success;
        this.message = message;
        this.code = code;
    }

    public CommonResp(Integer code, T content, String message) {
        this.code = code;
        this.content = content;
        this.message = message;
    }

    public boolean getSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("CommonResp{");
        sb.append("success=").append(success);
        sb.append(", code=").append(code);
        sb.append(", message='").append(message).append('\'');
        sb.append(", content=").append(content);
        sb.append('}');
        return sb.toString();
    }
}
