package io.roseiot.shared.mybatis;

import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import tools.jackson.databind.JsonNode;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

@MappedTypes(JsonNode.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class JsonbJsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

    private static final Jackson3TypeHandler JSON = new Jackson3TypeHandler(JsonNode.class);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode value, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, JSON.toJson(value), Types.OTHER);
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static JsonNode parse(String value) {
        return value == null ? null : (JsonNode) JSON.parse(value);
    }
}
