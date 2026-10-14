package com.liushao.article.controller;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.liushao.article.dao.ArticleDao;
import com.liushao.article.dao.CommentDao;
import com.liushao.article.dao.CommentThumbupDao;
import com.liushao.article.pojo.Article;
import com.liushao.article.pojo.Comment;
import com.liushao.article.service.ArticleService;
import com.liushao.article.service.CommentService;
import com.liushao.auth.CurrentUserContext;
import com.liushao.auth.JwtTokenService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ArticleController.class, CommentController.class}, properties = {
        "how.auth.jwt.secret=test-only-signing-key-not-for-production", "logging.level.root=WARN"
})
@Import({ArticleService.class, CommentService.class})
class ArticleControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService tokens;
    @MockBean private ArticleDao articleDao;
    @MockBean private CommentDao commentDao;
        @MockBean private CommentThumbupDao commentThumbupDao;
    @MockBean private RedisTemplate redisTemplate;
        @MockBean private com.liushao.auth.SessionVerifier sessions;
    @MockBean private com.liushao.article.dao.UserRoleDao userRoleDao;

    @ParameterizedTest
    @CsvSource({"POST,/article", "PUT,/article/one", "DELETE,/article/one", "PUT,/article/one/publish",
            "PUT,/article/one/unpublish", "PUT,/article/one/submit", "PUT,/article/one/approve", "PUT,/article/one/reject",
            "POST,/comment", "PUT,/comment/one", "DELETE,/comment/one", "PUT,/comment/thumbup/one"})
    void rejectsUnauthenticatedWrites(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(20003));
        verifyNoInteractions(articleDao, commentDao, redisTemplate);
        assertTrue(CurrentUserContext.get().isEmpty());
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        mvc.perform(post("/article").header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(articleDao);
    }

    @ParameterizedTest
    @CsvSource({"POST,/article,{}", "POST,/article,'{\"title\":\" \" ,\"content\":\"body\"}'",
            "PUT,/article/one,'{\"content\":\" \"}'", "POST,/comment,{}",
            "POST,/comment,'{\"articleid\":\"one\",\"content\":\" \"}'",
            "PUT,/comment/one,'{\"content\":\"\"}'", "POST,/article,null"})
    void rejectsInvalidBodiesBeforePersistence(String method, String path, String body) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("请求格式或参数不正确"));
        verifyNoInteractions(articleDao, commentDao);
        assertTrue(CurrentUserContext.get().isEmpty());
    }

    @Test
    void usesTokenIdentityForArticleAndCommentCreation() throws Exception {
        mvc.perform(post("/article").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"title\",\"content\":\"body\",\"userid\":\"forged\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true));
        mvc.perform(post("/comment").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleid\":\"one\",\"content\":\"body\",\"userid\":\"forged\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true));
        verify(articleDao).save(argThat(article -> "author".equals(article.getUserid())));
        verify(commentDao).save(argThat(comment -> "author".equals(comment.getUserid())));
        assertTrue(CurrentUserContext.get().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"other", "missing"})
    void rejectsNonAuthorAndMissingResourceWrites(String owner) throws Exception {
        Article article = new Article();
        article.setUserid(owner);
        Comment comment = new Comment();
        comment.setUserid(owner);
        when(articleDao.findById("one")).thenReturn("missing".equals(owner) ? Optional.empty() : Optional.of(article));
        when(commentDao.findById("one")).thenReturn("missing".equals(owner) ? Optional.empty() : Optional.of(comment));
        for (String path : List.of("/article/one", "/comment/one")) {
            mvc.perform(put(path).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"edit\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20003));
            mvc.perform(delete(path).header("Authorization", authorization()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20003));
        }
        verify(articleDao, never()).save(any());
        verify(articleDao, never()).delete(any(Article.class));
        verify(commentDao, never()).save(any());
        verify(commentDao, never()).delete(any(Comment.class));
    }

    @Test
    void allowsAuthorPartialUpdates() throws Exception {
        Article article = new Article();
        article.setUserid("author");
        article.setTitle("original");
        Comment comment = new Comment();
        comment.setUserid("author");
        comment.setArticleid("original");
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        when(commentDao.findById("one")).thenReturn(Optional.of(comment));
        for (String path : List.of("/article/one", "/comment/one")) {
            mvc.perform(put(path).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"edit\",\"userid\":\"forged\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true));
        }
        verify(articleDao).save(argThat(saved -> "original".equals(saved.getTitle()) && "author".equals(saved.getUserid())));
        verify(commentDao).save(argThat(saved -> "original".equals(saved.getArticleid()) && "edit".equals(saved.getContent())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0/10", "1/0", "1/101", "-2147483648/10", "text/10", "2147483648/10"})
    void rejectsInvalidPagination(String pagination) throws Exception {
        mvc.perform(post("/article/search/" + pagination).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(20001));
        verifyNoInteractions(articleDao);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void acceptsPaginationBoundaries(int size) throws Exception {
        when(articleDao.findAll(any(Specification.class), eq(PageRequest.of(0, size)))).thenReturn(Page.empty());
        mvc.perform(post("/article/search/1/" + size).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(articleDao).findAll(any(Specification.class), eq(PageRequest.of(0, size)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/article/missing", "/comment/missing"})
    void returnsNotFoundForMissingResource(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

        @Test
        void returnsNotFoundAndReleasesClaimForMissingThumbupTarget() throws Exception {
                when(commentDao.findByIdForUpdate("missing")).thenReturn(Optional.empty());
                mvc.perform(put("/comment/thumbup/missing").header("Authorization", authorization()))
                                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(20001))
                                .andExpect(jsonPath("$.message").value("资源不存在"));
                assertTrue(CurrentUserContext.get().isEmpty());
        }

    @Test
    void preservesDistinctCommentLookupRoutes() throws Exception {
        when(commentDao.findByArticleidOrderByPublishdateDesc("one")).thenReturn(List.of());
        mvc.perform(get("/comment/article/one")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        verify(commentDao).findByArticleidOrderByPublishdateDesc("one");
    }

    @Test
    void allowsAnyOriginByDefault() throws Exception {
        mvc.perform(get("/article").header(HttpHeaders.ORIGIN, "https://example.test"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
    }

    @Test
    void hidesUnexpectedFailureDetailsAndClearsIdentity() throws Exception {
        when(articleDao.findByState(Article.STATE_PUBLISHED)).thenThrow(new IllegalStateException("internal database detail"));
        mvc.perform(get("/article").header("Authorization", authorization()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"))
                .andExpect(jsonPath("$.data").isEmpty());
        assertTrue(CurrentUserContext.get().isEmpty());
    }

    @Test
    void wrapsMalformedJsonAndPreservesMethodAndMediaStatuses() throws Exception {
        mvc.perform(post("/article").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(20001));
        mvc.perform(patch("/article")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.flag").value(false));
        mvc.perform(post("/article").header("Authorization", authorization()).contentType(MediaType.TEXT_PLAIN).content("body"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.flag").value(false));
    }

    @Test
    void createsArticlesAsDraftsIgnoringClientLifecycleFields() throws Exception {
        mvc.perform(post("/article").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"title\",\"content\":\"body\",\"state\":\"1\",\"visits\":99,"
                                + "\"thumbup\":99,\"comment\":99,\"istop\":\"1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true));
        verify(articleDao).save(argThat(article -> Article.STATE_DRAFT.equals(article.getState())
                && article.getCreatetime() == null
                && Integer.valueOf(0).equals(article.getVisits())
                && Integer.valueOf(0).equals(article.getThumbup())
                && Integer.valueOf(0).equals(article.getComment())
                && "0".equals(article.getIstop())
                && article.getUpdatetime() != null));
    }

    @Test
    void publishesAndUnpublishesOwnArticle() throws Exception {
        Article article = new Article();
        article.setUserid("author");
        article.setState(Article.STATE_DRAFT);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        mvc.perform(put("/article/one/publish").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20000));
        assertEquals(Article.STATE_PUBLISHED, article.getState());
        assertNotNull(article.getCreatetime());
        mvc.perform(put("/article/one/unpublish").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20000));
        assertEquals(Article.STATE_DRAFT, article.getState());
        assertNotNull(article.getCreatetime());
        verify(articleDao, times(2)).save(article);
    }

    @ParameterizedTest
    @ValueSource(strings = {"other", "missing"})
    void rejectsNonAuthorPublishAndUnpublish(String owner) throws Exception {
        Article article = new Article();
        article.setUserid(owner);
        article.setState(Article.STATE_DRAFT);
        when(articleDao.findById("one")).thenReturn("missing".equals(owner) ? Optional.empty() : Optional.of(article));
        for (String action : List.of("publish", "unpublish")) {
            mvc.perform(put("/article/one/" + action).header("Authorization", authorization()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20003));
        }
        verify(articleDao, never()).save(any());
    }

    @Test
    void hidesDraftFromAnonymousButShowsItToItsAuthor() throws Exception {
        Article draft = new Article();
        draft.setUserid("author");
        draft.setState(Article.STATE_DRAFT);
        when(articleDao.findById("one")).thenReturn(Optional.of(draft));
        mvc.perform(get("/article/one")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("资源不存在"));
        mvc.perform(get("/article/one").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value(Article.STATE_DRAFT));
    }

    @Test
    void listsOnlyPublishedArticles() throws Exception {
        when(articleDao.findByState(Article.STATE_PUBLISHED)).thenReturn(List.of());
        mvc.perform(get("/article")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        verify(articleDao).findByState(Article.STATE_PUBLISHED);
        verify(articleDao, never()).findAll();
    }

    @Test
    void submitMovesOwnDraftToPendingReview() throws Exception {
        Article article = new Article();
        article.setUserid("author");
        article.setState(Article.STATE_DRAFT);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        mvc.perform(put("/article/one/submit").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20000));
        assertEquals(Article.STATE_PENDING, article.getState());
        verify(articleDao).save(article);
    }

    @ParameterizedTest
    @ValueSource(strings = {"other", "missing"})
    void rejectsSubmitFromNonAuthor(String owner) throws Exception {
        Article article = new Article();
        article.setUserid(owner);
        article.setState(Article.STATE_DRAFT);
        when(articleDao.findById("one")).thenReturn("missing".equals(owner) ? Optional.empty() : Optional.of(article));
        mvc.perform(put("/article/one/submit").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20003));
        verify(articleDao, never()).save(any());
    }

    @Test
    void approveAndRejectRequireAdministrator() throws Exception {
        Article article = new Article();
        article.setUserid("author");
        article.setState(Article.STATE_PENDING);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        for (String action : List.of("approve", "reject")) {
            mvc.perform(put("/article/one/" + action).header("Authorization", authorization()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20003));
        }
        verify(articleDao, never()).save(any());
    }

    @Test
    void administratorCanApprovePendingArticle() throws Exception {
        Article article = new Article();
        article.setUserid("author");
        article.setState(Article.STATE_PENDING);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        mvc.perform(put("/article/one/approve").header("Authorization", adminAuthorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20000));
        assertEquals(Article.STATE_PUBLISHED, article.getState());
        assertNotNull(article.getCreatetime());
    }

    @Test
    void administratorCanRejectPendingArticle() throws Exception {
        Article article = new Article();
        article.setUserid("author");
        article.setState(Article.STATE_PENDING);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        mvc.perform(put("/article/one/reject").header("Authorization", adminAuthorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20000));
        assertEquals(Article.STATE_DRAFT, article.getState());
    }

    @Test
    void reviewRejectsWrongState() throws Exception {
        Article article = new Article();
        article.setUserid("author");
        article.setState(Article.STATE_DRAFT);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        mvc.perform(put("/article/one/approve").header("Authorization", adminAuthorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("文章状态不允许该操作"));
        verify(articleDao, never()).save(any());
    }

    @Test
    void showsDraftToAdministrator() throws Exception {
        Article draft = new Article();
        draft.setUserid("author");
        draft.setState(Article.STATE_DRAFT);
        when(articleDao.findById("one")).thenReturn(Optional.of(draft));
        mvc.perform(get("/article/one").header("Authorization", adminAuthorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value(Article.STATE_DRAFT));
    }

    @Test
    void countsViewsForPublishedArticlesFromNonAuthors() throws Exception {
        Article article = new Article();
        article.setUserid("author");
        article.setState(Article.STATE_PUBLISHED);
        article.setVisits(5);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        when(articleDao.incrementVisits("one")).thenReturn(1);
        mvc.perform(get("/article/one"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.visits").value(6));
        verify(articleDao).incrementVisits("one");
    }

    @Test
    void doesNotCountAuthorViews() throws Exception {
        Article article = new Article();
        article.setUserid("author");
        article.setState(Article.STATE_PUBLISHED);
        article.setVisits(5);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        mvc.perform(get("/article/one").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.visits").value(5));
        verify(articleDao, never()).incrementVisits(any());
    }

    private String authorization() {
                when(sessions.isActive("a".repeat(64), "author")).thenReturn(true);
                return "Bearer " + tokens.issueToken("author", "test-user", "a".repeat(64), java.time.Instant.now().plusSeconds(3600));
    }

    private String adminAuthorization() {
                when(sessions.isActive("b".repeat(64), "admin")).thenReturn(true);
                when(userRoleDao.countAdministrator("admin")).thenReturn(1L);
                return "Bearer " + tokens.issueToken("admin", "admin-user", "b".repeat(64), java.time.Instant.now().plusSeconds(3600));
    }

        @Test
        void revokedSessionCannotWriteArticleOrComment() throws Exception {
                String token = authorization();
                when(sessions.isActive("a".repeat(64), "author")).thenReturn(false);
                for (String path : List.of("/article", "/comment")) {
                        mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                                        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(20003));
                }
                verifyNoInteractions(articleDao, commentDao, redisTemplate);
        }
}