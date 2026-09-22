package com.liushao.base.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "how.auth.jwt.secret=test-only-signing-key-not-for-production",
        "logging.level.root=WARN"
})
class UserRoleDaoTest {
    @MockBean private ObjectMapper objectMapper;
    @Autowired private UserRoleDao roles;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void requiresExistingUserAndExactAdminRoleAndObservesRevocation() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS tb_user (id VARCHAR(64) PRIMARY KEY)");
        assertEquals(0, roles.countAdministrator("role-test"));
        jdbc.update("INSERT INTO tb_user_role (user_id, role) VALUES (?, ?)", "role-test", "ADMIN");
        assertEquals(0, roles.countAdministrator("role-test"));
        jdbc.update("INSERT INTO tb_user (id) VALUES (?)", "role-test");
        assertEquals(1, roles.countAdministrator("role-test"));
        assertEquals(0, roles.countAdministrator("another-user"));
        jdbc.update("UPDATE tb_user_role SET role = ? WHERE user_id = ?", "USER", "role-test");
        assertEquals(0, roles.countAdministrator("role-test"));
        jdbc.update("UPDATE tb_user_role SET role = ? WHERE user_id = ?", "admin", "role-test");
        assertEquals(0, roles.countAdministrator("role-test"));
    }
}