package com.cherryoj.judgingservice.problem;

/** problem-service 给出的题目当前测试数据，字段以 contracts/problem-test-data.schema.json 为准。 */
public record ProblemTestData(String location, String digest, int caseCount, long totalBytes) {}
