package com.liushao.article.service;

import java.util.Date;
import java.util.List;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.liushao.article.dao.ArticleDao;
import com.liushao.article.dao.ArticleLabelDao;
import com.liushao.article.dao.ArticleThumbupDao;
import com.liushao.article.dao.CommentDao;
import com.liushao.article.dao.CommentThumbupDao;
import com.liushao.article.dao.UserRoleDao;
import com.liushao.article.pojo.Article;
import com.liushao.util.IdWorker;
import com.liushao.web.ForbiddenException;
import com.liushao.web.InvalidRequestException;
import com.liushao.web.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock private ArticleDao articleDao;
    @Mock private IdWorker idWorker;
    @Mock private UserRoleDao userRoleDao;
    @Mock private ArticleThumbupDao articleThumbupDao;
    @Mock private ArticleLabelDao articleLabelDao;
    @Mock private CommentDao commentDao;
    @Mock private CommentThumbupDao commentThumbupDao;

    private ArticleService service() {
        return new ArticleService(articleDao, idWorker, userRoleDao, articleThumbupDao, articleLabelDao,
                commentDao, commentThumbupDao);
    }

    private Article draft(String userId) {
        Article article = new Article();
        article.setUserid(userId);
        article.setState(Article.STATE_DRAFT);
        return article;
    }

    private Article publishedArticle(String userId, int visits) {
        Article article = new Article();
        article.setUserid(userId);
        article.setState(Article.STATE_PUBLISHED);
        article.setVisits(visits);
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
    void addStoresDistinctTrimmedLabels() {
        when(idWorker.nextId()).thenReturn(123L);
        when(articleLabelDao.countExistingLabels(List.of("java", "spring"))).thenReturn(2L);
        when(articleLabelDao.insertExistingLabels("123", List.of("java", "spring"))).thenReturn(2);
        Article article = new Article();
        article.setTitle("title");
        article.setContent("body");
        article.setLabelIds(List.of(" java ", "java", "spring"));

        service().add(article, "author");

        verify(articleLabelDao).deleteByArticleId("123");
        verify(articleLabelDao).insertExistingLabels("123", List.of("java", "spring"));
    }

    @Test
    void addRejectsUnknownLabelsBeforeSavingArticle() {
        when(articleLabelDao.countExistingLabels(List.of("missing"))).thenReturn(0L);
        Article article = new Article();
        article.setTitle("title");
        article.setContent("body");
        article.setLabelIds(List.of("missing"));

        assertThrows(InvalidRequestException.class, () -> service().add(article, "author"));

        verify(articleDao, never()).save(any());
    }

    @Test
    void publishSetsPublishedStateAndFirstPublishTime() {
        Article article = draft("author");
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));

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
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));

        assertTrue(service().publish("one", "author"));

        assertEquals(firstPublish, article.getCreatetime());
    }

    @Test
    void publishRejectsNonAuthorAndMissingArticle() {
        Article article = draft("other");
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));
        when(articleDao.findByIdForUpdate("missing")).thenReturn(Optional.empty());

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
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));

        assertTrue(service().unpublish("one", "author"));

        assertEquals(Article.STATE_DRAFT, article.getState());
        assertEquals(publishTime, article.getCreatetime());
        verify(articleDao).save(article);
    }

    @Test
    void submitMovesOwnDraftToPendingReview() {
        Article article = draft("author");
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));

        assertEquals(ArticleService.TransitionResult.SUCCESS, service().submit("one", "author"));

        assertEquals(Article.STATE_PENDING, article.getState());
        verify(articleDao).save(article);
    }

    @Test
    void submitRejectsNonAuthorWrongStateAndMissingArticle() {
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(draft("other")));
        Article published = draft("author");
        published.setState(Article.STATE_PUBLISHED);
        when(articleDao.findByIdForUpdate("two")).thenReturn(Optional.of(published));
        when(articleDao.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertEquals(ArticleService.TransitionResult.NOT_ALLOWED, service().submit("one", "author"));
        assertEquals(ArticleService.TransitionResult.INVALID_STATE, service().submit("two", "author"));
        assertEquals(ArticleService.TransitionResult.NOT_ALLOWED, service().submit("missing", "author"));

        verify(articleDao, never()).save(any());
    }

    @Test
    void approveRequiresAdministrator() {
        when(userRoleDao.countAdministrator("author")).thenReturn(0L);

        assertThrows(ForbiddenException.class, () -> service().approve("one", "author"));

        verify(articleDao, never()).findByIdForUpdate(any());
    }

    @Test
    void approvePublishesPendingArticleAndStampsPublishTime() {
        Article article = draft("author");
        article.setState(Article.STATE_PENDING);
        when(userRoleDao.countAdministrator("admin")).thenReturn(1L);
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));

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
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));

        assertEquals(ArticleService.TransitionResult.SUCCESS, service().reject("one", "admin"));

        assertEquals(Article.STATE_DRAFT, article.getState());
        verify(articleDao).save(article);
    }

    @Test
    void reviewRejectsWrongStateAndMissingArticle() {
        when(userRoleDao.countAdministrator("admin")).thenReturn(1L);
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(draft("author")));
        when(articleDao.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertEquals(ArticleService.TransitionResult.INVALID_STATE, service().approve("one", "admin"));
        assertThrows(ResourceNotFoundException.class, () -> service().reject("missing", "admin"));

        verify(articleDao, never()).save(any());
    }

    @Test
    void findByIdHidesDraftsFromNonAuthorsButShowsThemToAdministrators() {
        Article draft = draft("author");
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(draft));
        when(userRoleDao.countAdministrator("admin")).thenReturn(1L);

        assertSame(draft, service().findById("one", "author"));
        assertSame(draft, service().findById("one", "admin"));
        assertThrows(ResourceNotFoundException.class, () -> service().findById("one", "other"));
        assertThrows(ResourceNotFoundException.class, () -> service().findById("one", null));
    }

    @Test
    void countsVisitsWhenNonAuthorReadsPublishedArticle() {
        Article article = publishedArticle("author", 5);
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));
        when(articleDao.incrementVisits("one")).thenReturn(1);

        Article result = service().findById("one", "viewer");

        assertEquals(6, result.getVisits());
        verify(articleDao).incrementVisits("one");
    }

    @Test
    void countsAnonymousReadsOfPublishedArticles() {
        Article article = publishedArticle("author", 0);
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));
        when(articleDao.incrementVisits("one")).thenReturn(1);

        Article result = service().findById("one", null);

        assertEquals(1, result.getVisits());
        verify(articleDao).incrementVisits("one");
    }

    @Test
    void doesNotCountAuthorOrDraftReads() {
        Article article = publishedArticle("author", 5);
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));
        Article draft = draft("author");
        when(articleDao.findByIdForUpdate("draft")).thenReturn(Optional.of(draft));

        assertEquals(5, service().findById("one", "author").getVisits());
        service().findById("draft", "author");

        verify(articleDao, never()).incrementVisits(any());
    }

    @Test
    void updateReplacesLabelsOnlyWhenLabelsAreProvided() {
        Article existing = draft("author");
        existing.setId("one");
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(existing));
        when(articleLabelDao.findLabelIdsByArticleIdForUpdate("one")).thenReturn(List.of("java"));
        Article update = new Article();
        update.setId("one");
        update.setLabelIds(List.of());

        assertTrue(service().update(update, "author"));

        verify(articleLabelDao).deleteByArticleId("one");
        verify(articleLabelDao).decrementLabelCounts(List.of("java"));
        verify(articleLabelDao, never()).insertExistingLabels(any(), any());
    }

    @Test
    void labelReplacementUsesCurrentLockedRelationsForCounters() {
        Article existing = draft("author");
        existing.setId("one");
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(existing));
        when(articleLabelDao.countExistingLabels(List.of("requested"))).thenReturn(1L);
        when(articleLabelDao.findLabelIdsByArticleIdForUpdate("one")).thenReturn(List.of("latest"));
        when(articleLabelDao.insertExistingLabels("one", List.of("requested"))).thenReturn(1);
        Article update = new Article();
        update.setId("one");
        update.setLabelIds(List.of("requested"));

        assertTrue(service().update(update, "author"));

        verify(articleLabelDao).decrementLabelCounts(List.of("latest"));
        verify(articleLabelDao).incrementLabelCounts(List.of("requested"));
        verify(articleLabelDao, never()).findLabelIdsByArticleId(any());
    }

    @Test
    void incompleteLabelInsertionFailsBeforeIncrementingCounts() {
        when(idWorker.nextId()).thenReturn(123L);
        when(articleLabelDao.countExistingLabels(List.of("java", "spring"))).thenReturn(2L);
        when(articleLabelDao.insertExistingLabels("123", List.of("java", "spring"))).thenReturn(1);
        Article article = new Article();
        article.setLabelIds(List.of("java", "spring"));

        assertThrows(InvalidRequestException.class, () -> service().add(article, "author"));

        verify(articleLabelDao, never()).incrementLabelCounts(any());
    }

    @Test
    void deleteRemovesArticleLabelsBeforeDeletingArticle() {
        Article existing = draft("author");
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(existing));
        when(articleLabelDao.findLabelIdsByArticleIdForUpdate("one")).thenReturn(List.of("java"));

        assertTrue(service().delete("one", "author"));

        verify(articleLabelDao).deleteByArticleId("one");
        verify(articleLabelDao).decrementLabelCounts(List.of("java"));
        verify(commentThumbupDao).deleteByArticleId("one");
        verify(commentDao).deleteByArticleidBulk("one");
        verify(articleDao).delete(existing);
    }

    @Test
    void thumbupRegistersRelationAndIncrementsOnce() {
        Article article = publishedArticle("author", 0);
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));
        when(articleThumbupDao.insertIfAbsent("one", "viewer")).thenReturn(1);
        when(articleDao.incrementThumbup("one")).thenReturn(1);

        assertEquals(ArticleService.ThumbupResult.SUCCESS, service().thumbup("one", "viewer"));

        verify(articleDao).incrementThumbup("one");
    }

    @Test
    void thumbupRejectsDuplicateWithoutIncrementing() {
        Article article = publishedArticle("author", 3);
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));
        when(articleThumbupDao.insertIfAbsent("one", "viewer")).thenReturn(0);

        assertEquals(ArticleService.ThumbupResult.DUPLICATE, service().thumbup("one", "viewer"));

        verify(articleDao, never()).incrementThumbup(any());
    }

    @Test
    void thumbupRejectsMissingAndUnpublishedArticles() {
        when(articleDao.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        when(articleDao.findByIdForUpdate("draft")).thenReturn(Optional.of(draft("author")));

        assertEquals(ArticleService.ThumbupResult.NOT_FOUND, service().thumbup("missing", "viewer"));
        assertEquals(ArticleService.ThumbupResult.NOT_FOUND, service().thumbup("draft", "viewer"));

        verify(articleThumbupDao, never()).insertIfAbsent(any(), any());
    }

    @Test
    void thumbupReleasesClaimWhenCounterUpdateFails() {
        Article article = publishedArticle("author", 0);
        when(articleDao.findByIdForUpdate("one")).thenReturn(Optional.of(article));
        when(articleThumbupDao.insertIfAbsent("one", "viewer")).thenReturn(1);
        when(articleDao.incrementThumbup("one")).thenReturn(0);

        assertEquals(ArticleService.ThumbupResult.NOT_FOUND, service().thumbup("one", "viewer"));

        verify(articleThumbupDao).deleteClaim("one", "viewer");
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

    @Test
    void searchFiltersArticlesThatContainAllRequestedLabels() {
        when(articleLabelDao.countExistingLabels(List.of("java", "spring"))).thenReturn(2L);
        when(articleLabelDao.findArticleIdsWithAllLabels(List.of("java", "spring"), 2))
                .thenReturn(List.of("article-one"));
        when(articleDao.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());

        service().search(Map.of("labelIds", List.of("java", "spring")), 1, 10, null);

        verify(articleLabelDao).findArticleIdsWithAllLabels(List.of("java", "spring"), 2);
        verify(articleDao).findAll(any(Specification.class), eq(PageRequest.of(0, 10, ArticleService.DEFAULT_SORT)));
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
        Path visibilityPath = mock(Path.class);
        Predicate statePublished = mock(Predicate.class);
        Predicate legacyPublic = mock(Predicate.class);
        Predicate explicitlyPublic = mock(Predicate.class);
        Predicate publicFlag = mock(Predicate.class);
        Predicate published = mock(Predicate.class);
        Predicate ownerClause = null;
        when(root.get("state")).thenReturn(statePath);
        when(builder.equal(statePath, Article.STATE_PUBLISHED)).thenReturn(statePublished);
        when(root.get("ispublic")).thenReturn(visibilityPath);
        when(builder.isNull(visibilityPath)).thenReturn(legacyPublic);
        when(builder.equal(visibilityPath, "1")).thenReturn(explicitlyPublic);
        when(builder.or(legacyPublic, explicitlyPublic)).thenReturn(publicFlag);
        when(builder.and(statePublished, publicFlag)).thenReturn(published);
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
