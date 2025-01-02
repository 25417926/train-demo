<template>
  <p>
    <a-space>
      <a-button type="primary" @click="handleQuery()">刷新</a-button>
      <a-button type="primary" @click="onAdd">新增</a-button>
    </a-space>
  </p>
  <a-table :dataSource="trains"
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
          <a-popconfirm
              title="生成座位会清除已有记录，确认生成?"
              @confirm="OnGenSeat(record.code)"
              ok-text="确认" cancel-text="取消">
            <a style="color: yellowgreen">生成</a>
          </a-popconfirm>
        </a-space>
      </template>

    </template>
  </a-table>
  <a-modal v-model:visible="visible" title="车次" @ok="handleOk"
           ok-text="确认" cancel-text="取消">
    <a-form :model="train" :label-col="{span: 4}" :wrapper-col="{ span: 20 }">
      <a-form-item label="车次编号">
        <a-input v-model:value="train.code" :disabled="!!train.id"/>
      </a-form-item>
      <a-form-item label="车次类型">
        <a-select v-model:value="train.type" :disabled="!!train.id">
          <a-select-option v-for="item in TRAIN_TYPE_ARRAY" :key="item.type" :value="item.type" >
            {{item.type}}|{{item.desc}}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="始发站">

        <RailWayPlatFormDrop v-model="train.start"/>
      </a-form-item>
      <a-form-item label="始发站拼音">
        <a-input v-model:value="train.startPinyin" disabled/>
      </a-form-item>
      <a-form-item label="出发时间">
        <a-time-picker v-model:value="train.startTime" valueFormat="HH:mm:ss" placeholder="请选择时间" />
      </a-form-item>
      <a-form-item label="终点站">
        <RailWayPlatFormDrop v-model="train.end"/>
      </a-form-item>
      <a-form-item label="终点站拼音">
        <a-input v-model:value="train.endPinyin" disabled/>
      </a-form-item>
      <a-form-item label="到站时间">
        <a-time-picker v-model:value="train.endTime" valueFormat="HH:mm:ss" placeholder="请选择时间" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script>
import {defineComponent, ref, onMounted, watch} from 'vue';
import {message} from "ant-design-vue";
import {addTrain, deleteTrain, getTrainList, OnTrainSeatList} from "@/api/base/train";
import {pinyin} from "pinyin-pro";
import RailWayPlatFormDrop from "@/components/HomeComp/RailWayPlatFormDrop.vue";

export default defineComponent({
  name: "train-view",
  components: {RailWayPlatFormDrop},
  setup() {


    const TRAIN_TYPE_ARRAY = [
      { type: "G", desc: "高铁"},
      { type: "D", desc: "动车" },
      { type: "K", desc: "快速" }
    ];

    const visible = ref(false);
    let train = ref({
      id: undefined,
      code: undefined,
      type: undefined,
      start: undefined,
      startPinyin: undefined,
      startTime: undefined,
      end: undefined,
      endPinyin: undefined,
      endTime: undefined,
      createTime: undefined,
      updateTime: undefined,
    });
    const trains = ref([]);
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
      dataIndex: 'code',
      key: 'code',
    },
    {
      title: '车次类型',
      dataIndex: 'type',
      key: 'type',
    },
    {
      title: '始发站',
      dataIndex: 'start',
      key: 'start',
    },
    {
      title: '始发站拼音',
      dataIndex: 'startPinyin',
      key: 'startPinyin',
    },
    {
      title: '出发时间',
      dataIndex: 'startTime',
      key: 'startTime',
    },
    {
      title: '终点站',
      dataIndex: 'end',
      key: 'end',
    },
    {
      title: '终点站拼音',
      dataIndex: 'endPinyin',
      key: 'endPinyin',
    },
    {
      title: '到站时间',
      dataIndex: 'endTime',
      key: 'endTime',
    },
    {
      title: '操作',
      dataIndex: 'operation'
    }
    ];
    watch((()=>train.value.start, ()=>{
      if(window.Tool.isNotEmpty(train.value.start)){
        train.value.startPinyin = pinyin(train.value.start,{toneType: 'none'}).replaceAll(" ","")
      }else{
        train.value.startPinyin = ""
      }
    }))
    watch((()=>train.value.end, ()=>{
      if(window.Tool.isNotEmpty(train.value.end)){
        train.value.endPinyin = pinyin(train.value.end,{toneType: 'none'}).replaceAll(" ","")
      }else{
        train.value.endPinyin = ""
      }
    }))
    const onAdd = () => {
      train.value = {};
      visible.value = true;
    };

    const onEdit = (record) => {
      train.value = window.Tool.copy(record);
      visible.value = true;
    };
    const onDelete = async (id) => {
      try {
        const res = await deleteTrain(id);
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
        console.log(train.value)
        const res = await addTrain(train.value);
        if (res.code === 200) {
          visible.value = false;
          message.success(res.message);
          handleQuery(); // 刷新数据
        }
      } catch (error) {
        message.error(error.message);
      } finally {
        loading.value = false;
      }
    };

    const handleQuery = async (param = {page: 1, size: pagination.value.pageSize}) => {
      const res = await getTrainList(param);
      if (res.code === 200) {

        trains.value = res.content.list;
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
    const OnGenSeat = async (record) => {
      console.log(record)
      const res = await OnTrainSeatList({trainCode: record});
      if (res.code === 200) {
        message.success(res.message);
      } else {
        message.error('生成失败');
      }
    };
    onMounted(() => {
      handleQuery({
        page: 1,
        size: pagination.value.pageSize
      });
    });

    return {
      TRAIN_TYPE_ARRAY,
      train,
      visible,
      trains,
      pagination,
      columns,
      handleTableChange,
      handleQuery,
      loading,
      onAdd,
      handleOk,
      onEdit,
      onDelete,
      OnGenSeat
    };
  },
});
</script>
