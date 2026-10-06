package com.cherryoj.problemservice.bootstrap;

import com.cherryoj.problemservice.storage.TestDataStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 启动时清理中断的上传留下的临时文件；已经被题目地址指向的数据目录不会被动。 */
@Component
@ConditionalOnProperty(
        prefix = "cherry.problem.test-data", name = "recovery-enabled", havingValue = "true", matchIfMissing = true)
public class TestDataRecovery implements ApplicationRunner {

    private final TestDataStore store;

    public TestDataRecovery(TestDataStore store) {
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        store.recover();
    }
}
