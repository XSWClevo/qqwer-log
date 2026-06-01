package cn.mw.loganalysis.common.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(TraceIdFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void shouldLogOrdinaryApiRequests() throws Exception {
        appender.start();
        logger.addAppender(appender);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/vector-overview");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(2, appender.list.size());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("HTTP接口调用开始"));
        assertTrue(appender.list.get(1).getFormattedMessage().contains("HTTP接口调用结束"));
    }

    @Test
    void shouldSuppressAgentPollingRequestLogs() throws Exception {
        appender.start();
        logger.addAppender(appender);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/vector/agents/config");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(0, appender.list.size());
    }
}
