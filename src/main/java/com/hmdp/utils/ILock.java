package com.hmdp.utils;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁接口
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param key 锁的键
     * @param timeout 超时时间
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    boolean tryLock(String key, Integer timeout, TimeUnit timeUnit);

    /**
     * 释放锁
     * @param key 锁的键
     */
    void unlock(String key);

}