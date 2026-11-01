package com.github.krpkbhdn.ccmanager.voice.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceAssetCatalogTest {

    private static List<VoiceAsset> allAssets() {
        List<VoiceAsset> assets = new ArrayList<>();
        assets.add(VoiceAsset.SILERO_VAD);
        Arrays.stream(WhisperModel.values()).map(WhisperModel::asset).forEach(assets::add);
        Arrays.stream(RuntimeBuild.values()).map(RuntimeBuild::asset).forEach(assets::add);
        return assets;
    }

    @Test
    void everyAssetHasLowercaseSha256AndPositiveSize() {
        assertThat(allAssets()).allSatisfy(asset -> {
            assertThat(asset.sha256()).matches("[0-9a-f]{64}");
            assertThat(asset.sizeBytes()).isPositive();
        });
    }

    @Test
    void everyAssetUsesHttpsAndIsNotPinnedToMain() {
        assertThat(allAssets()).allSatisfy(asset -> {
            assertThat(asset.url()).startsWith("https://");
            assertThat(asset.url()).doesNotContain("/main/");
        });
    }

    @Test
    void aliasesAreUniqueAndResolvable() {
        List<String> aliases = Arrays.stream(WhisperModel.values()).map(WhisperModel::alias).toList();
        assertThat(aliases).doesNotHaveDuplicates().hasSize(8);
        assertThat(WhisperModel.byAlias("large-v3-turbo-q5_0")).contains(WhisperModel.LARGE_V3_TURBO_Q5_0);
        assertThat(WhisperModel.byAlias("nope")).isEmpty();
    }

    @Test
    void everyOldAliasStillResolvesToTheCurrentFile() {
        assertThat(WhisperModel.byAlias("base")).contains(WhisperModel.BASE_Q5_1);
        assertThat(WhisperModel.byAlias("small")).contains(WhisperModel.SMALL_Q5_1);
        assertThat(WhisperModel.byAlias("small-q8")).contains(WhisperModel.SMALL_Q8_0);
        assertThat(WhisperModel.byAlias("medium-q5")).contains(WhisperModel.LARGE_V3_TURBO_Q5_0);
        assertThat(WhisperModel.byAlias("medium-q5_0")).contains(WhisperModel.LARGE_V3_TURBO_Q5_0);
        assertThat(WhisperModel.byAlias("turbo")).contains(WhisperModel.LARGE_V3_TURBO_Q5_0);
        assertThat(WhisperModel.byAlias("turbo-q5_0")).contains(WhisperModel.LARGE_V3_TURBO_Q5_0);
        assertThat(WhisperModel.byAlias("turbo-q8")).contains(WhisperModel.LARGE_V3_TURBO_Q8_0);
        assertThat(WhisperModel.byAlias("turbo-q8_0")).contains(WhisperModel.LARGE_V3_TURBO_Q8_0);
        assertThat(WhisperModel.byAlias("turbo-full")).contains(WhisperModel.LARGE_V3_TURBO);
        assertThat(WhisperModel.byAlias("large-v3-q5")).contains(WhisperModel.LARGE_V3_Q5_0);
        assertThat(WhisperModel.byAlias("large-v3-turbo")).contains(WhisperModel.LARGE_V3_TURBO);
        assertThat(WhisperModel.byAlias("large-v3")).contains(WhisperModel.LARGE_V3);
    }

    @Test
    void quantizedAliasesSpellTheDisplayName() {
        assertThat(Arrays.stream(WhisperModel.values()).filter(model -> model.displayName().contains(" Q")))
                .allSatisfy(model -> assertThat(model.alias()).isEqualTo(model.displayName().toLowerCase().replace(' ', '-')));
    }

    @Test
    void displayNamesAreUniqueAndShowTheQuantizationOfTheFile() {
        assertThat(Arrays.stream(WhisperModel.values()).map(WhisperModel::displayName).toList())
                .containsExactly("Base Q5_1", "Small Q5_1", "Small Q8_0",
                        "Large v3 Turbo Q5_0", "Large v3 Turbo Q8_0", "Large v3 Turbo", "Large v3 Q5_0", "Large v3");
        assertThat(Arrays.stream(WhisperModel.values())).allSatisfy(model -> {
            String file = model.asset().relativePath();
            String quantization = file.replaceAll(".*-(q\\d_\\d)\\.bin|.*", "$1").toUpperCase();
            assertThat(model.displayName().endsWith(quantization)).isTrue();
            assertThat(model.asset().name()).isEqualTo("Whisper " + model.displayName());
        });
    }

    @Test
    void currentPlatformLookupDoesNotThrow() {
        assertThatCode(RuntimeBuild::forCurrentPlatform).doesNotThrowAnyException();
    }
}
