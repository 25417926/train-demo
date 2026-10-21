  <template>
    <p>
      <a-space>
        <a-button type="primary" @click="handleQuery()">刷新</a-button>
      </a-space>
    </p>
    <a-table :dataSource="payments"
             :columns="columns"
             :pagination="pagination"
             @change="handleTableChange"
             :loading="loading">
    </a-table>
  </template>

  <script>
  import { defineComponent, ref, onMounted } from 'vue';
  import { getPaymentList } from "@/api/business/Payment";

  export default defineComponent({
    name: "payment-view",
    setup() {
      const payments = ref([]);
      const pagination = ref({
        total: 0,
        current: 1,
        pageSize: 10,
      });
      let loading = ref(false);
      const columns = [
        { title: '支付id', dataIndex: 'id', key: 'id' },
        { title: '订单id', dataIndex: 'orderId', key: 'orderId' },
        { title: '会员id', dataIndex: 'memberId', key: 'memberId' },
        { title: '金额', dataIndex: 'amount', key: 'amount' },
        { title: '渠道', dataIndex: 'payChannel', key: 'payChannel' },
        { title: '状态', dataIndex: 'payStatus', key: 'payStatus' },
        { title: '支付时间', dataIndex: 'payTime', key: 'payTime' },
        { title: '流水号', dataIndex: 'tradeNo', key: 'tradeNo' },
        { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
      ];

      const handleQuery = async (param = { page: 1, size: pagination.value.pageSize }) => {
        const res = await getPaymentList(param);
        if (res && res.code === 200) {
          payments.value = res.content.list;
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
        payments,
        pagination,
        columns,
        handleTableChange,
        handleQuery,
        loading,
      };
    },
  });
  </script>
