package com.wk.pfmis.models;

public class Category {
    private final int id;
    private final String categoryName;
    private final String categoryType;

    public Category(int id, String categoryName, String categoryType) {
        this.id = id;
        this.categoryName = categoryName;
        this.categoryType = categoryType;
    }

    public int getId() {
        return id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategoryType() {
        return categoryType;
    }

    @Override
    public String toString() {
        return categoryName;
    }
}
