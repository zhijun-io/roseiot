package io.roseiot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import io.roseiot.platform.auth.recovery.RecoveryNotificationTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 集成测试公共基类：Testcontainers + 随机端口。
 * <ul>
 *   <li>{@code *MockMvcTests}：只用 {@link #mvc}，不用 {@link #restTestClient}。</li>
 *   <li>其他 {@code *Tests}：不用 {@link #mvc}，可按需使用 {@link #restTestClient}。</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, RecoveryNotificationTestConfig.class})
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
public abstract class AbstractIt {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected RestTestClient restTestClient;
}
