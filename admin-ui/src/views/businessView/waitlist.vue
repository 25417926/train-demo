<template>
  <div>
    <p>
      <a-space wrap>
        <a-date-picker v-model:value="params.date" valueFormat="YYYY-MM-DD" placeholder="乘车日期" />
        <a-input v-model:value="params.trainCode" placeholder="车次编号" allow-clear style="width: 130px" />
        <a-input v-model:value="params.start" placeholder="出发站" allow-clear style="width: 120px" />
        <a-input v-model:value="params.end" placeholder="到达站" allow-clear style="width: 120px" />
        <a-select v-model:value="params.seatType" placeholder="席别" allow-clear style="width: 120px">
          <a-select-option v-for="item in SEAT_TYPES" :key="item.code" :value="item.code">
            {{ item.desc }}
          </a-select-option>
        </a-select>
        <a-select v-model:value="params.status" placeholder="状态" allow-clear style="width: 140px">
          <a-select-option v-for="item in WAITLIST_STATUS" :key="item.code" :value="item.code">
            {{ item.desc }}
          </a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery()">查询</a-button>
        <a-button @click="onReset">重置</a-button>
      </a-space>
    </p>

    <a-table :dataSource="applications"
             :columns="columns"
             :pagination="pagination"
             rowKey="id"
             @change="handleTableChange"
             :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'seatType'">
          {{ seatTypeDesc(record.seatType) }}
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.statusDesc || record.status }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'operation'">
          <a-popconfirm v-if="record.status === 'W'"
                        title="确认取消该候补申请？"
                        ok-text="确认"
                        cancel-text="取消"
                        @confirm="onCancel(record)">
            <a>人工取消</a>
          </a-popconfirm>
          <span v-else style="color: #999">—</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script>
import { defineComponent, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { cancelWaitlist, getWaitlistList } from '@/api/business/Waitlist';

export default defineComponent({
  name: 'waitlist-view',
  setup() {
    const applications = ref([]);
    const loading = ref(false);
    const pagination = ref({ total: 0, current: 1, pageSize: 10 });

    const SEAT_TYPES = [
      { code: '1', desc: '一等座' },
      { code: '2', desc: '二等座' },
      { code: '3', desc: '软卧' },
      { code: '4', desc: '硬卧' }
    ];

    const WAITLIST_STATUS = [
      { code: 'W', desc: '排队中' },
      { code: 'D', desc: '已匹配待支付' },
      { code: 'C', desc: '已取消' },
      { code: 'E', desc: '已过期' },
      { code: 'F', desc: '匹配失败' }
    ];

    const params = ref({
      date: undefined,
      trainCode: undefined,
      start: undefined,
      end: undefined,
      seatType: undefined,
      status: undefined
    });

    const columns = [
      { title: '会员id', dataIndex: 'memberId', key: 'memberId' },
      { title: '乘客', dataIndex: 'passengerName', key: 'passengerName' },
      { title: '日期', dataIndex: 'date', key: 'date' },
      { title: '车次编号', dataIndex: 'trainCode', key: 'trainCode' },
      { title: '出发站', dataIndex: 'start', key: 'start' },
      { title: '到达站', dataIndex: 'end', key: 'end' },
      { title: '席别', dataIndex: 'seatType', key: 'seatType' },
      { title: '排队号', dataIndex: 'seqNo', key: 'seqNo' },
      { title: '状态', dataIndex: 'status', key: 'status' },
      { title: '补位订单', dataIndex: 'matchedOrderId', key: 'matchedOrderId' },
      { title: '申请时间', dataIndex: 'createTime', key: 'createTime' },
      { title: '操作', dataIndex: 'operation' }
    ];

    const seatTypeDesc = (code) => {
      const item = SEAT_TYPES.find(type => type.code === code);
      return item ? item.desc : code;
    };

    const statusColor = (status) => {
      if (status === 'W') return 'blue';
      if (status === 'D') return 'green';
      if (status === 'F') return 'red';
      return 'default';
    };

    const handleQuery = async (param = {
      page: 1,
      size: pagination.value.pageSize
    }) => {
      loading.value = true;
      try {
        const res = await getWaitlistList({
          page: param.page,
          size: param.size,
          date: params.value.date,
          trainCode: params.value.trainCode,
          start: params.value.start,
          end: params.value.end,
          seatType: params.value.seatType,
          status: params.value.status
        });
        if (res && res.code === 200) {
          applications.value = res.content.list;
          pagination.value.total = res.content.total;
        }
      } finally {
        loading.value = false;
      }
    };

    const handleTableChange = (page) => {
      pagination.value.current = page.current;
      pagination.value.pageSize = page.pageSize;
      handleQuery({ page: page.current, size: page.pageSize });
    };

    const onReset = () => {
      params.value = {
        date: undefined,
        trainCode: undefined,
        start: undefined,
        end: undefined,
        seatType: undefined,
        status: undefined
      };
      pagination.value.current = 1;
      handleQuery();
    };

    const onCancel = async (record) => {
      const res = await cancelWaitlist(record.id);
      if (res && res.code === 200) {
        message.success('候补已取消');
        handleQuery({ page: pagination.value.current, size: pagination.value.pageSize });
      } else if (res && res.message) {
        message.error(res.message);
      }
    };

    onMounted(() => {
      handleQuery();
    });

    return {
      applications,
      loading,
      pagination,
      params,
      columns,
      SEAT_TYPES,
      WAITLIST_STATUS,
      seatTypeDesc,
      statusColor,
      handleQuery,
      handleTableChange,
      onReset,
      onCancel
    };
  }
});
</script>
