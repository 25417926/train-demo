package com.guo.business.waitlist;

import cn.hutool.core.util.StrUtil;
import com.guo.business.domain.Waitlist_Application;
import com.guo.business.mapper.Waitlist_ApplicationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * I16 退避重试推进器：每秒扫描所有 {@code waitlist:retry:*}，到点的申请按原
 * {@code seq_no} 放回主队列，保证 30s 退避后仍按 FIFO 顺序排队。
 *
 * <p>入队脚本自带幂等，多个实例同时推进不会重复计数。</p>
 */
@Component
@ConditionalOnProperty(prefix = "train.waitlist", name = "retry-enabled",
        havingValue = "true", matchIfMissing = true)
public class WaitlistRetryPromoter {

    private static final Logger LOG = LoggerFactory.getLogger(WaitlistRetryPromoter.class);

    private final Waitlist_ApplicationMapper waitlistMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> enqueueScript;
    private final int batch;

    public WaitlistRetryPromoter(Waitlist_ApplicationMapper waitlistMapper,
                                 StringRedisTemplate redis,
                                 @Value("${train.waitlist.retry-batch:100}") int batch) {
        this.waitlistMapper = waitlistMapper;
        this.redis = redis;
        this.batch = Math.max(batch, 1);
        this.enqueueScript = load("lua/waitlist_enqueue.lua");
    }

    @Scheduled(fixedDelayString = "${train.waitlist.retry-poll-ms:1000}",
            initialDelayString = "${train.waitlist.retry-initial-delay-ms:5000}")
    public void promote() {
        Set<String> buckets;
        try {
            // 用索引 Set 代替 KEYS 模式扫描：Redis 单线程下 KEYS 会全库遍历，
            // 实测每次阻塞 125~187ms，直接把这些时延叠加到所有受理请求上
            buckets = redis.opsForSet().members(WaitlistKey.retryIndex());
        } catch (RuntimeException e) {
            LOG.error("读取候补退避队列索引失败", e);
            return;
        }
        if (buckets == null || buckets.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String bucket : buckets) {
            if (StrUtil.isBlank(bucket)) {
                continue;
            }
            promoteBucket(bucket, WaitlistKey.retry(bucket), now);
        }
    }

    private void promoteBucket(String bucket, String retryKey, long now) {
        List<String> due;
        try {
            Set<String> members = redis.opsForZSet().rangeByScore(retryKey, 0, now, 0, batch);
            due = members == null ? Collections.emptyList() : new java.util.ArrayList<>(members);
        } catch (RuntimeException e) {
            LOG.error("读取候补退避队列失败 retryKey={}", retryKey, e);
            return;
        }
        for (String member : due) {
            Long applicationId;
            try {
                applicationId = Long.valueOf(member);
            } catch (NumberFormatException e) {
                redis.opsForZSet().remove(retryKey, member);
                continue;
            }
            Waitlist_Application application = waitlistMapper.selectById(applicationId);
            if (application == null || !"W".equals(application.getStatus())) {
                redis.opsForZSet().remove(retryKey, member);
                continue;
            }
            try {
                redis.execute(enqueueScript,
                        Arrays.asList(WaitlistKey.queue(bucket), WaitlistKey.count(bucket)),
                        String.valueOf(application.getSeqNo()), String.valueOf(applicationId));
                redis.opsForZSet().remove(retryKey, member);
                LOG.info("候补退避到期回队 applicationId={} seqNo={} bucket={}",
                        applicationId, application.getSeqNo(), bucket);
            } catch (RuntimeException e) {
                LOG.error("候补退避回队失败，等待下次扫描 applicationId={}", applicationId, e);
            }
        }
    }

    private static DefaultRedisScript<Long> load(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            String body = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(body, Long.class);
        } catch (IOException e) {
            throw new IllegalStateException("候补 Lua 脚本加载失败：" + path, e);
        }
    }
}
