package com.github.krpkbhdn.ccmanager.review.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.review.Review;
import com.github.krpkbhdn.ccmanager.review.ReviewService;
import com.github.krpkbhdn.ccmanager.review.SubmittedImage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SubmitScreenshotsToolTest {

    private static final long PROJECT = 3;
    private static final String SESSION = "s1";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReviewService reviews = mock(ReviewService.class);
    private final SubmitScreenshotsTool tool = new SubmitScreenshotsTool(reviews);

    @Test
    void submitsTheImagesForTheCallingSession() {
        var path = Path.of("shot.png").toAbsolutePath();
        var review = mock(Review.class);
        when(review.id()).thenReturn(9L);
        when(reviews.submit(PROJECT, SESSION, "Git tab", "", List.of(new SubmittedImage(path, "dark")))).thenReturn(review);
        var arguments = mapper.createObjectNode().put("title", "Git tab");
        arguments.putArray("images").addObject().put("path", path.toString()).put("label", "dark");

        var text = tool.call(PROJECT, SESSION, arguments);

        assertThat(text).startsWith("Review #9 with 1 image(s) is waiting in the Review tab");
        verify(reviews).submit(PROJECT, SESSION, "Git tab", "", List.of(new SubmittedImage(path, "dark")));
    }

    @Test
    void rejectsMissingTitleAndEmptyImages() {
        var noTitle = mapper.createObjectNode();
        noTitle.putArray("images").addObject().put("path", "a.png");
        var noImages = mapper.createObjectNode().put("title", "t");
        noImages.putArray("images");

        assertThatThrownBy(() -> tool.call(PROJECT, SESSION, noTitle)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.call(PROJECT, SESSION, noImages)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutASessionItFails() {
        assertThatThrownBy(() -> tool.call(PROJECT, mapper.createObjectNode())).isInstanceOf(IllegalStateException.class);
    }
}
