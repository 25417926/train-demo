<template>
  <p>
    <a-space>
      <a-button type="primary" @click="handleQuery()">刷新</a-button>
      <a-button type="primary" @click="onAdd">新增</a-button>
    </a-space>
  </p>
  <a-table :dataSource="train_seats"
           :columns="columns"
           :pagination="pagination"
           @change="handleTableChange"
           :loading="loading">
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'operation'">
        <a-space>
          <a-popconfirm
              title="删除后不可恢复，确认删除?"
              @confirm="onDelete(record.id)"
              ok-text="确认" cancel-text="取消">
            <a style="color: red">删除</a>
          </a-popconfirm>
          <a @click="onEdit(record)">编辑</a>
        </a-space>
      </template>
      <template v-else-if="column.dataIndex === 'col'">
        <span v-for="item in SEAT_COL_ARRAY" :key="item.code">
          <span v-if="item.code === record.col">
            {{item.desc}}
          </span>
        </span>
      </template>
    </template>
  </a-table>
  <a-modal v-model:visible="visible" title="座位" @ok="handleOk"
           ok-text="确认" cancel-text="取消">
    <a-form :model="train_seat" :label-col="{span: 4}" :wrapper-col="{ span: 20 }">
      <a-form-item label="车次编号">
        <a-input v-model:value="train_seat.trainCode" />
      </a-form-item>
      <a-form-item label="厢序">
        <a-input v-model:value="train_seat.carriageIndex" />
      </a-form-item>
      <a-form-item label="排号">
        <a-input v-model:value="train_seat.row" />
      </a-form-item>
      <a-form-item label="列号">
        <a-select v-model:value="train_seat.col">
          <a-select-option v-for="item in SEAT_COL_ARRAY" :key="item.type" :value="item.code">
            {{item.desc}}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="座位类型">
        <a-select v-model:value="train_seat.seatType">
          <a-select-option v-for="item in SEAT_TYPE_ARRAY" :key="item.code" :value="item.type">
            {{item.desc}}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="同车厢座序">
        <a-input v-model:value="train_seat.carriageSeatIndex" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script>
import { defineComponent, ref, onMounted } from 'vue';
import {message} from "ant-design-vue";
import {addtrain_seat, deletetrain_seat, gettrain_seatList} from "@/api/train_seat"

export default defineComponent({
  name: "train-seat-view",
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
    {
      title: '操作',
      dataIndex: 'operation'
    }
    ];

    const onAdd = () => {
      train_seat.value = {};
      visible.value = true;
    };

    const onEdit = (record) => {
      train_seat.value = window.Tool.copy(record);
      visible.value = true;
    };
    const onDelete = async (id) => {
      try {
        const res = await deletetrain_seat(id);
        if (res.code === 200) {
          message.success(res.message);
          handleQuery(); // 刷新数据
        }
      } catch (error) {
        message.error('删除失败');
      }
    };
    const handleOk = async () => {
      loading.value = true;
      try {
        const res = await addtrain_seat(train_seat.value);
        if (res.code === 200) {
          visible.value = false;
          message.success(res.message);
          handleQuery(); // 刷新数据
        } else {
          message.error('添加失败');
        }
      } catch (error) {
        message.error('添加失败');
      } finally {
        loading.value = false;
      }
    };

    const handleQuery = async (param = {page: 1, size: pagination.value.pageSize}) => {
      const res = await gettrain_seatList(param);
      if (res.code === 200) {
        train_seats.value = res.content.list;
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
      SEAT_COL_ARRAY,
      SEAT_TYPE_ARRAY,
      train_seat,
      visible,
      train_seats,
      pagination,
      columns,
      handleTableChange,
      handleQuery,
      loading,
      onAdd,
      handleOk,
      onEdit,
      onDelete
    };
  },
});
</script>
