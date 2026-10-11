package io.roseiot.shared.webmvc;

import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorPageTest {

    @Test
    void shouldReturnNextCursorOnlyWhenAnotherPageExists() {
        CursorPage<Row> page = CursorPage.fromRows(List.of(new Row(5), new Row(8), new Row(13)), 2, Row::id);

        assertThat(page.items()).containsExactly(new Row(5), new Row(8));
        assertThat(CursorPage.afterId(page.nextCursor())).isEqualTo(8);
        assertThat(CursorPage.fromRows(List.of(new Row(13)), 2, Row::id).nextCursor()).isNull();
    }

    @Test
    void shouldRejectInvalidCursorAndPageSize() {
        assertThatThrownBy(() -> CursorPage.afterId("invalid"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                        .isEqualTo(ApiErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> CursorPage.checkedLimit(101))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                        .isEqualTo(ApiErrorCode.VALIDATION_ERROR));
    }

    private record Row(long id) {}
}
