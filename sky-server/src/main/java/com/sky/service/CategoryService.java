package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;

import java.util.List;

public interface CategoryService {
    PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    void startOrStop(Integer status, Long id);

    void save(CategoryDTO categorydto);

    void delete(Long id);

    void update(CategoryDTO categorydto);

    List<Category> list(Integer type);


    List<Category> userCategoryList(Integer type, Long shopId);
}
