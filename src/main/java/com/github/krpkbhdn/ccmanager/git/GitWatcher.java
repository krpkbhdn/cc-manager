package com.github.krpkbhdn.ccmanager.git;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Tells the UI when a repository changed under it (commits, checkouts, staging by Claude or an IDE): watches the .git
 * directory (HEAD, index, refs, merge/rebase state files) and the working tree, debounced.
 *
 * <p>One daemon thread serves every repository; a repository is watched while it has subscribers. Registering a
 * directory is costly on Windows, so a tree with more than {@value #MAX_TREE_DIRECTORIES} directories (build output and
 * dependency folders are skipped) is watched at its root only, plus .git. Directories are not filtered by .gitignore.
 */
@Component
public class GitWatcher {

    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);
    static final int MAX_TREE_DIRECTORIES = 2000;
    private static final Set<String> IGNORED_TREE_NAMES = Set.of(".git", "node_modules", "build", "target", ".gradle", "dist",
            ".idea", "out", ".venv", "__pycache__", ".next");
    private static final Set<String> GIT_STATE_NAMES = Set.of("HEAD", "ORIG_HEAD", "index", "MERGE_HEAD", "CHERRY_PICK_HEAD",
            "REVERT_HEAD", "rebase-merge", "rebase-apply", "packed-refs", "refs");
    private static final String LOCK_SUFFIX = ".lock";
    private static final Logger log = LoggerFactory.getLogger(GitWatcher.class);

    private enum Zone { GIT_ROOT, GIT_REFS, TREE }

    private record Registration(Watch watch, Zone zone) {}

    private final GitRunner git;
    private final Duration debounce;
    private final Object lock = new Object();
    private final Map<Path, Watch> watches = new HashMap<>();
    private final Map<WatchKey, Registration> registrations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debouncer = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofPlatform().daemon().name("git-watcher-debounce").unstarted(runnable));
    private final ExecutorService callbacks = Executors.newVirtualThreadPerTaskExecutor();
    private WatchService service;

    @Autowired
    public GitWatcher(GitRunner git) {
        this(git, DEFAULT_DEBOUNCE);
    }

    public GitWatcher(GitRunner git, Duration debounce) {
        this.git = git;
        this.debounce = debounce;
    }

    /** @return idempotent unsubscribe handle; the callback runs on an arbitrary thread, at most a few times per second */
    public Runnable subscribe(Path repo, Runnable onChange) {
        var root = repo.toAbsolutePath().normalize();
        var listener = new Listener(onChange);
        synchronized (lock) {
            watches.computeIfAbsent(root, this::open).listeners.add(listener);
        }
        var unsubscribed = new AtomicBoolean();
        return () -> {
            if (unsubscribed.compareAndSet(false, true)) {
                unsubscribe(root, listener);
            }
        };
    }

    @PreDestroy
    public void close() {
        synchronized (lock) {
            watches.values().forEach(Watch::cancelKeys);
            watches.clear();
            stopService();
        }
        debouncer.shutdownNow();
        callbacks.shutdownNow();
    }

    private Watch open(Path root) {
        startService();
        var watch = new Watch(root);
        resolveGitDirectory(root).ifPresent(gitDir -> {
            register(watch, gitDir, Zone.GIT_ROOT);
            registerTree(watch, gitDir.resolve("refs"), Zone.GIT_REFS, Integer.MAX_VALUE);
        });
        if (!registerTree(watch, root, Zone.TREE, MAX_TREE_DIRECTORIES)) {
            watch.rootOnly = true;
            register(watch, root, Zone.TREE);
        }
        return watch;
    }

    private Optional<Path> resolveGitDirectory(Path root) {
        try {
            var result = git.tryRun(root, "rev-parse", "--absolute-git-dir");
            return result.ok() ? Optional.of(Path.of(result.stdout().strip())) : Optional.empty();
        } catch (GitException e) {
            return Optional.empty();
        }
    }

    private void unsubscribe(Path root, Listener listener) {
        synchronized (lock) {
            var watch = watches.get(root);
            if (watch == null) {
                return;
            }
            watch.listeners.remove(listener);
            if (watch.listeners.isEmpty()) {
                watches.remove(root);
                watch.cancelKeys();
            }
            if (watches.isEmpty()) {
                stopService();
            }
        }
    }

    private void startService() {
        if (service != null) {
            return;
        }
        try {
            var created = FileSystems.getDefault().newWatchService();
            service = created;
            Thread.ofPlatform().daemon().name("git-watcher").start(() -> dispatch(created));
        } catch (IOException e) {
            throw new GitException(GitException.Kind.OTHER, "Cannot watch the file system", String.valueOf(e.getMessage()));
        }
    }

    private void stopService() {
        if (service == null) {
            return;
        }
        try {
            service.close();
        } catch (IOException e) {
            log.debug("closing watch service failed: {}", e.getMessage());
        }
        service = null;
        registrations.clear();
    }

    private void dispatch(WatchService watchService) {
        try {
            while (true) {
                handle(watchService.take());
            }
        } catch (ClosedWatchServiceException | InterruptedException e) {
            log.debug("git watcher stopped");
        }
    }

    private void handle(WatchKey key) {
        var registration = registrations.get(key);
        if (registration == null) {
            key.cancel();
            return;
        }
        var changed = false;
        for (var event : key.pollEvents()) {
            if (event.kind() == OVERFLOW) {
                changed = true;
            } else if (isRelevant(registration.zone(), (Path) event.context())) {
                changed = true;
                onCreated(registration, ((Path) key.watchable()).resolve((Path) event.context()), event.kind());
            }
        }
        if (!key.reset()) {
            registrations.remove(key);
        }
        if (changed) {
            registration.watch().changed();
        }
    }

    private static boolean isRelevant(Zone zone, Path name) {
        var text = name.toString();
        if (text.endsWith(LOCK_SUFFIX)) {
            return false;
        }
        return switch (zone) {
            case GIT_ROOT -> GIT_STATE_NAMES.contains(text);
            case GIT_REFS -> true;
            case TREE -> !IGNORED_TREE_NAMES.contains(text);
        };
    }

    private void onCreated(Registration registration, Path path, WatchEvent.Kind<?> kind) {
        if (kind != ENTRY_CREATE || registration.zone() == Zone.GIT_ROOT || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        synchronized (lock) {
            if (service != null && !registration.watch().rootOnly && registration.watch().isActive()) {
                var limit = registration.zone() == Zone.TREE ? MAX_TREE_DIRECTORIES - registration.watch().treeKeyCount() : Integer.MAX_VALUE;
                registerTree(registration.watch(), path, registration.zone(), limit);
            }
        }
    }

    /** @return false when the tree has more than {@code limit} directories (nothing is registered then) */
    private boolean registerTree(Watch watch, Path start, Zone zone, int limit) {
        var directories = new ArrayList<Path>();
        try {
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(start) && zone == Zone.TREE && IGNORED_TREE_NAMES.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    directories.add(dir);
                    return directories.size() > limit ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.debug("walking {} failed: {}", start, e.getMessage());
        }
        if (directories.size() > limit) {
            return false;
        }
        directories.forEach(directory -> register(watch, directory, zone));
        return true;
    }

    private void register(Watch watch, Path directory, Zone zone) {
        try {
            var key = directory.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            registrations.put(key, new Registration(watch, zone));
            watch.keys.add(key);
        } catch (IOException | ClosedWatchServiceException e) {
            log.debug("cannot watch {}: {}", directory, e.getMessage());
        }
    }

    private record Listener(Runnable action) {}

    private final class Watch {
        final Path root;
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        final List<WatchKey> keys = new CopyOnWriteArrayList<>();
        private final AtomicBoolean pending = new AtomicBoolean();
        volatile boolean rootOnly;

        Watch(Path root) {
            this.root = root;
        }

        boolean isActive() {
            return !listeners.isEmpty();
        }

        int treeKeyCount() {
            return (int) keys.stream().filter(key -> registrations.get(key) != null && registrations.get(key).zone() == Zone.TREE).count();
        }

        void changed() {
            if (pending.compareAndSet(false, true)) {
                try {
                    debouncer.schedule(this::fire, debounce.toMillis(), TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException e) {
                    pending.set(false);
                }
            }
        }

        private void fire() {
            pending.set(false);
            for (var listener : listeners) {
                callbacks.execute(() -> notify(listener));
            }
        }

        private void notify(Listener listener) {
            try {
                listener.action().run();
            } catch (RuntimeException e) {
                log.warn("git change listener for {} failed: {}", root, e.getMessage());
            }
        }

        void cancelKeys() {
            keys.forEach(key -> {
                key.cancel();
                registrations.remove(key);
            });
            keys.clear();
        }
    }
}
