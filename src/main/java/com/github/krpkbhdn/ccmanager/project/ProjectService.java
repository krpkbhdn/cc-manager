package com.github.krpkbhdn.ccmanager.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Projects are plain directories registered by the user; stored in SQLite. */
@Service
public class ProjectService {

    public List<Project> list() { throw todo(); }

    public Optional<Project> find(long id) { throw todo(); }

    /** @throws IllegalArgumentException with a user-readable message (not a directory, already added...) */
    public Project add(Path directory) { throw todo(); }

    public Project rename(long id, String name) { throw todo(); }

    public void remove(long id) { throw todo(); }

    /** @return unsubscribe handle; callback fires after any add/rename/remove */
    public Runnable subscribe(Runnable onChange) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
