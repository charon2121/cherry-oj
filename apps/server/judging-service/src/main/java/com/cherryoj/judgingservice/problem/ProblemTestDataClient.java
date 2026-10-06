package com.cherryoj.judgingservice.problem;

/** 判题时向 problem-service 取题目此刻的测试数据地址；地址不在提交时冻结。 */
public interface ProblemTestDataClient {
    /** 取不到（题目没有数据、服务不可用、响应不合法）一律抛 {@link ProblemTestDataException}。 */
    ProblemTestData current(String problemId, String traceParent);
}
