package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
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
    public Result queryTypeList(){
        //1.从Redis查询商铺类型列表
        List<String> list=stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, 9999);
        System.err.println(list);
        //2.redis存在商铺类型列表
        List<ShopType> shopTypeList=null;
        if(CollUtil.isNotEmpty(list)){
            String json=list.get(0);
            shopTypeList= JSONUtil.parseArray(json).toList(ShopType.class);
            return Result.ok(shopTypeList);
        }
        //3.不存在，在数据库中查询
        shopTypeList=lambdaQuery().orderByAsc(ShopType::getSort).list();
        //4.数据库中不存在，放回错误
        if(CollUtil.isEmpty(shopTypeList)){
            return Result.fail("商铺类型列表不存在");
        }
        //5.数据库中存在，写入Redis
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(shopTypeList));

        //6.返回
        return Result.ok(shopTypeList);
    }
}
