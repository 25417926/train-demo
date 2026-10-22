  <template>
    <p>
      <a-space>
        <a-button type="primary" @click="handleQuery()">刷新</a-button>
      </a-space>
    </p>
    <a-table :dataSource="refunds"
             :columns="columns"
             :pagination="pagination"
             @change="handleTableChange"
             :loading="loading">
    </a-table>
  </template>

  <script>
  import { defineComponent, ref, onMounted } from 'vue';
  import { getRefundList } from "@/api/business/Refund";

  export default defineComponent({
    name: "refund-view",
    setup() {
      const refunds = ref([]);
      const pagination = ref({
        total: 0,
        current: 1,
        pageSize: 10,
      });
      let loading = ref(false);
      const columns = [
        { title: '退款id', dataIndex: 'id', key: 'id' },
        { title: '订单id', dataIndex: 'orderId', key: 'orderId' },
        { title: '支付id', dataIndex: 'paymentId', key: 'paymentId' },
        { title: '会员id', dataIndex: 'memberId', key: 'memberId' },
        { title: '退款金额', dataIndex: 'amount', key: 'amount' },
        { title: '原因', dataIndex: 'reason', key: 'reason' },
        { title: '状态', dataIndex: 'refundStatus', key: 'refundStatus' },
        { title: '退款时间', dataIndex: 'refundTime', key: 'refundTime' },
        { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
      ];

      const handleQuery = async (param = { page: 1, size: pagination.value.pageSize }) => {
        const res = await getRefundList(param);
        if (res && res.code === 200) {
          refunds.value = res.content.list;
          pagination.value.total = res.content.total;
        }
      };

      const handleTableChange = (page) => {
        pagination.value.pageSize = page.pageSize;
        handleQuery({ page: page.current, size: page.pageSize });
      };

      onMounted(() => {
        handleQuery({ page: 1, size: pagination.value.pageSize });
      });

      return {
        refunds,
        pagination,
        columns,
        handleTableChange,
        handleQuery,
        loading,
      };
    },
  });
  </script>
