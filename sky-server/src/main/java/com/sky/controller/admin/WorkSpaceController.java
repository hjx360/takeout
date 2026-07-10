package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.WorkSpaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/workspace")
public class WorkSpaceController {
    @Autowired
    private WorkSpaceService workSpaceService;

    /**
     * 工作台-统计接口
     *
     * @return
     */
    @GetMapping("/businessData")
    public Result<BusinessDataVO> getBusinessData() {
        BusinessDataVO businessDataVO = workSpaceService.getBusinessData();
        return Result.success(businessDataVO);
    }

    /**
     * 工作台-套餐启售和禁售数量
     *
     * @return
     */
    @GetMapping("/overviewSetmeals")
    public Result<SetmealOverViewVO> getOverviewSetmeals() {
        SetmealOverViewVO setmealOverViewVO = workSpaceService.getOverviewSetmeals();
        return Result.success(setmealOverViewVO);
    }

    /**
     * 工作台-菜品启售和禁售数量
     *
     * @return
     */
    @GetMapping("/overviewDishes")
    public Result<DishOverViewVO> getOverviewDishes() {
        DishOverViewVO dishOverViewVO = workSpaceService.getOverviewDish();
        return Result.success(dishOverViewVO);
    }
    /**
     * 工作台-查询订单管理数据
     *
     * @return
     */
    @GetMapping("/overviewOrders")
    public Result<OrderOverViewVO> getOverviewOrders() {
        OrderOverViewVO orderOverViewVO = workSpaceService.getOverviewOrders();
        return Result.success(orderOverViewVO);
    }
}
