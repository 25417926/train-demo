<template>
  <p>
    <a-space>
      <TrainDropBox v-model="params.trainCode" style="width: 200px"/>
      <a-date-picker v-model:value="params.date" valueFormat="YYYY-MM-DD" placeholder="请选择日期"/>
      <RailWayPlatFormDrop v-model="params.start" style="width: 200px"/>
      <RailWayPlatFormDrop v-model="params.end" style="width: 200px"/>
      <a-button type="primary" @click="handleQuery()">刷新</a-button>
    </a-space>
  </p>
  <a-table :dataSource="daily_Train_Tickets"
           :columns="columns"
           :pagination="pagination"
           @change="handleTableChange"
           :loading="loading">
    <template #bodyCell="{ column,record}">
      <template v-if="column.dataIndex === 'station'">
        {{ record.start }}~{{ record.end }}
      </template>
      <template v-if="column.dataIndex === 'time'">
        {{ record.startTime }}~{{ record.endTime }}
      </template>

      <template v-if="column.dataIndex === 'duration'">
        {{ calDuration(record.startTime, record.endTime) }}<br>
        <div v-if="record.startTime.replace(':','') >=record.endTime.replace(':','')">
          次日到达
        </div>
        <div v-else>当日日到达</div>
      </template>
      <template v-if="column.dataIndex === 'ydz' ">
        <span v-if="record.ydz >0">
        {{ record.ydz }}<br>{{ record.ydzPrice }}￥
          </span>
        <span v-if="record.ydz <= 0 ">
        --
          </span>
      </template>
      <template v-if="column.dataIndex === 'edz' ">
         <span v-if="record.edz >0">
        {{ record.edz }}<br>{{ record.edzPrice }}￥
          </span>
        <span v-if="record.edz <= 0 ">
        --
          </span>
      </template>
      <template v-if="column.dataIndex === 'rw' ">
        <span v-if="record.rw > 0 ">
        {{ record.rw }}<br>{{ record.rwPrice }}￥
          </span>
        <span v-if="record.rw <= 0 ">
        --
          </span>

      </template>
      <template v-if="column.dataIndex === 'yw' ">
       <span v-if="record.yw > 0 ">
        {{ record.yw }}<br>{{ record.ywPrice }}￥
          </span>
        <span v-if="record.yw <= 0 ">
        --
          </span>

      </template>
    </template>
  </a-table>
</template>

<script>
import {defineComponent, ref, onMounted} from 'vue';

// import {message} from "ant-design-vue";
import {getDaily_Train_TicketList} from "@/api/Daily_Train_Ticket"
import TrainDropBox from "@/components/TrainDropBox.vue";
import RailWayPlatFormDrop from "@/components/RailWayPlatFormDrop.vue";
import dayjs from "dayjs";

export default defineComponent({
  name: "daily-train-ticket-view",
  components: {TrainDropBox, RailWayPlatFormDrop},
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
    const params = ref({
      trainCode: undefined,
      date: undefined,
      start: undefined,
      end: undefined,
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
        title: '车站',
        dataIndex: 'station',
        key: 'start',
      },
      {
        title: '时间',
        dataIndex: 'time',
        key: 'time',
      },
      {
        title: '历时',
        dataIndex: 'duration',
        key: 'duration',
      },
      {
        title: '一等座',
        dataIndex: 'ydz',
        key: 'ydz',
      },
      // {
      //   title: '一等座票价',
      //   dataIndex: 'ydzPrice',
      //   key: 'ydzPrice',
      // },
      {
        title: '二等座',
        dataIndex: 'edz',
        key: 'edz',
      },
      // {
      //   title: '二等座票价',
      //   dataIndex: 'edzPrice',
      //   key: 'edzPrice',
      // },
      {
        title: '软卧',
        dataIndex: 'rw',
        key: 'rw',
      },
      // {
      //   title: '软卧票价',
      //   dataIndex: 'rwPrice',
      //   key: 'rwPrice',
      // },
      {
        title: '硬卧',
        dataIndex: 'yw',
        key: 'yw',
      },
      // {
      //   title: '硬卧票价',
      //   dataIndex: 'ywPrice',
      //   key: 'ywPrice',
      // },
    ];
    const calDuration = (startTime, endTime) => {
      let diff = dayjs(endTime, 'HH:mm:ss').diff(dayjs(startTime, 'HH:mm:ss'));
      const send = Math.floor(diff / 1000)
      return dayjs('00:00:00', 'HH:mm:ss').second(send).format('HH:mm:ss');
    }
    const handleQuery = async (param = {
      page: 1,
      size: pagination.value.pageSize,
      code: params.value.trainCode,
      date: params.value.date,
      start: params.value.start,
      end: params.value.end
    }) => {
      const res = await getDaily_Train_TicketList(param);
      if (res.code === 200) {
        daily_Train_Tickets.value = res.content.list;
        pagination.value.total = res.content.total;
      }
    };

    const handleTableChange = (page) => {
      // console.log("看看自带的分页参数都有啥：" + JSON.stringify(page));
      pagination.value.current = page.current;
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
      params,
      calDuration
    };
  },
});
</script>
<style>

</style>