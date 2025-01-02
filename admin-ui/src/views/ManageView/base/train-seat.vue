<template>
  <p>
    <a-space>
      <TrainDropBox v-model="params.trainCode" style="width: 200px"/>
      <a-button type="primary" @click="handleQuery()">查找</a-button>

    </a-space>
  </p>
  <a-table :dataSource="train_seats"
           :columns="columns"
           :pagination="pagination"
           @change="handleChange"
           :loading="loading">
  </a-table>

</template>

<script>
import { defineComponent, ref, onMounted } from 'vue';
import {message} from "ant-design-vue";
import { gettrain_seatList} from "@/api/train_seat"
import TrainDropBox from "@/components/HomeComp/TrainDropBox.vue";

export default defineComponent({
  name: "train-seat-view",
  components: {TrainDropBox},
  setup() {
    const SEAT_COL_ARRAY = [
      { type: "1", desc: "A",code: "A"},
      { type: "1", desc: "C",code: "C"},
      { type: "1", desc: "D",code: "D"},
      { type: "1", desc: "F",code: "F"},
      { type: "2", desc: "A",code: "A"},
      { type: "2", desc: "B",code: "B"},
      { type: "2", desc: "C",code: "C"},
      { type: "2", desc: "D",code: "D"},
      { type: "2", desc: "F",code: "F"},
    ];
    const SEAT_TYPE_ARRAY = [
      { type: "1", desc: "一等座"},
      { type: "2", desc: "二等座" },
      { type: "3", desc: "软卧" },
      { type: "4", desc: "硬卧" }
    ];
    const params = ref({
      trainCode:undefined
    });
    const visible = ref(false);
    let train_seat = ref({
      id: undefined,
      trainCode: undefined,
      carriageIndex: undefined,
      row: undefined,
      col: undefined,
      seatType: undefined,
      carriageSeatIndex: undefined,
      createTime: undefined,
      updateTime: undefined,
    });
    const train_seats = ref([]);
    // 分页的三个属性名是固定的
    const pagination = ref({
      total: 0,
      current: 1,
      pageSize: 10,
    });
    let loading = ref(false);
    const columns = [
    {
      title: '车次编号',
      dataIndex: 'trainCode',
      key: 'trainCode',
    },
    {
      title: '厢序',
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
      title: '同车厢座序',
      dataIndex: 'carriageSeatIndex',
      key: 'carriageSeatIndex',
    },
    ];

    const handleQuery = async (param = {page: 1, size: pagination.value.pageSize, trainCode: params.value.trainCode}) => {
      const res = await gettrain_seatList(param);
      if (res.code === 200) {
        console.log(res);
        train_seats.value = res.content.list;
        pagination.value.total = res.content.total;
        console.log(pagination.value)
        message.success("查询成功");
      }
    };

    const handleChange = (page) => {
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
      SEAT_COL_ARRAY,
      SEAT_TYPE_ARRAY,
      train_seat,
      visible,
      train_seats,
      pagination,
      columns,
      handleChange,
      handleQuery,
      loading,
      params
    };
  },
});
</script>
<style>
.ant-pagination-options{
  display: none;
}
</style>