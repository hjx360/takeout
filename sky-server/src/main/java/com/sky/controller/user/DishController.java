package com.sky.controller.user;

import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
public class DishController {
    @Resource
    private DishService dishService;

    /**
     * 用户端根据分类ID查询菜品列表
     * 显式传递shopId参数，确保数据隔离和缓存正确性
     * @param categoryid 分类ID
     * @param shopId 店铺ID
     * @return 菜品列表
     */
    @GetMapping("/list")
    public Result<List<Dish>> list(Long categoryid, Long shopId) {
        List<Dish> list = dishService.userDishList(categoryid, shopId);
        return Result.success(list);
    }

    /**
     * 获取指定菜品的详细信息
     * @param id
     * @return
     */
    @GetMapping("/getById")
    public Result<DishVO> getById(Long id) {
        return Result.success(dishService.getById(id));
    }
}
