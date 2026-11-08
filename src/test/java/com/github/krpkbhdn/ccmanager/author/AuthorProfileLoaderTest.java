package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AuthorProfileLoaderTest {

    final AuthorProfileLoader loader = new AuthorProfileLoader();

    @Test
    void bundledCatalogIsValid() {
        var catalog = loader.catalog();

        assertThat(catalog.items()).isNotEmpty().allSatisfy(item -> assertThat(item.version()).isGreaterThanOrEqualTo(1));
        assertThat(catalog.byId("plugin.ponytail")).get().extracting(AuthorItem::name).isEqualTo("ponytail@ponytail");
        assertThat(loader.skill(catalog.byId("skill.jscpd").orElseThrow()).files()).contains("SKILL.md");
    }

    @Test
    void everyListedFileIsReadable() {
        loader.catalog().files().forEach(file -> assertThat(loader.text(file)).isNotBlank());
    }

    @Test
    void bundledDevTeamHasExactlyOneLead() {
        var team = loader.teamTemplate("team.dev-team");

        assertThat(team.name()).isEqualTo("Dev team");
        assertThat(team.roles()).extracting(role -> role.name()).doesNotHaveDuplicates();
        assertThat(team.roles()).filteredOn(role -> role.lead()).hasSize(1);
    }

    @Test
    void requiresClosureAddsRequirementsInCatalogOrder() {
        var closure = loader.catalog().requiresClosure(List.of("skill.dry-refactoring"));

        assertThat(closure).extracting(AuthorItem::id).containsExactly("skill.jscpd", "skill.dry-refactoring");
    }

    @Test
    void requiresClosureRejectsAnUnknownId() {
        assertThatThrownBy(() -> loader.catalog().requiresClosure(List.of("nope"))).isInstanceOf(IllegalArgumentException.class);
    }

    AuthorProfileLoader broken(UnaryOperator<List<AuthorItem>> change) {
        var bundled = loader.catalog();
        return new AuthorProfileLoader(new AuthorCatalog(change.apply(new ArrayList<>(bundled.items())), bundled.files()));
    }

    AuthorProfileLoader replacing(String id, UnaryOperator<AuthorItem> change) {
        return broken(items -> items.stream().map(item -> item.id().equals(id) ? change.apply(item) : item).toList());
    }

    static AuthorItem copy(AuthorItem item, List<String> requires, JsonNode fragment, int version) {
        return new AuthorItem(item.id(), item.area(), item.group(), item.title(), item.description(), item.kind(), version, requires,
                item.changes(), item.name(), item.source(), item.path(), item.config(), item.template(), fragment);
    }

    @Test
    void bundledCatalogHasOnlyUserLevelKinds() {
        assertThat(loader.catalog().items()).extracting(AuthorItem::kind)
                .containsOnly(AuthorKind.MARKETPLACE, AuthorKind.PLUGIN, AuthorKind.SKILL, AuthorKind.TEAM, AuthorKind.SETTINGS);
        assertThat(loader.catalog().byId("user.settings.attribution").orElseThrow().fragment())
                .isEqualTo(JsonMapper.builder().build().readTree("{\"attribution\":{\"commit\":\"\",\"pr\":\"\"}}"));
    }

    @Test
    void duplicateIdIsRejected() {
        var first = loader.catalog().items().getFirst();

        assertThatThrownBy(() -> broken(items -> {
            items.add(first);
            return items;
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicate item id");
    }

    @Test
    void unresolvedRequiresIsRejected() {
        assertThatThrownBy(() -> replacing("plugin.context7", plugin -> copy(plugin, List.of("marketplace.missing"), null, plugin.version())))
                .hasMessageContaining("requires unknown item marketplace.missing");
    }

    @Test
    void settingsWithoutAnObjectFragmentIsRejected() {
        assertThatThrownBy(() -> replacing("user.settings.attribution", item -> copy(item, item.requires(), null, item.version())))
                .hasMessageContaining("needs a non-empty fragment object");
        assertThatThrownBy(() -> replacing("user.settings.attribution",
                item -> copy(item, item.requires(), JsonMapper.builder().build().readTree("[1]"), item.version())))
                .hasMessageContaining("needs a non-empty fragment object");
    }

    @Test
    void versionBelowOneIsRejected() {
        assertThatThrownBy(() -> replacing("plugin.context7", plugin -> copy(plugin, plugin.requires(), null, 0)))
                .hasMessageContaining("version must be at least 1");
    }

    @Test
    void unsafePathIsRejected() {
        assertThatThrownBy(() -> new AuthorProfileLoader(new AuthorCatalog(loader.catalog().items(), List.of("../x"))))
                .hasMessageContaining("unsafe path");
    }

    @Test
    void missingResourceIsRejected() {
        assertThatThrownBy(() -> loader.open("nope.md")).isInstanceOf(IllegalArgumentException.class);
    }
}
