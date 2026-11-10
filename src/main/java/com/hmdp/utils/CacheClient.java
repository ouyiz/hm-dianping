package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    //方法1：普通写缓存
    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //方法2：写逻辑过期缓存
    // 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

    }

    //方法3：查询缓存，解决缓存穿透
    // 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit){
        //1.从redis查询店铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.存在，直接返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        //3.命中为空值
        if(json!=null){
            return null;
        }
        //4.查询数据库不存在
        R r= dbFallback.apply(id);
        if(r==null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //5.查询数据库后存在
        this.set(key,r,time,unit);
        return  r;
    }

    //方法4：查询缓存，解决缓存击穿
    //根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题

    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",LOCK_SHOP_TTL,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit){
        //1.从Redis查询店铺
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.redis中店铺为空,缓存没有就查数据库并写入逻辑过期缓存
        if(StrUtil.isBlank(json)){
            R r= dbFallback.apply(id);
            if(r==null){
                return null;
            }
            this.setWithLogicalExpire(key,r,time,unit);
            return r;
        }
        //3.命中店铺，json变对象
        RedisData redisData=JSONUtil.toBean(json,RedisData.class);
        R r=JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
        //4.店铺未过期
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return r;
        }
        //5.店铺过期，获取锁
        boolean isLock=tryLock(LOCK_SHOP_KEY+id);
        //6.获取锁成功
        if(isLock){
            //===========double check开始===============
            String jsonAgain=stringRedisTemplate.opsForValue().get(key);
            RedisData redisDataAgain=JSONUtil.toBean(jsonAgain,RedisData.class);
            if(redisDataAgain.getExpireTime().isAfter(LocalDateTime.now())){
                unlock(LOCK_SHOP_KEY+id);
                return JSONUtil.toBean(JSONUtil.toJsonStr(redisDataAgain.getData()), type);
            }
            //===========double check结束===============

            //7.开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                    R r1=dbFallback.apply(id);
                    this.setWithLogicalExpire(key,r1,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(LOCK_SHOP_KEY+id);
                }
            });
        }
        return r;
    }


}
