package com.example.plainredis.service.impl;

import com.example.plainredis.service.SecondKillService;
import org.redisson.RedissonLock;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.plainredis.utils.CodeUtils.generateCode;

/**
 * @author aloneMan
 * @projectName plain-redis
 * @createTime 2022-08-23 20:40:04
 * @description
 */

@Service
public class SecondKillServiceImpl implements SecondKillService {


    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private RedisTemplate redisTemplate;

    private ValueOperations valueOperations;

    private SetOperations setOperations;

    private RedissonClient redissonClient;

    public SecondKillServiceImpl(RedisTemplate redisTemplate, RedissonClient redissonClient) {
        this.valueOperations = redisTemplate.opsForValue();
        this.setOperations = redisTemplate.opsForSet();
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
    }

    @Override
    public void setInStock(Long number) {
        String s = generateCode();
        String shopNumberKey = new StringBuffer().append("commodity").append("9806").append(":id").toString();
        valueOperations.set(shopNumberKey, Long.toString(number));
    }

    @SuppressWarnings("AlibabaTransactionMustHaveRollback")
    @Override
//    @Transactional
    public void killShop(String userId, String shopKey) {
        List result = (List) redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.watch(shopKey);
                String commodityNum = (String) operations.opsForValue().get(shopKey);
                //??????????????????
                if (commodityNum == null) {
                    System.out.println("??????????????????");
                } else if (Long.parseLong(commodityNum) <= 0) {
                    System.out.println("????????????");
                } else if (operations.opsForSet().isMember("userId", userId)) {
                    System.out.println("????????????????????????");
                } else {
                    try {
                        operations.multi();
                        operations.opsForSet().add("userId", userId);
                        operations.opsForValue().decrement(shopKey);
                    } catch (Exception e) {
                        System.out.println("????????????");
                    } finally {
                        return operations.exec();
                    }
                }
                return null;
            }
        });
        if (result != null) {
            System.out.println("????????????");
        }
    }

    @Override
    public String lockKillShop(String userId, String commodityKey) {

        Long offset = Long.parseLong(userId);
        if (valueOperations.get("inStock") == null) {
            System.out.println("?????????????????????");
            return "?????????????????????";
        }
        Boolean toByUser = valueOperations.getBit("ToByUser", offset);
        if (toByUser) {
            System.out.println(atomicInteger.incrementAndGet());
            return "??????????????????";
        }
        //?????????
        RLock commodityLock = redissonClient.getLock("commodityLock");
        try {
            boolean b = commodityLock.tryLock(10000, TimeUnit.MILLISECONDS);
            //???????????????
            if (b) {
                //?????????????????????
                String inStock = (String) valueOperations.get("inStock");
                if (Integer.parseInt(inStock) == 0) {
                    //????????????????????????,?????????????????????????????????
//                    valueOperations.getAndExpire("inStock", 10, TimeUnit.MINUTES);
//                    valueOperations.getAndExpire("ToByUser", 10, TimeUnit.MINUTES);
                    System.out.println("????????????");
                    return "????????????,????????????";
                }
                valueOperations.decrement("inStock");
                //??????????????????
                valueOperations.setBit("ToByUser", offset, true);
                return "????????????";
            } else {
                System.out.println(atomicInteger.incrementAndGet());
                return "????????????,????????????";
            }
        }/* catch (InterruptedException e) {
            throw new RuntimeException(e);
        }*/ catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (commodityLock.isLocked() && commodityLock.isHeldByCurrentThread()) {
                //?????????
//            System.out.println("?????????");
                commodityLock.unlock();
            }
        }
    }

}
