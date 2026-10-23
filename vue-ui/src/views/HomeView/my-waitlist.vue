<template>
  <div class="my-waitlist">
    <p>
      <a-space>
        <a-button type="primary" @click="handleQuery(true)">刷新</a-button>
        <a-select v-model:value="statusFilter" style="width: 160px" allow-clear placeholder="全部状态"
                  @change="handleQuery(true)">
          <a-select-option v-for="item in WAITLIST_STATUS" :key="item.code" :value="item.code">
            {{ item.desc }}
          </a-select-option>
        </a-select>
        <span class="refresh-tip">每 5 秒自动刷新</span>
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
          <a-space>
            <a v-if="record.status === 'W'" @click="onCancel(record)">取消候补</a>
            <a v-if="record.status === 'D'" @click="onPay">去支付</a>
            <span v-if="record.status !== 'W' && record.status !== 'D'" class="muted">—</span>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script>
import { defineComponent, onBeforeUnmount, onMounted, ref } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import { cancelWaitlist, getMyWaitlist } from '@/api/waitlist';

const REFRESH_INTERVAL_MS = 5000;

export default defineComponent({
  name: 'my-waitlist-view',
  setup() {
    const router = useRouter();
    const applications = ref([]);
    const loading = ref(false);
    const refreshing = ref(false);
    const statusFilter = ref(undefined);
    const pagination = ref({ total: 0, current: 1, pageSize: 10 });

    const WAITLIST_STATUS = [
      { code: 'W', desc: '排队中' },
      { code: 'D', desc: '已匹配待支付' },
      { code: 'C', desc: '已取消' },
      { code: 'E', desc: '已过期' },
      { code: 'F', desc: '匹配失败' }
    ];

    const SEAT_TYPE_DESC = { '1': '一等座', '2': '二等座', '3': '软卧', '4': '硬卧' };

    const columns = [
      { title: '日期', dataIndex: 'date', key: 'date' },
      { title: '车次编号', dataIndex: 'trainCode', key: 'trainCode' },
      { title: '出发站', dataIndex: 'start', key: 'start' },
      { title: '到达站', dataIndex: 'end', key: 'end' },
      { title: '席别', dataIndex: 'seatType', key: 'seatType' },
      { title: '排队号', dataIndex: 'seqNo', key: 'seqNo' },
      { title: '候补状态', dataIndex: 'status', key: 'status' },
      { title: '申请时间', dataIndex: 'createTime', key: 'createTime' },
      { title: '操作', dataIndex: 'operation' }
    ];

    const seatTypeDesc = (code) => SEAT_TYPE_DESC[code] || code;
    const statusColor = (status) => {
      if (status === 'W') return 'blue';
      if (status === 'D') return 'green';
      if (status === 'F') return 'red';
      return 'default';
    };

    const fetchList = async (showLoading) => {
      if (refreshing.value) {
        return;
      }
      refreshing.value = true;
      if (showLoading) {
        loading.value = true;
      }
      try {
        const res = await getMyWaitlist({
          page: pagination.value.current,
          size: pagination.value.pageSize,
          status: statusFilter.value
        });
        if (res && res.code === 200 && res.content) {
          applications.value = res.content.list;
          pagination.value.total = res.content.total;
        } else if (res && res.message && showLoading) {
          message.error(res.message);
        }
      } finally {
        refreshing.value = false;
        loading.value = false;
      }
    };

    let timer = null;
    const handleQuery = (showLoading) => {
      pagination.value.current = 1;
      fetchList(showLoading);
    };

    const handleTableChange = (page) => {
      pagination.value.current = page.current;
      pagination.value.pageSize = page.pageSize;
      fetchList(true);
    };

    const onCancel = (record) => {
      Modal.confirm({
        title: '确认取消该候补申请？',
        content: `${record.date} ${record.trainCode} ${record.start}~${record.end} ${seatTypeDesc(record.seatType)}`,
        okText: '确认取消',
        cancelText: '再想想',
        onOk: async () => {
          const res = await cancelWaitlist(record.id);
          if (res && res.code === 200) {
            message.success('候补已取消');
            fetchList(true);
          } else if (res && res.message) {
            message.error(res.message);
          }
        }
      });
    };

    const onPay = () => {
      router.push('/my-order');
    };

    onMounted(() => {
      fetchList(true);
      timer = setInterval(() => fetchList(false), REFRESH_INTERVAL_MS);
    });

    onBeforeUnmount(() => {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    });

    return {
      applications,
      columns,
      pagination,
      loading,
      statusFilter,
      WAITLIST_STATUS,
      seatTypeDesc,
      statusColor,
      handleQuery,
      handleTableChange,
      onCancel,
      onPay
    };
  }
});
</script>

<style scoped>
.refresh-tip {
  color: #999;
}

.muted {
  color: #999;
}
</style>
