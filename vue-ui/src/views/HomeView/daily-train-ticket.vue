<template>
  <p>
    <a-space>
      <a-date-picker v-model:value="params.date" valueFormat="YYYY-MM-DD" placeholder="请选择日期"/>
      <RailWayPlatFormDrop v-model="params.start" style="width: 200px"/>
      <RailWayPlatFormDrop v-model="params.end" style="width: 200px"/>
      <a-button type="primary" @click="handleQuery()">查询</a-button>
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
        <div v-if="record.ydzWaitlist > 0" style="color:#fa8c16">候补{{ record.ydzWaitlist }}人</div>
      </template>
      <template v-if="column.dataIndex === 'edz' ">
         <span v-if="record.edz >0">
        {{ record.edz }}<br>{{ record.edzPrice }}￥
          </span>
        <span v-if="record.edz <= 0 ">
        --
          </span>
        <div v-if="record.edzWaitlist > 0" style="color:#fa8c16">候补{{ record.edzWaitlist }}人</div>
      </template>
      <template v-if="column.dataIndex === 'rw' ">
        <span v-if="record.rw > 0 ">
        {{ record.rw }}<br>{{ record.rwPrice }}￥
          </span>
        <span v-if="record.rw <= 0 ">
        --
          </span>
        <div v-if="record.rwWaitlist > 0" style="color:#fa8c16">候补{{ record.rwWaitlist }}人</div>

      </template>
      <template v-if="column.dataIndex === 'yw' ">
       <span v-if="record.yw > 0 ">
        {{ record.yw }}<br>{{ record.ywPrice }}￥
          </span>
        <span v-if="record.yw <= 0 ">
        --
          </span>
        <div v-if="record.ywWaitlist > 0" style="color:#fa8c16">候补{{ record.ywWaitlist }}人</div>
      </template>
      <template v-if="column.dataIndex === 'operation'">
        <a-space>
          <a-button type="primary" @click="onOrder(record)">预定</a-button>
          <a-button @click="onWaitlist(record)">候补</a-button>
        </a-space>
      </template>
    </template>
  </a-table>

  <!-- 候补申请入口 -->
  <a-modal v-model:visible="waitlistVisible"
           title="申请候补"
           ok-text="提交申请"
           cancel-text="取消"
           :confirm-loading="waitlistLoading"
           @ok="handleWaitlist">
    <a-descriptions :column="2" size="small" bordered style="margin-bottom: 16px">
      <a-descriptions-item label="日期">{{ waitlistForm.date }}</a-descriptions-item>
      <a-descriptions-item label="车次">{{ waitlistForm.trainCode }}</a-descriptions-item>
      <a-descriptions-item label="区间">{{ waitlistForm.start }} ~ {{ waitlistForm.end }}</a-descriptions-item>
    </a-descriptions>
    <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
      <a-form-item label="席别" required>
        <a-select v-model:value="waitlistForm.seatType" placeholder="请选择候补席别" style="width: 100%">
          <a-select-option v-for="item in waitlistSeatOptions" :key="item.code" :value="item.code">
            {{ item.label }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="乘车人" required>
        <a-select v-model:value="waitlistForm.passengerId" placeholder="请选择乘车人" style="width: 100%"
                  :not-found-content="'暂无乘车人，请先到乘车人管理添加'">
          <a-select-option v-for="item in passengers" :key="item.id" :value="item.id">
            {{ item.name }}（{{ item.idCard }}）
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-alert message="按申请先后排队，有票释放后自动补位，补位订单需在 5 分钟内完成支付。"
               type="info" show-icon />
    </a-form>
  </a-modal>
</template>

<script>
import {defineComponent, ref, onMounted} from 'vue';

// import {message} from "ant-design-vue";
import {getDaily_Train_TicketList} from "@/api/Daily_Train_Ticket"
import {queryMinePassenger} from "@/api/passenger";
import {applyWaitlist} from "@/api/waitlist";
import RailWayPlatFormDrop from "@/components/RailWayPlatFormDrop.vue";
import dayjs from "dayjs";
import {message} from "ant-design-vue";
import {useRouter} from "vue-router";
import { useStore } from 'vuex';

const WAITLIST_SEAT_TYPES = [
  { code: "1", desc: "一等座", field: "ydz" },
  { code: "2", desc: "二等座", field: "edz" },
  { code: "3", desc: "软卧", field: "rw" },
  { code: "4", desc: "硬卧", field: "yw" }
];
export default defineComponent({
  name: "daily-train-ticket-view",
  components: {RailWayPlatFormDrop},
  setup() {
    const store =useStore()
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
      {
        title: '操作',
        dataIndex: 'operation',
        key: 'operation',
      },

    ];
    const DailyTicker = ref({})
    const router = useRouter();

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
      if (window.Tool.isEmpty(param.date)) {
        message.error("日期不能为空")
        return;
      }
      if (window.Tool.isEmpty(param.start)) {
        message.error("初始站不能为空")
        return;
      }
      if (window.Tool.isEmpty(param.end)) {
        message.error("终点站不能为空")
        return;
      }

      const res = await getDaily_Train_TicketList(param);
      if (res.code === 200) {
        store.commit('Session_params/set_params', param )
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
    const onOrder = (record) => {
      DailyTicker.value = window.Tool.copy(record)
      store.commit('Session_params/set_order', DailyTicker.value )
      console.log("开始预定：" + JSON.stringify(record));
      console.log(router)

      router.push('/Order')

    }

    /* 候补申请入口 */
    const waitlistVisible = ref(false);
    const waitlistLoading = ref(false);
    const waitlistSeatOptions = ref([]);
    const passengers = ref([]);
    const waitlistForm = ref({
      date: undefined,
      trainCode: undefined,
      start: undefined,
      end: undefined,
      seatType: undefined,
      passengerId: undefined
    });

    const loadPassengers = async () => {
      const res = await queryMinePassenger();
      if (res && res.code === 200) {
        passengers.value = res.content || [];
      }
    };

    const onWaitlist = async (record) => {
      waitlistForm.value = {
        date: record.date,
        trainCode: record.trainCode,
        start: record.start,
        end: record.end,
        seatType: undefined,
        passengerId: undefined
      };
      waitlistSeatOptions.value = WAITLIST_SEAT_TYPES.map(item => {
        const count = Number(record[item.field]);
        let label = item.desc;
        if (!Number.isNaN(count)) {
          label = count > 0 ? `${item.desc}（余 ${count} 张）` : `${item.desc}（无票）`;
        }
        return { code: item.code, label };
      });
      waitlistVisible.value = true;
      if (passengers.value.length === 0) {
        await loadPassengers();
      }
    };

    const handleWaitlist = async () => {
      if (!waitlistForm.value.seatType) {
        message.error("请选择候补席别");
        return;
      }
      if (!waitlistForm.value.passengerId) {
        message.error("请选择乘车人");
        return;
      }
      waitlistLoading.value = true;
      try {
        const res = await applyWaitlist(waitlistForm.value);
        if (res && res.code === 200) {
          const seqNo = res.content && res.content.seqNo ? `，排队号 ${res.content.seqNo}` : "";
          message.success(`${res.message || "候补申请成功"}${seqNo}`);
          waitlistVisible.value = false;
        } else if (res) {
          message.error(res.message || "候补申请失败");
        }
      } finally {
        waitlistLoading.value = false;
      }
    };

    onMounted(() => {
      params.value =store.state.Session_params.params || {}
      if(window.Tool.isNotEmpty(params.value)){
        handleQuery({
          page: 1,
          size: pagination.value.pageSize,
          code: params.value.trainCode,
          date: params.value.date,
          start: params.value.start,
          end: params.value.end
        });
      }

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
      calDuration,
      onOrder,
      DailyTicker,
      waitlistVisible,
      waitlistLoading,
      waitlistSeatOptions,
      waitlistForm,
      passengers,
      onWaitlist,
      handleWaitlist
    };
  },
});
</script>
<style>

</style>