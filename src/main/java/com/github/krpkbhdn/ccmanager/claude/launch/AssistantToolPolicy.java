package com.github.krpkbhdn.ccmanager.claude.launch;

import java.util.List;

/** What the launcher needs to know of the Assistant's tools, so it does not depend on the app control package. */
public interface AssistantToolPolicy {

    /** @return the sorted names of the tools the Assistant may call without a permission dialog */
    List<String> autoApprovedToolNames();
}
