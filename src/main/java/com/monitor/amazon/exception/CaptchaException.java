package com.monitor.amazon.exception;

public class CaptchaException extends RuntimeException {
    public CaptchaException(String url) {
        super("CAPTCHA detected for URL: " + url);
    }
}
