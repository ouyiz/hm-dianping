package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    /**
     * 根据id查询商户信息
     * @param id
     * @return
     */
    @Override
    public Result queryShopById(Long id) {
        Shop shop=cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,id2->this.getById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if(shop==null){
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }



//    public Shop queryWithPassThrough(Long id){
//        //1.从Redis查询店铺缓存
//        String shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        //2.店铺缓存存在
//        //只不为空，不为“”
//        if(StrUtil.isNotBlank(shopJson)) {
//            Shop shop= JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //判断是否为空值
//        if(shopJson==null) {
//            return null;
//        }
//        //3.店铺缓存不存在，在数据库查询
//        Shop shop=getById(id);
//
//        //4.数据库不存在店铺
//        if(shop!=null) {
//            //将空值写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//
//        //5.存在店铺，写入Redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //6.返回
//        return shop;
//    }
//
//
//    //尝试获得锁
//    private boolean tryLock(String key){
//        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",LOCK_SHOP_TTL,TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    //释放锁
//    private void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }
//
//    //互斥锁解决缓存击穿
//    private Shop queryWithMutex(Long id){
//        //1.从Redis中查询店铺缓存
//        String shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//
//        //2.redis中存在店铺
//        if(StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        //3.命中的为“”
//        if(shopJson!=null){
//            return null;
//        }
//
//        //4.获取互斥锁
//        String lockKey=LOCK_SHOP_KEY+id;
//        Shop shop=null;
//        try{
//            boolean isLock=tryLock(lockKey);
//            //5.获取失败，休眠重试
//            if(!isLock){
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //===========Double check开始===========
//            //6.再次检查缓存
//            shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//            if(StrUtil.isNotBlank(shopJson)) {
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//            if(shopJson!=null) {
//                return null;
//            }
//            //===========Double check结束===========
//            //7.从数据库中查
//            shop=getById(id);
//            //8.数据库中不存在
//            if(shop==null) {
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //9.数据库中存在，写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        }catch (InterruptedException e){
//            throw new RuntimeException(e);
//        }finally {
//            unlock(lockKey);
//        }
//
//        //返回
//        return shop;
//    }
//
//    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException{
//        //1.查询店铺数据
//        Shop shop=getById(id);
//        //2.封装逻辑过期时间
//        RedisData redisData=new RedisData();
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        redisData.setData(shop);
//        //3.存入Redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//
//    }
//
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
//
//    //逻辑过期解决缓存击穿
//    private Shop queryWithLogicalExpire(Long id){
//        //1.从redis中找店铺
//        String shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        //2.店铺不存在
//        if(StrUtil.isBlank(shopJson)) {
//            return null;
//        }
//        //3.存在，转换对象
//        RedisData redisData=JSONUtil.toBean(shopJson,RedisData.class);
//        Shop shop=JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), Shop.class);
//
//        //4.没有过期
//        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
//            return shop;
//        }
//
//        //5.过期获得锁
//        boolean isLock=tryLock(LOCK_SHOP_KEY+id);
//        if(isLock){
//            //=============double check开始===================
//            //6.再次查询后没有过期
//            String jsonAgain=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//            RedisData redisDataAgain=JSONUtil.toBean(jsonAgain,RedisData.class);
//            if(redisDataAgain.getExpireTime().isAfter(LocalDateTime.now())){
//                return JSONUtil.toBean(JSONUtil.toJsonStr(redisDataAgain.getData()), Shop.class);
//            }
//            //=============double check开始===================
//            //7.开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    this.saveShop2Redis(id, 1800L);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }finally {
//                    unlock(LOCK_SHOP_KEY+id);
//                }
//            });
//        }
//        return shop;
//    }

    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Override
    public Result updateShop(Shop shop) {
        //1.判断商铺id是否存在
        if(shop.getId()==null) {
            return Result.fail("店铺id不存在");
        }
        //2.更新数据库
        updateById(shop);

        //3.删除redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return  Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId,Integer current,Double x,Double y) {
        //1.判断是否需要根据坐标查询
        if(x==null||y==null){
            Page<Shop> page=query()
                    .eq("type_id",typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //2.计算分也查询参数
        int from=(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end=current*SystemConstants.DEFAULT_PAGE_SIZE;
        //3.查询Redis
        String key=SHOP_GEO_KEY+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results=stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x,y),
                new Distance(5000),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
        );
        //4.解析出id
        if(results==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list=results.getContent();
        if(list.size()<=from){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids=new ArrayList<>();
        Map<String,Distance> distanceMap=new HashMap<>(list.size());
        list.stream().skip(from).forEach(result ->{
            String shopIdStr=result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance=result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //5.根据id查询shop
        List<Shop> shops = list(
                new LambdaQueryWrapper<Shop>()
                        .in(Shop::getId, ids)
                        .orderByDesc(Shop::getId)
        );
        for (Shop shop : shops) {
            Distance distance = distanceMap.get(shop.getId().toString());
            if (distance != null) {
                shop.setDistance(distance.getValue());
            }
        }
        //6.返回
        return Result.ok(shops);
    }
}
