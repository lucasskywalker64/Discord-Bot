package com.github.lucasskywalker64.persistence.data;

public record ShoutoutData(String username) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShoutoutData that = (ShoutoutData) o;
        return username.equals(that.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }
}
