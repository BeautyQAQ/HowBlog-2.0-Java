package com.liushao.base.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.liushao.base.pojo.UserRole;

public interface UserRoleDao extends Repository<UserRole, UserRole.Key> {
    @Query(value = "SELECT COUNT(*) FROM tb_user_role role_record "
            + "JOIN tb_user account ON account.id = role_record.user_id "
            + "WHERE role_record.user_id = :userId AND role_record.role = 'ADMIN'", nativeQuery = true)
    long countAdministrator(@Param("userId") String userId);
}