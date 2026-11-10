package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import cn.hutool.core.lang.UUID;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 简单Redis分布式实现类
 */
@Component
public class SimpleRedisLock implements ILock {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    /**
     * 尝试获取锁
     * @param key 锁的键
     * @param timeout 超时时间
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    @Override
    public boolean tryLock(String key, Integer timeout, TimeUnit timeUnit){
        String threadId=ID_PREFIX+Thread.currentThread().getId();
        Boolean success=stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+key,threadId,timeout,timeUnit);
        return BooleanUtil.isTrue(success);
    }

    /**
     * 释放锁
     * @param key 锁的键
     */
    @Override
    public void unlock(String key){
        String KEYS=KEY_PREFIX+key;
        String ARGC=ID_PREFIX+Thread.currentThread().getId();
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEYS), ARGC);
    }





}
