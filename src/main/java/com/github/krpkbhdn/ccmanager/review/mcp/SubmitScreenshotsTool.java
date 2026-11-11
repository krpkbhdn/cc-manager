package com.github.krpkbhdn.ccmanager.review.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpTool;
import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.review.ReviewService;
import com.github.krpkbhdn.ccmanager.review.SubmittedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class SubmitScreenshotsTool implements McpTool {

    private final ReviewService reviews;

    SubmitScreenshotsTool(ReviewService reviews) {
        this.reviews = reviews;
    }

    @Override
    public String name() {
        return "submit_screenshots";
    }

    @Override
    public String description() {
        return "Hands screenshot files to the user for visual review in the Review tab of cc-manager. "
                + "Only PNG and JPEG files are accepted. Submit, then stop: the user's feedback arrives later as a prompt.";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{
                "title":{"type":"string","description":"What is being reviewed"},
                "note":{"type":"string","description":"Optional short note on what changed"},
                "images":{"type":"array","minItems":1,"items":{"type":"object","properties":{
                "path":{"type":"string","description":"Absolute path of a PNG or JPEG file"},
                "label":{"type":"string","description":"Screen and state shown, e.g. 'Git tab, dark'"}},
                "required":["path"]}}},
                "required":["title","images"]}""";
    }

    @Override
    public String call(long projectId, JsonNode arguments) {
        throw new IllegalStateException("A review needs the calling session");
    }

    @Override
    public String call(long projectId, String ccmSessionId, JsonNode arguments) {
        var title = McpToolDescriptor.requiredText(arguments, "title");
        var images = new ArrayList<SubmittedImage>();
        for (var image : arguments.path("images")) {
            images.add(new SubmittedImage(Path.of(McpToolDescriptor.requiredText(image, "path")), image.path("label").asString("").strip()));
        }
        if (images.isEmpty()) {
            throw new IllegalArgumentException("Argument 'images' needs at least one image");
        }
        var review = reviews.submit(projectId, ccmSessionId, title, arguments.path("note").asString("").strip(), images);
        return "Review #%d with %d image(s) is waiting in the Review tab of cc-manager for the user; their feedback will arrive later as a prompt. Do not poll for it."
                .formatted(review.id(), images.size());
    }
}
