package com.github.lucasskywalker64.api.youtube.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Link {

    @JacksonXmlProperty(localName = "rel", isAttribute = true)
    public String rel;

    @JacksonXmlProperty(localName = "href", isAttribute = true)
    public String href;
}
