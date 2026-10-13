package io.roseiot.shared.webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    @Test
    void addsGeneratedRequestIdToResponseProblemAndClearsMdc() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "untrusted-client-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiHttpSupport apiHttpSupport = new ApiHttpSupport(
                tools.jackson.databind.json.JsonMapper.builder().build());

        filter.doFilterInternal(
                request,
                response,
                (ignoredRequest, ignoredResponse) ->
                        apiHttpSupport.writeError(response, 400, "bad_request", "Malformed request body"));

        String requestId = response.getHeader(RequestCorrelationFilter.HEADER_NAME);
        assertThat(requestId).matches("[0-9a-f]{32}").isNotEqualTo("untrusted-client-value");
        assertThat(response.getContentAsString()).contains("\"requestId\":\"" + requestId + "\"");
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }
}
