package com.github.krpkbhdn.ccmanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebScreenshotStoreTest {

    private static final byte[] PNG = TestPng.tiny();

    @TempDir
    Path temp;

    private WebScreenshotStore store(long maxBytes) {
        return new WebScreenshotStore(new AppProperties(temp),
                new WebReviewProperties(600, List.of("display"), maxBytes, 200));
    }

    @Test
    void savesUnderProjectAndRevisionFolder() {
        var file = store(1000).save(3, 7, 2, PNG);

        assertThat(file).isEqualTo("item-2.png");
        assertThat(temp.resolve("web-review/3/7/item-2.png")).hasBinaryContent(PNG);
    }

    @Test
    void rejectsTruncatedPng() {
        var truncated = java.util.Arrays.copyOf(PNG, PNG.length / 2);

        assertThatThrownBy(() -> store(1000).save(1, 1, 1, truncated)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPngAndOversizedData() {
        assertThatThrownBy(() -> store(1000).save(1, 1, 1, new byte[] {1, 2, 3})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store(5).save(1, 1, 1, PNG)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("larger");
    }

    @Test
    void movesAndDeletesFiles() {
        var store = store(1000);
        store.save(1, 1, 1, PNG);

        store.move(1, 1, 2, 1);
        assertThat(temp.resolve("web-review/1/1/item-1.png")).doesNotExist();
        assertThat(temp.resolve("web-review/1/2/item-1.png")).exists();

        store.delete(1, 2, 1);
        assertThat(Files.exists(temp.resolve("web-review/1/2/item-1.png"))).isFalse();
    }
}
