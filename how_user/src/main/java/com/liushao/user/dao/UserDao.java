package com.liushao.user.dao;

import com.liushao.user.pojo.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * @author huangshen
 */
public interface UserDao extends JpaRepository<User, String> {

	List<User> findAllByMobile(String mobile);
}
