package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String key = CACHE_SHOP_TYPE_KEY;
        Set<String> typeSet = stringRedisTemplate.opsForZSet().range(key, 0, -1);
        if (CollectionUtil.isNotEmpty(typeSet)){
            //如果redis命中
            //创建结果列表
            List<ShopType> typeList = new ArrayList<>();
            for (String jsonStr : typeSet) {
                ShopType shopType = JSONUtil.toBean(jsonStr, ShopType.class);
                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }
        //未命中，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //不存在，错误
        if(CollectionUtil.isEmpty(typeList)){
            return Result.fail("店铺类型不存在");
        }
        //存在，写入Redis
        for (ShopType shopType : typeList) {
            String jsonStr = JSONUtil.toJsonStr(shopType);
            stringRedisTemplate.opsForZSet().add(key, jsonStr, shopType.getSort());
        }

        return Result.ok(typeList);
    }
}
