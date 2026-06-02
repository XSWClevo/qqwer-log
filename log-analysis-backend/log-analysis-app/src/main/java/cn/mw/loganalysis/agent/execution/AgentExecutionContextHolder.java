package cn.mw.loganalysis.agent.execution;

/**
 * 线程级的智能助手执行上下文。
 *
 * LangChain4j 调用 @Tool 方法时仍然运行在当前请求线程里，
 * 所以这里用 ThreadLocal 传递“当前数据源”这类请求级信息。
 */
public final class AgentExecutionContextHolder {

    private static final ThreadLocal<AgentExecutionContext> CONTEXT = new ThreadLocal<>();

    private AgentExecutionContextHolder() {
    }

    public static void set(AgentExecutionContext context) {
        CONTEXT.set(context);
    }

    public static AgentExecutionContext get() {
        return CONTEXT.get();
    }

    public static AgentExecutionContext require() {
        AgentExecutionContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("智能助手执行上下文不存在");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
