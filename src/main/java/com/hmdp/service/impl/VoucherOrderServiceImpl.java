package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWoker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

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
    private RedisIdWoker redisIdWoker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            //2.1不是0，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //2.2为0，有购买资格，把下单信息保存到阻塞队列
        long orderId = redisIdWoker.nextId("order");
        //TODO 保存阻塞队列

        //3返回订单id
        return Result.ok(orderId);

    }


/*    @Override
    public Result seckillVoucher(Long voucherId) {

        //1查询秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀未开始");
        }
        //3判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //已经结束
            return Result.fail("秒杀已结束");
        }
        //4库存是否充足
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足!");
        }

        Long userId = UserHolder.getUser().getId();
        //创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if(!isLock){
            //获取锁失败，返回错误信息或者重试
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return  proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }

    }*/

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5一人一单
        Long userId = UserHolder.getUser().getId();
        //5.1查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId)
                .count();
        //5.2判断是否存在
        if (count > 0) {
            //用户已经购买过了
            return Result.fail("用户已经购买过");
        }

        //6扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")//set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0)//where id = ? and stock > 0
                .update();
        if (!success) {
            //扣减失败
            return Result.fail("库存不足!");
        }

        //7创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1订单id
        long orderId = redisIdWoker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2用户id

        voucherOrder.setUserId(userId);
        //7.3代金券id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);

        //返回订单id
        return Result.ok(orderId);

    }
}
