package com.liushao.user.controller;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-profile;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "how.auth.jwt.secret=test-only-signing-key-not-for-production",
        "how.auth.rate-limit.enabled=false", "how.auth.cleanup.enabled=false",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
public class UserProfileTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserDao users;
    @Autowired private JdbcTemplate jdbc;
    private String userId;
    private String token;

    @BeforeEach
    void fixture() throws Exception {
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR 'com.liushao.user.controller.UserProfileTest.utcTimestamp'");
        userId = UUID.randomUUID().toString();
        User user = new User();
        user.setId(userId);
        user.setMobile(userId);
        user.setPassword("test-password");
        user.setNickname("Alice");
        user.setSex("1");
        user.setAvatar("https://example.test/old.png");
        user.setEmail("alice@example.test");
        user.setInterest("coding");
        user.setPersonality("quiet");
        users.saveAndFlush(user);
        String response = mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mobile", userId, "password", "test-password"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = mapper.readTree(response).path("data").path("token").asText();
    }

    public static LocalDateTime utcTimestamp(int precision) {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mvc.perform(get("/user/profile"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(20003));
        mvc.perform(put("/user/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsSanitizedProfile() throws Exception {
        mvc.perform(get("/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("Alice"))
                .andExpect(jsonPath("$.data.email").value("alice@example.test"))
                .andExpect(jsonPath("$.data.interest").value("coding"))
                .andExpect(jsonPath("$.data.fanscount").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void partialUpdateChangesOnlyProvidedFields() throws Exception {
        mvc.perform(put("/user/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  Bob  \",\"email\":\"bob@example.test\",\"birthday\":803347200000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.message").value("修改成功"))
                .andExpect(jsonPath("$.data.nickname").value("Bob"))
                .andExpect(jsonPath("$.data.email").value("bob@example.test"))
                .andExpect(jsonPath("$.data.birthday").value("1995-06-17T00:00:00.000+00:00"))
                .andExpect(jsonPath("$.data.mobile").value(userId))
                .andExpect(jsonPath("$.data.sex").value("1"))
                .andExpect(jsonPath("$.data.avatar").value("https://example.test/old.png"));
        User stored = users.findById(userId).orElseThrow();
        assertEquals("Bob", stored.getNickname());
        assertNotNull(stored.getUpdatedate());
    }

    @Test
    void ignoresNonUpdatableFields() throws Exception {
        String passwordBefore = users.findById(userId).orElseThrow().getPassword();
        mvc.perform(put("/user/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13800000000\",\"password\":\"hacked\",\"fanscount\":9999,\"id\":\"other\"}"))
                .andExpect(status().isOk());
        User stored = users.findById(userId).orElseThrow();
        assertEquals(userId, stored.getMobile());
        assertEquals(passwordBefore, stored.getPassword());
        assertNotEquals("hacked", stored.getPassword());
        assertNull(stored.getFanscount());
    }

    @Test
    void rejectsBlankOversizedOrMalformedFields() throws Exception {
        for (String body : new String[]{
                "{\"nickname\":\"   \"}",
                "{\"nickname\":\"" + "x".repeat(101) + "\"}",
                "{\"email\":\"not-an-email\"}",
                "{\"avatar\":\"\"}",
                "{\"sex\":\"" + "y".repeat(17) + "\"}"
        }) {
            mvc.perform(put("/user/profile").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(20001));
        }
        User stored = users.findById(userId).orElseThrow();
        assertEquals("Alice", stored.getNickname());
    }

    @Test
    void emptyUpdateIsAcceptedWithoutChangingFields() throws Exception {
        mvc.perform(put("/user/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("Alice"))
                .andExpect(jsonPath("$.data.email").value("alice@example.test"));
    }
}
