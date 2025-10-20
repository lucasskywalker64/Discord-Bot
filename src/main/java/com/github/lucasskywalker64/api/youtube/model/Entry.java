package com.github.lucasskywalker64.api.youtube.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

public class Entry {

    @JacksonXmlProperty(localName = "videoId", namespace = "http://www.youtube.com/xml/schemas/2015")
    public String videoId;

    @JacksonXmlProperty(localName = "channelId", namespace = "http://www.youtube.com/xml/schemas/2015")
    public String channelId;

    @JacksonXmlProperty(localName = "title", namespace = "http://www.w3.org/2005/Atom")
    public String title;

    @JacksonXmlProperty(localName = "link", namespace = "http://www.w3.org/2005/Atom")
    @JacksonXmlElementWrapper(useWrapping = false)
    public List<Link> links;

    @JacksonXmlProperty(localName = "published", namespace = "http://www.w3.org/2005/Atom")
    public String published;

    @JacksonXmlProperty(localName = "updated", namespace = "http://www.w3.org/2005/Atom")
    public String updated;

    public String getVideoUrl() {
        if (links == null) {
            return null;
        }
        for (Link link : links) {
            if ("alternate".equals(link.rel)) {
                return link.href;
            }
        }
        return null;
    }
}
