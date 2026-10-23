<template>
  <p>
    <a-space>
      <a-button type="primary" @click="handleQuery()">刷新</a-button>
    </a-space>
  </p>
  <a-table :dataSource="orders"
           :columns="columns"
           :pagination="pagination"
           @change="handleTableChange"
           :loading="loading">
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'operation'">
        <a-space>
          <a v-if="record.status === '成功' || record.status === '候补待支付'" @click="onPay(record)">支付</a>
          <a v-if="record.status === '已支付'" style="color: orange" @click="onRefund(record)">退票</a>
          <a v-if="record.status === '已支付'" style="color: green" @click="onChange(record)">改签</a>
        </a-space>
      </template>
    </template>
  </a-table>

  <!-- 支付弹窗 -->
  <a-modal v-model:visible="payVisible" title="发起支付" @ok="handlePay" :confirm-loading="payLoading"
           ok-text="确认支付" cancel-text="取消">
    <a-form :model="payForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="订单号">
        <a-input v-model:value="payForm.orderId" disabled />
      </a-form-item>
      <a-form-item label="支付金额">
        <a-input-number v-model:value="payForm.amount" :min="0" style="width: 100%" />
      </a-form-item>
      <a-form-item label="支付渠道">
        <a-select v-model:value="payForm.payChannel">
          <a-select-option value="A">支付宝</a-select-option>
          <a-select-option value="W">微信</a-select-option>
          <a-select-option value="B">银行卡</a-select-option>
        </a-select>
      </a-form-item>
    </a-form>
  </a-modal>

  <!-- 退票弹窗 -->
  <a-modal v-model:visible="refundVisible" title="申请退票" @ok="handleRefund" :confirm-loading="refundLoading"
           ok-text="确认退票" cancel-text="取消">
    <a-form :model="refundForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="订单号">
        <a-input v-model:value="refundForm.orderId" disabled />
      </a-form-item>
      <a-form-item label="退票原因">
        <a-textarea v-model:value="refundForm.reason" :rows="3" placeholder="请输入退票原因" />
      </a-form-item>
    </a-form>
  </a-modal>

  <!-- 改签弹窗 -->
  <a-modal v-model:visible="changeVisible" title="申请改签" @ok="handleChange" :confirm-loading="changeLoading"
           width="600px"
           ok-text="确认改签" cancel-text="取消">
    <a-form :model="changeForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="原订单号">
        <a-input v-model:value="changeForm.originalOrderId" disabled />
      </a-form-item>
      <a-form-item label="新车次日期">
        <a-date-picker v-model:value="changeForm.newOrder.date" valueFormat="YYYY-MM-DD" style="width: 100%" />
      </a-form-item>
      <a-form-item label="新车次编号">
        <a-input v-model:value="changeForm.newOrder.trainCode" />
      </a-form-item>
      <a-form-item label="出发站">
        <a-input v-model:value="changeForm.newOrder.start" />
      </a-form-item>
      <a-form-item label="到达站">
        <a-input v-model:value="changeForm.newOrder.end" />
      </a-form-item>
      <a-form-item label="余票 ID">
        <a-input v-model:value="changeForm.newOrder.dailyTrainTicketId" />
      </a-form-item>
      <a-alert message="改签的新订单参数（车票、座位明细）需要按下单流程提供，演示版暂时只接受最简字段。" type="info" show-icon />
    </a-form>
  </a-modal>
</template>

<script>
import { defineComponent, ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { getMyOrderList } from '@/api/Order';
import { payOrder } from '@/api/payment';
import { applyRefund } from '@/api/refund';
import { applyChange } from '@/api/change';

export default defineComponent({
  name: 'my-order-view',
  setup() {
    const orders = ref([]);
    const pagination = ref({ total: 0, current: 1, pageSize: 10 });
    const loading = ref(false);
    const columns = [
      { title: '订单号', dataIndex: 'id', key: 'id' },
      { title: '会员id', dataIndex: 'memberId', key: 'memberId' },
      { title: '日期', dataIndex: 'date', key: 'date' },
      { title: '车次编号', dataIndex: 'trainCode', key: 'trainCode' },
      { title: '出发站', dataIndex: 'start', key: 'start' },
      { title: '到达站', dataIndex: 'end', key: 'end' },
      { title: '订单状态', dataIndex: 'status', key: 'status' },
      { title: '操作', dataIndex: 'operation' }
    ];

    /* 支付 */
    const payVisible = ref(false);
    const payLoading = ref(false);
    const payForm = ref({ orderId: undefined, amount: 0, payChannel: 'A' });
    const onPay = (record) => {
      payForm.value = { orderId: record.id, amount: 0, payChannel: 'A' };
      payVisible.value = true;
    };
    const handlePay = async () => {
      payLoading.value = true;
      try {
        const res = await payOrder(payForm.value);
        if (res && res.code === 200) {
          message.success('支付成功');
          payVisible.value = false;
          handleQuery();
        } else if (res) {
          message.error(res.message || '支付失败');
        }
      } finally {
        payLoading.value = false;
      }
    };

    /* 退票 */
    const refundVisible = ref(false);
    const refundLoading = ref(false);
    const refundForm = ref({ orderId: undefined, reason: '' });
    const onRefund = (record) => {
      refundForm.value = { orderId: record.id, reason: '' };
      refundVisible.value = true;
    };
    const handleRefund = async () => {
      refundLoading.value = true;
      try {
        const res = await applyRefund(refundForm.value);
        if (res && res.code === 200) {
          message.success('退票成功');
          refundVisible.value = false;
          handleQuery();
        } else if (res) {
          message.error(res.message || '退票失败');
        }
      } finally {
        refundLoading.value = false;
      }
    };

    /* 改签 */
    const changeVisible = ref(false);
    const changeLoading = ref(false);
    const changeForm = ref({
      originalOrderId: undefined,
      newOrder: {
        date: undefined,
        trainCode: undefined,
        start: undefined,
        end: undefined,
        dailyTrainTicketId: undefined,
        tickets: [],
        code: '',
        codeToken: ''
      }
    });
    const onChange = (record) => {
      changeForm.value = {
        originalOrderId: record.id,
        newOrder: {
          date: record.date,
          trainCode: record.trainCode,
          start: record.start,
          end: record.end,
          dailyTrainTicketId: record.dailyTrainTicketId,
          tickets: [],
          code: '',
          codeToken: ''
        }
      };
      changeVisible.value = true;
    };
    const handleChange = async () => {
      changeLoading.value = true;
      try {
        const res = await applyChange(changeForm.value);
        if (res && res.code === 200) {
          message.success('改签成功');
          changeVisible.value = false;
          handleQuery();
        } else if (res) {
          message.error(res.message || '改签失败');
        }
      } finally {
        changeLoading.value = false;
      }
    };

    const handleQuery = async (param = { page: 1, size: pagination.value.pageSize }) => {
      loading.value = true;
      try {
        const res = await getMyOrderList(param);
        if (res && res.code === 200) {
          orders.value = res.content.list;
          pagination.value.total = res.content.total;
        }
      } finally {
        loading.value = false;
      }
    };

    const handleTableChange = (page) => {
      pagination.value.current = page.current;
      handleQuery({ page: page.current, size: page.pageSize });
    };

    onMounted(() => {
      handleQuery({ page: 1, size: pagination.value.pageSize });
    });

    return {
      orders,
      pagination,
      columns,
      loading,
      handleQuery,
      handleTableChange,
      payVisible,
      payLoading,
      payForm,
      onPay,
      handlePay,
      refundVisible,
      refundLoading,
      refundForm,
      onRefund,
      handleRefund,
      changeVisible,
      changeLoading,
      changeForm,
      onChange,
      handleChange
    };
  }
});
</script>
