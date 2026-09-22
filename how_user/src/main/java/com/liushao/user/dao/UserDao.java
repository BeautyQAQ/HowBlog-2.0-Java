package com.liushao.user.dao;

import com.liushao.user.pojo.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author huangshen
 */
public interface UserDao extends JpaRepository<User, String> {

}
