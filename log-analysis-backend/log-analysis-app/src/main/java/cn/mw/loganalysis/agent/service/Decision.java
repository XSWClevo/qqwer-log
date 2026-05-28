package cn.mw.loganalysis.agent.service;

/**
 * Agent 决策节点抽象。
 *
 * 参考导航 Agent 的 Decision：每个决策节点只关心一段上下文处理，
 * 上层工作流负责按顺序编排节点。
 */
abstract class Decision {

    private final String name;
    private final String description;

    /**
     * 初始化决策节点名称和说明。
     */
    protected Decision(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 返回决策节点编码。
     */
    String getName() {
        return name;
    }

    /**
     * 返回决策节点说明。
     */
    String getDescription() {
        return description;
    }

    /**
     * 执行决策节点，并把结果写回运行时上下文。
     */
    abstract Object execute(AgentRuntimeContext context);
}
