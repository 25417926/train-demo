  <template>
    <p>
      <a-space>
        <a-button type="primary" @click="handleQuery()">刷新</a-button>
        
      </a-space>
    </p>
    <a-table :dataSource="daily_Train_Tickets"
             :columns="columns"
             :pagination="pagination"
             @change="handleTableChange"
             :loading="loading">
      <template #bodyCell="{ column}">
        <template v-if="column.dataIndex === 'operation'">
        </template>
      </template>
    </a-table>
  </template>

  <script>
  import { defineComponent, ref, onMounted } from 'vue';
  // import {message} from "ant-design-vue";
  import {getDaily_Train_TicketList} from "@/api/business/Daily_Train_Ticket"
  export default defineComponent({
    name: "daily-train-ticket-view",
    setup() {
      const visible = ref(false);
      let daily_Train_Ticket = ref({
        id: undefined,
        date: undefined,
        trainCode: undefined,
        start: undefined,
        startPinyin: undefined,
        startTime: undefined,
        startIndex: undefined,
        end: undefined,
        endPinyin: undefined,
        endTime: undefined,
        endIndex: undefined,
        ydz: undefined,
        ydzPrice: undefined,
        edz: undefined,
        edzPrice: undefined,
        rw: undefined,
        rwPrice: undefined,
        yw: undefined,
        ywPrice: undefined,
        createTime: undefined,
        updateTime: undefined,
      });
      const daily_Train_Tickets = ref([]);
      // 分页的三个属性名是固定的
      const pagination = ref({
        total: 0,
        current: 1,
        pageSize: 10,
      });
      let loading = ref(false);
      const columns = [
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
        title: '出发站拼音',
        dataIndex: 'startPinyin',
        key: 'startPinyin',
      },
      {
        title: '出发时间',
        dataIndex: 'startTime',
        key: 'startTime',
      },
      {
        title: '出发站序',
        dataIndex: 'startIndex',
        key: 'startIndex',
      },
      {
        title: '到达站',
        dataIndex: 'end',
        key: 'end',
      },
      {
        title: '到达站拼音',
        dataIndex: 'endPinyin',
        key: 'endPinyin',
      },
      {
        title: '到站时间',
        dataIndex: 'endTime',
        key: 'endTime',
      },
      {
        title: '到站站序',
        dataIndex: 'endIndex',
        key: 'endIndex',
      },
      {
        title: '一等座余票',
        dataIndex: 'ydz',
        key: 'ydz',
      },
      {
        title: '一等座票价',
        dataIndex: 'ydzPrice',
        key: 'ydzPrice',
      },
      {
        title: '二等座余票',
        dataIndex: 'edz',
        key: 'edz',
      },
      {
        title: '二等座票价',
        dataIndex: 'edzPrice',
        key: 'edzPrice',
      },
      {
        title: '软卧余票',
        dataIndex: 'rw',
        key: 'rw',
      },
      {
        title: '软卧票价',
        dataIndex: 'rwPrice',
        key: 'rwPrice',
      },
      {
        title: '硬卧余票',
        dataIndex: 'yw',
        key: 'yw',
      },
      {
        title: '硬卧票价',
        dataIndex: 'ywPrice',
        key: 'ywPrice',
      },
      ];


      const handleQuery = async (param = {page: 1, size: pagination.value.pageSize}) => {
        const res = await getDaily_Train_TicketList(param);
        if (res.code === 200) {
          daily_Train_Tickets.value = res.content.list;
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
        daily_Train_Ticket,
        visible,
        daily_Train_Tickets,
        pagination,
        columns,
        handleTableChange,
        handleQuery,
        loading,
      };
    },
  });
  </script>
