package com.github.lucasskywalker64.api.youtube.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "feed", namespace = "http://www.w3.org/2005/Atom")
public class Feed {

    @JacksonXmlProperty(localName = "entry")
    public Entry entry;
}
