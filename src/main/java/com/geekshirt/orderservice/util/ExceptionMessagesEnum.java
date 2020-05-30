package com.geekshirt.orderservice.util;

public enum ExceptionMessagesEnum {

    INCORRECT_REQUEST_EMPTY_ITEM_ORDER("Empty Item not allow in Order"),
    ACCOUNT_NOT_FOUND("Account not found");

    ExceptionMessagesEnum(String message) {
        value = message;
    }

    private final String value;


    public String getValue() {
        return value;
    }
}
