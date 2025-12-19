package com.github.lucasskywalker64.api.youtube.model;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Feed {

    public static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    public static final String YT_NS = "http://www.youtube.com/xml/schemas/2015";

    @JacksonXmlProperty(localName = "entry", namespace = ATOM_NS)
    public Entry entry;
}
