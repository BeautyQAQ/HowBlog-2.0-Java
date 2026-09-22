package com.liushao.user.pojo;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

public class UtcDateTimeType implements UserType {
    @Override
    public int[] sqlTypes() { return new int[] {Types.TIMESTAMP}; }

    @Override
    public Class<LocalDateTime> returnedClass() { return LocalDateTime.class; }

    @Override
    public boolean equals(Object first, Object second) { return Objects.equals(first, second); }

    @Override
    public int hashCode(Object value) { return Objects.hashCode(value); }

    @Override
    public Object nullSafeGet(ResultSet rows, String[] names,
            SharedSessionContractImplementor session, Object owner) throws SQLException {
        return rows.getObject(names[0], LocalDateTime.class);
    }

    @Override
    public void nullSafeSet(PreparedStatement statement, Object value, int index,
            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(index, (LocalDateTime) value, Types.TIMESTAMP);
        }
    }

    @Override
    public Object deepCopy(Object value) { return value; }

    @Override
    public boolean isMutable() { return false; }

    @Override
    public Serializable disassemble(Object value) { return (Serializable) value; }

    @Override
    public Object assemble(Serializable cached, Object owner) { return cached; }

    @Override
    public Object replace(Object original, Object target, Object owner) { return original; }
}