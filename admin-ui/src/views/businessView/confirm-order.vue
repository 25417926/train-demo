  <template>
    <p>
      <a-space>
        <a-button type="primary" @click="handleQuery()">刷新</a-button>
        
      </a-space>
    </p>
    <a-table :dataSource="confirm_Orders"
             :columns="columns"
             :pagination="pagination"
             @change="handleTableChange"
             :loading="loading">
      <template #bodyCell="{ column}">
        <template v-if="column.dataIndex === 'operation'">
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <span v-for="item in CONFIRM_ORDER_STATUS_ARRAY" :key="item.code">
            <span v-if="item.code === record.status">
              {{item.desc}}
            </span>
          </span>
        </template>
      </template>
    </a-table>
  </template>

  <script>
  import { defineComponent, ref, onMounted } from 'vue';
  //import {message} from "ant-design-vue";
  import {getConfirm_OrderList} from "@/api/business/Confirm_Order"
  export default defineComponent({
    name: "confirm-order-view",
    setup() {
      const CONFIRM_ORDER_STATUS_ARRAY = window.CONFIRM_ORDER_STATUS_ARRAY;
      const visible = ref(false);
      let confirm_Order = ref({
        id: undefined,
        memberId: undefined,
        date: undefined,
        trainCode: undefined,
        start: undefined,
        end: undefined,
        dailyTrainTicketId: undefined,
        tickets: undefined,
        status: undefined,
        createTime: undefined,
        updateTime: undefined,
      });
      const confirm_Orders = ref([]);
      // 分页的三个属性名是固定的
      const pagination = ref({
        total: 0,
        current: 1,
        pageSize: 10,
      });
      let loading = ref(false);
      const columns = [
      {
        title: '会员id',
        dataIndex: 'memberId',
        key: 'memberId',
      },
      {
        title: '日期',
        dataIndex: 'date',
        key: 'date',
      },
      {
        title: '车次编号',
        dataIndex: 'trainCode',
        key: 'trainCode',
      },
      {
        title: '出发站',
        dataIndex: 'start',
        key: 'start',
      },
      {
        title: '到达站',
        dataIndex: 'end',
        key: 'end',
      },
      {
        title: '余票ID',
        dataIndex: 'dailyTrainTicketId',
        key: 'dailyTrainTicketId',
      },
      {
        title: '车票',
        dataIndex: 'tickets',
        key: 'tickets',
      },
      {
        title: '订单状态',
        dataIndex: 'status',
        key: 'status',
      },
      ];


      const handleQuery = async (param = {page: 1, size: pagination.value.pageSize}) => {
        const res = await getConfirm_OrderList(param);
        if (res.code === 200) {
          confirm_Orders.value = res.content.list;
          pagination.value.total = res.total;
        }
      };

      const handleTableChange = (page) => {
        // console.log("看看自带的分页参数都有啥：" + JSON.stringify(page));
        pagination.value.pageSize = page.pageSize;
        handleQuery({
          page: page.current,
          size: page.pageSize
        });
      };

      onMounted(() => {
        handleQuery({
          page: 1,
          size: pagination.value.pageSize
        });
      });

      return {
        CONFIRM_ORDER_STATUS_ARRAY,
        confirm_Order,
        visible,
        confirm_Orders,
        pagination,
        columns,
        handleTableChange,
        handleQuery,
        loading,
      };
    },
  });
  </script>
