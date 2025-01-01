<template>
  <p>
    <a-space>
      <TrainDropBox v-model="params.station_code" style="width: 200px"/>
      <a-button type="primary" @click="handleQuery()">查找</a-button>
      <a-button type="primary" @click="onAdd">新增</a-button>
    </a-space>
  </p>
  <a-table :dataSource="train_Stations"
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
    </template>
  </a-table>
  <a-modal v-model:visible="visible" title="火车车站" @ok="handleOk"
           ok-text="确认" cancel-text="取消">
    <a-form :model="train_Station" :label-col="{span: 4}" :wrapper-col="{ span: 20 }">
      <a-form-item label="车次编号">
      <TrainDropBox v-model="train_Station.trainCode"/>
      </a-form-item>
      <a-form-item label="站序">
        <a-input v-model:value="train_Station.index" />
      </a-form-item>
      <a-form-item label="站名">
        <RailWayPlatFormDrop v-model="train_Station.name"/>
      </a-form-item>
      <a-form-item label="站名拼音">
        <a-input v-model:value="train_Station.namePinyin" disabled/>
      </a-form-item>
      <a-form-item label="进站时间">
        <a-time-picker v-model:value="train_Station.inTime" valueFormat="HH:mm:ss" placeholder="请选择时间" />
      </a-form-item>
      <a-form-item label="出站时间">
        <a-time-picker v-model:value="train_Station.outTime" valueFormat="HH:mm:ss" placeholder="请选择时间" />
      </a-form-item>
      <a-form-item label="停站时长">
        <a-time-picker v-model:value="train_Station.stopTime" valueFormat="HH:mm:ss" placeholder="请选择时间" disabled/>
      </a-form-item>
      <a-form-item label="里程（公里）">
        <a-input v-model:value="train_Station.km" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script>
import {defineComponent, ref, onMounted, watch} from 'vue';
import {message} from "ant-design-vue";
import {addTrain_Station, deleteTrain_Station, getTrain_StationList} from "@/api/train_station"
import {pinyin} from "pinyin-pro";
import TrainDropBox from "@/components/HomeComp/TrainDropBox.vue";
import RailWayPlatFormDrop from "@/components/HomeComp/RailWayPlatFormDrop.vue";
import dayjs from 'dayjs';

export default defineComponent({
  name: "train-station-view",
  components: {TrainDropBox, RailWayPlatFormDrop},
  setup() {

    const visible = ref(false);
    const params = ref({
      station_code:undefined
    });
    let train_Station = ref({
      id: undefined,
      trainCode: undefined,
      index: undefined,
      name: undefined,
      namePinyin: undefined,
      inTime: undefined,
      outTime: undefined,
      stopTime: undefined,
      km: undefined,
      createTime: undefined,
      updateTime: undefined,
    });
    const train_Stations = ref([]);
    // 自动计算停车时长
    watch(() => train_Station.value.inTime, () => {
      let diff = dayjs(train_Station.value.outTime, 'HH:mm:ss').diff(dayjs(train_Station.value.inTime, 'HH:mm:ss'));
      const send =Math.floor(diff / 1000)
      train_Station.value.stopTime = dayjs('00:00:00', 'HH:mm:ss').second(send).format('HH:mm:ss');
    });

// 自动计算停车时长
    watch(() => train_Station.value.outTime, () => {
      let diff = dayjs(train_Station.value.outTime, 'HH:mm:ss').diff(dayjs(train_Station.value.inTime, 'HH:mm:ss'));
      const send =Math.floor(diff / 1000)
      train_Station.value.stopTime = dayjs('00:00:00', 'HH:mm:ss').second(send).format('HH:mm:ss');
    });
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
      title: '站序',
      dataIndex: 'index',
      key: 'index',
    },
    {
      title: '站名',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '站名拼音',
      dataIndex: 'namePinyin',
      key: 'namePinyin',
    },
    {
      title: '进站时间',
      dataIndex: 'inTime',
      key: 'inTime',
    },
    {
      title: '出站时间',
      dataIndex: 'outTime',
      key: 'outTime',
    },
    {
      title: '停站时长',
      dataIndex: 'stopTime',
      key: 'stopTime',
    },
    {
      title: '里程（公里）',
      dataIndex: 'km',
      key: 'km',
    },
    {
      title: '操作',
      dataIndex: 'operation'
    }
    ];
    watch(() => train_Station.value.name, ()=>{
      if(window.Tool.isNotEmpty(train_Station.value.name)){
        train_Station.value.namePinyin = pinyin(train_Station.value.name,{toneType: 'none'}).replaceAll(" ","")
      }else{
        train_Station.value.namePinyin = ""
        train_Station.value.namePinyin = ""
      }
    })
    const onAdd = () => {
      train_Station.value = {};
      visible.value = true;
    };

    const onEdit = (record) => {
      train_Station.value = window.Tool.copy(record);
      visible.value = true;
    };
    const onDelete = async (id) => {
      try {
        const res = await deleteTrain_Station(id);
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
        const res = await addTrain_Station(train_Station.value);
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

    const handleQuery = async () => {
      const param= {page: 1, size: pagination.value.pageSize,trainCode: params.value.station_code}
      const res = await getTrain_StationList(param);
      if (res.code === 200) {
        train_Stations.value = res.content.list;
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
      train_Station,
      visible,
      train_Stations,
      pagination,
      columns,
      handleTableChange,
      handleQuery,
      loading,
      onAdd,
      handleOk,
      onEdit,
      onDelete,
      params
    };
  },
});
</script>
