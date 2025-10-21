package com.github.lucasskywalker64.api.youtube.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Entry {

    @JacksonXmlProperty(localName = "videoId")
    public String videoId;

    public String title;

    public String published;

    public String updated;

    public Link link;
}
