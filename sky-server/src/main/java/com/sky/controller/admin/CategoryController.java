package com.sky.controller.admin;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;
    /**
     * 分类分页查询
     *
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult> page(CategoryPageQueryDTO categoryPageQueryDTO) {
        PageResult pageResult = categoryService.pageQuery(categoryPageQueryDTO);
        return Result.success(pageResult);
    }
    /**
     * 新增分类
     * @param categorydto
     * @return
     */
    @PostMapping
    public Result save(@RequestBody CategoryDTO categorydto) {
        categoryService.save(categorydto);
        return Result.success(categorydto);
    }
    /**
     * 分类删除
     * @param id
     * @return
     */
    @DeleteMapping
    public Result delete(Long id) {
        categoryService.delete(id);
        return Result.success();
    }
    /**
     * 修改分类
     * @param categorydto
     * @return
     */
    @PutMapping
    public Result update(@RequestBody CategoryDTO categorydto) {
        categoryService.update(categorydto);
        return Result.success();
    }
    /**
     * 分类启用禁用
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    public Result startOrStop(@PathVariable("status") Integer status, Long id) {
        categoryService.startOrStop(status, id);
        return Result.success();
    }
    @GetMapping("/list")
    public Result<List<Category>> list(Integer type) {
        List<Category> categoryList = categoryService.list(type);
        return Result.success(categoryList);
    }
}
