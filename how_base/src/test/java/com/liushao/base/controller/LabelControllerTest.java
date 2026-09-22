package com.liushao.base.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.liushao.auth.CurrentUserContext;
import com.liushao.auth.JwtTokenService;
import com.liushao.base.dao.LabelDao;
import com.liushao.base.pojo.Label;
import com.liushao.base.service.LabelService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LabelController.class, properties = {
        "how.auth.jwt.secret=test-only-signing-key-not-for-production", "logging.level.root=WARN"
})
@Import(LabelService.class)
class LabelControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService tokens;
    @MockBean private LabelDao labelDao;

    @ParameterizedTest
    @CsvSource({"POST,/label", "PUT,/label/one", "DELETE,/label/one"})
    void rejectsUnauthenticatedWrites(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(20003));
        verifyNoInteractions(labelDao);
    }

    @ParameterizedTest
    @CsvSource({"POST,/label,{}", "POST,/label,'{\"labelname\":\" \"}'",
            "POST,/label,'{\"labelname\":\"Java\",\"count\":-1}'",
            "PUT,/label/one,'{\"labelname\":\"\"}'", "PUT,/label/one,'{\"fans\":-1}'"})
    void rejectsInvalidBodies(String method, String path, String body) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("请求格式或参数不正确"));
        verifyNoInteractions(labelDao);
        assertTrue(CurrentUserContext.get().isEmpty());
    }

    @Test
    void createsLabelAndSupportsPartialUpdate() throws Exception {
        Label existing = new Label();
        existing.setId("one");
        existing.setLabelname("Java");
        when(labelDao.findById("one")).thenReturn(Optional.of(existing));
        mvc.perform(post("/label").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"forged\",\"labelname\":\"Spring\",\"count\":0,\"fans\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true));
        verify(labelDao).save(argThat(label -> "Spring".equals(label.getLabelname()) && !"forged".equals(label.getId())));
        mvc.perform(put("/label/one").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommend\":\"1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true));
        verify(labelDao).save(argThat(label -> "Java".equals(label.getLabelname()) && "1".equals(label.getRecommend())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void returnsNotFoundInsteadOfFalseSuccess(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/label/missing").header("Authorization", authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("资源不存在"));
        verify(labelDao, never()).save(any());
        verify(labelDao, never()).delete(any(Label.class));
    }

    @Test
    void allowsAuthenticatedDeleteAndPublicRead() throws Exception {
        Label label = new Label();
        label.setId("one");
        when(labelDao.findById("one")).thenReturn(Optional.of(label));
        when(labelDao.findAll()).thenReturn(List.of(label));
        mvc.perform(get("/label")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value("one"));
        mvc.perform(delete("/label/one").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true));
        verify(labelDao).delete(label);
    }

    @Test
    void hidesStorageFailureAndWrapsMalformedJson() throws Exception {
        when(labelDao.findAll()).thenThrow(new IllegalStateException("internal database detail"));
        mvc.perform(get("/label")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
        mvc.perform(post("/label").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(20001));
    }

    private String authorization() {
        return "Bearer " + tokens.issueToken("author", "test-user");
    }
}