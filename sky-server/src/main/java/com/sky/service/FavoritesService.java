package com.sky.service;

import com.sky.dto.ShopDTO;
import com.sky.entity.Favorites;
import com.sky.result.PageResult;

public interface FavoritesService {
    void addShopToFavorites(Favorites favorites);

    void deleteShopFromFavorites(Long shopId);

    PageResult getFavorites(ShopDTO shopDTO);
}
