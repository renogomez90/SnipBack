package com.hipoint.snipback.room.entities;

import java.util.List;

public class AllCategory {
    private long categoryTitle;
    List<CategoryItem> categoryItemList;

    public AllCategory(long categoryTitle) {
        this.categoryTitle = categoryTitle;
    }

    public List<CategoryItem> getCategoryItemList() {
        return categoryItemList;
    }

    public void setCategoryItemList(List<CategoryItem> categoryItemList) {
        this.categoryItemList = categoryItemList;
    }

    public long getCategoryTitle() {
        return categoryTitle;
    }

    public void setCategoryTitle(long categoryTitle) {
        this.categoryTitle = categoryTitle;
    }
}
