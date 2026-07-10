package com.sky.controller.user;

import com.sky.entity.Category;
import com.sky.result.Result;
import com.sky.service.CategoryService;
import com.sun.xml.internal.bind.v2.TODO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userCategoryController")
@RequestMapping("/user/category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;
    /**
     * 分类查询
     * @return
     */
    @GetMapping("/list")
    public Result<List<Category>> userCategoryList(Integer type,Long shopId) {
        return Result.success(categoryService.userCategoryList(type,shopId));
    }
}
