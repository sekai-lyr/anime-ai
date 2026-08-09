package com.example.demo.weather.exception;

/**
天气API异常类。
 * 天气服务调用失败时抛出的业务异常。
 */
public class WeatherApiException extends RuntimeException {

    private final int statusCode;

    public WeatherApiException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public WeatherApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public WeatherApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}