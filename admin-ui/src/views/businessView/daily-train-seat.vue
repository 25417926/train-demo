<template>
  <p>
    <a-space>
      <TrainDropBox v-model="params.trainCode" style="width: 200px"/>
      <a-button type="primary" @click="handleQuery()">查找</a-button>

    </a-space>
  </p>
  <a-table :dataSource="daily_Train_Seats"
           :columns="columns"
           :pagination="pagination"
           @change="handleTableChange"
           :loading="loading">
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'operation'">
      </template>
      <template v-else-if="column.dataIndex === 'col'">
        <span v-for="item in SEAT_COL_ARRAY" :key="item.code">
          <span v-if="item.code === record.col && item.type === record.seatType">

              {{ item.desc }}

          </span>
        </span>
      </template>
      <template v-else-if="column.dataIndex === 'seatType'">
        <span v-for="item in SEAT_TYPE_ARRAY" :key="item.code">
          <span v-if="item.type === record.seatType">
            {{ item.desc }}
          </span>
        </span>
      </template>
    </template>
  </a-table>
</template>

<script>
import {defineComponent, ref, onMounted} from 'vue';
import {message} from "ant-design-vue";
import { getDaily_Train_SeatList} from "@/api/business/Daily_Train_Seat"
import TrainDropBox from "@/components/HomeComp/TrainDropBox.vue";

export default defineComponent({
  name: "daily-train-seat-view",
  components: {TrainDropBox},
  setup() {
    const SEAT_COL_ARRAY = [
      {type: "1", desc: "A", code: "A"},
      {type: "1", desc: "C", code: "C"},
      {type: "1", desc: "D", code: "D"},
      {type: "1", desc: "F", code: "F"},
      {type: "2", desc: "A", code: "A"},
      {type: "2", desc: "B", code: "B"},
      {type: "2", desc: "C", code: "C"},
      {type: "2", desc: "D", code: "D"},
      {type: "2", desc: "F", code: "F"},
    ];
    const SEAT_TYPE_ARRAY = [
      {type: "1", desc: "一等座"},
      {type: "2", desc: "二等座"},
      {type: "3", desc: "软卧"},
      {type: "4", desc: "硬卧"}
    ];
    const visible = ref(false);
    const params = ref({
      trainCode: undefined,
    })
    let daily_Train_Seat = ref({
      id: undefined,
      date: undefined,
      trainCode: undefined,
      carriageIndex: undefined,
      row: undefined,
      col: undefined,
      seatType: undefined,
      carriageSeatIndex: undefined,
      sell: undefined,
      createTime: undefined,
      updateTime: undefined,
    });
    const daily_Train_Seats = ref([]);
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
        title: '箱序',
        dataIndex: 'carriageIndex',
        key: 'carriageIndex',
      },
      {
        title: '排号',
        dataIndex: 'row',
        key: 'row',
      },
      {
        title: '列号',
        dataIndex: 'col',
        key: 'col',
      },
      {
        title: '座位类型',
        dataIndex: 'seatType',
        key: 'seatType',
      },
      {
        title: '同车箱座序',
        dataIndex: 'carriageSeatIndex',
        key: 'carriageSeatIndex',
      },
      {
        title: '售卖情况',
        dataIndex: 'sell',
        key: 'sell',
      },
    ];


    const handleQuery = async (param = {page: 1, size: pagination.value.pageSize, code: params.value.trainCode,}) => {
      try {
        const res = await getDaily_Train_SeatList(param);
        if (res.code === 200 ) {
          daily_Train_Seats.value = res.content.list;
          pagination.value.total = res.total;
          message.success("查询" + res.message)
        }

      }catch (e){
        message.error("查询失败")
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
      SEAT_COL_ARRAY,
      SEAT_TYPE_ARRAY,
      daily_Train_Seat,
      visible,
      daily_Train_Seats,
      pagination,
      columns,
      handleTableChange,
      handleQuery,
      loading,
      params
    };
  },
});
</script>
