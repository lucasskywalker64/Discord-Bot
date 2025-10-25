package com.github.lucasskywalker64.api.youtube.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Entry {

    @JacksonXmlProperty(localName = "videoId", namespace = Feed.YT_NS)
    public String videoId;

    @JacksonXmlProperty(localName = "title", namespace = Feed.ATOM_NS)
    public String title;

    @JacksonXmlProperty(localName = "published", namespace = Feed.ATOM_NS)
    public String published;

    @JacksonXmlProperty(localName = "updated", namespace = Feed.ATOM_NS)
    public String updated;

    @JacksonXmlProperty(localName = "link", namespace = Feed.ATOM_NS)
    public Link link;
}
