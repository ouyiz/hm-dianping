package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.VoucherServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ShopServiceImplTest {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private IVoucherService voucherService;

    // 菜品预热测试
    @Test
    public void testSetWithLogicalExpire(){
        // 1.查询数据库
        Shop shop = shopService.getById(1L);
        // 2.加载菜品到redis
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L,shop,10L, TimeUnit.SECONDS);
    }

    // 添加秒杀券
    @Test
    public void testSeckill() {
        Voucher voucher = new Voucher();

        // 所属商铺
        voucher.setShopId(1L);

        // 优惠券基本信息
        voucher.setTitle("100元代金券");
        voucher.setSubTitle("周一至周日均可使用");
        voucher.setRules("全场通用");
        voucher.setPayValue(8000L);       // 支付80元，单位：分
        voucher.setActualValue(10000L);   // 抵扣100元，单位：分

        // 1表示秒杀券
        voucher.setType(1);

        // 秒杀库存
        voucher.setStock(10);

        // 秒杀开始、结束时间
        voucher.setBeginTime(LocalDateTime.now());
        voucher.setEndTime(LocalDateTime.now().plusDays(1));

        // 保存秒杀券
        voucherService.addSeckillVoucher(voucher);
    }
}
