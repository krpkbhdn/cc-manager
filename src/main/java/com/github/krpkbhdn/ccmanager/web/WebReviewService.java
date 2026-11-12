package com.github.krpkbhdn.ccmanager.web;

import com.github.krpkbhdn.ccmanager.review.ReviewStatus;
import com.github.krpkbhdn.ccmanager.web.WebReviewRepository.ItemRef;
import com.github.krpkbhdn.ccmanager.workspace.ChangeListeners;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Web review: picked page elements with comments and CSS changes, grouped into numbered revisions that are sent to an agent.
 * Files are touched only after the database change committed, best effort, so a failure never leaves a row without its bookkeeping.
 */
@Service
@EnableConfigurationProperties(WebReviewProperties.class)
public class WebReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(WebReviewService.class);

    private final WebReviewRepository repository;
    private final TransactionTemplate transaction;
    private final WebReviewProperties properties;
    private final WebScreenshotStore screenshots;
    private final WebFeedbackWriter feedbackWriter;
    private final ChangeListeners listeners = new ChangeListeners("Web review");

    public WebReviewService(JdbcClient jdbc, TransactionTemplate transaction, WebReviewProperties properties, WebScreenshotStore screenshots,
                            WebFeedbackWriter feedbackWriter) {
        this.repository = new WebReviewRepository(jdbc);
        this.transaction = transaction;
        this.properties = properties;
        this.screenshots = screenshots;
        this.feedbackWriter = feedbackWriter;
    }

    public BrowserLimits browserLimits() {
        return new BrowserLimits(properties.maxLiveBrowsers(), properties.idleBrowserTtl());
    }

    public Duration screenshotTimeout() {
        return properties.screenshotTimeout();
    }

    /** The project's PENDING revision, created when missing; there is never more than one. */
    public WebRevision currentRevision(long projectId) {
        var existing = repository.pendingRevision(projectId);
        if (existing.isPresent()) {
            return existing.get();
        }
        var revision = transaction.execute(status -> repository.pendingOrNewRevision(projectId));
        listeners.fire();
        return revision;
    }

    public List<WebRevision> revisions(long projectId) {
        return repository.revisions(projectId);
    }

    public Optional<WebRevision> findRevision(long revisionId) {
        return repository.findRevision(revisionId);
    }

    /** Overlay items of one page: OPEN, SENT and DONE of every revision. */
    public List<WebItem> items(long projectId, String pageKey) {
        return repository.overlayItems(projectId, pageKey);
    }

    public List<WebItem> allItems(long revisionId) {
        return repository.revisionItems(revisionId);
    }

    /** Pages ordered by last visit, the most recently visited last. */
    public List<WebPage> pages(long projectId) {
        return repository.pages(projectId);
    }

    /** Records a visit: upserts the page with its real URL and title and moves it last; listeners fire only for a new page or a changed URL or title. */
    public void touchPage(long projectId, String url, String title) {
        var pageKey = PageKey.of(url);
        var changed = transaction.execute(status -> repository.upsertPage(projectId, pageKey, url.strip(), title == null ? "" : title).changed());
        if (changed) {
            listeners.fire();
        }
    }

    public WebItem addItem(long projectId, String url, String title, ElementAnchor anchor, String comment, List<StyleChange> changes) {
        return addItem(projectId, url, title, anchor, comment, changes, null);
    }

    /**
     * @param viewport where the item was left, null when unknown
     * @throws IllegalArgumentException for a URL that is not http, https or file, or when the pending revision is full
     */
    public WebItem addItem(long projectId, String url, String title, ElementAnchor anchor, String comment, List<StyleChange> changes,
            WebViewport viewport) {
        requireContent(anchor, comment, changes);
        var pageKey = PageKey.of(url);
        var item = transaction.execute(status -> {
            var revision = repository.pendingOrNewRevision(projectId);
            requireRoom(revision.id());
            var page = repository.upsertPage(projectId, pageKey, url.strip(), title == null ? "" : title);
            return repository.insertItem(projectId, revision.id(), page.id(), anchor, comment, changes, viewport);
        });
        listeners.fire();
        return item;
    }

    /** @throws IllegalArgumentException for an unknown item @throws IllegalStateException when the item is not OPEN */
    public void updateItem(long itemId, String comment, List<StyleChange> changes) {
        requireContent(comment, changes);
        transaction.executeWithoutResult(status -> {
            var ref = repository.itemRef(itemId).orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
            if (ref.status() != WebItemStatus.OPEN) {
                throw new IllegalStateException("Only an open item can be edited.");
            }
            repository.updateContent(itemId, comment, changes);
        });
        listeners.fire();
    }

    /**
     * User-driven moves only: any item can be dismissed or reopened (OPEN, which moves an item of an older revision into the current PENDING
     * one); SENT and DONE are set by {@link #prepareFeedback} and {@link #approve} alone.
     * @throws IllegalStateException for a request to set SENT or DONE
     */
    public void setStatus(long itemId, WebItemStatus status) {
        if (status != WebItemStatus.OPEN && status != WebItemStatus.DISMISSED) {
            throw new IllegalStateException("An item becomes " + status + " only by sending or approving its revision.");
        }
        var move = transaction.execute(s -> {
            var ref = repository.itemRef(itemId).orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
            if (ref.status() == status) {
                return Optional.<ScreenshotMove>empty();
            }
            var revisionId = ref.revisionId();
            Optional<ScreenshotMove> moved = Optional.empty();
            if (status == WebItemStatus.OPEN && !repository.isPending(revisionId)) {
                revisionId = repository.pendingOrNewRevision(ref.projectId()).id();
                requireRoom(revisionId);
                moved = Optional.of(new ScreenshotMove(ref, revisionId));
            }
            repository.moveAndSetStatus(itemId, status, revisionId);
            return moved;
        });
        move.ifPresent(m -> bestEffort(() -> screenshots.move(m.ref().projectId(), m.ref().revisionId(), m.toRevisionId(), m.ref().n())));
        listeners.fire();
    }

    public void deleteItem(long itemId) {
        var ref = repository.itemRef(itemId);
        if (ref.isPresent() && Boolean.TRUE.equals(transaction.execute(status -> repository.deleteItem(itemId)))) {
            bestEffort(() -> screenshots.delete(ref.get().projectId(), ref.get().revisionId(), ref.get().n()));
            listeners.fire();
        }
    }

    /** @throws IllegalArgumentException when the bytes are not a PNG or too large, or the item is gone */
    public void saveScreenshot(long itemId, byte[] png) {
        var ref = repository.itemRef(itemId).orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        var file = screenshots.save(ref.projectId(), ref.revisionId(), ref.n(), png);
        repository.setScreenshot(itemId, file);
        listeners.fire();
    }

    /**
     * In one transaction: marks exactly the OPEN items it read and the revision SENT, then writes the markdown from that same list, so a
     * failed write rolls everything back and an item added meanwhile lands in the next revision.
     * @throws IllegalStateException when the revision is not PENDING (e.g. already sent)
     * @throws IllegalArgumentException for an unknown revision or one without open items
     */
    public WebFeedback prepareFeedback(long revisionId) {
        var feedback = transaction.execute(status -> {
            var revision = repository.findRevision(revisionId).orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
            if (revision.status() != ReviewStatus.PENDING) {
                throw new IllegalStateException("Revision r" + revision.number() + " was already sent.");
            }
            var items = repository.revisionItems(revisionId).stream().filter(item -> item.status() == WebItemStatus.OPEN).toList();
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Revision r" + revision.number() + " has no open items to send.");
            }
            items.forEach(item -> repository.markSent(item.id()));
            repository.markRevisionSent(revisionId);
            return feedbackWriter.write(repository.projectName(revision.projectId()), revision, repository.pages(revision.projectId()), items,
                    screenshots.directory(revision.projectId(), revisionId));
        });
        listeners.fire();
        return feedback;
    }

    public void bindSession(long revisionId, String ccmSessionId) {
        repository.bindSession(revisionId, ccmSessionId);
        listeners.fire();
    }

    /** @throws IllegalStateException when the revision is not SENT, so OPEN items of a PENDING revision are never stranded */
    public void approve(long revisionId) {
        transaction.executeWithoutResult(s -> {
            var revision = repository.findRevision(revisionId).orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
            if (revision.status() != ReviewStatus.SENT) {
                throw new IllegalStateException("Only a sent revision can be approved.");
            }
            repository.approveRevision(revisionId);
        });
        listeners.fire();
    }

    /** @throws IllegalStateException when the project already has a PENDING revision */
    public WebRevision startNextRevision(long projectId) {
        var revision = transaction.execute(status -> {
            if (repository.pendingRevision(projectId).isPresent()) {
                throw new IllegalStateException("A pending revision already exists.");
            }
            try {
                return repository.createRevision(projectId);
            } catch (DataIntegrityViolationException e) {
                throw new IllegalStateException("A pending revision already exists.", e);
            }
        });
        listeners.fire();
        return revision;
    }

    /** Deletes APPROVED revisions settled before the cutoff with their items, screenshots and markdown; PENDING and SENT ones are never touched. */
    public int deleteApprovedBefore(Instant cutoff) {
        var old = repository.approvedBefore(cutoff);
        if (old.isEmpty()) {
            return 0;
        }
        transaction.executeWithoutResult(status -> old.forEach(revision -> repository.deleteRevision(revision.id())));
        old.forEach(revision -> bestEffort(() -> screenshots.deleteRevision(revision.projectId(), revision.id(), revision.number())));
        listeners.fire();
        return old.size();
    }

    /** @return unsubscribe handle; the callback fires on any thread after a change made here */
    public Runnable subscribe(Runnable onChange) {
        return listeners.add(onChange);
    }

    private void requireRoom(long revisionId) {
        if (repository.itemCount(revisionId) >= properties.maxItemsPerRevision()) {
            throw new IllegalArgumentException("This revision already has " + properties.maxItemsPerRevision() + " items: send it first.");
        }
    }

    private static void requireContent(ElementAnchor anchor, String comment, List<StyleChange> changes) {
        if (anchor == null) {
            throw new IllegalArgumentException("The item needs an element.");
        }
        requireContent(comment, changes);
    }

    private static void requireContent(String comment, List<StyleChange> changes) {
        if (comment == null || changes == null || changes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The item needs a comment and a list of style changes (possibly empty).");
        }
    }

    private static void bestEffort(Runnable fileOperation) {
        try {
            fileOperation.run();
        } catch (UncheckedIOException e) {
            LOG.warn("A web review file could not be updated: {}", e.getCause().getClass().getSimpleName());
        }
    }

    private record ScreenshotMove(ItemRef ref, long toRevisionId) {
    }
}
