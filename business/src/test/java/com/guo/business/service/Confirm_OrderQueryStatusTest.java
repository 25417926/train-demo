package com.guo.business.service;

import com.github.pagehelper.PageHelper;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.req.Confirm_OrderQueryReq;
import com.guo.business.resp.Confirm_OrderQueryResp;
import com.guo.common.resp.PageResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * I07 验收：我的订单查询同时返回 statusCode（原始码）与 status（中文描述，兼容旧前端）。
 */
class Confirm_OrderQueryStatusTest {

    @AfterEach
    void clearPage() {
        PageHelper.clearPage();
    }

    @Test
    void queryListReturnsStatusCodeAndKeepsStatusDesc() {
        Confirm_OrderMapper mapper = mock(Confirm_OrderMapper.class);
        Confirm_Order order = new Confirm_Order();
        order.setId(9001L);
        order.setMemberId(7L);
        order.setTrainCode("G1");
        order.setStatus("S");
        when(mapper.selectByExample(any())).thenReturn(Collections.singletonList(order));

        Confirm_OrderService service = new Confirm_OrderService();
        ReflectionTestUtils.setField(service, "confirm_OrderMapper", mapper);

        Confirm_OrderQueryReq req = new Confirm_OrderQueryReq();
        req.setPage(1);
        req.setSize(10);
        PageResp<Confirm_OrderQueryResp> resp = service.QueryList(req);

        assertEquals(1, resp.getList().size());
        Confirm_OrderQueryResp item = resp.getList().get(0);
        assertEquals("S", item.getStatusCode());
        assertEquals("成功", item.getStatus());
    }
}
