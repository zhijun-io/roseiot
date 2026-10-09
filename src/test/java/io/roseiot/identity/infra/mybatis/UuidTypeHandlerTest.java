package io.roseiot.identity.infra.mybatis;

import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UuidTypeHandlerTest {

    private static final UUID SAMPLE = UUID.fromString("019a0000-0000-7000-8000-0000000000a1");

    private final UuidTypeHandler cut = new UuidTypeHandler();

    @Test
    void shouldWriteUuidAsObjectParameter() throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);

        cut.setNonNullParameter(statement, 1, SAMPLE, null);

        verify(statement).setObject(1, SAMPLE);
    }

    @Test
    void shouldReadUuidObjectByName() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("id")).thenReturn(SAMPLE);

        assertThat(cut.getNullableResult(resultSet, "id")).isEqualTo(SAMPLE);
    }

    @Test
    void shouldReadUuidStringByIndex() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(1)).thenReturn(SAMPLE.toString());

        assertThat(cut.getNullableResult(resultSet, 1)).isEqualTo(SAMPLE);
    }

    @Test
    void shouldReadUuidFromCallableStatement() throws SQLException {
        CallableStatement statement = mock(CallableStatement.class);
        when(statement.getObject(2)).thenReturn(SAMPLE);

        assertThat(cut.getNullableResult(statement, 2)).isEqualTo(SAMPLE);
    }

    @Test
    void shouldReadNullAsNullAcrossAccessors() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("id")).thenReturn(null);
        when(resultSet.getObject(1)).thenReturn(null);
        CallableStatement statement = mock(CallableStatement.class);
        when(statement.getObject(2)).thenReturn(null);

        assertThat(cut.getNullableResult(resultSet, "id")).isNull();
        assertThat(cut.getNullableResult(resultSet, 1)).isNull();
        assertThat(cut.getNullableResult(statement, 2)).isNull();
    }
}
