# Project Conventions

## Java

- Java 开发中，涉及判空时优先使用 Apache Commons 提供的工具类。
- 常见场景优先使用 `org.apache.commons.lang3.StringUtils`、`org.apache.commons.lang3.ObjectUtils`、`org.apache.commons.collections4.CollectionUtils`、`org.apache.commons.collections4.MapUtils`。
- 如果当前场景没有合适的 Apache 工具类，再使用 Java 自带的判空方式，例如 `Objects.isNull`、`Objects.nonNull` 或显式 `null` 判断。
