package cn.mw.loganalysis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 日志分析系统单体应用启动类
 */
@SpringBootApplication
@MapperScan("cn.mw.loganalysis.**.mapper")
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class LogAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogAnalysisApplication.class, args);
    }
}
