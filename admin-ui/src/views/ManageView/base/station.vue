<template>
  <p>
    <a-space>
      <a-button type="primary" @click="handleQuery()">刷新</a-button>
      <a-button type="primary" @click="onAdd">新增</a-button>
    </a-space>
  </p>
  <a-table :dataSource="stations"
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
  <a-modal v-model:visible="visible" title="车站" @ok="handleOk"
           ok-text="确认" cancel-text="取消">
    <a-form :model="station" :label-col="{span: 4}" :wrapper-col="{ span: 20 }">
      <a-form-item label="站名">
        <a-input v-model:value="station.name"/>
      </a-form-item>
      <a-form-item label="站名拼音">
        <a-input v-model:value="station.namePinyin" disabled/>
      </a-form-item>
      <a-form-item label="站名首字母" >

        <a-input v-model:value="station.namePy" disabled/>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script>
import {defineComponent, ref, onMounted, watch} from 'vue';
import {message} from "ant-design-vue";
import {addStation,  deleteStation, getStationList} from "@/api/base/station"
import {pinyin} from "pinyin-pro";

export default defineComponent({
  name: "station-view",
  setup() {
    const visible = ref(false);
    let station = ref({
      id: undefined,
      name: undefined,
      namePinyin: undefined,
      namePy: undefined,
      createTime: undefined,
      updateTime: undefined,
    });
    const stations = ref([]);
    // 分页的三个属性名是固定的
    let pagination = ref({
      total: 0,
      current: 1,
      pageSize: 10,
    });
    let loading = ref(false);
    const columns = [
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
        title: '站名拼音首字母',
        dataIndex: 'namePy',
        key: 'namePy',
      },
      {
        title: '操作',
        dataIndex: 'operation'
      }
    ];
    // 监听 station.value.name 的变化
    watch(()=> station.value.name ,()=>{
      // 当 station.value.name 不为空时，更新其拼音表示
      if(window.Tool.isNotEmpty(station.value.name)){
        // 更新 station.value.namePinyin 为全拼，去除空格
        station.value.namePinyin = pinyin(station.value.name, {toneType: 'none'}).replaceAll(" ","")
        // 更新 station.value.namePy 为首字母拼音，转换为大写并去除空格
        station.value.namePy = pinyin(station.value.name, {toneType: 'none', pattern: 'first'}).toUpperCase().replaceAll(" ","");
      }else{
        station.value={}
      }
    })
    const onAdd = () => {
      station.value = {};
      visible.value = true;
    };

    const onEdit = (record) => {
      station.value = window.Tool.copy(record);
      visible.value = true;
    };
    const onDelete = async (id) => {
      try {
        const res = await deleteStation(id);
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
        const res = await addStation(station.value);
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
      const res = await getStationList(param);
      console.log(res)
      if (res.code === 200) {
        stations.value = res.content.list;
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
      station,
      visible,
      stations,
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
