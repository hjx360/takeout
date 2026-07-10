package com.sky.controller.user;

import com.sky.dto.ShopDTO;
import com.sky.entity.Favorites;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.FavoritesService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/user/favorites")
public class FavoritesController {
    @Resource
    private FavoritesService favoritesService;

    /**
     * 添加店铺到收藏夹
     *
     * @param
     * @return
     */
    @PostMapping("/add")
    public Result addShopToFavorites(@RequestBody Favorites favorites) {
        favoritesService.addShopToFavorites(favorites);
        return Result.success();
    }

    /**
     * 删除收藏夹中的店铺
     *
     * @param
     * @return
     */
    @DeleteMapping("/delete")
    public Result deleteShopFromFavorites(Long shopId) {
        favoritesService.deleteShopFromFavorites(shopId);
        return Result.success();
    }

    /**
     * 查看收藏夹中的店铺
     *
     * @param
     * @return
     */
    @GetMapping("/getFavorites")
    public Result<PageResult> getFavorites(ShopDTO shopDTO) {
        return Result.success(favoritesService.getFavorites(shopDTO));
    }
}
