package com.vcc.apigrocerystore.global;

public class ErrorCode {
    private ErrorCode() {
    }

    public static final int DATE_TIME_MUST_NOT_EMPTY = 601;
    public static final int DATE_TIME_INVALID = 602;
    public static final int USER_NAME_MUST_NOT_EMPTY = 603;
    public static final int FULL_NAME_MUST_NOT_EMPTY = 604;
    public static final int PASS_WORD_MUST_NOT_EMPTY = 605;
    public static final int ITEM_CODE_MUST_NOT_EMPTY = 606;
    public static final int ITEM_PRICE_MUST_LONG = 607;
    public static final int ITEM_BRAND_MUST_NOT_EMPTY = 608;
}