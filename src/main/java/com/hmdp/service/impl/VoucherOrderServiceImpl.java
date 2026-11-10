package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private SimpleRedisLock simpleRedisLock;
    @Resource
    private RedissonConfig redissonConfig;
    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //创建JDK自带的阻塞队列，用于存储待处理的优惠券订单任务
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    //创建单线程的线程池，用于处理订单秒杀任务
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();

    @PostConstruct
    private  void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //Redis消息组的名字
    String queueName="stream.orders";

    //内部类--获取Redis的消息队列
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2.判断消息获取是否成功
                    if (list == null || list.isEmpty()){
                        // 如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 3.解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 4.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    // 5.ACK确认消息已处理 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                    // 处理异常消息
                    handlePendingList();
                }
            }
        }
    }

    //处理异常消息
    private void handlePendingList(){
        while (true){
            try {
                // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                // 2.判断消息获取是否成功
                if (list == null || list.isEmpty()){
                    // 如果获取失败，说明pending-list没有异常消息，结束循环
                    break;
                }
                // 3.解析消息中的订单信息
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> values = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                // 4.如果获取成功，可以下单
                handleVoucherOrder(voucherOrder);
                // 5.ACK确认消息已处理 SACK stream.orders g1 id
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            } catch (Exception e) {
                log.error("pending-list处理订单异常",e);
                // 休眠一下，不需要频率这么高
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    //创建订单
    private void handleVoucherOrder(VoucherOrder voucherOrder)  throws Exception{
        //1.获取当前登录用户id
        Long userId=voucherOrder.getUserId();
        //2.使用Redisson创建锁对象
        RLock lock = redissonClient.getLock("lock:order"+userId);
        //3.使用Redisson获取锁
        boolean isLock = lock.tryLock(-1,10,TimeUnit.SECONDS);
        //4.判断锁是否存在
        if(!isLock){
            log.error("不允许重复下单");
            return;
        }
        try{
            proxy.VoucherOrder(voucherOrder);
        }catch (IllegalStateException e){
            throw new RuntimeException(e);
        }finally {
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    @Transactional
    public void VoucherOrder(VoucherOrder voucherOrder) {
        //5.一人一单
        //5.1获取当前登录用户id
        Long userId=voucherOrder.getUserId();
        //5.2查询订单
        long count=lambdaQuery()
                .eq(VoucherOrder::getUserId,userId)
                .eq(VoucherOrder::getVoucherId,voucherOrder.getVoucherId())
                .count();
        //5.2判断是否已经存在
        if(count>0){
            log.error("用户已经购买过一次");
            return;
        }
        //6.扣减库存
        boolean success=seckillVoucherService.lambdaUpdate()
                .setSql("stock=stock-1")
                .eq(SeckillVoucher::getVoucherId,voucherOrder.getVoucherId())
                .gt(SeckillVoucher::getStock,0)
                .update();
        if(!success){
            log.error("库存不足");
            return;
        }

        //7.创建订单
        save(voucherOrder);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //基于Redis执行Lua脚本实现异步优惠券秒杀下单
        //1.获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        //2.先生成订单id
        long orderId=redisIdWorker.nextId("order");
        //3获取代理对象
        proxy=(IVoucherOrderService)AopContext.currentProxy();
        //3.执行lua脚本
        Long result=stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        //4.检查Lua执行结果
        int r=result.intValue();
        //不为0代表没有购买资格
        if(r!=0){
            return Result.fail(r==1?"库存不足":"不允许重复下单");
        }



        return Result.ok(orderId);
    }

//    /**
//     * 优惠券秒杀下单
//     * @param voucherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId){
//        //1.查询优惠券信息
//        SeckillVoucher voucher=seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        LocalDateTime now=LocalDateTime.now();
//        if(voucher.getBeginTime().isAfter(now)){
//            return Result.fail("秒杀尚未开始");
//        }
//        //3.判断秒杀是否结束
//        if(voucher.getEndTime().isBefore(now)){
//            return Result.fail("秒杀已结束");
//        }
//        //4.判断库存是否充足
//        if(voucher.getStock()<1){
//            return Result.fail("库存不足");
//        }
//
//        //5.1 获取当前登录用户id
//        Long userId = UserHolder.getUser().getId();
//        //5.2 获取锁
//        // String key="order:"+userId;
//        // boolean isLock=simpleRedisLock.tryLock(key,1200, TimeUnit.SECONDS);
//
//        //使用Redisson创建锁对象
//        RLock lock=redissonClient.getLock("lock:order:"+userId);
//        //使用Redisson获取锁，参数分别为：等待时间、过期时间、时间单位
//        //-1表示无限等待，知道获得锁为止
//        boolean isLock;
//        try {
//            isLock = lock.tryLock(-1, TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        //5.3判断锁是否存在
//        if(!isLock){
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }catch (Exception e){
//            throw new RuntimeException(e);
//        }finally {
//            lock.unlock();
//        }
//    }
//
//    //创建订单
//    @Transactional
//    public Result createVoucherOrder(Long voucherId){
//        //5.一人一单
//        //获取用户登录id
//        Long userId= UserHolder.getUser().getId();
//
//        //查询订单
//        long count=lambdaQuery()
//                .eq(VoucherOrder::getUserId,userId)
//                .eq(VoucherOrder::getVoucherId,voucherId)
//                .count();
//
//        //订单存在过
//        if(count>0){
//            return Result.fail("领取次数过多");
//        }
//
//        //订单不存在，扣减库存
//        boolean success=seckillVoucherService.lambdaUpdate()
//                .setSql("stock=stock-1")
//                .eq(SeckillVoucher::getVoucherId,voucherId)
//                .gt(SeckillVoucher::getStock,0)
//                .update();
//        if(!success){
//            return Result.fail("库存不足");
//        }
//
//        //创建订单
//        VoucherOrder voucherOrder=new VoucherOrder();
//        //设置id
//        long id=redisIdWorker.nextId("order");
//        voucherOrder.setId(id);
//        //设置用户id
//        voucherOrder.setUserId(userId);
//        //设置优惠券id
//        voucherOrder.setVoucherId(voucherId);
//        //保存到数据库
//        save(voucherOrder);
//
//        //返回订单id
//        return Result.ok(voucherId);
//    }
}
