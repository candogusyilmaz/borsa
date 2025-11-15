package dev.canverse.stocks.repository.mybatis;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class JsonbTypeHandler<T> extends BaseTypeHandler<T> {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final JavaType javaType;

    public JsonbTypeHandler(Class<T> type) {
        this.javaType = mapper.getTypeFactory().constructType(type);
    }

    // For collections like List<String>, List<CustomObject>
    public JsonbTypeHandler(Class<?> collectionType, Class<?> elementType) {
        this.javaType = mapper.getTypeFactory()
                .constructCollectionType(List.class, elementType);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, mapper.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("JSONB serialization failed", e);
        }
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    private T parseJson(String value) throws SQLException {
        if (value == null) return null;
        try {
            return mapper.readValue(value, javaType);
        } catch (Exception e) {
            throw new SQLException("JSONB deserialization failed", e);
        }
    }
}