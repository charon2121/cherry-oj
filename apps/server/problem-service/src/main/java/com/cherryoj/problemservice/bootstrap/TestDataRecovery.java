package com.cherryoj.problemservice.bootstrap;

import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.storage.TestDataStore;
import com.cherryoj.problemservice.storage.TestDataStore.StoredDirectory;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 定期清理中断的上传与已经过保留期且无引用的目录。 */
@Component
@EnableScheduling
@ConditionalOnProperty(
        prefix = "cherry.problem.test-data",
        name = "recovery-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TestDataRecovery implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(TestDataRecovery.class);
    private final TestDataStore store;
    private final AdminProblemMapper problems;
    private final TransactionTemplate transactions;

    public TestDataRecovery(TestDataStore store, AdminProblemMapper problems, TransactionTemplate transactions) {
        this.store = store;
        this.problems = problems;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void recover() {
        store.recover();
        var byProblem = store.storedDirectories().stream().collect(Collectors.groupingBy(StoredDirectory::problemId));
        byProblem.forEach((problemId, directories) -> {
            try {
                transactions.executeWithoutResult(status -> {
                    // 和上传使用同一把锁，避免在交付目录到提交事务之间标记或清理目录。
                    var problem = problems.findProblemForUpdate(problemId);
                    String current = problem == null ? null : problem.testDataLocation();
                    for (var directory : directories) {
                        store.reclaim(
                                directory,
                                location ->
                                        location.equals(current) || problems.testDataLocationIsReferenced(location));
                    }
                });
            } catch (RuntimeException error) {
                LOG.warn("testdata.cleanup.reference_check_failed problemId={}", problemId);
            }
        });
    }
}
