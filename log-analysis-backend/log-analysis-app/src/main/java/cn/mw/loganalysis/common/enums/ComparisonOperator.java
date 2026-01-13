package cn.mw.loganalysis.common.enums;

import lombok.Getter;

/**
 * 比较操作符枚举
 */
@Getter
public enum ComparisonOperator {
    GT("gt", ">", "大于"),
    GTE("gte", ">=", "大于等于"),
    LT("lt", "<", "小于"),
    LTE("lte", "<=", "小于等于"),
    EQ("eq", "=", "等于");

    private final String code;
    private final String symbol;
    private final String description;

    ComparisonOperator(String code, String symbol, String description) {
        this.code = code;
        this.symbol = symbol;
        this.description = description;
    }

    public static ComparisonOperator fromCode(String code) {
        for (ComparisonOperator operator : values()) {
            if (operator.code.equals(code)) {
                return operator;
            }
        }
        return null;
    }

    public static String getSymbol(String code) {
        ComparisonOperator operator = fromCode(code);
        return operator != null ? operator.symbol : code;
    }
}
