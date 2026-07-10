package com.sky.controller.user;

import com.sky.entity.Setmeal;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
public class SetmealController {
    @Resource
    private SetmealService setmealService;

    /**
     * 用户端获取某个分类下的套餐信息
     * 显式传递shopId参数，确保数据隔离和缓存正确性
     *
     * @param categoryId 分类ID
     * @param shopId 店铺ID
     * @return 套餐列表
     */
    @RequestMapping("/list")
    public Result<List<Setmeal>> list(Long categoryId, Long shopId) {
        return Result.success(setmealService.userSetmealList(categoryId, shopId));
    }

    /**
     * 根据id查询套餐详情
     *
     * @param id
     * @return
     */
    @RequestMapping("/dish/{id}")
    public Result<List<DishItemVO>> getById(Long id) {
        return Result.success(setmealService.getDishItemVOListById(id));
    }
}
