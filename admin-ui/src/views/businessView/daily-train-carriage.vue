<template>
  <p>
    <a-space>
      <a-button type="primary" @click="handleQuery()">刷新</a-button>
      <a-button type="primary" @click="onAdd">新增</a-button>
    </a-space>
  </p>
  <a-table :dataSource="daily_Train_Carriages"
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
      <template v-else-if="column.dataIndex === 'seatType'">
        <span v-for="item in SEAT_TYPE_ARRAY" :key="item.code">
          <span v-if="item.code === record.seatType">
            {{item.desc}}
          </span>
        </span>
      </template>
    </template>
  </a-table>
  <a-modal v-model:visible="visible" title="每日车厢" @ok="handleOk"
           ok-text="确认" cancel-text="取消">
    <a-form :model="daily_Train_Carriage" :label-col="{span: 4}" :wrapper-col="{ span: 20 }">
      <a-form-item label="日期">
        <a-date-picker v-model:value="daily_Train_Carriage.date" valueFormat="YYYY-MM-DD" placeholder="请选择日期" />
      </a-form-item>
      <a-form-item label="车次编号">
        <TrainDropBox v-model="daily_Train_Carriage.trainCode" />
      </a-form-item>
      <a-form-item label="箱序">
        <a-input v-model:value="daily_Train_Carriage.index" />
      </a-form-item>
      <a-form-item label="座位类型">
        <a-select v-model:value="daily_Train_Carriage.seatType">
          <a-select-option v-for="item in SEAT_TYPE_ARRAY" :key="item.code" :value="item.code">
            {{item.desc}}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="排数">
        <a-input v-model:value="daily_Train_Carriage.rowCount" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script>
import { defineComponent, ref, onMounted } from 'vue';
import {message} from "ant-design-vue";
import {addDaily_Train_Carriage, deleteDaily_Train_Carriage, getDaily_Train_CarriageList} from "@/api/business/Daily_Train_Carriage"
import TrainDropBox from "@/components/HomeComp/TrainDropBox.vue";

export default defineComponent({
  name: "daily-train-carriage-view",
  components: {TrainDropBox},
  setup() {
    const SEAT_TYPE_ARRAY = [
      { code: "1", desc: "一等座"},
      { code: "2", desc: "二等座" },
      { code: "3", desc: "软卧" },
      { code: "4", desc: "硬卧" }
    ];
    const visible = ref(false);
    let daily_Train_Carriage = ref({
      id: undefined,
      date: undefined,
      trainCode: undefined,
      index: undefined,
      seatType: undefined,
      seatCount: undefined,
      rowCount: undefined,
      colCount: undefined,
      createTime: undefined,
      updateTime: undefined,
    });
    const daily_Train_Carriages = ref([]);
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
      dataIndex: 'index',
      key: 'index',
    },
    {
      title: '座位类型',
      dataIndex: 'seatType',
      key: 'seatType',
    },
    {
      title: '座位数',
      dataIndex: 'seatCount',
      key: 'seatCount',
    },
    {
      title: '排数',
      dataIndex: 'rowCount',
      key: 'rowCount',
    },
    {
      title: '列数',
      dataIndex: 'colCount',
      key: 'colCount',
    },
    {
      title: '操作',
      dataIndex: 'operation'
    }
    ];

    const onAdd = () => {
      daily_Train_Carriage.value = {};
      visible.value = true;
    };

    const onEdit = (record) => {
      daily_Train_Carriage.value = window.Tool.copy(record);
      visible.value = true;
    };
    const onDelete = async (id) => {
      try {
        const res = await deleteDaily_Train_Carriage(id);
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
        const res = await addDaily_Train_Carriage(daily_Train_Carriage.value);
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
      const res = await getDaily_Train_CarriageList(param);
      if (res.code === 200) {
        daily_Train_Carriages.value = res.content.list;
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
      SEAT_TYPE_ARRAY,
      daily_Train_Carriage,
      visible,
      daily_Train_Carriages,
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
