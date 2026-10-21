  <template>
    <p>
      <a-space>
        <a-button type="primary" @click="handleQuery()">刷新</a-button>
      </a-space>
    </p>
    <a-table :dataSource="changeRecords"
             :columns="columns"
             :pagination="pagination"
             @change="handleTableChange"
             :loading="loading">
    </a-table>
  </template>

  <script>
  import { defineComponent, ref, onMounted } from 'vue';
  import { getChangeRecordList } from "@/api/business/ChangeRecord";

  export default defineComponent({
    name: "change-record-view",
    setup() {
      const changeRecords = ref([]);
      const pagination = ref({
        total: 0,
        current: 1,
        pageSize: 10,
      });
      let loading = ref(false);
      const columns = [
        { title: '记录id', dataIndex: 'id', key: 'id' },
        { title: '原订单id', dataIndex: 'originalOrderId', key: 'originalOrderId' },
        { title: '新订单id', dataIndex: 'newOrderId', key: 'newOrderId' },
        { title: '会员id', dataIndex: 'memberId', key: 'memberId' },
        { title: '类型', dataIndex: 'changeType', key: 'changeType' },
        { title: '状态', dataIndex: 'changeStatus', key: 'changeStatus' },
        { title: '手续费', dataIndex: 'fee', key: 'fee' },
        { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
      ];

      const handleQuery = async (param = { page: 1, size: pagination.value.pageSize }) => {
        const res = await getChangeRecordList(param);
        if (res && res.code === 200) {
          changeRecords.value = res.content.list;
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
        changeRecords,
        pagination,
        columns,
        handleTableChange,
        handleQuery,
        loading,
      };
    },
  });
  </script>
