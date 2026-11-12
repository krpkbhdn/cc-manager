package com.github.krpkbhdn.ccmanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PageKeyTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "HTTP://LocalHost:5173/Settings | http://localhost:5173/Settings",
            "http://a.com:80/x | http://a.com/x",
            "https://a.com:443/x | https://a.com/x",
            "https://a.com:8443/x | https://a.com:8443/x",
            "http://user:pw@a.com/x | http://a.com/x",
            "http://a.com | http://a.com/",
            "http://a.com/ | http://a.com/",
            "http://a.com/x/ | http://a.com/x",
            "http://a.com/x#section | http://a.com/x",
            "http://a.com/#/settings | http://a.com/#/settings",
            "http://a.com/#!/legacy | http://a.com/#!/legacy",
            "http://a.com/x?b=2&a=1 | http://a.com/x?a=1&b=2",
            "http://a.com/x?utm_source=n&id=3&fbclid=z&gclid=q | http://a.com/x?id=3",
            "http://a.com/x?UTM_medium=m | http://a.com/x",
            "file:///C:/site/index.html | file:///C:/site/index.html"})
    void normalises(String url, String expected) {
        assertThat(PageKey.of(url)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,<b>x</b>", "ftp://a.com/x", "about:blank", "/relative", "http://", "http://a b.com", "", "  "})
    void rejectsUnsafeOrInvalidUrls(String url) {
        assertThatThrownBy(() -> PageKey.of(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " => ", value = {
            "http://a.com/x?sort=a|b => http://a.com/x?sort=a%7Cb",
            "http://a.com/x?sort=a%7Cb => http://a.com/x?sort=a%7Cb",
            "http://a.com/x?q={\"a\":1} => http://a.com/x?q=%7B%22a%22:1%7D",
            "http://a.com/p^q/<r> => http://a.com/p%5Eq/%3Cr%3E"})
    void keysUrlsWithCharactersChromiumLeavesUnescaped(String url, String expected) {
        assertThat(PageKey.of(url)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "http://a.com/x?author=me&keyword=k&zipcode=1&lang_code=en | http://a.com/x?author=me&keyword=k&zipcode=1&lang_code=en",
            "http://a.com/x?apiKey=1&api-key=2&csrf_token=3&Authorization=4&sid=5&sig=6 | http://a.com/x?apiKey=…&api-key=…&csrf_token=…&Authorization=…&sid=…&sig=…"})
    void redactsWholeParameterNamesOnly(String url, String expected) {
        assertThat(PageKey.redacted(url)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "http://u:pw@Localhost:80/a/?token=abc&x=1 | http://Localhost:80/a/?token=…&x=1",
            "http://a.com/x?access_token=1&id_token=2&Password=3&session_id=4&code=5&apikey=6 | http://a.com/x?access_token=…&id_token=…&Password=…&session_id=…&code=…&apikey=…",
            "http://a.com/#/cb?token=abc&q=1 | http://a.com/#/cb?token=…&q=1",
            "http://a.com/x#access_token=abc | http://a.com/x",
            "http://a.com/x?plain=1&flag | http://a.com/x?plain=1&flag"})
    void redactsUserInfoAndSecretValuesButKeepsKeys(String url, String expected) {
        assertThat(PageKey.redacted(url)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void redactedRejectsUnsafeSchemes() {
        assertThatThrownBy(() -> PageKey.redacted("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void rejectsNull() {
        assertThatThrownBy(() -> PageKey.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
