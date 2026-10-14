package com.liushao.article.service;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.liushao.article.dao.ArticleDao;
import com.liushao.article.dao.UserRoleDao;
import com.liushao.article.pojo.Article;
import com.liushao.util.IdWorker;
import com.liushao.web.ForbiddenException;
import com.liushao.web.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock private ArticleDao articleDao;
    @Mock private IdWorker idWorker;
    @Mock private UserRoleDao userRoleDao;

    private ArticleService service() {
        return new ArticleService(articleDao, idWorker, userRoleDao);
    }

    private Article draft(String userId) {
        Article article = new Article();
        article.setUserid(userId);
        article.setState(Article.STATE_DRAFT);
        return article;
    }

    @Test
    void addCreatesDraftWithServerOwnedFields() {
        when(idWorker.nextId()).thenReturn(123L);
        Article article = new Article();
        article.setTitle("title");
        article.setContent("body");
        article.setState(Article.STATE_PUBLISHED);
        article.setCreatetime(new Date(0));
        article.setVisits(99);
        article.setThumbup(99);
        article.setComment(99);
        article.setIstop("1");

        service().add(article, "author");

        assertEquals("123", article.getId());
        assertEquals("author", article.getUserid());
        assertEquals(Article.STATE_DRAFT, article.getState());
        assertNull(article.getCreatetime());
        assertEquals("0", article.getIstop());
        assertEquals(0, article.getVisits());
        assertEquals(0, article.getThumbup());
        assertEquals(0, article.getComment());
        assertNotNull(article.getUpdatetime());
        verify(articleDao).save(article);
    }

    @Test
    void publishSetsPublishedStateAndFirstPublishTime() {
        Article article = draft("author");
        when(articleDao.findById("one")).thenReturn(Optional.of(article));

        assertTrue(service().publish("one", "author"));

        assertEquals(Article.STATE_PUBLISHED, article.getState());
        assertNotNull(article.getCreatetime());
        verify(articleDao).save(article);
    }

    @Test
    void publishKeepsExistingPublishTime() {
        Article article = draft("author");
        Date firstPublish = new Date(1_000_000L);
        article.setCreatetime(firstPublish);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));

        assertTrue(service().publish("one", "author"));

        assertEquals(firstPublish, article.getCreatetime());
    }

    @Test
    void publishRejectsNonAuthorAndMissingArticle() {
        Article article = draft("other");
        when(articleDao.findById("one")).thenReturn(Optional.of(article));
        when(articleDao.findById("missing")).thenReturn(Optional.empty());

        assertFalse(service().publish("one", "author"));
        assertFalse(service().publish("missing", "author"));

        verify(articleDao, never()).save(any());
    }

    @Test
    void unpublishReturnsToDraftKeepingPublishTime() {
        Article article = new Article();
        article.setUserid("author");
        article.setState(Article.STATE_PUBLISHED);
        Date publishTime = new Date(1_000_000L);
        article.setCreatetime(publishTime);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));

        assertTrue(service().unpublish("one", "author"));

        assertEquals(Article.STATE_DRAFT, article.getState());
        assertEquals(publishTime, article.getCreatetime());
        verify(articleDao).save(article);
    }

    @Test
    void submitMovesOwnDraftToPendingReview() {
        Article article = draft("author");
        when(articleDao.findById("one")).thenReturn(Optional.of(article));

        assertEquals(ArticleService.TransitionResult.SUCCESS, service().submit("one", "author"));

        assertEquals(Article.STATE_PENDING, article.getState());
        verify(articleDao).save(article);
    }

    @Test
    void submitRejectsNonAuthorWrongStateAndMissingArticle() {
        when(articleDao.findById("one")).thenReturn(Optional.of(draft("other")));
        Article published = draft("author");
        published.setState(Article.STATE_PUBLISHED);
        when(articleDao.findById("two")).thenReturn(Optional.of(published));
        when(articleDao.findById("missing")).thenReturn(Optional.empty());

        assertEquals(ArticleService.TransitionResult.NOT_ALLOWED, service().submit("one", "author"));
        assertEquals(ArticleService.TransitionResult.INVALID_STATE, service().submit("two", "author"));
        assertEquals(ArticleService.TransitionResult.NOT_ALLOWED, service().submit("missing", "author"));

        verify(articleDao, never()).save(any());
    }

    @Test
    void approveRequiresAdministrator() {
        when(userRoleDao.countAdministrator("author")).thenReturn(0L);

        assertThrows(ForbiddenException.class, () -> service().approve("one", "author"));

        verify(articleDao, never()).findById(any());
    }

    @Test
    void approvePublishesPendingArticleAndStampsPublishTime() {
        Article article = draft("author");
        article.setState(Article.STATE_PENDING);
        when(userRoleDao.countAdministrator("admin")).thenReturn(1L);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));

        assertEquals(ArticleService.TransitionResult.SUCCESS, service().approve("one", "admin"));

        assertEquals(Article.STATE_PUBLISHED, article.getState());
        assertNotNull(article.getCreatetime());
        verify(articleDao).save(article);
    }

    @Test
    void rejectReturnsPendingArticleToDraft() {
        Article article = draft("author");
        article.setState(Article.STATE_PENDING);
        when(userRoleDao.countAdministrator("admin")).thenReturn(1L);
        when(articleDao.findById("one")).thenReturn(Optional.of(article));

        assertEquals(ArticleService.TransitionResult.SUCCESS, service().reject("one", "admin"));

        assertEquals(Article.STATE_DRAFT, article.getState());
        verify(articleDao).save(article);
    }

    @Test
    void reviewRejectsWrongStateAndMissingArticle() {
        when(userRoleDao.countAdministrator("admin")).thenReturn(1L);
        when(articleDao.findById("one")).thenReturn(Optional.of(draft("author")));
        when(articleDao.findById("missing")).thenReturn(Optional.empty());

        assertEquals(ArticleService.TransitionResult.INVALID_STATE, service().approve("one", "admin"));
        assertThrows(ResourceNotFoundException.class, () -> service().reject("missing", "admin"));

        verify(articleDao, never()).save(any());
    }

    @Test
    void findByIdHidesDraftsFromNonAuthorsButShowsThemToAdministrators() {
        Article draft = draft("author");
        when(articleDao.findById("one")).thenReturn(Optional.of(draft));
        when(userRoleDao.countAdministrator("admin")).thenReturn(1L);

        assertSame(draft, service().findById("one", "author"));
        assertSame(draft, service().findById("one", "admin"));
        assertThrows(ResourceNotFoundException.class, () -> service().findById("one", "other"));
        assertThrows(ResourceNotFoundException.class, () -> service().findById("one", null));
    }

    @Test
    void searchRestrictsAnonymousViewerToPublishedArticles() {
        SearchCapture capture = runSearch(null);

        assertSame(capture.published(), capture.captured());
    }

    @Test
    void searchAllowsAuthenticatedViewerToSeeOwnArticles() {
        SearchCapture capture = runSearch("viewer");

        assertSame(capture.ownerClause(), capture.captured());
    }

    @Test
    void searchLetsAdministratorsSeeEveryState() {
        when(userRoleDao.countAdministrator("admin")).thenReturn(1L);
        Root root = mock(Root.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path statePath = mock(Path.class);
        when(articleDao.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());

        service().search(Map.of(), 1, 10, "admin");

        ArgumentCaptor<Specification<Article>> specification = ArgumentCaptor.forClass(Specification.class);
        verify(articleDao).findAll(specification.capture(), any(PageRequest.class));
        specification.getValue().toPredicate(root, mock(CriteriaQuery.class), builder);
        verify(builder, never()).or(any(Predicate.class), any(Predicate.class));
        verify(builder, never()).equal(statePath, Article.STATE_PUBLISHED);
    }

    private record SearchCapture(Predicate captured, Predicate published, Predicate ownerClause) {
    }

    /**
     * 捕获 search 生成的 Specification 并用模拟的 Criteria API 求值，返回合并后的可见性条件。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private SearchCapture runSearch(String viewerId) {
        Root root = mock(Root.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path statePath = mock(Path.class);
        Path userPath = mock(Path.class);
        Predicate published = mock(Predicate.class);
        Predicate ownerClause = null;
        when(root.get("state")).thenReturn(statePath);
        when(builder.equal(statePath, Article.STATE_PUBLISHED)).thenReturn(published);
        if (viewerId != null) {
            when(root.get("userid")).thenReturn(userPath);
            Predicate owner = mock(Predicate.class);
            when(builder.equal(userPath, viewerId)).thenReturn(owner);
            ownerClause = mock(Predicate.class);
            when(builder.or(published, owner)).thenReturn(ownerClause);
        }
        when(articleDao.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());

        service().search(Map.of(), 1, 10, viewerId);

        ArgumentCaptor<Specification<Article>> specification = ArgumentCaptor.forClass(Specification.class);
        verify(articleDao).findAll(specification.capture(), any(PageRequest.class));
        specification.getValue().toPredicate(root, query, builder);

        ArgumentCaptor<Predicate> combined = ArgumentCaptor.forClass(Predicate.class);
        verify(builder).and(combined.capture());
        return new SearchCapture(combined.getValue(), published, ownerClause);
    }
}
