package com.github.lucasskywalker64.web;

import com.github.zafarkhaja.semver.Version;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class VersionReader {

    private VersionReader() {}

    public static Version readVersion(String html) {
        Document doc = Jsoup.parse(html);

        Element versionTag = doc.selectFirst("meta[name=version]");
        if (versionTag != null) {
            return Version.parse(versionTag.attr("content"));
        }
        return null;
    }
}
