package com.liushao.base.pojo;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

@Entity
@Table(name = "tb_user_role")
@IdClass(UserRole.Key.class)
public class UserRole {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    private String role;

    public static class Key implements Serializable {
        private String userId;
        private String role;

        public Key() {
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(role, key.role);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, role);
        }
    }
}