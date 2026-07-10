package com.sky.controller.user;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import com.sky.vo.ShopCartALLVo;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController("userShoppingCartController")
@RequestMapping("/user/shoppingCart")
public class ShoppingCartController {
    @Resource
    private ShoppingCartService shoppingCartService;
    /**
     * 添加购物车
     * @param
     * @return
     */
    @PostMapping("/add")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        shoppingCartService.add(shoppingCartDTO);
        return Result.success();
    }
    /**
     * 查看购物车
     * @return
     */
    @GetMapping("/list")
    public Result<List<ShoppingCart>> list(@RequestParam Long shopId) {
        return Result.success(shoppingCartService.listShoppingCart(shopId));
    }
    /**
     * 清空购物车
     * @return
     */
    @DeleteMapping("/clean")
    public Result clean(Long id, Long shopId) {
        shoppingCartService.clean(shopId, id);
        return Result.success();
    }
    /**
     * 查看所有购物车
     * @return
     */
    @GetMapping("/listAll")
    public Result<List<ShopCartALLVo>> listAll() {
        return Result.success(shoppingCartService.listAll());
    }
}
